package fr.lordfinn.steveparty.client.entity.costume;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Client: the animated box of one Box Costume wearer (or of the item icon), on the Hiding Trader's model: its own
 * "costume_close" when the wearer hides (one continuous motion: the box drops at once, the flaps fold over it, then it
 * holds as a block), "costume_open" when they stand up (flaps flipping open, the box rising straight back), then the
 * merchant's "walk" or "idle" (the box up to the chin). Only the box bones are drawn; the wearer's body follows the
 * box's height (see BoxCostumeClient#bodySquash).
 */
public class BoxCostumeAnimatable implements GeoAnimatable {
    static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    static final RawAnimation CLOSED_ANIM = RawAnimation.begin().thenPlayAndHold("costume_close");
    static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlay("costume_open");
    static final RawAnimation HIDDEN_ANIM = RawAnimation.begin().thenPlayAndHold("hidden");

    /** Short blend between animations: hiding and standing up start at once. */
    static final int TRANSITION_TICKS = 2;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    /** Hiding in the box (sneaking with the costume on). */
    boolean hidden = false;
    /** Walking (with hysteresis, see BoxCostumeClient). */
    boolean walking = false;
    /** Ticks since the wearer started hiding (0 when out). */
    int hiddenTicks = 0;
    /** Yaw of the box closed on the ground: the quarter turn nearest to the wearer's body yaw when it closed. */
    float hiddenYaw = 0.0F;
    /** Client ticks before the sounds of the box closing / opening, -1 when idle. */
    int closeSoundTicks = -1, placeSoundTicks = -1, openSoundTicks = -1;
    int walkSwitchTicks = 0;
    BlockState block = Blocks.GOLD_BLOCK.getDefaultState();
    @Nullable
    BlockPos pos = null;
    /** As last drawn: how far up the wearer's body the box is (0 on the ground, 1 worn) and how open its arm holes are (0-1). */
    float lift = 1.0F, armHole = 1.0F;

    public BlockState getBlock() {
        return block;
    }

    /** @return where the wearer is (biome tints of the box), null for the item icon. */
    @Nullable
    public BlockPos getPos() {
        return pos;
    }

    public boolean isHidden() {
        return hidden;
    }

    public float getLift() {
        return lift;
    }

    public float getArmHole() {
        return armHole;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Box", TRANSITION_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<BoxCostumeAnimatable> event) {
        AnimationController<BoxCostumeAnimatable> controller = event.getController();
        RawAnimation current = controller.getCurrentRawAnimation();
        if (hidden) {
            if (current == CLOSED_ANIM || current == HIDDEN_ANIM) return event.setAndContinue(current);
            // Already hidden when first seen: closed at once, without the closing
            return event.setAndContinue(current == null ? HIDDEN_ANIM : CLOSED_ANIM);
        }
        if (current == null) return event.setAndContinue(walking ? WALK_ANIM : IDLE_ANIM);
        // Standing up, then walk / idle: chained here rather than queued (see HidingTraderEntity#idleAnimController)
        if (current == CLOSED_ANIM || current == HIDDEN_ANIM || (current == OPEN_ANIM && !controller.hasAnimationFinished())) {
            return event.setAndContinue(OPEN_ANIM);
        }
        return event.setAndContinue(walking ? WALK_ANIM : IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return RenderUtil.getCurrentTick();
    }
}
