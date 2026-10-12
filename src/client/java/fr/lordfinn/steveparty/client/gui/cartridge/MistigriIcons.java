package fr.lordfinn.steveparty.client.gui.cartridge;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * The small pictograms of the Mistigri's sentence list, one PNG each at their native size under
 * {@code textures/gui/mistigri/} (retouched by hand): who a sentence falls on ({@code target_<id>.png}, 11 × 11, its
 * mark in its target's colour), the badge over a kind's icon ({@code badge_lose / gain / share.png}, 7 × 7), the weight
 * ({@code weight.png}, 7 × 7) and the picked cards ({@code cards.png}, 10 × 10).
 */
final class MistigriIcons {
    /** A target pictogram's size. */
    static final int SIZE = 11;

    private MistigriIcons() {
    }

    private static Identifier texture(String name) {
        return Steveparty.id("textures/gui/mistigri/" + name + ".png");
    }

    private static void draw(DrawContext context, String name, int x, int y, int w, int h, boolean dim) {
        RenderSystem.enableBlend();
        if (dim) RenderSystem.setShaderColor(0.45F, 0.45F, 0.45F, 1F);
        context.drawTexture(texture(name), x, y, 0, 0, w, h, w, h);
        if (dim) RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    /** {@code target}'s pictogram, {@link #SIZE} square, dimmed when {@code off}. */
    static void target(DrawContext context, Target target, int x, int y, boolean off) {
        draw(context, "target_" + target.id, x, y, SIZE, SIZE, off);
    }

    /** The weight (a kettlebell), 7 square, dimmed at 0. */
    static void weight(DrawContext context, int x, int y, boolean off) {
        draw(context, "weight", x, y, 7, 7, off);
    }

    /** The picked cards, 10 square. */
    static void cards(DrawContext context, int x, int y) {
        draw(context, "cards", x, y, 10, 10, false);
    }

    /**
     * The badge over a kind's 16-pixel icon at ({@code x}, {@code y}), in its bottom-right corner: a red minus (they
     * lose), a green plus (they get), blue arrows (the common pot); none for the others (their icon says it).
     */
    static void badge(DrawContext context, Kind kind, int x, int y) {
        String name = switch (kind) {
            case LOSE_COINS, LOSE_STARS -> "badge_lose";
            case PITY -> "badge_gain";
            case SHARE_COINS, SHARE_STARS -> "badge_share";
            default -> null;
        };
        if (name != null) draw(context, name, x + 9, y + 9, 7, 7, false);
    }
}
