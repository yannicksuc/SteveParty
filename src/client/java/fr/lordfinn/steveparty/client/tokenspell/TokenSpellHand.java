package fr.lordfinn.steveparty.client.tokenspell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * First person hand of the token spell: while the spell screen is open, the wand's tip (its jewel) follows the mouse
 * cursor anywhere on the screen, scribbles while the player draws, and thrusts forward when the spell is cast.
 * Client-side rendering only. Render thread only.
 * <p>
 * The wand lies along the arm, which comes from a shoulder below the bottom corner of the hand's side: the wand is
 * rolled to point from that shoulder towards the cursor (so it points upwards or sideways, never upside down), then
 * moved so that its jewel is drawn right under the cursor. Where the jewel is comes from the held item's own first
 * person display transform, read from its model, so the tip stays right if the model changes.
 */
public final class TokenSpellHand {
    /** Depth of the held item's pivot in view space (vanilla: -0.72). */
    private static final float ITEM_DEPTH = 0.72F;
    /** Vanilla resting position of the held item (right arm; x is mirrored for the left arm). */
    private static final float REST_X = 0.56F, REST_Y = -0.52F;
    /** Centre of the Kamek wand's jewel, in model space (model pixels / 16: x 8, y 29, z 8). */
    private static final Vector3f JEWEL = new Vector3f(0.5F, 29F / 16F, 0.5F);
    private static final float SMOOTHING = 14F;
    /** The arm comes from this point, below the bottom corner of the hand's side (in half screen widths / heights). */
    private static final float SHOULDER_X = 0.95F, SHOULDER_Y = -1.6F;
    /** The wand never points lower than this (sine of the angle above horizontal). */
    private static final float MIN_UPWARDS = 0.2F;

    private static boolean active;
    private static float x, y, press;
    private static long lastNanos;

    private TokenSpellHand() {
    }

    /**
     * Called every frame by the spell screen.
     *
     * @param screenX  cursor x, -1 (left edge) .. 1 (right edge)
     * @param screenY  cursor y, -1 (top edge) .. 1 (bottom edge)
     * @param pressing how much the wand presses forward: 0 (idle) .. 1 (drawing, casting)
     */
    public static void aim(float screenX, float screenY, float pressing) {
        long now = System.nanoTime();
        // A minimized window reports absurd cursor positions
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)) return;
        screenX = MathHelper.clamp(screenX, -1.1F, 1.1F);
        screenY = MathHelper.clamp(screenY, -1.1F, 1.1F);
        if (!active) {
            active = true;
            x = screenX;
            y = screenY;
            press = 0;
            lastNanos = now;
        }
        float dt = Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        float k = 1 - (float) Math.exp(-dt * SMOOTHING);
        x += (screenX - x) * k;
        y += (screenY - y) * k;
        press += (pressing - press) * k;
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Where the jewel of the resting wand is, relative to the held item's pivot, in view space: the held item's first
     * person display transform (from its model) applied to the jewel's model position, like the item renderer does.
     */
    private static Vector3f restTip(ItemStack stack, Arm arm) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean left = arm == Arm.LEFT;
        BakedModel model = client.getItemRenderer().getModel(stack, client.world, client.player, 0);
        Transformation transformation = model.getTransformation().getTransformation(
                left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND);
        MatrixStack matrices = new MatrixStack();
        transformation.apply(left, matrices);
        matrices.translate(-0.5F, -0.5F, -0.5F);
        return matrices.peek().getPositionMatrix().transformPosition(new Vector3f(JEWEL));
    }

    /**
     * Pose for the current cursor: {pivot x, pivot y, roll (counterclockwise, radians)}, the pivot at the held item's
     * depth, such that the jewel is drawn under the cursor.
     */
    private static float[] pose(float side, Vector3f tip) {
        float tanHalfHeight = halfHeight(1);
        float tanHalfWidth = tanHalfHeight * aspect();
        float cursorX = x * tanHalfWidth, cursorY = -y * tanHalfHeight;
        float dx = cursorX - side * SHOULDER_X * tanHalfWidth, dy = cursorY - SHOULDER_Y * tanHalfHeight;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) {
            dx = 0;
            dy = 1;
        } else {
            dx /= length;
            dy /= length;
        }
        if (dy < MIN_UPWARDS) {
            dy = MIN_UPWARDS;
            dx = Math.signum(dx == 0 ? -side : dx) * (float) Math.sqrt(1 - dy * dy);
        }
        // Angles from "straight up", clockwise: wanted on screen, and of the resting wand
        float wanted = (float) Math.atan2(dx, dy);
        float resting = (float) Math.atan2(tip.x(), tip.y());
        float roll = resting - wanted;
        float cos = MathHelper.cos(roll), sin = MathHelper.sin(roll);
        float tipX = tip.x() * cos - tip.y() * sin, tipY = tip.x() * sin + tip.y() * cos;
        // The jewel is deeper than the pivot: at that depth, the cursor's line of sight is further out
        float tipDepth = ITEM_DEPTH - tip.z();
        return new float[]{cursorX * tipDepth - tipX, cursorY * tipDepth - tipY, roll};
    }

    /** Places the held wand so that its jewel follows the cursor (inside the item's own matrix push). */
    public static void apply(MatrixStack matrices, Arm arm, ItemStack stack) {
        if (!active) return;
        float side = arm == Arm.RIGHT ? 1 : -1;
        float[] pose = pose(side, restTip(stack, arm));
        // Small circles while drawing: the wand scribbles
        float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        float scribble = press * 0.01F;
        float pivotX = pose[0] + MathHelper.sin(time * 22) * scribble;
        float pivotY = pose[1] + MathHelper.cos(time * 22) * scribble;

        // Rotation around the pivot, then back to where vanilla puts the held item
        matrices.translate(pivotX, pivotY, -ITEM_DEPTH - press * 0.04F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pose[2]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(-press * 0.12F));
        matrices.translate(-side * REST_X, -REST_Y, ITEM_DEPTH);
    }

    /** World position of the wand's jewel, roughly: it is drawn under the cursor, just in front of the camera. */
    public static Vec3d tipInWorld(double distance) {
        return screenInWorld(x, y, distance);
    }

    /** World position just in front of the camera, on the line of sight through a point of the screen (-1..1). */
    public static Vec3d screenInWorld(float screenX, float screenY, double distance) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        float tanHalfHeight = halfHeight(1);
        float tanHalfWidth = tanHalfHeight * aspect();
        Vector3f forward = camera.getHorizontalPlane(), up = camera.getVerticalPlane(), left = camera.getDiagonalPlane();
        float right = screenX * tanHalfWidth, down = screenY * tanHalfHeight;
        Vec3d direction = new Vec3d(
                forward.x() - left.x() * right - up.x() * down,
                forward.y() - left.y() * right - up.y() * down,
                forward.z() - left.z() * right - up.z() * down);
        return camera.getPos().add(direction.multiply(distance));
    }

    /**
     * Where a world position is drawn on the screen: {x, y} from -1 (left / top edge) to 1 (right / bottom edge), or
     * null when it is behind the camera. The inverse of {@link #screenInWorld}.
     */
    public static float[] worldToScreen(Vec3d position) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d d = position.subtract(camera.getPos());
        Vector3f forward = camera.getHorizontalPlane(), up = camera.getVerticalPlane(), left = camera.getDiagonalPlane();
        double depth = d.x * forward.x() + d.y * forward.y() + d.z * forward.z();
        if (depth < 0.05) return null;
        double right = -(d.x * left.x() + d.y * left.y() + d.z * left.z());
        double above = d.x * up.x() + d.y * up.y() + d.z * up.z();
        float tanHalfHeight = halfHeight(1);
        return new float[]{(float) (right / depth / (tanHalfHeight * aspect())), (float) (-above / depth / tanHalfHeight)};
    }

    private static float halfHeight(float depth) {
        int fov = MinecraftClient.getInstance().options.getFov().getValue();
        return depth * (float) Math.tan(Math.toRadians(fov / 2.0));
    }

    private static float aspect() {
        var window = MinecraftClient.getInstance().getWindow();
        return window.getFramebufferHeight() == 0 ? 16F / 9F : (float) window.getFramebufferWidth() / window.getFramebufferHeight();
    }
}
