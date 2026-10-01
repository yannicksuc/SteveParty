package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.cartridge.CartridgePanel;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.payloads.custom.CartridgeSlotScrollPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * A cartridge's own menu (right click in the air with it, or a block's hook): its shell only, and the player's
 * inventory under it when a module needs it (the Inventory Cartridge's ghost slots take their items from it).
 */
public class CartridgeScreen extends HandledScreen<CartridgeScreenHandler> {
    /** Kept free around the shell, in the window. */
    static final int MARGIN = 4;
    private final CartridgePanel panel;

    public CartridgeScreen(CartridgeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        MinecraftClient client = MinecraftClient.getInstance();
        boolean fixed = handler.withInventory();
        this.panel = new CartridgePanel(client, () -> handler.ref().resolve(inventory.player), () -> handler.ref().pos().orElse(null),
                () -> handler.syncId, () -> handler.ref().mayEdit(inventory.player),
                fixed ? CartridgeLayout.MAX_CONTENT_WITH_INVENTORY : CartridgeLayout.MAX_CONTENT_ALONE,
                fixed ? CartridgeLayout.SHELL_W_WITH_INVENTORY : 0, fixed ? CartridgeLayout.SHELL_H_WITH_INVENTORY : 0);
    }

    /** With the inventory: the handler's fixed size (its slots); alone: the shell's own size, within the window. */
    private void fit() {
        if (handler.withInventory()) {
            panel.setAvailable(CartridgeLayout.SHELL_W_WITH_INVENTORY, CartridgeLayout.SHELL_H_WITH_INVENTORY);
            backgroundWidth = handler.backgroundWidth();
            backgroundHeight = handler.backgroundHeight();
        } else {
            panel.setAvailable(width - 2 * MARGIN, height - 2 * MARGIN);
            backgroundWidth = panel.width();
            backgroundHeight = panel.height();
        }
    }

    @Override
    protected void init() {
        fit();
        super.init();
        panel.setOrigin(x + handler.shellX(), y);
        titleX = -10000; // the label of the shell shows the name
        playerInventoryTitleX = (backgroundWidth - CartridgeLayout.INVENTORY_W) / 2 + 8;
        playerInventoryTitleY = CartridgeLayout.SHELL_H_WITH_INVENTORY + CartridgeLayout.INVENTORY_GAP + 6;
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // The texts may take more or fewer lines (a chest linked, a network joined...): the shell follows
        if (panel.tick() && client != null) init(client, width, height);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        panel.render(context, mouseX, mouseY);
        if (handler.withInventory()) drawInventoryPanel(context, x + (backgroundWidth - CartridgeLayout.INVENTORY_W) / 2,
                y + CartridgeLayout.SHELL_H_WITH_INVENTORY + CartridgeLayout.INVENTORY_GAP);
    }

    /** The player's inventory: the light panel of the mod's inventories, its 3 rows and the hotbar. */
    static void drawInventoryPanel(DrawContext context, int px, int py) {
        PartyGui.panel(context, px, py, CartridgeLayout.INVENTORY_W, CartridgeLayout.INVENTORY_H, PartyGui.PANEL);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slot(context, px + 7 + col * 18, py + 16 + row * 18);
        }
        for (int col = 0; col < 9; col++) slot(context, px + 7 + col * 18, py + 74);
    }

    static void slot(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
        context.fill(sx, sy, sx + 17, sy + 1, 0xFF373737);
        context.fill(sx, sy, sx + 1, sy + 17, 0xFF373737);
        context.fill(sx + 1, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
        context.fill(sx + 17, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        if (handler.withInventory()) {
            context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, PartyGui.TEXT_DARK, false);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        CartridgePanel.drawGhostMarks(context, handler.slots, x, y);
        if (!panel.renderTooltip(context, mouseX, mouseY)) drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (panel.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double amount = verticalAmount + horizontalAmount;
        if (scrollGhost(handler.slots, handler.syncId, x, y, mouseX, mouseY, amount)) return true;
        if (panel.mouseScrolled(mouseX, mouseY, amount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** The wheel over a ghost slot: its quantity (predicted here, applied by the server). */
    static boolean scrollGhost(java.util.List<Slot> slots, int syncId, int originX, int originY, double mouseX, double mouseY, double amount) {
        if (amount == 0) return false;
        for (Slot slot : slots) {
            if (!(slot instanceof GhostSlot ghost) || !slot.isEnabled()) continue;
            if (mouseX < originX + slot.x - 1 || mouseX >= originX + slot.x + 17 || mouseY < originY + slot.y - 1 || mouseY >= originY + slot.y + 17) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) return true;
            ghost.onScroll(amount);
            ClientPlayNetworking.send(CartridgeSlotScrollPayload.fromScroll(syncId, slot.id, amount));
            return true;
        }
        return false;
    }
}
