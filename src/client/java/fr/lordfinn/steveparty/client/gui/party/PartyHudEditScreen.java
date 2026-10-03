package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.party.PartyHudLayout.Hud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * « Party HUD layout »: the player drags each party HUD where they want it, resizes it (the handle at its bottom-right
 * corner, or the mouse wheel over it), shows or hides it, and resets one or both. HUDs snap to the edges and to the
 * centre lines. Without a running party, a made-up one is shown. Saved when the screen closes.
 */
public class PartyHudEditScreen extends Screen {
    private static final int SNAP = 6;
    private static final int HANDLE = 6;
    private final @Nullable Screen parent;
    private @Nullable Hud dragged;
    private boolean resizing;
    private float grabX, grabY;
    private float resizeStartDistance, resizeStartScale;
    private boolean snappedX, snappedY;
    private float snapLineX = -1, snapLineY = -1;
    private ButtonWidget turnBarToggle, standingsToggle, noticeToggle;
    private PartyHudModel sample;

    public PartyHudEditScreen(@Nullable Screen parent) {
        super(Text.translatable("screen.steveparty.party_hud_layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        sample = PartyHudModel.sample(client.player == null ? null : client.player.getUuid(),
                client.player == null ? "Steve" : client.player.getGameProfile().getName());
        // Two rows in the middle of the screen: clear of the HUDs' default places (the bar and the standings at the
        // top, the notice over the hotbar)
        int centerX = width / 2;
        int top = buttonsTop();
        turnBarToggle = addDrawableChild(ButtonWidget.builder(toggleText(Hud.TURN_BAR), button -> toggle(Hud.TURN_BAR))
                .dimensions(centerX - 206, top, 100, 20).build());
        standingsToggle = addDrawableChild(ButtonWidget.builder(toggleText(Hud.STANDINGS), button -> toggle(Hud.STANDINGS))
                .dimensions(centerX - 102, top, 100, 20).build());
        noticeToggle = addDrawableChild(ButtonWidget.builder(toggleText(Hud.NOTICE), button -> toggle(Hud.NOTICE))
                .dimensions(centerX + 2, top, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(centerX + 106, top, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.steveparty.party_hud_layout.reset_turn_bar"), button -> reset(Hud.TURN_BAR))
                .dimensions(centerX - 206, top + 24, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.steveparty.party_hud_layout.reset_standings"), button -> reset(Hud.STANDINGS))
                .dimensions(centerX - 102, top + 24, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.steveparty.party_hud_layout.reset_notice"), button -> reset(Hud.NOTICE))
                .dimensions(centerX + 2, top + 24, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.steveparty.party_hud_layout.reset_all"), button -> {
            for (Hud hud : Hud.values()) reset(hud);
        }).dimensions(centerX + 106, top + 24, 100, 20).build());
        PartyHud.editing = true;
    }

    /** The buttons' first row: a little under the middle of the screen. */
    private int buttonsTop() {
        return height / 2 - 6;
    }

    private static Text hudName(Hud hud) {
        return Text.translatable(switch (hud) {
            case TURN_BAR -> "screen.steveparty.party_hud_layout.turn_bar";
            case STANDINGS -> "screen.steveparty.party_hud_layout.standings";
            case NOTICE -> "screen.steveparty.party_hud_layout.notice";
        });
    }

    private static Text toggleText(Hud hud) {
        return Text.translatable(PartyHudLayout.get(hud).visible ? "screen.steveparty.party_hud_layout.shown" : "screen.steveparty.party_hud_layout.hidden", hudName(hud));
    }

    private void toggle(Hud hud) {
        PartyHudLayout.Placement placement = PartyHudLayout.get(hud);
        placement.visible = !placement.visible;
        PartyHudLayout.changed();
        refreshToggles();
    }

    private void refreshToggles() {
        turnBarToggle.setMessage(toggleText(Hud.TURN_BAR));
        standingsToggle.setMessage(toggleText(Hud.STANDINGS));
        noticeToggle.setMessage(toggleText(Hud.NOTICE));
    }

    private void reset(Hud hud) {
        PartyHudLayout.reset(hud);
        refreshToggles();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Drawn by render(), under the HUDs: a light veil only, the HUDs are judged over the real scene
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x30000000);
        // Guides while a HUD snaps
        if (dragged != null && !resizing) {
            if (snapLineX >= 0) context.fill(Math.round(snapLineX), 0, Math.round(snapLineX) + 1, height, 0x80FFD23A);
            if (snapLineY >= 0) context.fill(0, Math.round(snapLineY), width, Math.round(snapLineY) + 1, 0x80FFD23A);
        }
        PartyHudModel real = PartyHud.model();
        PartyHud.draw(context, real != null ? real : sample, true, true, true);
        for (Hud hud : Hud.values()) drawFrame(context, hud, mouseX, mouseY);

        // How it works, over the buttons
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("screen.steveparty.party_hud_layout.help"), Math.min(320, width - 40));
        int y = buttonsTop() - 4 - lines.size() * 10 - (real == null ? 10 : 0);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, y - 12, 0xFFFFFFFF);
        for (OrderedText line : lines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xFFD0D0D0);
            y += 10;
        }
        if (real == null)
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.steveparty.party_hud_layout.sample"), width / 2, y, 0xFF9A9A9A);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawFrame(DrawContext context, Hud hud, int mouseX, int mouseY) {
        float[] b = PartyHud.bounds(hud);
        int x = Math.round(b[0]), y = Math.round(b[1]), w = Math.round(b[2]), h = Math.round(b[3]);
        if (w <= 0 || h <= 0) return;
        boolean active = dragged == hud || isOver(hud, mouseX, mouseY);
        int color = active ? 0xFFFFD23A : 0x90FFFFFF;
        context.drawBorder(x - 1, y - 1, w + 2, h + 2, color);
        context.fill(x + w - HANDLE + 1, y + h - HANDLE + 1, x + w + 1, y + h + 1, color);
        PartyHudLayout.Placement placement = PartyHudLayout.get(hud);
        Text label = Text.translatable("screen.steveparty.party_hud_layout.label", hudName(hud),
                String.format(java.util.Locale.ROOT, "%.2f", placement.scale).replaceAll("0+$", "").replaceAll("\\.$", ""));
        int labelWidth = textRenderer.getWidth(label);
        int labelX = MathHelper.clamp(x, 2, Math.max(2, width - labelWidth - 2));
        int labelY = y + h + 3 + 9 < height ? y + h + 3 : y - 11;
        context.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2, labelY + 9, 0xA0000000);
        context.drawText(textRenderer, label, labelX, labelY, active ? 0xFFFFD23A : 0xFFFFFFFF, false);
    }

    private static boolean isOver(Hud hud, double mouseX, double mouseY) {
        float[] b = PartyHud.bounds(hud);
        return mouseX >= b[0] - 2 && mouseX <= b[0] + b[2] + 2 && mouseY >= b[1] - 2 && mouseY <= b[1] + b[3] + 2;
    }

    private static boolean isOverHandle(Hud hud, double mouseX, double mouseY) {
        float[] b = PartyHud.bounds(hud);
        return mouseX >= b[0] + b[2] - HANDLE - 1 && mouseX <= b[0] + b[2] + 3 && mouseY >= b[1] + b[3] - HANDLE - 1 && mouseY <= b[1] + b[3] + 3;
    }

    private static @Nullable Hud hudAt(double mouseX, double mouseY) {
        // The turn bar is drawn first: the standings are on top when they overlap
        Hud[] huds = Hud.values();
        for (int i = huds.length - 1; i >= 0; i--) {
            if (isOver(huds[i], mouseX, mouseY)) return huds[i];
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        Hud hud = hudAt(mouseX, mouseY);
        if (hud == null) return false;
        float[] b = PartyHud.bounds(hud);
        dragged = hud;
        PartyHud.dragging = true;
        resizing = isOverHandle(hud, mouseX, mouseY);
        grabX = (float) mouseX - b[0];
        grabY = (float) mouseY - b[1];
        resizeStartDistance = Math.max(8, (float) Math.hypot(mouseX - b[0], mouseY - b[1]));
        resizeStartScale = PartyHudLayout.get(hud).scale;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragged == null || button != 0) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        float[] b = PartyHud.bounds(dragged);
        if (resizing) {
            float distance = (float) Math.hypot(mouseX - b[0], mouseY - b[1]);
            rescale(dragged, resizeStartScale * distance / resizeStartDistance);
            return true;
        }
        float x = (float) mouseX - grabX, y = (float) mouseY - grabY;
        float w = b[2], h = b[3];
        snapLineX = -1;
        snapLineY = -1;
        // Snap to the edges (their margin) and to the centre lines
        if (Math.abs(x - PartyHudLayout.MARGIN) < SNAP) x = PartyHudLayout.MARGIN;
        else if (Math.abs(x + w - (width - PartyHudLayout.MARGIN)) < SNAP) x = width - PartyHudLayout.MARGIN - w;
        else if (Math.abs(x + w / 2 - width / 2f) < SNAP) {
            x = width / 2f - w / 2;
            snapLineX = width / 2f;
        }
        if (Math.abs(y - PartyHudLayout.MARGIN) < SNAP) y = PartyHudLayout.MARGIN;
        else if (Math.abs(y + h - (height - PartyHudLayout.MARGIN)) < SNAP) y = height - PartyHudLayout.MARGIN - h;
        else if (Math.abs(y + h / 2 - height / 2f) < SNAP) {
            y = height / 2f - h / 2;
            snapLineY = height / 2f;
        }
        x = MathHelper.clamp(x, 0, Math.max(0, width - w));
        y = MathHelper.clamp(y, 0, Math.max(0, height - h));
        PartyHudLayout.place(dragged, x, y, w, h, width, height);
        return true;
    }

    /** New scale, the HUD's top-left corner staying where it is. */
    private void rescale(Hud hud, float scale) {
        PartyHudLayout.Placement placement = PartyHudLayout.get(hud);
        float snapped = PartyHudLayout.snapScale(scale);
        if (snapped == placement.scale) return;
        float[] b = PartyHud.bounds(hud);
        float ratio = snapped / placement.scale;
        placement.scale = snapped;
        PartyHudLayout.place(hud, b[0], b[1], b[2] * ratio, b[3] * ratio, width, height);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragged != null) {
            dragged = null;
            PartyHud.dragging = false;
            resizing = false;
            snapLineX = -1;
            snapLineY = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Hud hud = hudAt(mouseX, mouseY);
        if (hud == null || verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        PartyHudLayout.Placement placement = PartyHudLayout.get(hud);
        rescale(hud, placement.scale + Math.signum((float) verticalAmount) * PartyHudLayout.SCALE_STEP);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        // Always called (Escape, another screen, disconnection), unlike close()
        super.removed();
        PartyHud.editing = false;
        PartyHud.dragging = false;
        PartyHudLayout.save();
    }
}
