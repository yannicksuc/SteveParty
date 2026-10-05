package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.client.utils.TileStampTextures;
import fr.lordfinn.steveparty.components.TileStampComponent;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;

/** The look stamped on a cartridge, drawn in its tooltip as it shows on a tile (see TileStamping). */
public class TileStampTooltipComponent implements TooltipComponent {
    private static final int SIZE = TileStampTextures.SIDE;
    private final TileStampComponent stamp;

    public TileStampTooltipComponent(TileStampComponent stamp) {
        this.stamp = stamp;
    }

    public static void register() {
        TooltipComponentCallback.EVENT.register(data -> data instanceof TileStampComponent stamp ? new TileStampTooltipComponent(stamp) : null);
    }

    @Override
    public int getHeight() {
        return SIZE + 2;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        return SIZE;
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) {
        RenderSystem.enableBlend();
        context.drawTexture(TileStampTextures.get(stamp), x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.disableBlend();
    }
}
