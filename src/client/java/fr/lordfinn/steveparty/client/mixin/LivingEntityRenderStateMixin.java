package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import fr.lordfinn.steveparty.client.access.SquishStretchState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements SmoothFlipState, SquishStretchState {
    private float flipProgress = 0.0F;
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
