package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilGunScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The Stencil Hammer's refill, a wheel like its own: at the top two title halves (Dyes on the left, Stencils on the
 * right), then a sector per slot down each side, each a real slot (an empty one shows a see-through dye or creeper
 * stencil), and Back in the middle (to the hammer's wheel). The player's inventory and hotbar are under it.
 */
public class StencilGunScreen extends HandledScreen<StencilGunScreenHandler> {
    private static final Identifier INVENTORY = Identifier.ofVanilla("textures/gui/container/generic_54.png");
    private static final Identifier BACK = Steveparty.id("wheel/back");
    private static final int SELECTED_STENCIL = 0xFFFFD83D;
    private static final int SELECTED_DYE = 0xFF5FD3FF;
    /** The plates: the mod's teal, the titles a shade darker. */
    private static final int TEAL = 0x7FA3A9, TITLE = 0x55767B;
    private static final int SLOT_DARK = 0xFF373737, SLOT = 0xFF8B8B8B, LIGHT = 0xFFFFFFFF;
    private static final ToolWheel.Layout WHEEL = layout();

    public StencilGunScreen(StencilGunScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = StencilGunScreenHandler.WIDTH;
        this.backgroundHeight = StencilGunScreenHandler.HEIGHT;
        this.playerInventoryTitleX = StencilGunScreenHandler.INVENTORY_X + 8;
        this.playerInventoryTitleY = StencilGunScreenHandler.INVENTORY_Y + 3;
    }

    /** The wheel's plates: the two titles at the top, a sector per slot (their slots sit on them). */
    private static ToolWheel.Layout layout() {
        float header = StencilGunScreenHandler.HEADER;
        Runnable none = () -> {
        };
        List<ToolWheel.Sector> dyes = new ArrayList<>(), stencils = new ArrayList<>();
        for (int i = 0; i < StencilGunItem.DYE_SLOTS; i++) dyes.add(new ToolWheel.Sector(Text.empty(), null, TEAL, null, false, true, none));
        for (int i = 0; i < StencilGunItem.STENCIL_SLOTS; i++) stencils.add(new ToolWheel.Sector(Text.empty(), null, TEAL, null, false, true, none));
        ToolWheel.Sector title = new ToolWheel.Sector(Text.empty(), null, TITLE, null, false, true, none);
        return new ToolWheel.Layout(List.of(new ToolWheel.Ring(StencilGunScreenHandler.RING_INNER, StencilGunScreenHandler.RING_OUTER)),
                List.of(new ToolWheel.Arc(0, -header, 0, List.of(title)), new ToolWheel.Arc(0, 0, header, List.of(title)),
                        new ToolWheel.Arc(0, header, 180, stencils), new ToolWheel.Arc(0, 180, 360 - header, dyes)),
                null, null, null, null);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int cx = x + StencilGunScreenHandler.CENTER_X, cy = y + StencilGunScreenHandler.CENTER_Y;
        boolean hub = hub(mouseX, mouseY);
        for (ToolWheel.WheelRaster.Run run : ToolWheel.WheelRaster.runs(WHEEL, null, -1, hub, 1f, false, false)) {
            context.fill(cx + run.x0(), cy + run.y(), cx + run.x1(), cy + run.y() + 1, run.color());
        }
        // A vanilla slot on each sector
        for (int i = 0; i < StencilGunItem.SIZE; i++) {
            Slot slot = handler.slots.get(i);
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            context.fill(sx, sy, sx + 18, sy + 18, SLOT_DARK);
            context.fill(sx + 1, sy + 1, sx + 18, sy + 18, LIGHT);
            context.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
        }
        // Back, in the middle
        RenderSystem.enableBlend();
        context.drawGuiTexture(BACK, cx - 16, cy - 16, 32, 32);
        // The player's inventory: the bottom of a vanilla chest
        context.drawTexture(INVENTORY, x + StencilGunScreenHandler.INVENTORY_X, y + StencilGunScreenHandler.INVENTORY_Y, 0, 126, 176, 96);
        RenderSystem.disableBlend();
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // The two title halves at the top of the wheel
        double r = (StencilGunScreenHandler.RING_INNER + StencilGunScreenHandler.RING_OUTER) / 2.0;
        double a = Math.toRadians(StencilGunScreenHandler.HEADER / 2);
        int ty = StencilGunScreenHandler.CENTER_Y - (int) Math.round(Math.cos(a) * r) - 4;
        int dx = (int) Math.round(Math.sin(a) * r);
        title(context, Text.translatable("screen.steveparty.stencil_gun.dyes"), StencilGunScreenHandler.CENTER_X - dx, ty);
        title(context, Text.translatable("screen.steveparty.stencil_gun.stencils"), StencilGunScreenHandler.CENTER_X + dx, ty);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFF404040, false);
    }

    private void title(DrawContext context, Text text, int centerX, int y) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, 0xFFFFFFFF, true);
    }

    private boolean hub(double mouseX, double mouseY) {
        double dx = mouseX - (x + StencilGunScreenHandler.CENTER_X), dy = mouseY - (y + StencilGunScreenHandler.CENTER_Y);
        return dx * dx + dy * dy < Math.pow(StencilGunScreenHandler.RING_INNER - 3, 2);
    }

    @Override
    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        double dx = mouseX - (x + StencilGunScreenHandler.CENTER_X), dy = mouseY - (y + StencilGunScreenHandler.CENTER_Y);
        boolean onWheel = dx * dx + dy * dy < Math.pow(StencilGunScreenHandler.RING_OUTER + 2, 2);
        boolean onInventory = mouseX >= x + StencilGunScreenHandler.INVENTORY_X && mouseX < x + StencilGunScreenHandler.INVENTORY_X + 176
                && mouseY >= y + StencilGunScreenHandler.INVENTORY_Y && mouseY < y + StencilGunScreenHandler.HEIGHT;
        return !onWheel && !onInventory;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Back to the hammer's wheel
        if (button == 0 && hub(mouseX, mouseY) && handler.getCursorStack().isEmpty() && client != null && client.player != null) {
            client.player.closeHandledScreen();
            ToolWheel.reopen();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawSelection(context);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        if (handler.getCursorStack().isEmpty()) {
            if (hub(mouseX, mouseY)) {
                context.drawTooltip(textRenderer, Text.translatable("wheel.steveparty.back"), mouseX, mouseY);
            } else if (focusedSlot instanceof StencilGunScreenHandler.FilteredSlot slot && !slot.hasStack()) {
                context.drawTooltip(textRenderer, Text.translatable(slot.takesStencils()
                        ? "screen.steveparty.stencil_gun.stencil_hint" : "screen.steveparty.stencil_gun.dye_hint"), mouseX, mouseY);
            }
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
