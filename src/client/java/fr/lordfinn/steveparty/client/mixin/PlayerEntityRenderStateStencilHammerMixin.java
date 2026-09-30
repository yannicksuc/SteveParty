package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.StencilHammerRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateStencilHammerMixin implements StencilHammerRenderState {
    @Unique
    private float steveparty$hammerStrike = -1;
    @Unique
    private Arm steveparty$hammerArm = Arm.RIGHT;

    @Override
    public float steveparty$getHammerStrike() {
        return steveparty$hammerStrike;
    }

    @Override
    public Arm steveparty$getHammerArm() {
        return steveparty$hammerArm;
    }

    @Override
    public void steveparty$setHammerStrike(float ticks, Arm arm) {
        this.steveparty$hammerStrike = ticks;
        this.steveparty$hammerArm = arm;
    }
}
