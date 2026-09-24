package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.TrafficSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.WoodenPanelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Orientation;
import org.joml.Vector3f;

import java.util.List;

/**
 * Where the symbol of each stencil block is drawn. Quads are given in model space (pixels, the sign's front facing
 * north, before its 16-way turn) for the signs, in block pixels for sprayed paint.
 * <p>
 * A quad's corners are its top left, top right, bottom right and bottom left as seen by someone looking at it
 * (texture u to the right, v downwards), slightly in front of the face so that it never z-fights with it.
 */
public final class SymbolLayouts {
    /** Distance between a face and its symbol, in pixels. */
    private static final float GAP = 0.05F;
    private static final float PAINT_GAP = 0.15F;

    public record SymbolQuad(Vector3f topLeft, Vector3f topRight, Vector3f bottomRight, Vector3f bottomLeft, Vector3f normal) {
    }

    private SymbolLayouts() {
    }

    // Traffic sign: the board of traffic_sign.json, tilted by 22.5° around x at (8, 0, 3); symbol on its front only
    private static final List<SymbolQuad> TRAFFIC_SIGN = List.of(
            tiltX(front(new Vector3f(8, 7.75F, 1 - GAP), 16, 16), 22.5F, 0, 3));

    // Wooden panel: the planks of the board (z = 3) between its rails, one symbol pixel per board pixel
    private static final List<SymbolQuad> WOODEN_PANEL = List.of(front(new Vector3f(8, 9, 3 - GAP), 16, 16));

    // Plastic road sign: front of the plate (z = 3), the whole 16x16 plate (the diamond turned with it).
    // The rock sign's engraving is dug in its stone: drawn in the chunk mesh, not here
    private static final List<SymbolQuad> PLATE = List.of(front(new Vector3f(8, 8, 3 - GAP), 16, 16));
    private static final List<SymbolQuad> PLATE_DIAMOND = List.of(turned(front(new Vector3f(8, 8, 3 - GAP), 16, 16), 45));

    /** @return the symbol quads of a sign in model space, or an empty list if it shows no symbol. */
    public static List<SymbolQuad> forSign(BlockState state) {
        if (state.getBlock() instanceof TrafficSignBlock) return TRAFFIC_SIGN;
        if (state.getBlock() instanceof WoodenPanelBlock) return WOODEN_PANEL;
        if (state.getBlock() instanceof PlasticRoadSignBlock) {
            return state.get(PlasticRoadSignBlock.PLATE).turned() ? PLATE_DIAMOND : PLATE;
        }
        return List.of();
    }

    /** Sprayed paint: the whole face of the block it is on, top of the symbol towards the orientation's rotation. */
    public static SymbolQuad forPaint(BlockState state) {
        Orientation orientation = state.get(StencilPaintBlock.ORIENTATION);
        Vector3f facing = orientation.getFacing().getUnitVector();
        Vector3f up = orientation.getRotation().getUnitVector();
        // Seen from outside the face: right = up x facing
        Vector3f right = new Vector3f(up).cross(facing);
        Vector3f center = new Vector3f(8, 8, 8).sub(new Vector3f(facing).mul(8 - PAINT_GAP));
        return quad(center, right, up, 16, 16, facing);
    }

    // ---------------------------------------------------------------- building quads

    /** Quad on a face looking north (-z): seen from the front, right is -x. */
    private static SymbolQuad front(Vector3f center, float width, float height) {
        return quad(center, new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), width, height, new Vector3f(0, 0, -1));
    }

    private static SymbolQuad quad(Vector3f center, Vector3f right, Vector3f up, float width, float height, Vector3f normal) {
        Vector3f r = new Vector3f(right).mul(width / 2);
        Vector3f u = new Vector3f(up).mul(height / 2);
        return new SymbolQuad(
                new Vector3f(center).sub(r).add(u),
                new Vector3f(center).add(r).add(u),
                new Vector3f(center).add(r).sub(u),
                new Vector3f(center).sub(r).sub(u),
                new Vector3f(normal));
    }

    /** Turns a quad by {@code degrees} (clockwise as seen from its front) around its own centre. */
    private static SymbolQuad turned(SymbolQuad quad, float degrees) {
        Vector3f center = new Vector3f(quad.topLeft()).add(quad.bottomRight()).mul(0.5F);
        // Clockwise seen from the front = counter clockwise around the normal
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(-degrees), quad.normal());
        return new SymbolQuad(
                turn(quad.topLeft(), center, rotation), turn(quad.topRight(), center, rotation),
                turn(quad.bottomRight(), center, rotation), turn(quad.bottomLeft(), center, rotation),
                new Vector3f(quad.normal()));
    }

    private static Vector3f turn(Vector3f point, Vector3f center, org.joml.Quaternionf rotation) {
        return rotation.transform(new Vector3f(point).sub(center)).add(center);
    }

    /** Same rotation as a model element rotated by {@code degrees} around x at (originY, originZ). */
    private static SymbolQuad tiltX(SymbolQuad quad, float degrees, float originY, float originZ) {
        return new SymbolQuad(
                tiltX(quad.topLeft(), degrees, originY, originZ), tiltX(quad.topRight(), degrees, originY, originZ),
                tiltX(quad.bottomRight(), degrees, originY, originZ), tiltX(quad.bottomLeft(), degrees, originY, originZ),
                new org.joml.Quaternionf().rotationX((float) Math.toRadians(degrees)).transform(new Vector3f(quad.normal())));
    }

    private static Vector3f tiltX(Vector3f point, float degrees, float originY, float originZ) {
        Vector3f relative = new Vector3f(point.x, point.y - originY, point.z - originZ);
        new org.joml.Quaternionf().rotationX((float) Math.toRadians(degrees)).transform(relative);
        return relative.add(0, originY, originZ);
    }
}
