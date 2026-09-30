package fr.lordfinn.steveparty.client.access;

import net.minecraft.util.Arm;

/** A player's Stencil Hammer strike on its render state: ticks into the swing (-1: none) and the arm swinging it. */
public interface StencilHammerRenderState {
    float steveparty$getHammerStrike();

    Arm steveparty$getHammerArm();

    void steveparty$setHammerStrike(float ticks, Arm arm);
}
