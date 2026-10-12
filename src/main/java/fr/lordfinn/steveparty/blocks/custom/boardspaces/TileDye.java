package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.api.board.BoardSpaceRoles;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The dye of a board space: a dye used on a tile or a check point colours its (active) cartridge
 * ({@link ModComponents#COLOR}, see ABoardSpaceBehavior#onUseWithItem), the colour travelling with the cartridge. A
 * sponge, a wet sponge or a brush takes it off: the cartridge's own colour again (its item's, or its
 * {@link CartridgeItem#tileColor}), nothing used (the brush wears as when it brushes). A wet sponge or a brush first
 * takes a tile's stamped look off (TileStamping), its dye on the next click. Nothing happens when there is no dye.
 * <p>
 * Roles whose colour is worked out from their settings (a shop, a star space, a teleport tile...) keep no dye
 * ({@link ABoardSpaceBehavior#keepsDye}). Decided the same way on both sides (the client has the cartridges), so
 * that the client predicts it.
 */
public final class TileDye {
    /**
     * A check point whose cartridge is dyed: its crystal drawn in the dye's colour (a grey model tinted by its colour
     * provider). Kept in step with the cartridge by the server ({@link #syncState}), false by default (and so in
     * worlds saved before).
     */
    public static final BooleanProperty DYED = BooleanProperty.of("dyed");

    private TileDye() {
    }

    /**
     * The colour {@code cartridge} has of its own, undyed: its item's (a Stop cartridge's anthracite...), else its tile
     * colour; null for none (the plain white space).
     */
    public static @Nullable Integer baseColor(ItemStack cartridge) {
        Integer own = cartridge.getItem().getComponents().get(ModComponents.COLOR);
        if (own != null) return own;
        if (cartridge.getItem() instanceof CartridgeItem item && item.tileColor() >= 0) return item.tileColor();
        return null;
    }

    /** Whether {@code cartridge} carries a dye: a colour other than its own, on a role that keeps it. */
    public static boolean isDyed(ItemStack cartridge) {
        if (cartridge == null || cartridge.isEmpty() || !(cartridge.getItem() instanceof CartridgeItem)) return false;
        Integer color = cartridge.get(ModComponents.COLOR);
        if (color == null || Objects.equals(color, baseColor(cartridge))) return false;
        return BoardSpaceRoles.behaviorOf(cartridge).keepsDye();
    }

    /** Whether the board space {@code space} shows a dye (its active cartridge's). */
    public static boolean isDyed(BoardSpaceBlockEntity space) {
        return isDyed(space.getActiveCartridgeItemStack());
    }

    /** Server side: the block state of a check point follows its cartridge's dye ({@link #DYED}). */
    public static void syncState(BoardSpaceBlockEntity space) {
        World world = space.getWorld();
        if (world == null || world.isClient) return;
        BlockState state = space.getCachedState();
        if (!state.contains(DYED) || !world.getBlockState(space.getPos()).isOf(state.getBlock())) return;
        boolean dyed = isDyed(space);
        if (state.get(DYED) != dyed) world.setBlockState(space.getPos(), state.with(DYED, dyed), Block.NOTIFY_ALL);
    }

    /**
     * Called for a click with an item on a board space (after TileStamping, which takes a stamped look off first).
     * @return the result, or null when {@code stack} takes no dye off (the space handles the click as usual)
     */
    public static @Nullable ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand) {
        boolean brush = stack.isOf(Items.BRUSH);
        if (!brush && !stack.isOf(Items.SPONGE) && !stack.isOf(Items.WET_SPONGE)) return null;
        if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space) || !isDyed(space)) return null;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        int dye = Objects.requireNonNull(cartridge.get(ModComponents.COLOR));
        clear(space);
        if (brush) {
            stack.damage(1, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            effects(world, state, pos, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, 1.0F, ParticleTypes.WHITE_ASH);
        } else if (stack.isOf(Items.WET_SPONGE)) {
            effects(world, state, pos, SoundEvents.ENTITY_GENERIC_SPLASH, 1.4F, ParticleTypes.SPLASH);
        } else {
            // The dry sponge soaks the colour up
            effects(world, state, pos, SoundEvents.BLOCK_SPONGE_ABSORB, 1.0F,
                    new DustParticleEffect(Vec3d.unpackRgb(dye).toVector3f(), 1.0F));
        }
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
        return ActionResult.SUCCESS;
    }

    /** Server side: the active cartridge of {@code space} back to its own colour, sent to the clients. */
    public static void clear(BoardSpaceBlockEntity space) {
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (cartridge.isEmpty()) return;
        Integer base = baseColor(cartridge);
        if (base != null) ABoardSpaceBehavior.setColor(space, base);
        else {
            cartridge.remove(ModComponents.COLOR);
            syncState(space); // a check point shows its own blue again
        }
        space.update(); // saves, sends the cartridge (the clients redraw the space) and follows with the check point's state
    }

    private static void effects(World world, BlockState state, BlockPos pos, SoundEvent sound, float pitch, ParticleEffect particle) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, pitch);
        if (world instanceof ServerWorld serverWorld) {
            Vec3d at = BoardSpaces.effectsPos(serverWorld, state, pos);
            double spread = state.getBlock() instanceof ATileBlock ? 0.3 : 0.15;
            serverWorld.spawnParticles(particle, at.x, at.y, at.z, 10, spread, 0.1, spread, 0.05);
        }
    }
}
