package fr.lordfinn.steveparty.blocks.custom.signs;

import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * What the items do on a {@link StencilCanvasBlock}:
 * <ul>
 *     <li>stencil + dye (one in each hand, either way round): paints the stencil's shape in that colour;</li>
 *     <li>stencil alone: engraves the shape, unpainted;</li>
 *     <li>dye alone: repaints the symbol already there;</li>
 *     <li>glow ink sac: the paint glows; sponge: it stops glowing;</li>
 *     <li>brush, held on it: fades the symbol a little every half second, then scrubs it off;</li>
 *     <li>wet sponge: washes the symbol off;</li>
 *     <li>stencil gun: sprays its selected stencil in its selected colour.</li>
 * </ul>
 * On a cut-out panel ({@link StencilCanvasBlock#usesSilhouette()}) a stencil and an axe (one in each hand) cut the
 * board along the stencil, using the axe; a wet sponge gives it back its whole board.
 * Nothing is used up when nothing changes (same symbol, same colour...).
 */
public final class StencilInteractions {
    private StencilInteractions() {
    }

    /** What a brush step did. */
    public enum BrushResult { NOTHING, FADED, GONE }

    /** Called from {@code Block#onUseWithItem}, once per hand. */
    public static ActionResult onUseWithItem(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand) {
        if (!(world.getBlockEntity(pos) instanceof StencilCanvasBlockEntity canvas)) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        boolean silhouette = state.getBlock() instanceof StencilCanvasBlock block && block.usesSilhouette();
        // One interaction per click: handled on the pass of the hand holding the leading item
        Hand acting = isTool(main, silhouette) ? Hand.MAIN_HAND : isTool(off, silhouette) ? Hand.OFF_HAND : null;
        if (acting != hand) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;

        ItemStack leading = acting == Hand.MAIN_HAND ? main : off;
        if (leading.isOf(Items.BRUSH)) {
            if (silhouette || !canvas.hasShape()) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
            if (!world.isClient) brush(canvas, player, acting);
            return ActionResult.SUCCESS;
        }

        Runnable action = resolve(canvas, main, off, player, silhouette);
        if (action == null) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        if (!world.isClient) {
            action.run();
            world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
        }
        return ActionResult.SUCCESS;
    }

    public static boolean isTool(ItemStack stack, boolean silhouette) {
        if (stack.getItem() instanceof StencilItem || stack.isOf(Items.WET_SPONGE)) return true;
        if (silhouette) return stack.getItem() instanceof AxeItem;
        return stack.getItem() instanceof DyeItem || stack.getItem() instanceof StencilGunItem || stack.isOf(Items.BRUSH)
                || stack.isOf(Items.GLOW_INK_SAC) || stack.isOf(Items.SPONGE);
    }

    /**
     * One brush step (at most every {@link StencilCanvasBlockEntity#BRUSH_INTERVAL} ticks while the brush is held):
     * the symbol fades a little, and is scrubbed off after {@link StencilCanvasBlockEntity#MAX_FADE} steps.
     */
    public static BrushResult brush(StencilCanvasBlockEntity canvas, PlayerEntity player, Hand hand) {
        World world = canvas.getWorld();
        if (world == null || !canvas.hasShape() || !canvas.tryBrushStep(world.getTime())) return BrushResult.NOTHING;
        BlockPos pos = canvas.getPos();
        ItemStack brush = player.getStackInHand(hand);
        brush.damage(1, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        world.playSound(null, pos, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        }
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, canvas.getCachedState()));
        if (canvas.getFade() < StencilCanvasBlockEntity.MAX_FADE) {
            canvas.setFade(canvas.getFade() + 1);
            return BrushResult.FADED;
        }
        canvas.clearSymbol();
        return BrushResult.GONE;
    }

    /** @return what the click does (run server side only), or null if it would change nothing. */
    private static @Nullable Runnable resolve(StencilCanvasBlockEntity canvas, ItemStack main, ItemStack off,
                                              PlayerEntity player, boolean silhouette) {
        World world = canvas.getWorld();
        BlockPos pos = canvas.getPos();
        if (world == null) return null;

        ItemStack stencil = main.getItem() instanceof StencilItem ? main : off.getItem() instanceof StencilItem ? off : ItemStack.EMPTY;
        ItemStack leading = isTool(main, silhouette) ? main : off;

        if (silhouette) {
            if (leading.isOf(Items.WET_SPONGE)) {
                if (!canvas.hasShape()) return null;
                return () -> {
                    canvas.clearSymbol();
                    play(world, pos, SoundEvents.BLOCK_WOOD_PLACE, 0.8F);
                };
            }
            // Cutting needs the stencil and an axe
            Hand axeHand = main.getItem() instanceof AxeItem ? Hand.MAIN_HAND : off.getItem() instanceof AxeItem ? Hand.OFF_HAND : null;
            if (stencil.isEmpty() || axeHand == null) return null;
            byte[] shape = StencilItem.getShape(stencil);
            if (sameShape(canvas, shape)) return null;
            return () -> {
                canvas.setSymbol(shape, canvas.getColor());
                player.getStackInHand(axeHand).damage(1, player, axeHand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                play(world, pos, SoundEvents.ITEM_AXE_STRIP, 1.0F);
            };
        }

        if (main.getItem() instanceof StencilGunItem) {
            StencilGunItem.Load load = StencilGunItem.selectedLoad(main);
            if (load.shape() == null) return null;
            if (sameSymbol(canvas, load.shape(), load.color())) return null;
            return () -> {
                canvas.setSymbol(load.shape(), load.color());
                if (load.color() != null && !player.isCreative()) StencilGunItem.consumeDye(main, load.dyeSlot());
                StencilGunItem.playSpray(world, pos, load.color());
            };
        }

        ItemStack dye = main.getItem() instanceof DyeItem ? main : off.getItem() instanceof DyeItem ? off : ItemStack.EMPTY;

        if (!stencil.isEmpty()) {
            byte[] shape = StencilItem.getShape(stencil);
            DyeColor color = dye.isEmpty() ? null : ((DyeItem) dye.getItem()).getColor();
            if (sameSymbol(canvas, shape, color)) return null;
            return () -> {
                canvas.setSymbol(shape, color);
                play(world, pos, SoundEvents.BLOCK_METAL_PLACE, 1.0F);
                if (color != null) {
                    play(world, pos, SoundEvents.ITEM_DYE_USE, 1.0F);
                    if (!player.isCreative()) dye.decrement(1);
                }
            };
        }
        if (!dye.isEmpty()) {
            DyeColor color = ((DyeItem) dye.getItem()).getColor();
            if (!canvas.hasShape() || (canvas.getColor() == color && canvas.getFade() == 0)) return null;
            return () -> {
                canvas.setColor(color);
                play(world, pos, SoundEvents.ITEM_DYE_USE, 1.0F);
                if (!player.isCreative()) dye.decrement(1);
            };
        }
        if (leading.isOf(Items.GLOW_INK_SAC)) {
            if (canvas.isGlowing()) return null;
            return () -> {
                canvas.setGlowing(true);
                play(world, pos, SoundEvents.ITEM_GLOW_INK_SAC_USE, 1.0F);
                if (!player.isCreative()) leading.decrement(1);
            };
        }
        if (leading.isOf(Items.SPONGE)) {
            if (!canvas.isGlowing()) return null;
            return () -> {
                canvas.setGlowing(false);
                play(world, pos, SoundEvents.BLOCK_SPONGE_ABSORB, 1.0F);
            };
        }
        if (leading.isOf(Items.WET_SPONGE)) {
            if (!canvas.hasShape() && !canvas.isGlowing()) return null;
            return () -> {
                canvas.clearSymbol();
                play(world, pos, SoundEvents.ENTITY_GENERIC_SPLASH, 1.4F);
                if (world instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
                }
            };
        }
        return null;
    }

    private static boolean sameShape(StencilCanvasBlockEntity canvas, byte[] shape) {
        return canvas.hasShape() && Arrays.equals(canvas.getShape(), shape);
    }

    private static boolean sameSymbol(StencilCanvasBlockEntity canvas, byte[] shape, @Nullable DyeColor color) {
        return sameShape(canvas, shape) && Objects.equals(canvas.getColor(), color) && canvas.getFade() == 0;
    }

    private static void play(World world, BlockPos pos, SoundEvent sound, float pitch) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, pitch);
    }
}
