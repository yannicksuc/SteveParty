package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.utils.ClientTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The logo intro played once per game launch before the looping logo (TitleScreenBackground): the owner's intro video
 * at 30 fps, cut by the art sources into tiles that are stored only when they change, packed in
 * textures/gui/title/logo_intro_N.png and indexed by logo_intro.txt (format in the script). Each frame is drawn as
 * one batch of quads per atlas, in the video's union box; the final logo's rect inside that box is what the caller
 * lays out like the looping logo. ~19 MB of VRAM, freed by {@link #free} once played.
 */
final class TitleLogoIntro {
    private static final Identifier INDEX = Steveparty.id("textures/gui/title/logo_intro.txt");

    final int boxW, boxH, logoX, logoY, logoW, logoH, fps;
    private final int tile, cols;
    private final Identifier[] atlases;
    /** Per cell: its atlas and the UVs (0..1) of its tile's corners. */
    private final int[] cellAtlas;
    private final float[] cellU0, cellV0, cellU1, cellV1;
    /** Per frame, per place in the box (row by row): the cell drawn there, -1 for none. */
    private final int[][] frames;

    private TitleLogoIntro(int[] box, int[] tiling, int fps, List<int[]> atlasSizes, List<String> frameLines) {
        boxW = box[0];
        boxH = box[1];
        logoX = box[2];
        logoY = box[3];
        logoW = box[4];
        logoH = box[5];
        tile = tiling[0];
        cols = tiling[1];
        int places = cols * tiling[2];
        this.fps = fps;

        atlases = new Identifier[atlasSizes.size()];
        int cellCount = 0;
        for (int[] a : atlasSizes) cellCount += a[2] * a[3];
        cellAtlas = new int[cellCount];
        cellU0 = new float[cellCount];
        cellV0 = new float[cellCount];
        cellU1 = new float[cellCount];
        cellV1 = new float[cellCount];
        int cell = 0, stride = tile + 2;
        for (int a = 0; a < atlasSizes.size(); a++) {
            atlases[a] = Steveparty.id("textures/gui/title/logo_intro_" + a + ".png");
            int[] s = atlasSizes.get(a);
            for (int k = 0; k < s[2] * s[3]; k++, cell++) {
                int u = (k % s[2]) * stride + 1, v = (k / s[2]) * stride + 1;
                cellAtlas[cell] = a;
                cellU0[cell] = u / (float) s[0];
                cellV0[cell] = v / (float) s[1];
                cellU1[cell] = (u + tile) / (float) s[0];
                cellV1[cell] = (v + tile) / (float) s[1];
            }
        }

        frames = new int[frameLines.size()][];
        int[] state = new int[places];
        Arrays.fill(state, -1);
        for (int f = 0; f < frames.length; f++) {
            String[] changes = frameLines.get(f).trim().split(" ");
            for (int i = 1; i < changes.length; i++) {
                int colon = changes[i].indexOf(':');
                int c = Integer.parseInt(changes[i].substring(colon + 1));
                if (c >= cellCount) throw new IllegalArgumentException("cell " + c);
                state[Integer.parseInt(changes[i].substring(0, colon))] = c;
            }
            frames[f] = state.clone();
        }
    }

    /** The intro, or empty when its index or an atlas is missing or broken: the title screen then shows the loop. */
    static Optional<TitleLogoIntro> load(ResourceManager manager) {
        try {
            Optional<Resource> index = manager.getResource(INDEX);
            if (index.isEmpty()) return Optional.empty();
            int[] box = null, tiling = null;
            int fps = 0;
            List<int[]> atlases = new ArrayList<>();
            List<String> frames = new ArrayList<>();
            try (BufferedReader reader = index.get().getReader()) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.startsWith("frame")) frames.add(line);
                    else if (line.startsWith("box ")) box = ints(line, 6);
                    else if (line.startsWith("tile ")) tiling = ints(line, 3);
                    else if (line.startsWith("fps ")) fps = ints(line, 1)[0];
                    else if (line.startsWith("atlas ")) atlases.add(ints(line, 4));
                }
            }
            if (box == null || tiling == null || fps <= 0 || atlases.isEmpty() || frames.isEmpty()) return Optional.empty();
            TitleLogoIntro intro = new TitleLogoIntro(box, tiling, fps, atlases, frames);
            for (Identifier atlas : intro.atlases) {
                if (manager.getResource(atlas).isEmpty()) return Optional.empty();
            }
            return Optional.of(intro);
        } catch (Exception e) {
            Steveparty.LOGGER.warn("Title logo intro unavailable, showing the looping logo", e);
            return Optional.empty();
        }
    }

    private static int[] ints(String line, int count) {
        String[] parts = line.trim().split(" ");
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = Integer.parseInt(parts[i + 1]);
        return values;
    }

    int frameCount() {
        return frames.length;
    }

    /** Uploads the atlases now (TextureManager loads lazily), so the clock starts after the hitch, not during it. */
    void preload(MinecraftClient client) {
        for (Identifier atlas : atlases) client.getTextureManager().getTexture(atlas);
    }

    void free(MinecraftClient client) {
        for (Identifier atlas : atlases) ClientTextures.destroy(atlas);
    }

    /** Draws a frame in box coordinates (the caller's matrices place the box); blending is the caller's. */
    void draw(DrawContext context, int frame) {
        int[] cells = frames[Math.max(0, Math.min(frame, frames.length - 1))];
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        for (int a = 0; a < atlases.length; a++) {
            BufferBuilder buffer = null;
            for (int place = 0; place < cells.length; place++) {
                int c = cells[place];
                if (c < 0 || cellAtlas[c] != a) continue;
                if (buffer == null) buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                float x0 = (place % cols) * tile, y0 = (place / cols) * tile, x1 = x0 + tile, y1 = y0 + tile;
                buffer.vertex(matrix, x0, y0, 0).texture(cellU0[c], cellV0[c]);
                buffer.vertex(matrix, x0, y1, 0).texture(cellU0[c], cellV1[c]);
                buffer.vertex(matrix, x1, y1, 0).texture(cellU1[c], cellV1[c]);
                buffer.vertex(matrix, x1, y0, 0).texture(cellU1[c], cellV0[c]);
            }
            if (buffer == null) continue;
            BuiltBuffer built = buffer.endNullable();
            if (built == null) continue;
            RenderSystem.setShaderTexture(0, atlases[a]);
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }
}
