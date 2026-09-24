package fr.lordfinn.steveparty.client.model.sign;

import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.RockSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.WoodenCutoutPanelBlock;
import fr.lordfinn.steveparty.stencil.StencilShape;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Function;
import java.util.function.Supplier;

/** The stencil sign models (see {@link SignModel}), one per kind of sign. */
public final class StencilSignModels {
    // Placeholder textures of the JSON models, swapped for the material's
    static final Identifier OAK_PLANKS = Identifier.ofVanilla("block/oak_planks");
    static final Identifier OAK_LOG = Identifier.ofVanilla("block/stripped_oak_log");
    static final Identifier OAK_LOG_TOP = Identifier.ofVanilla("block/stripped_oak_log_top");
    static final Identifier ROCK_SIDE = Identifier.ofVanilla("block/stone");
    static final Identifier ROCK_TOP = Identifier.ofVanilla("block/smooth_stone");

    private static final Vector3f NORTH = new Vector3f(0, 0, -1), SOUTH = new Vector3f(0, 0, 1);
    private static final Vector3f UP = new Vector3f(0, 1, 0), DOWN = new Vector3f(0, -1, 0);
    private static final Vector3f EAST = new Vector3f(1, 0, 0), WEST = new Vector3f(-1, 0, 0);

    private StencilSignModels() {
    }

    static Function<Sprite, Sprite> wood(@Nullable Identifier material) {
        if (material == null) return Function.identity();
        MaterialSprites.Wood wood = MaterialSprites.wood(SignMaterial.WOOD.resolve(material));
        return sprite -> {
            Identifier id = sprite.getContents().getId();
            if (id.equals(OAK_PLANKS)) return wood.planks();
            if (id.equals(OAK_LOG)) return wood.log();
            if (id.equals(OAK_LOG_TOP)) return wood.logTop();
            return sprite;
        };
    }

    static Function<Sprite, Sprite> rock(@Nullable Identifier material) {
        if (material == null) return Function.identity();
        MaterialSprites.Rock rock = MaterialSprites.rock(SignMaterial.ROCK.resolve(material));
        return sprite -> {
            Identifier id = sprite.getContents().getId();
            if (id.equals(ROCK_SIDE)) return rock.side();
            if (id.equals(ROCK_TOP)) return rock.top();
            return sprite;
        };
    }

    // ---------------------------------------------------------------- traffic sign

    /** Traffic sign: its JSON model turned; the material one re-textured with its planks. */
    static final class TrafficSign extends SignModel {
        private final boolean material;

        TrafficSign(BakedModel base, boolean material) {
            super(base);
            this.material = material;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            out.model(base, state, random, material ? wood(look.material()) : Function.identity(), 0);
        }
    }

    // ---------------------------------------------------------------- wooden panel

    /** Wooden panel: the board (base model); the post through its block is drawn by the fence below. */
    static final class Panel extends SignModel {
        Panel(BakedModel board) {
            super(board);
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            out.model(base, state, random, wood(look.material()), 0);
        }
    }

    // ---------------------------------------------------------------- cut-out panel

    /**
     * Cut-out panel: a one block board built from the stencil, one pixel of planks
     * per stencil pixel, with sides where the outline goes.
     */
    static final class CutoutPanel extends SignModel {
        private final Sprite oakPlanks;

        CutoutPanel(BakedModel base, Sprite oakPlanks) {
            super(base);
            this.oakPlanks = oakPlanks;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            Sprite planks = look.material() == null ? oakPlanks : MaterialSprites.wood(SignMaterial.WOOD.resolve(look.material())).planks();
            byte[] shape = look.shape();
            if (StencilShape.isBlank(shape)) shape = StencilShape.full();
            final float z0 = WoodenCutoutPanelBlock.BOARD_Z, z1 = z0 + WoodenCutoutPanelBlock.BOARD_DEPTH;
            final float thin = WoodenCutoutPanelBlock.BOARD_DEPTH / 16F;
            for (int y = 0; y < 16; y++) {
                float top = WoodenCutoutPanelBlock.BOARD_Y + 16 - y, bottom = top - 1;
                float v0 = y / 16F, v1 = (y + 1) / 16F;
                // Front and back: one quad per run of pixels of the row
                for (int x = 0; x < 16; ) {
                    if (!StencilShape.get(shape, x, y)) {
                        x++;
                        continue;
                    }
                    int end = x;
                    while (end + 1 < 16 && StencilShape.get(shape, end + 1, y)) end++;
                    // Seen from the front (looking +z) stencil x goes towards -x: the run starts on the larger x
                    float seenLeft = seenLeftX(x), seenRight = seenLeftX(end) - 1;
                    out.quad(new Vector3f(seenLeft, top, z0), new Vector3f(seenLeft, bottom, z0), new Vector3f(seenRight, bottom, z0),
                            new Vector3f(seenRight, top, z0), NORTH, planks, x / 16F, v0, (end + 1) / 16F, v1);
                    out.quad(new Vector3f(seenRight, top, z1), new Vector3f(seenRight, bottom, z1), new Vector3f(seenLeft, bottom, z1),
                            new Vector3f(seenLeft, top, z1), SOUTH, planks, (15 - end) / 16F, v0, (16 - x) / 16F, v1);
                    x = end + 1;
                }
                // Outline: a side wherever the next pixel is empty
                for (int x = 0; x < 16; x++) {
                    if (!StencilShape.get(shape, x, y)) continue;
                    float xMax = seenLeftX(x), xMin = xMax - 1;
                    float u0 = x / 16F, u1 = (x + 1) / 16F;
                    if (!StencilShape.get(shape, x, y - 1)) {
                        out.quad(new Vector3f(xMin, top, z0), new Vector3f(xMin, top, z1), new Vector3f(xMax, top, z1),
                                new Vector3f(xMax, top, z0), UP, planks, u0, 0, u1, thin);
                    }
                    if (!StencilShape.get(shape, x, y + 1)) {
                        out.quad(new Vector3f(xMax, bottom, z0), new Vector3f(xMax, bottom, z1), new Vector3f(xMin, bottom, z1),
                                new Vector3f(xMin, bottom, z0), DOWN, planks, u0, 0, u1, thin);
                    }
                    if (!StencilShape.get(shape, x - 1, y)) {
                        out.quad(new Vector3f(xMax, top, z1), new Vector3f(xMax, bottom, z1), new Vector3f(xMax, bottom, z0),
                                new Vector3f(xMax, top, z0), EAST, planks, 0, v0, thin, v1);
                    }
                    if (!StencilShape.get(shape, x + 1, y)) {
                        out.quad(new Vector3f(xMin, top, z0), new Vector3f(xMin, bottom, z0), new Vector3f(xMin, bottom, z1),
                                new Vector3f(xMin, top, z1), WEST, planks, 0, v0, thin, v1);
                    }
                }
            }
        }

        /** Model x of the side of stencil column {@code x} that is on the left seen from the front (the larger x). */
        private static float seenLeftX(int x) {
            return WoodenCutoutPanelBlock.BOARD_X + 16 - x;
        }
    }

    // ---------------------------------------------------------------- rock sign

    /**
     * Rock sign: the leaning stone (base model, drawn upright in the JSON and leant here) whose 16x16 front is built
     * from the engraving: engraved pixels are dug one pixel deep, their bottom darker (or lighter, see
     * {@link #LIGHT_ENGRAVING}) or painted with the dye, glowing with glow ink; plus pebbles at its foot: one of the
     * sets behind it, and now and then one in front of it (which ones depends on where it stands).
     */
    static final class RockSign extends SignModel {
        /** Engraving bottom lighter than the stone (unshaded) instead of darker. */
        static final boolean LIGHT_ENGRAVING = false;
        private static final int DARK_BOTTOM = 0xFF6C6C6C;
        private static final int WALL = 0xFF9A9A9A;
        private static final float DEPTH = 1;
        /** Chance, in percent, of each set of pebbles in front of the stone (the rest of the time: none). */
        private static final int FRONT_PEBBLES_CHANCE = 15;
        /** The top of the stone, in model pixels; it reaches the side of its block towards a joined neighbour. */
        private static final float TOP_X1 = 2, TOP_X2 = 14, TOP_Y1 = 16, TOP_Y2 = 19, TOP_Z1 = 6, TOP_Z2 = 10;
        private final BakedModel[] backPebbles, frontPebbles;
        private final Sprite stone, smoothStone;

        RockSign(BakedModel base, BakedModel[] backPebbles, BakedModel[] frontPebbles, Sprite stone, Sprite smoothStone) {
            super(base);
            this.backPebbles = backPebbles;
            this.frontPebbles = frontPebbles;
            this.stone = stone;
            this.smoothStone = smoothStone;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            Function<Sprite, Sprite> rock = rock(look.material());
            float pivot = RockSignBlock.BACK_Z / 16F;
            Output leant = out.with(new Matrix4f().translate(0, 0, pivot)
                    .rotateX((float) Math.toRadians(RockSignBlock.TILT_DEGREES)).translate(0, 0, -pivot));
            boolean plusX = (look.joins() & RockSignBlock.JOIN_PLUS_X) != 0, minusX = (look.joins() & RockSignBlock.JOIN_MINUS_X) != 0;
            // The sides against a joined neighbour are hidden
            leant.model(base, state, random, rock, 0, face -> !(face == Direction.EAST && plusX) && !(face == Direction.WEST && minusX));
            MaterialSprites.Rock material = look.material() == null ? null : MaterialSprites.rock(SignMaterial.ROCK.resolve(look.material()));
            Sprite side = material == null ? stone : material.side(), top = material == null ? smoothStone : material.top();
            top(leant, minusX ? 0 : TOP_X1, plusX ? 16 : TOP_X2, !minusX, !plusX, side, top);
            engraving(leant, look, side);
            // Full avalanche: neighbouring rocks (seeds differing in a few high bits) get unrelated pebbles
            long hash = HashCommon.murmurHash3(seed);
            if (backPebbles.length > 0) out.model(backPebbles[(int) Math.floorMod(hash, (long) backPebbles.length)], state, random, rock, 0);
            int front = (int) Math.floorMod(hash >>> 16, 100L) / FRONT_PEBBLES_CHANCE;
            if (front < frontPebbles.length) out.model(frontPebbles[front], state, random, rock, 0);
        }

        /** The top of the stone from x1 to x2 (pixels), textured like a vanilla block, its ends drawn only if asked. */
        private static void top(Output out, float x1, float x2, boolean minusEnd, boolean plusEnd, Sprite side, Sprite top) {
            float y1 = TOP_Y1, y2 = TOP_Y2, z1 = TOP_Z1, z2 = TOP_Z2, h = (y2 - y1) / 16;
            // Front (north) and back (south)
            out.quad(new Vector3f(x2, y2, z1), new Vector3f(x2, y1, z1), new Vector3f(x1, y1, z1), new Vector3f(x1, y2, z1),
                    NORTH, side, (16 - x2) / 16, 0, (16 - x1) / 16, h);
            out.quad(new Vector3f(x1, y2, z2), new Vector3f(x1, y1, z2), new Vector3f(x2, y1, z2), new Vector3f(x2, y2, z2),
                    SOUTH, side, x1 / 16, 0, x2 / 16, h);
            // Up
            out.quad(new Vector3f(x1, y2, z1), new Vector3f(x1, y2, z2), new Vector3f(x2, y2, z2), new Vector3f(x2, y2, z1),
                    UP, top, x1 / 16, z1 / 16, x2 / 16, z2 / 16);
            // Ends (west / east)
            if (minusEnd) out.quad(new Vector3f(x1, y2, z1), new Vector3f(x1, y1, z1), new Vector3f(x1, y1, z2), new Vector3f(x1, y2, z2),
                    WEST, side, z1 / 16, 0, z2 / 16, h);
            if (plusEnd) out.quad(new Vector3f(x2, y2, z2), new Vector3f(x2, y1, z2), new Vector3f(x2, y1, z1), new Vector3f(x2, y2, z1),
                    EAST, side, (16 - z2) / 16, 0, (16 - z1) / 16, h);
        }

        private static void engraving(Output out, Look look, Sprite side) {
            byte[] shape = look.shape();
            float front = RockSignBlock.FRONT_Z, bottomZ = front + DEPTH;
            if (StencilShape.isBlank(shape)) {
                out.quad(new Vector3f(16, 16, front), new Vector3f(16, 0, front), new Vector3f(0, 0, front), new Vector3f(0, 16, front),
                        NORTH, side, 0, 0, 1, 1);
                return;
            }
            SignModel.Light bottomLight = LIGHT_ENGRAVING ? SignModel.Light.UNSHADED : SignModel.Light.SHADED;
            int bottomColor = LIGHT_ENGRAVING ? -1 : DARK_BOTTOM;
            if (look.color() != null) {
                // Paint, fading back to the bare engraving as it is brushed
                int paint = 0xFF000000 | look.color().getEntityColor();
                float fade = look.fade() / (float) StencilCanvasBlockEntity.MAX_FADE;
                bottomColor = ColorHelper.lerp(fade, paint, bottomColor);
                if (look.glowing() && look.fade() < StencilCanvasBlockEntity.MAX_FADE) bottomLight = SignModel.Light.EMISSIVE;
            }
            for (int y = 0; y < 16; y++) {
                float top = 16 - y, bottom = top - 1;
                float v0 = y / 16F, v1 = (y + 1) / 16F;
                for (int x = 0; x < 16; ) {
                    boolean engraved = StencilShape.get(shape, x, y);
                    int end = x;
                    while (end + 1 < 16 && StencilShape.get(shape, end + 1, y) == engraved) end++;
                    float seenLeft = 16 - x, seenRight = 15 - end;
                    float z = engraved ? bottomZ : front;
                    out.quad(new Vector3f(seenLeft, top, z), new Vector3f(seenLeft, bottom, z), new Vector3f(seenRight, bottom, z),
                            new Vector3f(seenRight, top, z), NORTH, side, x / 16F, v0, (end + 1) / 16F, v1,
                            engraved ? bottomColor : -1, engraved ? bottomLight : SignModel.Light.SHADED);
                    x = end + 1;
                }
                // Walls of the dug pixels, towards the pixels left standing
                for (int x = 0; x < 16; x++) {
                    if (!StencilShape.get(shape, x, y)) continue;
                    float xMax = 16 - x, xMin = xMax - 1;
                    float u0 = x / 16F, u1 = (x + 1) / 16F, d = DEPTH / 16F;
                    if (y > 0 && !StencilShape.get(shape, x, y - 1)) {
                        out.quad(new Vector3f(xMax, top, front), new Vector3f(xMax, top, bottomZ), new Vector3f(xMin, top, bottomZ),
                                new Vector3f(xMin, top, front), DOWN, side, u0, v0, u1, v0 + d, WALL, SignModel.Light.SHADED);
                    }
                    if (y < 15 && !StencilShape.get(shape, x, y + 1)) {
                        out.quad(new Vector3f(xMin, bottom, front), new Vector3f(xMin, bottom, bottomZ), new Vector3f(xMax, bottom, bottomZ),
                                new Vector3f(xMax, bottom, front), UP, side, u0, v1 - d, u1, v1, WALL, SignModel.Light.SHADED);
                    }
                    if (x > 0 && !StencilShape.get(shape, x - 1, y)) {
                        out.quad(new Vector3f(xMax, top, front), new Vector3f(xMax, bottom, front), new Vector3f(xMax, bottom, bottomZ),
                                new Vector3f(xMax, top, bottomZ), WEST, side, u0, v0, u0 + d, v1, WALL, SignModel.Light.SHADED);
                    }
                    if (x < 15 && !StencilShape.get(shape, x + 1, y)) {
                        out.quad(new Vector3f(xMin, top, bottomZ), new Vector3f(xMin, bottom, bottomZ), new Vector3f(xMin, bottom, front),
                                new Vector3f(xMin, top, front), EAST, side, u1 - d, v0, u1, v1, WALL, SignModel.Light.SHADED);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- plastic road sign

    /**
     * Plastic road sign: the post of the fence below and a 16x16 plate built from its mask, textured like the plastic
     * blocks: each plate pixel takes the plastic block texel matching its place (its outline gets the block's
     * bevelled edge). The diamond is the square plate turned by 45°.
     */
    static final class PlasticRoadSign extends SignModel {
        private final Sprite[] plastic;

        /** @param plastic plastic block sprite of each {@link DyeColor} (by id) */
        PlasticRoadSign(BakedModel base, Sprite[] plastic) {
            super(base);
            this.plastic = plastic;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            PlasticRoadSignBlock.Plate plate = state.contains(PlasticRoadSignBlock.PLATE) ? state.get(PlasticRoadSignBlock.PLATE) : PlasticRoadSignBlock.Plate.ROUND;
            Sprite sprite = plastic[(look.plateColor() == null ? DyeColor.WHITE : look.plateColor()).getId()];
            Output plateOut = plate.turned()
                    ? out.with(new Matrix4f().translate(0.5F, 0.5F, 0).rotateZ((float) Math.toRadians(45)).translate(-0.5F, -0.5F, 0))
                    : out;
            plate(plateOut, plate.mask(), sprite);
        }

        /** Plastic texel (x, y) of a plate pixel: its own place, or the edge of the block texture on the plate's outline. */
        private static int texelX(byte[] mask, int x, int y) {
            if (!StencilShape.get(mask, x - 1, y)) return 0;
            if (!StencilShape.get(mask, x + 1, y)) return 15;
            return Math.clamp(x, 1, 14);
        }

        private static int texelY(byte[] mask, int x, int y) {
            if (!StencilShape.get(mask, x, y - 1)) return 0;
            if (!StencilShape.get(mask, x, y + 1)) return 15;
            return Math.clamp(y, 1, 14);
        }

        private static void plate(Output out, byte[] mask, Sprite sprite) {
            final float z0 = PlasticRoadSignBlock.PLATE_Z, z1 = z0 + PlasticRoadSignBlock.PLATE_DEPTH;
            final float t = 1 / 16F;
            for (int y = 0; y < 16; y++) {
                float top = 16 - y, bottom = top - 1;
                for (int x = 0; x < 16; ) {
                    if (!StencilShape.get(mask, x, y)) {
                        x++;
                        continue;
                    }
                    // Run of pixels whose texels follow each other on one texture row
                    int tx = texelX(mask, x, y), ty = texelY(mask, x, y);
                    int end = x;
                    while (end + 1 < 16 && StencilShape.get(mask, end + 1, y) && texelY(mask, end + 1, y) == ty
                            && texelX(mask, end + 1, y) == tx + (end + 1 - x)) end++;
                    float seenLeft = 16 - x, seenRight = 15 - end;
                    float u0 = tx * t, u1 = (tx + end - x + 1) * t, v0 = ty * t, v1 = v0 + t;
                    out.quad(new Vector3f(seenLeft, top, z0), new Vector3f(seenLeft, bottom, z0), new Vector3f(seenRight, bottom, z0),
                            new Vector3f(seenRight, top, z0), NORTH, sprite, u0, v0, u1, v1);
                    out.quad(new Vector3f(seenRight, top, z1), new Vector3f(seenRight, bottom, z1), new Vector3f(seenLeft, bottom, z1),
                            new Vector3f(seenLeft, top, z1), SOUTH, sprite, u1, v0, u0, v1);
                    x = end + 1;
                }
                // Plate edges, with the colour of the outline texels
                for (int x = 0; x < 16; x++) {
                    if (!StencilShape.get(mask, x, y)) continue;
                    float xMax = 16 - x, xMin = xMax - 1;
                    float u = texelX(mask, x, y) * t, v = texelY(mask, x, y) * t;
                    if (!StencilShape.get(mask, x, y - 1)) {
                        out.quad(new Vector3f(xMin, top, z0), new Vector3f(xMin, top, z1), new Vector3f(xMax, top, z1),
                                new Vector3f(xMax, top, z0), UP, sprite, u, v, u + t, v + t);
                    }
                    if (!StencilShape.get(mask, x, y + 1)) {
                        out.quad(new Vector3f(xMax, bottom, z0), new Vector3f(xMax, bottom, z1), new Vector3f(xMin, bottom, z1),
                                new Vector3f(xMin, bottom, z0), DOWN, sprite, u, v, u + t, v + t);
                    }
                    if (!StencilShape.get(mask, x - 1, y)) {
                        out.quad(new Vector3f(xMax, top, z1), new Vector3f(xMax, bottom, z1), new Vector3f(xMax, bottom, z0),
                                new Vector3f(xMax, top, z0), EAST, sprite, u, v, u + t, v + t);
                    }
                    if (!StencilShape.get(mask, x + 1, y)) {
                        out.quad(new Vector3f(xMin, top, z0), new Vector3f(xMin, bottom, z0), new Vector3f(xMin, bottom, z1),
                                new Vector3f(xMin, top, z1), WEST, sprite, u, v, u + t, v + t);
                    }
                }
            }
        }
    }
}
