package fr.lordfinn.steveparty.blocks.custom;

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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Tagging the banner on the front of a podium, like stamping a tile: the Stencil Hammer (its selected stencil and
 * dye, with its strike) or a stencil and a dye (one in each hand) draw the pattern on the banner in the dye's colour;
 * a wet sponge washes it off. Only a click on the front face of the podium tags it. The look is kept by the column's
 * bottom block (see {@link PodiumBlockEntity#getBannerStamp}).
 * <p>
 * Decided the same way on both sides so that the client predicts it.
 */
public final class PodiumBanner {
    private PodiumBanner() {
    }

    /** @return the result, or null when the click doesn't tag nor wash the banner */
    public static @Nullable ActionResult onUseWithItem(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                       @Nullable BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND || hit == null || !PodiumBlock.isPodium(state) || hit.getSide() != state.get(PodiumBlock.FACING))
            return null;
        PodiumBlockEntity podium = PodiumBlock.master(world, pos);
        if (podium == null) return null;
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        TileStampComponent current = podium.getBannerStamp();

        Hand hammerHand = main.getItem() instanceof StencilGunItem ? Hand.MAIN_HAND : off.getItem() instanceof StencilGunItem ? Hand.OFF_HAND : null;
        if (hammerHand != null) {
            ItemStack gun = player.getStackInHand(hammerHand);
            StencilGunItem.Load load = StencilGunItem.selectedLoad(gun);
            if (load.shape() == null || load.color() == null) return null;
            if (StencilHammerStrike.isCoolingDown(player, hammerHand)) return ActionResult.CONSUME;
            if (current != null && current.sameAs(load.shape(), load.color())) return ActionResult.CONSUME;
            if (!world.isClient) {
                podium.setBannerStamp(TileStampComponent.of(load.shape(), load.color()));
                if (!player.isCreative()) StencilGunItem.consumeDye(gun, load.dyeSlot());
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
            }
            StencilHammerStrike.strike(world, player, hammerHand, pos, hit.getPos(), hit.getSide(), load.color());
            return ActionResult.CONSUME;
        }

        ItemStack stencil = main.getItem() instanceof StencilItem ? main : off.getItem() instanceof StencilItem ? off : ItemStack.EMPTY;
        ItemStack dye = main.getItem() instanceof DyeItem ? main : off.getItem() instanceof DyeItem ? off : ItemStack.EMPTY;
        if (!stencil.isEmpty() && !dye.isEmpty()) {
            byte[] shape = StencilItem.getShape(stencil);
            if (StencilShape.isBlank(shape)) return ActionResult.CONSUME;
            DyeColor color = ((DyeItem) dye.getItem()).getColor();
            if (current != null && current.sameAs(shape, color)) return ActionResult.CONSUME;
            if (!world.isClient) {
                podium.setBannerStamp(TileStampComponent.of(shape, color));
                play(world, hit.getPos(), SoundEvents.ITEM_DYE_USE);
                if (!player.isCreative()) dye.decrement(1);
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
            }
            return ActionResult.SUCCESS;
        }

        if (main.isOf(Items.WET_SPONGE) || off.isOf(Items.WET_SPONGE)) {
            if (current == null) return null;
            if (!world.isClient) {
                podium.setBannerStamp(null);
                play(world, hit.getPos(), SoundEvents.ENTITY_GENERIC_SPLASH);
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
            }
            return ActionResult.SUCCESS;
        }
        return null;
    }

    private static void play(World world, Vec3d at, SoundEvent sound) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
