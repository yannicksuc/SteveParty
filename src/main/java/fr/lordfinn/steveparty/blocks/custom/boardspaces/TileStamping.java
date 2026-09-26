package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilHammerStrike;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Stamping a new look on a tile ({@link TileStampComponent}), like painting a stencil sign:
 * <ul>
 *     <li>a stencil and a dye (one in each hand), or the Stencil Hammer (its selected stencil and dye, with its
 *     strike): the whole face takes the dye's colour, the pattern drawn on it in a darker shade;</li>
 *     <li>a wet sponge washes the look off.</li>
 * </ul>
 * Where the look goes: on the tile itself while it holds no cartridge; on its (active) cartridge otherwise. The
 * cartridge prevails: a tile shows its cartridge's look (or its plain cartridge face), and its own look again once the
 * cartridge is taken out; the look travels with the cartridge. Nothing is used up when nothing changes.
 * <p>
 * Decided the same way on both sides (the client has the tile's cartridges), so that the client predicts it.
 */
public final class TileStamping {
    private TileStamping() {
    }

    /** The look a tile shows: its cartridge's when it holds one, else its own (null: its plain face). */
    public static @Nullable TileStampComponent displayedStamp(BoardSpaceBlockEntity tile, ItemStack cartridge) {
        if (cartridge != null && !cartridge.isEmpty()) return cartridge.get(ModComponents.TILE_STAMP);
        return tile.getStamp();
    }

    /**
     * Called for a click with items on a tile (main hand pass, before the tile's role gets the click).
     * @return the result, or null when the click doesn't stamp nor wash the tile (the tile handles it as usual)
     */
    public static @Nullable ActionResult onUseWithItem(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                       @Nullable BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND || !(state.getBlock() instanceof ATileBlock)) return null;
        if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tile)) return null;
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        TileStampComponent current = displayedStamp(tile, cartridge);

        // The Stencil Hammer (from either hand): its selected stencil and dye, struck
        Hand hammerHand = main.getItem() instanceof StencilGunItem ? Hand.MAIN_HAND : off.getItem() instanceof StencilGunItem ? Hand.OFF_HAND : null;
        if (hammerHand != null) {
            ItemStack gun = player.getStackInHand(hammerHand);
            StencilGunItem.Load load = StencilGunItem.selectedLoad(gun);
            // Stamping needs a pattern and a colour: an engraving hammer does nothing here
            if (load.shape() == null || load.color() == null) return null;
            if (StencilHammerStrike.isCoolingDown(player, hammerHand)) return ActionResult.CONSUME;
            if (current != null && current.sameAs(load.shape(), load.color())) return ActionResult.CONSUME;
            if (!world.isClient) {
                apply(tile, cartridge, TileStampComponent.of(load.shape(), load.color()));
                if (!player.isCreative()) StencilGunItem.consumeDye(gun, load.dyeSlot());
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
            }
            Vec3d hitPos = hit != null ? hit.getPos() : Vec3d.ofCenter(pos);
            Direction side = hit != null ? hit.getSide() : Direction.UP;
            // The swing, the thud and the splash of paint (played by the clients)
            StencilHammerStrike.strike(world, player, hammerHand, pos, hitPos, side, load.color());
            return ActionResult.CONSUME;
        }

        // A stencil and a dye, one in each hand
        ItemStack stencil = main.getItem() instanceof StencilItem ? main : off.getItem() instanceof StencilItem ? off : ItemStack.EMPTY;
        ItemStack dye = main.getItem() instanceof DyeItem ? main : off.getItem() instanceof DyeItem ? off : ItemStack.EMPTY;
        if (!stencil.isEmpty() && !dye.isEmpty()) {
            byte[] shape = StencilItem.getShape(stencil);
            if (StencilShape.isBlank(shape)) return ActionResult.CONSUME;
            DyeColor color = ((DyeItem) dye.getItem()).getColor();
            if (current != null && current.sameAs(shape, color)) return ActionResult.CONSUME;
            if (!world.isClient) {
                apply(tile, cartridge, TileStampComponent.of(shape, color));
                play(world, pos, SoundEvents.BLOCK_METAL_PLACE, 1.0F);
                play(world, pos, SoundEvents.ITEM_DYE_USE, 1.0F);
                if (!player.isCreative()) dye.decrement(1);
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
            }
            return ActionResult.SUCCESS;
        }

        // A wet sponge washes the look off
        if (main.isOf(Items.WET_SPONGE) || off.isOf(Items.WET_SPONGE)) {
            if (current == null) return null;
            if (!world.isClient) {
                apply(tile, cartridge, null);
                play(world, pos, SoundEvents.ENTITY_GENERIC_SPLASH, 1.4F);
                if (world instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
                }
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
            }
            return ActionResult.SUCCESS;
        }
        return null;
    }

    /** Puts {@code stamp} (null: none) where it belongs: on the cartridge if the tile holds one, else on the tile. */
    public static void apply(BoardSpaceBlockEntity tile, ItemStack cartridge, @Nullable TileStampComponent stamp) {
        if (cartridge != null && !cartridge.isEmpty()) {
            if (Objects.equals(cartridge.get(ModComponents.TILE_STAMP), stamp)) return;
            if (stamp == null) cartridge.remove(ModComponents.TILE_STAMP);
            else cartridge.set(ModComponents.TILE_STAMP, stamp);
            tile.update(); // saves and sends the cartridge to the clients
        } else {
            tile.setStamp(stamp);
        }
    }

    private static void play(World world, BlockPos pos, SoundEvent sound, float pitch) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, pitch);
    }
}
