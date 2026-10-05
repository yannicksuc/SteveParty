package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import fr.lordfinn.steveparty.client.access.SquishStretchState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 1.21.1 has no render states: what the renderer mixins work out for a living entity at each frame is kept on the
 * entity itself (filled at the start of LivingEntityRenderer#render, read while it is drawn).
 */
@Mixin(LivingEntity.class)
public class LivingEntityRenderStateMixin implements SmoothFlipState, SquishStretchState {
    @Unique
    private float flipProgress = 0.0F;
    @Unique
    private float steveparty$stretch = 1.0F;

    @Override
    public float steveparty$getStretch() {
        return steveparty$stretch;
    }

    @Override
    public void steveparty$setStretch(float stretch) {
        this.steveparty$stretch = stretch;
    }

    @Override
    public float getFlipProgress() {
        return flipProgress;
    }

    @Override
    public void setFlipProgress(float progress) {
        this.flipProgress = progress;
    }
}
