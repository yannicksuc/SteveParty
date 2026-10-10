package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.hud.HudPlacements.Hud;
import fr.lordfinn.steveparty.hud.HudPlacements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * « Party HUD layout »: the player drags each party HUD where they want it, resizes it (the handle at its bottom-right
 * corner, or the mouse wheel over it), shows or hides it, and resets one or both. HUDs snap to the edges and to the
 * centre lines. Without a running party, a made-up one is shown. Saved when the screen closes.
 */
public class PartyHudEditScreen extends Screen {
    private static final int SNAP = 6;
    private static final int HANDLE = 6;
    /** The width of the buttons' column (toggle, anchor picker, reset). */
    private static final int COLUMN_W = 128;
    private final @Nullable Screen parent;
    private @Nullable Hud dragged;
    private boolean resizing;
    private float grabX, grabY;
    private float resizeStartDistance, resizeStartScale;
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
        // A column on the left (free at the default layout: the bar and its notice at the top, the standings on the
        // right): per HUD its toggle, its anchor picker and its reset; then « reset all » and « done »
        int left = 4, top = buttonsTop();
        ButtonWidget[] toggles = new ButtonWidget[Hud.values().length];
        for (Hud hud : Hud.values()) {
            int y = top + hud.ordinal() * 24;
            toggles[hud.ordinal()] = addDrawableChild(ButtonWidget.builder(toggleText(hud), button -> toggle(hud))
                    .dimensions(left, y, 84, 20).build());
            addDrawableChild(new AnchorPicker(hud, left + 86, y));
            ButtonWidget reset = addDrawableChild(ButtonWidget.builder(Text.literal("\u21BA"), button -> reset(hud))
                    .dimensions(left + 108, y, 20, 20).build());
            reset.setTooltip(Tooltip.of(Text.translatable(switch (hud) {
                case TURN_BAR -> "screen.steveparty.party_hud_layout.reset_turn_bar";
                case STANDINGS -> "screen.steveparty.party_hud_layout.reset_standings";
                case NOTICE -> "screen.steveparty.party_hud_layout.reset_notice";
            })));
        }
        turnBarToggle = toggles[Hud.TURN_BAR.ordinal()];
        standingsToggle = toggles[Hud.STANDINGS.ordinal()];
        noticeToggle = toggles[Hud.NOTICE.ordinal()];
        int last = top + Hud.values().length * 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.steveparty.party_hud_layout.reset_all"), button -> {
            for (Hud hud : Hud.values()) reset(hud);
        }).dimensions(left, last, 63, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(left + 65, last, 41, 20).build());
        ButtonWidget help = addDrawableChild(ButtonWidget.builder(Text.literal("?"), button -> {
        }).dimensions(left + 108, last, 20, 20).build());
        help.setTooltip(Tooltip.of(Text.translatable("screen.steveparty.party_hud_layout.help")));
        PartyHud.editing = true;
    }

    /** The buttons' column: its top, under the turn bar and its notice, centred on the screen when there is room. */
    private int buttonsTop() {
        int column = (Hud.values().length + 1) * 24 - 4;
        return Math.max(Math.min(100, height - column - 26), (height - column) / 2);
    }

    /** An anchor picker: the nine anchors of a HUD, its anchor lit; a click puts it at another one. */
    private final class AnchorPicker extends ClickableWidget {
        private final Hud hud;

        AnchorPicker(Hud hud, int x, int y) {
            super(x, y, 20, 20, Text.translatable("screen.steveparty.party_hud_layout.anchor", hudName(hud)));
            this.hud = hud;
            setTooltip(Tooltip.of(Text.translatable("screen.steveparty.party_hud_layout.anchor", hudName(hud))));
        }

        private int cellAt(double mouseX, double mouseY) {
            int cx = (int) Math.floor((mouseX - getX() - 2) / 6), cy = (int) Math.floor((mouseY - getY() - 2) / 6);
            return cx >= 0 && cx < 3 && cy >= 0 && cy < 3 ? cy * 3 + cx : -1;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? 0xFF505050 : 0xFF303030);
            context.drawBorder(getX(), getY(), width, height, isHovered() ? 0xFFFFFFFF : 0xFF8B8B8B);
            int hovered = isHovered() ? cellAt(mouseX, mouseY) : -1;
            HudPlacements.Anchor current = PartyHudLayout.get(hud).anchor;
            for (int i = 0; i < 9; i++) {
                int x = getX() + 3 + (i % 3) * 6, y = getY() + 3 + (i / 3) * 6;
                int colour = current.ordinal() == i ? 0xFFFFD23A : i == hovered ? 0xFFFFFFFF : 0xFF8B8B8B;
                context.fill(x, y, x + 4, y + 4, colour);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            int cell = cellAt(mouseX, mouseY);
            if (cell >= 0) PartyHudLayout.moveTo(hud, HudPlacements.Anchor.values()[cell]);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
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
        HudPlacements.Placement placement = PartyHudLayout.get(hud);
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
        // The HUDs stack by depth (HudDepth): here right over the screen's shade, the frames and widgets in front of them
        HudDepth.restart(context, 1);
        PartyHud.draw(context, real != null ? real : sample, true, true, true);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, HudDepth.top(context));
        for (Hud hud : Hud.values()) drawFrame(context, hud, mouseX, mouseY);

        // While a HUD is dragged: the nine anchors, the one it will stick to lit
        if (dragged != null && !resizing) drawAnchors(context, dragged);
        // The title over the column (how it works: the « ? » button's tooltip, nothing over the HUDs)
        int under = buttonsTop() + (Hud.values().length + 1) * 24 + 2;
        int titleHeight = UiText.wrapped(context, textRenderer, title, 4, under, COLUMN_W, 0xFFFFFFFF, true);
        if (real == null) UiText.wrapped(context, textRenderer, Text.translatable("screen.steveparty.party_hud_layout.sample"),
                4, under + titleHeight + 1, COLUMN_W, 0xFF9A9A9A, true);
        super.render(context, mouseX, mouseY, delta);
        context.getMatrices().pop();
    }

    /** The nine anchor points of the screen, the dragged HUD's (the zone its centre is in) lit. */
    private void drawAnchors(DrawContext context, Hud hud) {
        float[] b = PartyHud.bounds(hud);
        HudPlacements.Anchor active = HudPlacements.Anchor.nearest(
                b[0] + b[2] / 2, b[1] + b[3] / 2, width, height);
        for (HudPlacements.Anchor anchor : HudPlacements.Anchor.values()) {
            int x = MathHelper.clamp(Math.round(anchor.screenX(width)), 3, width - 4);
            int y = MathHelper.clamp(Math.round(anchor.screenY(height)), 3, height - 4);
            int r = anchor == active ? 3 : 2;
            context.fill(x - r - 1, y - r - 1, x + r + 1, y + r + 1, 0xC0000000);
            context.fill(x - r, y - r, x + r, y + r, anchor == active ? 0xFFFFD23A : 0xB0FFFFFF);
        }
    }

    private void drawFrame(DrawContext context, Hud hud, int mouseX, int mouseY) {
        float[] b = PartyHud.bounds(hud);
        int x = Math.round(b[0]), y = Math.round(b[1]), w = Math.round(b[2]), h = Math.round(b[3]);
        if (w <= 0 || h <= 0) return;
        boolean active = dragged == hud || isOver(hud, mouseX, mouseY);
        int color = active ? 0xFFFFD23A : 0x90FFFFFF;
        context.drawBorder(x - 1, y - 1, w + 2, h + 2, color);
        context.fill(x + w - HANDLE + 1, y + h - HANDLE + 1, x + w + 1, y + h + 1, color);
        HudPlacements.Placement placement = PartyHudLayout.get(hud);
        Text label = Text.translatable("screen.steveparty.party_hud_layout.label", hudName(hud),
                String.format(Locale.ROOT, "%.2f", placement.scale).replaceAll("0+$", "").replaceAll("\\.$", ""));
        // On the screen, scrolling if it is wider
        int labelWidth = Math.min(textRenderer.getWidth(label), width - 4);
        int labelX = MathHelper.clamp(x, 2, Math.max(2, width - labelWidth - 2));
        int labelY = y + h + 3 + 9 < height ? y + h + 3 : y - 11;
        context.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2, labelY + 9, 0xA0000000);
        UiText.line(context, textRenderer, label, labelX, labelY, labelWidth, active ? 0xFFFFD23A : 0xFFFFFFFF, false);
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
        HudPlacements.Placement placement = PartyHudLayout.get(hud);
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
        HudPlacements.Placement placement = PartyHudLayout.get(hud);
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
