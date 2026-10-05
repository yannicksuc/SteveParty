package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.TelescopeRenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** A player's pose at a Telescope's eyepiece (1.21.1 has no render states: kept on the player, filled each frame). */
@Mixin(AbstractClientPlayerEntity.class)
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
