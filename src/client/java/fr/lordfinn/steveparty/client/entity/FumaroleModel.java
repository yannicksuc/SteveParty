package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.render.geo.GeoBones;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleEntity;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleHead;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fumarole's model (geo/entity/fumarole.geo.json, the nether turtle export of SteveParty-Workshop): its
 * animations all carry the S neck pose. On top of them:
 * <ul>
 *     <li><b>The tank's lava</b>: the {@code tank_lava} bone raised {@code 32 x buckets / 27} px (eased on the
 *     client); hidden from the model pass, FumaroleRenderer draws its own animated lava there.</li>
 *     <li><b>The vents</b> ({@code vent<suffix>}, one a head): hidden too, FumaroleRenderer draws each head's state
 *     texture there, full bright.</li>
 *     <li><b>The turrets</b>: each head's drawn aim (FumaroleEntity#clientYaw, #clientPitch: its yaw from the body,
 *     both from its rest, FumaroleHead#restYaw, #restPitch) spread over its last
 *     {@link #AIM_SEGMENTS} neck segments ({@code neck<suffix>_NN}) and its head ({@code head<suffix>}).</li>
 * </ul>
 */
public class FumaroleModel extends DefaultedEntityGeoModel<FumaroleEntity> {
    /** The neck segments of a head that turn for its aim, with the head itself. */
    static final int AIM_SEGMENTS = 6;
    private static final int MAX_SEGMENTS = 20;
    private static final float MAX_PITCH = 50;
    /** The tank's lava rises this many pixels from empty to full. */
    static final float TANK_RISE = 32;
    /** Each head's turning bones, found once the model is baked (null until then). */
    private GeoBone[][] aimBones;

    public FumaroleModel() {
        super(Steveparty.id("fumarole"));
    }

    private GeoBone[][] aimBones() {
        if (aimBones != null) return aimBones;
        GeoBone[][] bones = new GeoBone[FumaroleEntity.HEADS.length][];
        for (FumaroleHead head : FumaroleEntity.HEADS) {
            List<GeoBone> chain = new ArrayList<>();
            for (int i = 1; i <= MAX_SEGMENTS; i++) {
                GeoBone segment = getAnimationProcessor().getBone(String.format("neck%s_%02d", head.suffix(), i));
                if (segment != null) chain.add(segment);
            }
            List<GeoBone> turning = new ArrayList<>(chain.subList(Math.max(0, chain.size() - AIM_SEGMENTS), chain.size()));
            GeoBone skull = getAnimationProcessor().getBone(head.name("head"));
            if (skull != null) turning.add(skull);
            bones[head.index()] = turning.toArray(new GeoBone[0]);
        }
        if (getAnimationProcessor().getRegisteredBones().isEmpty()) return bones; // not baked yet: look again next frame
        return aimBones = bones;
    }

    @Override
    public void setCustomAnimations(FumaroleEntity fumarole, long instanceId, AnimationState<FumaroleEntity> animationState) {
        super.setCustomAnimations(fumarole, instanceId, animationState);
        float partial = animationState.getPartialTick();
        GeoBone lava = getAnimationProcessor().getBone("tank_lava");
        if (lava != null) {
            lava.setHidden(true);
            lava.setPosY(TANK_RISE * fumarole.tankLevel(partial) / FumaroleEntity.TANK_MAX);
        }
        for (FumaroleHead head : FumaroleEntity.HEADS) {
            GeoBones.hide(getAnimationProcessor(), head.name("vent"), true);
        }
        if (fumarole.deathTime > 0) return;
        GeoBone body = getAnimationProcessor().getBone("body");
        if (body != null) body.setRotZ(body.getRotZ() + fumarole.moods.bodyRoll);
        GeoBone shell = getAnimationProcessor().getBone("shell");
        if (shell != null) shell.setRotZ(shell.getRotZ() + fumarole.moods.shellRoll);
        GeoBone[][] bones = aimBones();
        for (FumaroleHead head : FumaroleEntity.HEADS) {
            int i = head.index();
            if (fumarole.isPumping() && i == FumaroleEntity.PUMP_HEAD) continue;
            GeoBone[] turning = bones[i];
            if (turning.length == 0) continue;
            float yaw = MathHelper.lerp(partial, fumarole.prevClientYaw[i], fumarole.clientYaw[i]) - head.restYaw();
            float pitch = MathHelper.clamp(MathHelper.lerp(partial, fumarole.prevClientPitch[i], fumarole.clientPitch[i])
                    - head.restPitch(), -MAX_PITCH, MAX_PITCH);
            float yawShare = -yaw * MathHelper.RADIANS_PER_DEGREE / turning.length;
            float pitchShare = -pitch * MathHelper.RADIANS_PER_DEGREE / turning.length;
            for (GeoBone bone : turning) {
                bone.setRotY(bone.getRotY() + yawShare);
                bone.setRotX(bone.getRotX() + pitchShare);
            }
            GeoBone skull = turning[turning.length - 1];
            skull.setRotZ(skull.getRotZ() + fumarole.moods.roll[i]);
        }
    }
}
