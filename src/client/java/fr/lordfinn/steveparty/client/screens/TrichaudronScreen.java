package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronScreenHandler;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A tamed Trichaudron's screen, in its own colours: the light warm grey of its cauldron's top, its name on an orange
 * plate (its cartridge's colour). On the left its saddle slot; beside it, a basin: itself, turning
 * after the mouse, in front of its lava, as high as its tank is full (a notch every {@link TrichaudronEntity#SPILL_PER}
 * buckets, the count on hover). Below, the player's inventory, straight on the panel. Everything on the slots' 18 px grid.
 */
public class TrichaudronScreen extends HandledScreen<TrichaudronScreenHandler> {
    /** The panel: its cauldron's top, a light warm grey. */
    private static final PartyGui.Theme CAULDRON = new PartyGui.Theme(0xFF1E1A17, 0xFFF4F0EA, 0xFFD5CEC6, 0xFF8C8279);
    /** The name plate: its cartridge's orange. */
    private static final PartyGui.Theme ORANGE = new PartyGui.Theme(0xFF3A1404, 0xFFFFA45E,
            0xFF000000 | TrichaudronCartridgeItem.COLOR, 0xFF9E3A05);
    /** Its slots: a darker warm grey, their shadow along the top. */
    private static final int SLOT_FILL = 0xFFA39990, SLOT_SHADOW = 0xFF54483F;
    /** The basin: its iron rim, its dark inside, the lava's bright surface, the tank's notches. */
    private static final int RIM = 0xFF5E544C, RIM_LIGHT = 0xFFF4F0EA, INSIDE = 0xFF2A2522, INSIDE_LOW = 0xFF3A2A22,
            SURFACE = 0xFFFFC23A, NOTCH = 0xFF8C8279;
    private static final Identifier LAVA = Identifier.ofVanilla("block/lava_still");
    /** The basin: from the second column of the slots' grid to the panel's right margin, three rows high. */
    private static final int BASIN_X = 25, BASIN_Y = 17, BASIN_W = 176 - 7 - BASIN_X, BASIN_H = 54;
    /** Its preview's scale (pixels a block: it is huge). */
    private static final int PREVIEW_SIZE = 5;

    public TrichaudronScreen(TrichaudronScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        PartyGui.panel(context, x, y, backgroundWidth, backgroundHeight, CAULDRON);
        for (Slot slot : handler.slots) PartyGui.slot(context, x + slot.x, y + slot.y, SLOT_FILL, SLOT_SHADOW);
        drawBasin(context, x + BASIN_X, y + BASIN_Y, mouseX, mouseY);
        PartyGui.titlePlate(context, textRenderer, x + backgroundWidth / 2, y - 11, 0, title, ORANGE);
    }

    /** Its cauldron: a rim, the lava up to its tank's level, itself in front. */
    private void drawBasin(DrawContext context, int left, int top, int mouseX, int mouseY) {
        int right = left + BASIN_W, bottom = top + BASIN_H;
        PartyGui.panel(context, left, top, BASIN_W, BASIN_H, new PartyGui.Theme(0xFF1E1A17, RIM, RIM, RIM_LIGHT));
        int inL = left + 3, inT = top + 3, inR = right - 3, inB = bottom - 3;
        context.fillGradient(inL, inT, inR, inB, INSIDE, INSIDE_LOW);
        TrichaudronEntity trichaudron = handler.trichaudron();
        int tank = trichaudron == null ? 0 : trichaudron.getTank();
        int level = Math.round((inB - inT) * tank / (float) TrichaudronEntity.TANK_MAX);
        if (level > 0) {
            Sprite lava = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(LAVA);
            context.enableScissor(inL, inB - level, inR, inB);
            for (int tx = inL; tx < inR; tx += 16) {
                for (int ty = inB - 16; ty > inB - level - 16; ty -= 16) context.drawSprite(tx, ty, 0, 16, 16, lava);
            }
            context.disableScissor();
            context.fillGradient(inL, inB - level, inR, inB, 0x30000000, 0x70000000);
            context.fill(inL, inB - level, inR, inB - level + 1, SURFACE);
        }
        // the tank's notches, every SPILL_PER buckets, along the right edge
        for (int b = TrichaudronEntity.SPILL_PER; b < TrichaudronEntity.TANK_MAX; b += TrichaudronEntity.SPILL_PER) {
            int ny = inB - Math.round((inB - inT) * b / (float) TrichaudronEntity.TANK_MAX);
            context.fill(inR - 3, ny, inR, ny + 1, NOTCH);
        }
        if (trichaudron != null) {
            InventoryScreen.drawEntity(context, inL, inT, inR, inB, PREVIEW_SIZE, 0.0625f, mouseX, mouseY, trichaudron);
        }
    }

    /** No titles: the plate names it, the slots and the basin speak for themselves. */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Slot saddle = handler.getSlot(0);
        if (!saddle.hasStack()) {
            PartyGui.ghostItem(context, new ItemStack(Items.SADDLE), x + saddle.x, y + saddle.y, null, 0xAAA39990);
        }
        TrichaudronEntity trichaudron = handler.trichaudron();
        if (trichaudron != null && focusedSlot == null
                && HitArea.contains(mouseX, mouseY, x + BASIN_X, y + BASIN_Y, BASIN_W, BASIN_H)) {
            context.drawTooltip(textRenderer, Text.translatable("screen.steveparty.trichaudron.tank",
                    trichaudron.getTank(), TrichaudronEntity.TANK_MAX), mouseX, mouseY);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
