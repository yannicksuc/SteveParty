package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
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
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The Stencil Hammer's refill, a wheel like its own: at the top two title halves (Dyes on the left, Stencils on the
 * right), then a sector per slot down each side, each a real slot (an empty one shows a see-through dye or creeper
 * stencil), and Back in the middle (to the hammer's wheel). The player's inventory and hotbar are under it.
 */
public class StencilGunScreen extends HandledScreen<StencilGunScreenHandler> {
    private static final Identifier BACK = Steveparty.id("wheel/back");
    private static final int SELECTED_STENCIL = 0xFFFFD83D;
    private static final int SELECTED_DYE = 0xFF5FD3FF;
    /** The plates: the mod's teal, the titles a shade darker. */
    /** The mini-game page's colours: its paper, its punched holes, its teal rules and tabs, its ink. */
    private static final ConsolePaint.Ramp PAPER_RAMP = ConsolePaint.Ramp.of(0x7e9192, 0xffffff, 0xe6f3f4, 0xc7dbdc);
    private static final ConsolePaint.Ramp HOLE = ConsolePaint.Ramp.of(0x7e9192, 0x9fb4b6, 0xb9cacb, 0x9fb4b6);
    private static final int PAPER = ToolWheel.PAPER & 0xFFFFFF, TEAL = 0xFF00B3BD, TEAL2 = 0xFF008C95, ORANGE = 0xFDA757;
    private static final int INK = 0xFF1E3A40, EDGE = 0xFF7E9192, SLOT_BODY = 0xFFF7FBFB, WHITE = 0xFFFFFFFF;
    /** The punched holes down the left edge, as on the mini-game page. */
    private static final int[] HOLES = {20, 70, 120, 170, 220, 270, 320};
    private static final ToolWheel.Layout WHEEL = layout();
    /** Free GUI pixels kept around the screen; with less room (large GUI scales) it is all drawn shrunk to fit. */
    private static final int FIT_MARGIN = 4;
    /** Scale the screen is drawn at: 1, or less when the window is too small for it at this GUI scale. */
    private float fit = 1f;

    public StencilGunScreen(StencilGunScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = StencilGunScreenHandler.WIDTH;
        this.backgroundHeight = StencilGunScreenHandler.HEIGHT;
        this.playerInventoryTitleX = StencilGunScreenHandler.INVENTORY_X + 8;
        this.playerInventoryTitleY = StencilGunScreenHandler.INVENTORY_Y + 4;
    }

    /**
     * At a large GUI scale the refill (340 px tall) does not fit in the window: it is then laid out on a larger
     * virtual screen and drawn shrunk ({@link #fit}), mouse coordinates converted (as the Dice Forge does), so its 18
     * slots and the whole inventory stay visible and usable. The screen's size stays the real one afterwards.
     */
    @Override
    protected void init() {
        int realWidth = this.width, realHeight = this.height;
        if (client != null) {
            realWidth = client.getWindow().getScaledWidth();
            realHeight = client.getWindow().getScaledHeight();
        }
        fit = Math.min(1f, Math.min(realWidth / (float) (backgroundWidth + 2 * FIT_MARGIN),
                realHeight / (float) (backgroundHeight + 2 * FIT_MARGIN)));
        this.width = MathHelper.ceil(realWidth / fit);
        this.height = MathHelper.ceil(realHeight / fit);
        super.init();
        this.width = realWidth;
        this.height = realHeight;
    }

    /** The darkened world behind the screen covers the whole window, not just its shrunk part. */
    @Override
    public void renderInGameBackground(DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().scale(1f / fit, 1f / fit, 1f);
        super.renderInGameBackground(context);
        context.getMatrices().pop();
    }

    private double toScreen(double coordinate) {
        return coordinate / fit;
    }

    /** The wheel's plates: the two titles at the top, a sector per slot (their slots sit on them). */
    private static ToolWheel.Layout layout() {
        float header = StencilGunScreenHandler.HEADER;
        Runnable none = () -> {
        };
        List<ToolWheel.Sector> dyes = new ArrayList<>(), stencils = new ArrayList<>();
        for (int i = 0; i < StencilGunItem.DYE_SLOTS; i++) dyes.add(new ToolWheel.Sector(Text.empty(), null, PAPER, null, false, true, none));
        for (int i = 0; i < StencilGunItem.STENCIL_SLOTS; i++) stencils.add(new ToolWheel.Sector(Text.empty(), null, PAPER, null, false, true, none));
        // The titles in the colours of the page's tabs: orange (dyes), teal (stencils)
        ToolWheel.Sector dyeTitle = new ToolWheel.Sector(Text.empty(), null, ORANGE, null, false, true, none);
        ToolWheel.Sector stencilTitle = new ToolWheel.Sector(Text.empty(), null, TEAL & 0xFFFFFF, null, false, true, none);
        return new ToolWheel.Layout(List.of(new ToolWheel.Ring(StencilGunScreenHandler.RING_INNER, StencilGunScreenHandler.RING_OUTER)),
                List.of(new ToolWheel.Arc(0, -header, 0, List.of(dyeTitle)), new ToolWheel.Arc(0, 0, header, List.of(stencilTitle)),
                        new ToolWheel.Arc(0, header, 180, stencils), new ToolWheel.Arc(0, 180, 360 - header, dyes)),
                null, null, null, null);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int cx = x + StencilGunScreenHandler.CENTER_X, cy = y + StencilGunScreenHandler.CENTER_Y;
        int w = StencilGunScreenHandler.WIDTH, h = StencilGunScreenHandler.HEIGHT;
        // One sheet of the mini-game page's paper: its drop shadow, punched holes, two teal rules at the top and the bottom
        context.fill(x + 2, y + h, x + w, y + h + 1, 0x69000000);
        context.fill(x + 2, y + h + 1, x + w, y + h + 2, 0x32000000);
        ConsolePaint.box(context, x, y, w, h, PAPER_RAMP, 1, 1);
        for (int hy : HOLES) ConsolePaint.disc(context, x + 2, y + hy, 6, HOLE);
        for (int ry : new int[]{3, h - 7}) {
            context.fill(x + 10, y + ry, x + w - 10, y + ry + 1, TEAL);
            context.fill(x + 10, y + ry + 2, x + w - 10, y + ry + 3, TEAL2);
        }
        boolean hub = hub(mouseX, mouseY);
        for (ToolWheel.WheelRaster.Run run : ToolWheel.WheelRaster.runs(WHEEL, null, -1, hub, 1f, false, false)) {
            context.fill(cx + run.x0(), cy + run.y(), cx + run.x1(), cy + run.y() + 1, run.color());
        }
        // Light slots with their outline, on the wheel's sectors and in the inventory under it
        for (Slot slot : handler.slots) ConsolePaint.inset(context, x + slot.x - 1, y + slot.y - 1, 17, 17, SLOT_BODY, EDGE, WHITE);
        // Back, in the middle
        RenderSystem.enableBlend();
        context.drawGuiTexture(BACK, cx - 16, cy - 16, 32, 32);
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
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, INK, false);
    }

    private void title(DrawContext context, Text text, int centerX, int y) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, 0xFFFFFFFF, true);
    }

    private boolean hub(double mouseX, double mouseY) {
        double dx = mouseX - (x + StencilGunScreenHandler.CENTER_X), dy = mouseY - (y + StencilGunScreenHandler.CENTER_Y);
        return dx * dx + dy * dy < Math.pow(StencilGunScreenHandler.RING_INNER - 3, 2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = toScreen(mouseX);
        mouseY = toScreen(mouseY);
        // Back to the hammer's wheel
        if (button == 0 && hub(mouseX, mouseY) && handler.getCursorStack().isEmpty() && client != null && client.player != null) {
            client.player.closeHandledScreen();
            ToolWheel.reopen();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(toScreen(mouseX), toScreen(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(toScreen(mouseX), toScreen(mouseY), button, toScreen(deltaX), toScreen(deltaY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(toScreen(mouseX), toScreen(mouseY), horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Drawn shrunk when the window is too small (see init), the tooltips at full size
        int screenX = (int) toScreen(mouseX), screenY = (int) toScreen(mouseY);
        context.getMatrices().push();
        context.getMatrices().scale(fit, fit, 1f);
        super.render(context, screenX, screenY, delta);
        drawSelection(context);
        context.getMatrices().pop();
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        if (handler.getCursorStack().isEmpty()) {
            if (hub(screenX, screenY)) {
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
