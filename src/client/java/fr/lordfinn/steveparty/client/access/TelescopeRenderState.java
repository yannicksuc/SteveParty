package fr.lordfinn.steveparty.client.access;

/** On a player's render state: how he is drawn while he looks through a Telescope (TelescopePoses). */
public interface TelescopeRenderState {
    /**
     * @param ease     0 not at the telescope .. 1 at its eyepiece
     * @param bend     0 standing .. 1 bent as low as sneaking
     * @param dx       his drawn feet, moved by this much (blocks), ease included
     * @param aimPitch the tube's tilt (degrees, negative: up); NaN: his right hand is not on the tube (it holds something)
     */
    void steveparty$setTelescope(float ease, float bend, double dx, double dy, double dz, float aimPitch);

    float steveparty$telescopeEase();

    float steveparty$telescopeBend();

    double steveparty$telescopeDx();

    double steveparty$telescopeDy();

    double steveparty$telescopeDz();

    float steveparty$telescopePitch();
}
