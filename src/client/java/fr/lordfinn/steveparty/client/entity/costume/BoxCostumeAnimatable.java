package fr.lordfinn.steveparty.client.entity.costume;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
 * Client: the animated box of one Box Costume wearer (or of the item icon), played with the Hiding Trader's own
 * animations: "closed" when the wearer hides (the box drops to the ground and closes, then "hidden" holds it), a quick
 * pop straight back up when they come out, into "walk" or "idle" (the box up to the chin). Only the box bones are drawn.
 */
public class BoxCostumeAnimatable implements GeoAnimatable {
    static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    static final RawAnimation CLOSED_ANIM = RawAnimation.begin().thenPlayAndHold("closed");
    static final RawAnimation HIDDEN_ANIM = RawAnimation.begin().thenPlayAndHold("hidden");

    /** Ticks the box takes to pop up to the wearer's chin, flaps flipping open, when he comes out. */
    static final int POP_TICKS = 4;

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
    /** As last drawn: how far up the wearer's body the box is (0 on the ground, 1 worn) and how open its arm holes are (0-1). */
    float lift = 1.0F, armHole = 1.0F;

    public BlockState getBlock() {
        return block;
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
        controllers.add(new AnimationController<>(this, "Box", POP_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<BoxCostumeAnimatable> event) {
        AnimationController<BoxCostumeAnimatable> controller = event.getController();
        RawAnimation current = controller.getCurrentRawAnimation();
        if (hidden) {
            if (current == CLOSED_ANIM || current == HIDDEN_ANIM) return event.setAndContinue(current);
            // Already hidden when first seen: closed at once, without the closing
            return event.setAndContinue(current == null ? HIDDEN_ANIM : CLOSED_ANIM);
        }
        // Coming out: no "open" (the merchant's box stays 0.85 s on the ground before it rises, the wearer's torso would
        // show above it): the controller's short transition pops the box straight up, flaps flipping open
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
