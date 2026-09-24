package fr.lordfinn.steveparty.client.model.sign;

import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.blocks.custom.signs.WoodenCutoutPanelBlock;
import fr.lordfinn.steveparty.stencil.StencilShape;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
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

    /** Wooden panel: the post (base model) and the board. */
    static final class Panel extends SignModel {
        private final BakedModel board;

        Panel(BakedModel post, BakedModel board) {
            super(post);
            this.board = board;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            Function<Sprite, Sprite> wood = wood(look.material());
            out.model(base, state, random, wood, 0);
            out.model(board, state, random, wood, 0);
        }
    }

    /**
     * Cut-out panel: the post (base model), and a board built from the stencil: every stencil pixel is a
     * {@link WoodenCutoutPanelBlock#PIXEL} pixel square of planks, with sides where the outline goes.
     */
    static final class CutoutPanel extends SignModel {
        private final Sprite oakPlanks;

        CutoutPanel(BakedModel post, Sprite oakPlanks) {
            super(post);
            this.oakPlanks = oakPlanks;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            out.model(base, state, random, wood(look.material()), 0);
            Sprite planks = look.material() == null ? oakPlanks : MaterialSprites.wood(SignMaterial.WOOD.resolve(look.material())).planks();
            byte[] shape = look.shape();
            if (StencilShape.isBlank(shape)) shape = StencilShape.full();
            board(out, shape, planks);
        }

        private static void board(Output out, byte[] shape, Sprite planks) {
            final float p = WoodenCutoutPanelBlock.PIXEL;
            final float z0 = WoodenCutoutPanelBlock.BOARD_Z, z1 = z0 + WoodenCutoutPanelBlock.BOARD_DEPTH;
            final float thin = WoodenCutoutPanelBlock.BOARD_DEPTH / 16F;
            for (int y = 0; y < 16; y++) {
                float top = WoodenCutoutPanelBlock.BOARD_Y + (16 - y) * p, bottom = top - p;
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
                    float seenLeft = modelX(x), seenRight = modelX(end) - p;
                    out.quad(new Vector3f(seenLeft, top, z0), new Vector3f(seenLeft, bottom, z0), new Vector3f(seenRight, bottom, z0),
                            new Vector3f(seenRight, top, z0), new Vector3f(0, 0, -1), planks, x / 16F, v0, (end + 1) / 16F, v1);
                    out.quad(new Vector3f(seenRight, top, z1), new Vector3f(seenRight, bottom, z1), new Vector3f(seenLeft, bottom, z1),
                            new Vector3f(seenLeft, top, z1), new Vector3f(0, 0, 1), planks, (15 - end) / 16F, v0, (16 - x) / 16F, v1);
                    x = end + 1;
                }
                // Outline: a side wherever the next pixel is empty
                for (int x = 0; x < 16; x++) {
                    if (!StencilShape.get(shape, x, y)) continue;
                    float xMax = modelX(x), xMin = xMax - p;
                    float u0 = x / 16F, u1 = (x + 1) / 16F;
                    if (!StencilShape.get(shape, x, y - 1)) {
                        out.quad(new Vector3f(xMin, top, z0), new Vector3f(xMin, top, z1), new Vector3f(xMax, top, z1),
                                new Vector3f(xMax, top, z0), new Vector3f(0, 1, 0), planks, u0, 0, u1, thin);
                    }
                    if (!StencilShape.get(shape, x, y + 1)) {
                        out.quad(new Vector3f(xMax, bottom, z0), new Vector3f(xMax, bottom, z1), new Vector3f(xMin, bottom, z1),
                                new Vector3f(xMin, bottom, z0), new Vector3f(0, -1, 0), planks, u0, 0, u1, thin);
                    }
                    if (!StencilShape.get(shape, x - 1, y)) {
                        // Left of the pixel seen from the front: the +x side
                        out.quad(new Vector3f(xMax, top, z1), new Vector3f(xMax, bottom, z1), new Vector3f(xMax, bottom, z0),
                                new Vector3f(xMax, top, z0), new Vector3f(1, 0, 0), planks, 0, v0, thin, v1);
                    }
                    if (!StencilShape.get(shape, x + 1, y)) {
                        out.quad(new Vector3f(xMin, top, z0), new Vector3f(xMin, bottom, z0), new Vector3f(xMin, bottom, z1),
                                new Vector3f(xMin, top, z1), new Vector3f(-1, 0, 0), planks, 0, v0, thin, v1);
                    }
                }
            }
        }

        /** Model x of the side of stencil column {@code x} that is on the left seen from the front (the larger x). */
        private static float modelX(int x) {
            return WoodenCutoutPanelBlock.BOARD_X + (16 - x) * WoodenCutoutPanelBlock.PIXEL;
        }
    }

    /** Rock sign: the standing stone (base model) and one of the pebble sets, picked from the position. */
    static final class RockSign extends SignModel {
        private final BakedModel[] pebbles;

        RockSign(BakedModel stone, BakedModel[] pebbles) {
            super(stone);
            this.pebbles = pebbles;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            Function<Sprite, Sprite> rock = rock(look.material());
            out.model(base, state, random, rock, 0);
            if (pebbles.length > 0) out.model(pebbles[(int) Math.floorMod(HashCommon.mix(seed), (long) pebbles.length)], state, random, rock, 0);
        }
    }

    /** Plastic road sign: the pole (base model) and the plate of its shape, in its plastic colour. */
    static final class PlasticRoadSign extends SignModel {
        private final BakedModel[] plates;
        private final Sprite[] plastic;
        private final Identifier placeholder;

        /**
         * @param plates      one per {@link PlasticRoadSignBlock.Plate}
         * @param plastic     flat plastic sprite of each {@link DyeColor} (by id)
         * @param placeholder texture of the plate in the JSON models, swapped for the colour's
         */
        PlasticRoadSign(BakedModel pole, BakedModel[] plates, Sprite[] plastic, Identifier placeholder) {
            super(pole);
            this.plates = plates;
            this.plastic = plastic;
            this.placeholder = placeholder;
        }

        @Override
        protected void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random) {
            out.model(base, state, random, Function.identity(), 0);
            PlasticRoadSignBlock.Plate plate = state.contains(PlasticRoadSignBlock.PLATE) ? state.get(PlasticRoadSignBlock.PLATE) : PlasticRoadSignBlock.Plate.ROUND;
            DyeColor color = look.plateColor() == null ? DyeColor.WHITE : look.plateColor();
            Sprite colored = plastic[color.getId()];
            out.model(plates[plate.ordinal()], state, random,
                    sprite -> sprite.getContents().getId().equals(placeholder) ? colored : sprite, 0);
        }
    }
}
