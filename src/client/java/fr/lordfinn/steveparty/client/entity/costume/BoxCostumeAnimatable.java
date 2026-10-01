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
 * animations: "closed" when the wearer hides (the box drops to the ground and closes, then "hidden" holds it), "open"
 * when they come out, then "walk" or "idle" (the box at the waist). Only the box bones are drawn.
 */
public class BoxCostumeAnimatable implements GeoAnimatable {
    static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlay("open");
    static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    static final RawAnimation CLOSED_ANIM = RawAnimation.begin().thenPlayAndHold("closed");
    static final RawAnimation HIDDEN_ANIM = RawAnimation.begin().thenPlayAndHold("hidden");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    /** Hiding in the box (sneaking with the costume on). */
    boolean hidden = false;
    /** Walking (with hysteresis, see BoxCostumeClient). */
    boolean walking = false;
    /** Ticks since the wearer started hiding (0 when out). */
    int hiddenTicks = 0;
    /** Client ticks before the sounds of the box closing / opening, -1 when idle. */
    int closeSoundTicks = -1, placeSoundTicks = -1, openSoundTicks = -1;
    int walkSwitchTicks = 0;
    BlockState block = Blocks.GOLD_BLOCK.getDefaultState();

    public BlockState getBlock() {
        return block;
    }

    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Box", 3, this::animate));
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
        // "open" then "idle" are chained here rather than queued (see HidingTraderEntity#idleAnimController)
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
