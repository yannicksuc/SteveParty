package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilGunScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * The player's inventory with the Stencil Hammer's slots on its sides: its stencils on the left, its dyes on the
 * right, 3 x 3 each, an empty slot showing the silhouette of what it takes. Where the vanilla inventory crafts, the
 * hammer. The stencil and the colour it strikes with are framed; an empty hammer slot names what it takes.
 */
public class StencilGunScreen extends HandledScreen<StencilGunScreenHandler> {
    private static final Identifier INVENTORY = Identifier.ofVanilla("textures/gui/container/inventory.png");
    private static final int SELECTED_STENCIL = 0xFFFFD83D;
    private static final int SELECTED_DYE = 0xFF5FD3FF;
    /** The vanilla panel colours. */
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, OUTLINE = 0xFF000000;
    private static final int SLOT_DARK = 0xFF373737, SLOT = 0xFF8B8B8B;
    private static final int SIDE_HEIGHT = 7 + 3 * 18 + 7;

    public StencilGunScreen(StencilGunScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = StencilGunScreenHandler.WIDTH;
        this.backgroundHeight = 166;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        int inventoryX = x + StencilGunScreenHandler.INVENTORY_X;
        RenderSystem.enableBlend();
        context.drawTexture(INVENTORY, inventoryX, y, 0, 0, 176, 166);
        RenderSystem.disableBlend();
        // No crafting here: the hammer stands in its place
        context.fill(inventoryX + 87, y + 4, inventoryX + 172, y + 80, PANEL);
        context.getMatrices().push();
        context.getMatrices().translate(inventoryX + 113, y + 25, 0);
        context.getMatrices().scale(2, 2, 1);
        if (client != null && client.player != null && handler.getGunSlot() >= 0) {
            context.drawItem(client.player.getInventory().getStack(handler.getGunSlot()), 0, 0);
        }
        context.getMatrices().pop();
        if (client != null && client.player != null) {
            InventoryScreen.drawEntity(context, inventoryX + 26, y + 8, inventoryX + 75, y + 78, 30, 0.0625F, mouseX, mouseY, client.player);
        }
        // The hammer's slots, a panel on each side
        panel(context, x, y, StencilGunScreenHandler.SIDE, SIDE_HEIGHT);
        panel(context, inventoryX + 176 + StencilGunScreenHandler.GAP, y, StencilGunScreenHandler.SIDE, SIDE_HEIGHT);
        for (int i = 0; i < StencilGunItem.SIZE; i++) {
            Slot slot = handler.slots.get(i);
            slot(context, x + slot.x - 1, y + slot.y - 1);
        }
    }

    /** A vanilla panel: black outline with cut corners, white light top left, grey shadow bottom right. */
    private static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x + 1, y, x + width - 1, y + height, OUTLINE);
        context.fill(x, y + 1, x + width, y + height - 1, OUTLINE);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, LIGHT);
        context.fill(x + 3, y + 3, x + width - 1, y + height - 1, SHADOW);
        context.fill(x + 3, y + 3, x + width - 3, y + height - 3, PANEL);
        context.fill(x + width - 3, y + 1, x + width - 1, y + 3, PANEL);
        context.fill(x + 1, y + height - 3, x + 3, y + height - 1, PANEL);
    }

    /** A vanilla slot, 18 x 18. */
    private static void slot(DrawContext context, int x, int y) {
        context.fill(x, y, x + 18, y + 18, SLOT_DARK);
        context.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        context.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // No titles: the silhouettes and the tooltips tell what goes where
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawSelection(context);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        // An empty slot of the hammer: what it takes
        if (focusedSlot instanceof StencilGunScreenHandler.FilteredSlot slot && !slot.hasStack() && handler.getCursorStack().isEmpty()) {
            boolean stencils = slot.takesStencils();
            context.drawTooltip(textRenderer, List.of(
                    Text.translatable(stencils ? "wheel.steveparty.hammer.empty_stencil" : "wheel.steveparty.hammer.empty_dye"),
                    Text.translatable(stencils ? "screen.steveparty.stencil_gun.stencil_hint" : "screen.steveparty.stencil_gun.dye_hint")
                            .formatted(Formatting.GRAY)), mouseX, mouseY);
        }
    }

    /** Frames the selected stencil and dye (from the gun being loaded, updated as the player loads it). */
    private void drawSelection(DrawContext context) {
        if (client == null || client.player == null || handler.getGunSlot() < 0) return;
        ItemStack gun = client.player.getInventory().getStack(handler.getGunSlot());
        if (!(gun.getItem() instanceof StencilGunItem)) return;
        List<ItemStack> contents = StencilGunItem.contents(gun);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(gun));
        if (!contents.get(selection.stencil()).isEmpty()) frame(context, handler.slots.get(selection.stencil()), SELECTED_STENCIL);
        if (selection.dye() != StencilGunSelection.ENGRAVE) {
            frame(context, handler.slots.get(StencilGunItem.STENCIL_SLOTS + selection.dye()), SELECTED_DYE);
        }
    }

    private void frame(DrawContext context, Slot slot, int color) {
        context.drawBorder(this.x + slot.x - 1, this.y + slot.y - 1, 18, 18, color);
    }
}
