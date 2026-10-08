package fr.lordfinn.steveparty.entities.custom.pawn;

import net.minecraft.text.Text;

/**
 * Poses of a player pawn's statue, cycled with a right click. Angles in degrees, in the biped model's frame: a
 * negative arm or leg pitch swings it forward (-90: straight ahead, -180: straight up), a positive arm roll lifts the
 * right arm sideways (the left arm mirrors with a negative roll), a positive head pitch looks down. The head is wider
 * than the shoulders: an arm is raised overhead by its roll (it stays out of the head), not by its pitch.
 * <p>
 * Saved by {@link #id()} (the order of the cycle can change without breaking saved pawns).
 */
public enum PlayerPawnPose {
    //            head              right arm          left arm            right leg      left leg
    STAND("stand", 0, 0, 0, 0, 0, 4, 0, 0, -4, 0, 0, 0, 0),
    WAVE("wave", 0, -10, -6, 0, -10, 140, 0, 0, -6, 0, 0, 0, 0),
    VICTORY("victory", -15, 0, 0, 0, 0, 150, 0, 0, -150, 0, 4, 0, -4),
    FIST_PUMP("fist_pump", -10, 0, 0, -10, 0, 150, 0, 0, -8, 0, 0, 0, 0),
    DICE_PUNCH("dice_punch", -30, 0, 0, 0, 0, 152, 20, 0, -25, 0, 0, -45, 0),
    TROPHY("trophy", -20, 0, 0, -25, 0, 145, -25, 0, -145, 0, 0, 0, 0),
    POINT("point", 0, -15, 0, -90, -15, 0, 0, 0, -6, 0, 0, 0, 0),
    SALUTE("salute", 0, 0, 0, -130, 40, 30, 0, 0, -4, 0, 0, 0, 0),
    THINKER("thinker", 18, 10, 0, -115, 40, 0, -55, -35, 0, 0, 0, 0, 0),
    SHRUG("shrug", 0, 0, 12, -40, 0, 40, -40, 0, -40, 0, 0, 0, 0),
    T_POSE("t_pose", 0, 0, 0, 0, 0, 90, 0, 0, -90, 0, 0, 0, 0),
    FLEX("flex", -8, 0, 0, -20, 0, 125, -20, 0, -125, 4, 4, 0, -4),
    RUN("run", -5, 0, 0, 55, 0, 6, -60, 0, -6, -45, 0, 40, 0),
    KICK("kick", 5, 0, 0, 0, 0, 30, 20, 0, -30, -75, 0, 0, 0),
    ZOMBIE("zombie", 0, 0, 8, -90, 0, 0, -90, 0, 0, 0, 0, 0, 0),
    DAB("dab", 35, -35, 0, -105, 65, 0, 0, 0, -125, 0, 0, 0, 0),
    SULK("sulk", 40, 0, 0, 10, 0, 2, 10, 0, -2, 0, 0, 0, 0),
    BALLERINA("ballerina", -10, 0, 0, 0, 0, 150, 0, 0, -95, 0, 0, -30, -25);

    private static final PlayerPawnPose[] VALUES = values();

    private final String id;
    public final float headPitch, headYaw, headRoll;
    public final float rightArmPitch, rightArmYaw, rightArmRoll;
    public final float leftArmPitch, leftArmYaw, leftArmRoll;
    public final float rightLegPitch, rightLegRoll;
    public final float leftLegPitch, leftLegRoll;

    PlayerPawnPose(String id, float headPitch, float headYaw, float headRoll,
                   float rightArmPitch, float rightArmYaw, float rightArmRoll,
                   float leftArmPitch, float leftArmYaw, float leftArmRoll,
                   float rightLegPitch, float rightLegRoll, float leftLegPitch, float leftLegRoll) {
        this.id = id;
        this.headPitch = headPitch;
        this.headYaw = headYaw;
        this.headRoll = headRoll;
        this.rightArmPitch = rightArmPitch;
        this.rightArmYaw = rightArmYaw;
        this.rightArmRoll = rightArmRoll;
        this.leftArmPitch = leftArmPitch;
        this.leftArmYaw = leftArmYaw;
        this.leftArmRoll = leftArmRoll;
        this.rightLegPitch = rightLegPitch;
        this.rightLegRoll = rightLegRoll;
        this.leftLegPitch = leftLegPitch;
        this.leftLegRoll = leftLegRoll;
    }

    public String id() {
        return id;
    }

    public Text displayName() {
        return Text.translatable("pose.steveparty.player_pawn." + id);
    }

    public PlayerPawnPose next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** @return the pose at {@code index} in the cycle (wrapped, so a forged or stale index is still a pose). */
    public static PlayerPawnPose byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    /** @return the pose saved as {@code id}, or {@link #STAND}. */
    public static PlayerPawnPose byId(String id) {
        for (PlayerPawnPose pose : VALUES) {
            if (pose.id.equals(id)) return pose;
        }
        return STAND;
    }
}
