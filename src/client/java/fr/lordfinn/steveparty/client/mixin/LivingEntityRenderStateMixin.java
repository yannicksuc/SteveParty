package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements SmoothFlipState {
    private float flipProgress = 0.0F;

    @Override
    public float getFlipProgress() {
        return flipProgress;
    }

    @Override
    public void setFlipProgress(float progress) {
        this.flipProgress = progress;
    }
}
