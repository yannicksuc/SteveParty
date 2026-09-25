package fr.lordfinn.steveparty.client.tokenspell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * First person hand of the token spell: while the spell screen is open, the hand holding the Tokenizer Wand follows
 * the mouse cursor (the wand tip leans towards it), scribbles while the player draws, and thrusts forward when the
 * spell is cast. Client-side rendering only. Render thread only.
 */
public final class TokenSpellHand {
    /** Depth of the held item in view space (vanilla: -0.72). */
    private static final float ITEM_DEPTH = 0.72F;
    /** Vanilla resting position of the held item (right arm; x is mirrored for the left arm). */
    private static final float REST_X = 0.56F, REST_Y = -0.52F;
    /** The wand's tip sits about this high above the hand. */
    private static final float TIP_HEIGHT = 0.3F;
    /** Share of the way from the resting position to the cursor the hand travels (1: the tip reaches the cursor). */
    private static final float FOLLOW = 0.55F;
    private static final float SMOOTHING = 14F;
    /** The hand does not follow the cursor closer to the screen centre than this (screen x, 0 = centre, 1 = edge). */
    private static final float MIN_SIDE = 0.3F;

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
        if (!active) {
            // First frame: start from the resting position, the hand then glides towards the cursor
            active = true;
            x = restScreenX();
            y = restScreenY();
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

    /** Cursor x as the hand follows it: kept on the hand's side of the screen, so the wand never hides the mob. */
    private static float sideX(float side) {
        return side > 0 ? Math.max(x, MIN_SIDE) : Math.min(x, -MIN_SIDE);
    }

    /** Moves and tilts the held wand towards the cursor (inside the item's own matrix push). */
    public static void apply(MatrixStack matrices, Arm arm) {
        if (!active) return;
        float side = arm == Arm.RIGHT ? 1 : -1;
        float halfHeight = halfHeight(ITEM_DEPTH);
        float halfWidth = halfHeight * aspect();
        // Where the cursor is, at the depth of the held item
        float cursorX = sideX(side) * halfWidth, cursorY = -y * halfHeight;
        float restX = side * REST_X, restTipY = REST_Y + TIP_HEIGHT;
        float dx = (cursorX - restX) * FOLLOW;
        float dy = (cursorY - restTipY) * FOLLOW;
        // Small circles while drawing: the wand scribbles
        float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        float scribble = press * 0.012F;
        dx += MathHelper.sin(time * 22) * scribble;
        dy += MathHelper.cos(time * 22) * scribble;

        // Rotations around the hand, not around the camera
        matrices.translate(restX + dx, REST_Y + dy, -ITEM_DEPTH - press * 0.08F);
        // Leans towards the cursor: sideways (roll) and forward (pitch)
        float lean = MathHelper.clamp((cursorX - restX) * 0.9F, -1.1F, 1.1F);
        float reach = MathHelper.clamp((cursorY - restTipY) * 0.9F, -0.8F, 0.8F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(-lean * 0.55F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(-reach * 0.35F - press * 0.25F));
        matrices.translate(-restX, -REST_Y, ITEM_DEPTH);
    }

    /**
     * World position of the wand's tip, roughly: just in front of the camera, on the line of sight through the point
     * of the screen where the tip is drawn.
     */
    public static Vec3d tipInWorld(double distance, Arm arm) {
        float side = arm == Arm.RIGHT ? 1 : -1;
        float restX = side * restScreenX(), restY = restScreenY();
        return screenInWorld(restX + (sideX(side) - restX) * FOLLOW, restY + (y - restY) * FOLLOW, distance);
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

    private static float restScreenX() {
        return REST_X / (halfHeight(ITEM_DEPTH) * aspect());
    }

    private static float restScreenY() {
        return -(REST_Y + TIP_HEIGHT) / halfHeight(ITEM_DEPTH);
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
