package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.TelescopeRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** A player's pose at a Telescope's eyepiece, kept on his render state. */
@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateTelescopeMixin implements TelescopeRenderState {
    @Unique
    private float steveparty$telescopeEase, steveparty$telescopeBend, steveparty$telescopePitch;
    @Unique
    private double steveparty$telescopeDx, steveparty$telescopeDy, steveparty$telescopeDz;

    @Override
    public void steveparty$setTelescope(float ease, float bend, double dx, double dy, double dz, float aimPitch) {
        this.steveparty$telescopeEase = ease;
        this.steveparty$telescopeBend = bend;
        this.steveparty$telescopeDx = dx;
        this.steveparty$telescopeDy = dy;
        this.steveparty$telescopeDz = dz;
        this.steveparty$telescopePitch = aimPitch;
    }

    @Override
    public float steveparty$telescopeEase() {
        return steveparty$telescopeEase;
    }

    @Override
    public float steveparty$telescopeBend() {
        return steveparty$telescopeBend;
    }

    @Override
    public double steveparty$telescopeDx() {
        return steveparty$telescopeDx;
    }

    @Override
    public double steveparty$telescopeDy() {
        return steveparty$telescopeDy;
    }

    @Override
    public double steveparty$telescopeDz() {
        return steveparty$telescopeDz;
    }

    @Override
    public float steveparty$telescopePitch() {
        return steveparty$telescopePitch;
    }
}
