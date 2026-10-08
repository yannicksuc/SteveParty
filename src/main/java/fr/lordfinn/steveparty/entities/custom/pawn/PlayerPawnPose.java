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
    VICTORY("victory", -15, 0, 0, 0, 0, 150, 0, 0, -150, 0, 4, 0, -4, 0.3F, 0, 0, false),
    // Jumping to punch the dice overhead: the head turned away from the arm, legs apart, off the base
    DICE_PUNCH("dice_punch", -35, 25, 15, 0, 0, 155, 25, 0, -30, -65, 0, 50, 0, 5, 0, 0, false),
    POINT("point", 0, -15, 0, -90, -15, 0, 0, 0, -6, 0, 0, 0, 0),
    SALUTE("salute", 0, 0, 0, -130, 40, 30, 0, 0, -4, 0, 0, 0, 0),
    THINKER("thinker", 18, 10, 0, -115, 40, 0, -55, -35, 0, 0, 0, 0, 0),
    SHRUG("shrug", 0, 0, 12, -40, 0, 40, -40, 0, -40, 0, 0, 0, 0),
    T_POSE("t_pose", 0, 0, 0, 0, 0, 90, 0, 0, -90, 0, 0, 0, 0),
    FLEX("flex", -8, 0, 0, -20, 0, 125, -20, 0, -125, 4, 4, 0, -4, 0.3F, 0, 0, false),
    RUN("run", -5, 0, 0, 55, 0, 6, -60, 0, -6, -45, 0, 40, 0),
    KICK("kick", 5, 0, 0, 0, 0, 30, 20, 0, -30, -75, 0, 0, 0),
    ZOMBIE("zombie", 0, 0, 8, -90, 0, 0, -90, 0, 0, 0, 0, 0, 0),
    // Both arms along the same diagonal: the right one stretched up and out, the left one across the face, the
    // face tucked into it
    DAB("dab", 15, -10, 12, 0, 0, 130, -100, 30, 0, 0, 0, 0, 0),
    SULK("sulk", 40, 0, 0, 10, 0, 2, 10, 0, -2, 0, 0, 0, 0),
    BALLERINA("ballerina", -10, 0, 0, 0, 0, 150, 0, 0, -95, 0, 0, -30, -25),
    // Sitting on the base: lowered by the length of the legs, the thighs resting on it (the base is thin: the legs
    // can't hang down from its edge, they would go into the ground)
    SIT_EDGE("sit_edge", 10, 0, 0, -35, 0, 10, -35, 0, -10, -80, 4, -74, -4, -9.5F, 5, 0, false),
    CROSS_LEGGED("cross_legged", 0, 0, 0, -45, -15, 0, -45, 15, 0, -90, 35, -90, -35, -9, 0, 0, false),
    LOUNGE("lounge", -15, 0, 0, 35, 0, 15, 35, 0, -15, -120, 6, -115, -6, -10.5F, 0, 25, false),
    KNEES_HUGGED("knees_hugged", 20, 0, 0, -75, -20, 0, -75, 20, 0, -135, 2, -135, -2, -10.5F, 0, 0, false),
    // Upside down on its hands, looking at the floor: the head bent back, clear of the base
    HANDSTAND("handstand", -90, 0, 0, 0, 0, 172, 0, 0, -172, 0, 10, 0, -10, 0, 0, 0, true);

    private static final PlayerPawnPose[] VALUES = values();

    private final String id;
    public final float headPitch, headYaw, headRoll;
    public final float rightArmPitch, rightArmYaw, rightArmRoll;
    public final float leftArmPitch, leftArmYaw, leftArmRoll;
    public final float rightLegPitch, rightLegRoll;
    public final float leftLegPitch, leftLegRoll;
    /** The whole figure moved up (negative: down, e.g. sitting) and forward, in model pixels. */
    public final float lift, forward;
    /** The whole figure leaning back (positive) or forward, around its hips, in degrees. */
    public final float tilt;
    /** Upside down, standing on its hands (raised arms). */
    public final boolean upsideDown;

    PlayerPawnPose(String id, float headPitch, float headYaw, float headRoll,
                   float rightArmPitch, float rightArmYaw, float rightArmRoll,
                   float leftArmPitch, float leftArmYaw, float leftArmRoll,
                   float rightLegPitch, float rightLegRoll, float leftLegPitch, float leftLegRoll) {
        this(id, headPitch, headYaw, headRoll, rightArmPitch, rightArmYaw, rightArmRoll, leftArmPitch, leftArmYaw, leftArmRoll,
                rightLegPitch, rightLegRoll, leftLegPitch, leftLegRoll, 0, 0, 0, false);
    }

    PlayerPawnPose(String id, float headPitch, float headYaw, float headRoll,
                   float rightArmPitch, float rightArmYaw, float rightArmRoll,
                   float leftArmPitch, float leftArmYaw, float leftArmRoll,
                   float rightLegPitch, float rightLegRoll, float leftLegPitch, float leftLegRoll,
                   float lift, float forward, float tilt, boolean upsideDown) {
        this.id = id;
        this.lift = lift;
        this.forward = forward;
        this.tilt = tilt;
        this.upsideDown = upsideDown;
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

    /**
     * Whether it reads well from afar, on a podium: a big, clear gesture (not sitting, upside down nor subtle).
     */
    public boolean podium() {
        return switch (this) {
            case WAVE, VICTORY, DICE_PUNCH, POINT, SALUTE, T_POSE, FLEX, RUN, KICK, DAB, BALLERINA -> true;
            default -> false;
        };
    }

    /** One of the {@link #podium} poses, picked by {@code seed} (any int). */
    public static PlayerPawnPose podiumPose(int seed) {
        PlayerPawnPose[] podium = java.util.Arrays.stream(VALUES).filter(PlayerPawnPose::podium).toArray(PlayerPawnPose[]::new);
        return podium[Math.floorMod(seed, podium.length)];
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
