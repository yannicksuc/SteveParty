package fr.lordfinn.steveparty.client.screens.pageeditor;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.RichTextBox;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.client.minigame.PageImagePicker;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImage;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.minigame.PageZone;
import fr.lordfinn.steveparty.minigame.PageZoneTool;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * The « Page » tab: its picture (picked on the player's computer, or dropped on the window), its title, its formats (read
 * only: pawn chips, « modifier » leads to the Formats tab), its zone and its options, its description across the page
 * (formatted as it is shown, on ruled lines, scrolling, with its toolbar and palette), Copy / Unlink. What is written is
 * kept while another tab is shown; the picture is sent as soon as it is picked.
 */
public final class PageTab {
    /** The description: its label and toolbar right under the picture's buttons, its ruled lines down to the bottom row. */
    private static final int TOOL = 14, TOOL_GAP = 2, TOOLBAR_Y = M + 117, AREA_Y = TOOLBAR_Y + 17;
    private static final int AREA_LINES = (BOTTOM_Y - 4 - AREA_Y) / 10, AREA_H = AREA_LINES * 10 + 1;
    /** The formats: their label and the chips under it, in the right column. */
    private static final int FORMATS_Y = M + 34, CHIPS_Y = FORMATS_Y + 12;
    /** The page's zone: its size on a line, then « Tracer la zone » and its ×, level with the picture's buttons. */
    private static final int ZONE_ROW_Y = M + 84, ZONE_TEXT_Y = ZONE_ROW_Y - 11;
    /** Under the zone: its options, « Remettre l'arène en état » and « Mode aventure » (checkboxes). */
    private static final int OPTIONS_Y = ZONE_ROW_Y + 19, CHECK = 10;
    /** The palette's colours (0: the text's own), in two rows; its swatches. */
    private static final char[] PALETTE = {0, 'f', '7', 'c', '6', 'e', 'a', 'b', '9', 'd'};
    private static final int SWATCH = 12, SWATCH_GAP = 2, PALETTE_COLUMNS = 5;
    private static final int PALETTE_WIDTH = PALETTE_COLUMNS * SWATCH + (PALETTE_COLUMNS - 1) * SWATCH_GAP + 6, PALETTE_HEIGHT = 2 * SWATCH + SWATCH_GAP + 6;
    private static final Identifier EMPTY_PAGE = Steveparty.id("mini_game_page/empty");

    private final PageEditor editor;
    private final boolean linked;
    private @Nullable RichTextBox titleBox;
    private @Nullable RichTextBox descriptionBox;
    /** The description's toolbar: bold, italic, colour (its palette), clear the formatting. */
    private @Nullable Tool boldTool, italicTool, colorTool, clearTool;
    private boolean palette;
    private @Nullable ConsoleButton removeButton;
    private @Nullable ConsoleButton clearZoneButton;
    /** What is typed in the fields, kept while another tab is shown. */
    private String titleValue, descriptionValue;
    /** The picture just picked, shown until the server says what became of it. */
    private @Nullable MiniGamePageImage pendingImage;
    private boolean picking;

    public PageTab(PageEditor editor, MiniGamePageData data, boolean linked) {
        this.editor = editor;
        this.linked = linked;
        this.titleValue = data.title();
        this.descriptionValue = data.description();
    }

    private TextRenderer font() {
        return editor.font();
    }

    public @Nullable RichTextBox titleBox() {
        return titleBox;
    }

    public String title() {
        return titleValue;
    }

    public String description() {
        return descriptionValue;
    }

    /** Keeps what is typed in the fields (they are built again on every resize and tab change). */
    public void keepTexts() {
        if (titleBox != null) titleValue = titleBox.getPlain();
        if (descriptionBox != null) descriptionValue = descriptionBox.getCodes();
    }

    /** Another tab is shown: its widgets are gone. */
    public void clear() {
        titleBox = null;
        descriptionBox = null;
        boldTool = italicTool = colorTool = clearTool = null;
        palette = false;
        removeButton = null;
        clearZoneButton = null;
    }

    // ------------------------------------------------------------------ widgets

    public void init() {
        String title = titleValue, description = descriptionValue;
        int x = editor.left(), y = editor.top(), lx = x + LX, rx = x + RX;
        boolean canEdit = editor.canEdit();

        // ---- Left: picture
        ConsoleButton choose = editor.add(new ConsoleButton(lx, y + M + 84, CW - 20, 16, Text.translatable(KEY + "image.choose"),
                ConsoleButton.Kind.PAPER_TEAL, null, this::pickImage));
        choose.setTooltip(Tooltip.of(Text.translatable(KEY + "image.choose.hint", MiniGamePageImages.MAX_WIDTH, MiniGamePageImages.MAX_HEIGHT)));
        choose.active = canEdit;
        // « Remove »: a square button with a cross, its name in its tooltip
        removeButton = editor.add(new ConsoleButton(lx + CW - 16, y + M + 84, 16, 16, Text.translatable(KEY + "image.remove"),
                ConsoleButton.Kind.PAPER, null, () -> {
            pendingImage = null;
            editor.send(new MiniGamePagePayloads.Action(editor.hand(), editor.pageId(), MiniGamePagePayloads.Action.Kind.CLEAR_IMAGE));
        })).decoration(PagePaint::cross);
        removeButton.setTooltip(Tooltip.of(Text.translatable(KEY + "image.remove")));

        // ---- Right, level with the picture's buttons: the zone, drawn with the page in hand (the editor closes)
        ConsoleButton draw = editor.add(new ConsoleButton(rx, y + ZONE_ROW_Y, CW - 20, 16, Text.translatable(KEY + "zone.draw"),
                ConsoleButton.Kind.PAPER_TEAL, null, () -> {
            editor.save();
            editor.send(new MiniGamePagePayloads.Action(editor.hand(), editor.pageId(), MiniGamePagePayloads.Action.Kind.DRAW_ZONE));
            editor.close();
        }));
        draw.setTooltip(Tooltip.of(Text.translatable(KEY + "zone.draw.hint", PageZone.MAX_SIDE)));
        draw.active = canEdit;
        clearZoneButton = editor.add(new ConsoleButton(rx + CW - 16, y + ZONE_ROW_Y, 16, 16, Text.translatable(KEY + "zone.clear"),
                ConsoleButton.Kind.PAPER, null, () -> editor.send(new MiniGamePagePayloads.Action(editor.hand(), editor.pageId(), MiniGamePagePayloads.Action.Kind.CLEAR_ZONE))))
                .decoration(PagePaint::cross);
        clearZoneButton.setTooltip(Tooltip.of(Text.translatable(KEY + "zone.clear")));
        // Its options: the restore only with a zone (greyed, with why, without one); the adventure mode always
        Text restoreLabel = Text.translatable(KEY + "option.restore"), adventureLabel = Text.translatable(KEY + "option.adventure");
        int adventureWidth = CHECK + 4 + font().getWidth(adventureLabel);
        editor.add(new OptionCheck(rx, y + OPTIONS_Y, CW - adventureWidth - 4, restoreLabel, () -> editor.current().restores(),
                () -> editor.current().zone() != null, () -> action(MiniGamePagePayloads.Action.Kind.RESTORE),
                () -> editor.current().zone() == null ? Text.empty().append(Text.translatable(KEY + "option.restore.tooltip")).append("\n")
                        .append(Text.translatable(KEY + "option.restore.no_zone").formatted(Formatting.RED))
                        : Text.translatable(KEY + "option.restore.tooltip")));
        editor.add(new OptionCheck(rx + CW - adventureWidth, y + OPTIONS_Y, adventureWidth, adventureLabel, () -> editor.current().adventure(),
                () -> true, () -> action(MiniGamePagePayloads.Action.Kind.ADVENTURE), () -> Text.translatable(KEY + "option.adventure.tooltip")));

        // ---- The bottom row, on the left: the copies
        Text linkState = Text.translatable(KEY + (linked ? "link.linked" : "link.single")).formatted(Formatting.GRAY);
        ConsoleButton copy = editor.add(new ConsoleButton(lx, y + BOTTOM_Y, 70, BOTTOM_H, Text.translatable(KEY + "copy"), ConsoleButton.Kind.PAPER, null, () -> {
            editor.save();
            action(MiniGamePagePayloads.Action.Kind.COPY);
        }));
        copy.setTooltip(Tooltip.of(Text.empty().append(linkState).append("\n").append(Text.translatable(KEY + "copy.hint"))));
        copy.active = canEdit;
        ConsoleButton unlink = editor.add(new ConsoleButton(lx + 74, y + BOTTOM_Y, 70, BOTTOM_H, Text.translatable(KEY + "unlink"), ConsoleButton.Kind.PAPER, null, () -> {
            editor.save();
            action(MiniGamePagePayloads.Action.Kind.UNLINK);
        }));
        unlink.setTooltip(Tooltip.of(Text.empty().append(linkState).append("\n").append(Text.translatable(KEY + "unlink.hint"))));
        unlink.active = canEdit && linked;

        // ---- Right: the title, written on its line
        titleBox = new RichTextBox(font(), rx, y + M + 15, CW, 12, Text.translatable(KEY + "field.title"),
                Text.translatable(KEY + "field.title.placeholder"), MiniGamePageData.MAX_TITLE_LENGTH, 1, PAPER)
                .paper(2, 2, 1, INK, INK3, 0xFF9FD8FF).plain(true);
        titleBox.setPlain(title);
        titleBox.setEditable(canEdit);
        editor.add(titleBox);

        // The description, across the page: edited as it is shown (never its codes), on ruled lines, the counter on the last
        RichTextBox descriptionBox = new RichTextBox(font(), lx, y + AREA_Y, FULL, (AREA_LINES - 1) * 10 + 2, Text.translatable(KEY + "field.description"),
                Text.translatable(KEY + "field.description.placeholder"), MiniGamePageData.MAX_DESCRIPTION_LENGTH, MiniGamePageData.MAX_DESCRIPTION_LINES, PAPER)
                .paper(6, 2, AREA_LINES - 1, INK, INK3, 0xFF9FD8FF);
        this.descriptionBox = descriptionBox;
        descriptionBox.setCodes(description);
        descriptionBox.setEditable(canEdit);
        editor.add(descriptionBox);

        // Its toolbar, on the line of its label: on the selection, or on what is typed next
        int tx = lx + FULL - 4 * TOOL - 3 * TOOL_GAP, ty = y + TOOLBAR_Y;
        boldTool = tool(tx, ty, "bold", "Ctrl+B", descriptionBox::toggleBold, descriptionBox::isBold);
        italicTool = tool(tx + (TOOL + TOOL_GAP), ty, "italic", "Ctrl+I", descriptionBox::toggleItalic, descriptionBox::isItalic);
        colorTool = tool(tx + 2 * (TOOL + TOOL_GAP), ty, "color", null, () -> palette = !palette, () -> palette);
        clearTool = tool(tx + 3 * (TOOL + TOOL_GAP), ty, "clear", null, descriptionBox::clearFormatting, () -> false);
        palette = false;
    }

    private void action(MiniGamePagePayloads.Action.Kind kind) {
        editor.send(new MiniGamePagePayloads.Action(editor.hand(), editor.pageId(), kind));
    }

    /** An option of the page: a checkbox and its label; greyed when it can't be changed (its tooltip says why). */
    private final class OptionCheck extends PressableWidget {
        private final BooleanSupplier on, enabled;
        private final Runnable action;
        private final Supplier<Text> tooltip;
        private @Nullable Text shownTooltip;

        OptionCheck(int left, int top, int width, Text label, BooleanSupplier on, BooleanSupplier enabled, Runnable action, Supplier<Text> tooltip) {
            super(left, top, width, CHECK + 1, label);
            this.on = on;
            this.enabled = enabled;
            this.action = action;
            this.tooltip = tooltip;
        }

        @Override
        public void onPress() {
            if (active) action.run();
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            active = editor.canEdit() && enabled.getAsBoolean();
            Text wanted = tooltip.get();
            if (!wanted.equals(shownTooltip)) {
                shownTooltip = wanted;
                setTooltip(Tooltip.of(wanted));
            }
            int left = getX(), top = getY();
            ConsolePaint.box(context, left, top, CHECK, CHECK, active ? KEYCAP : KEYCAP_OFF, 1, 1);
            if (active && (isHovered() || isFocused())) ConsolePaint.highlight(context, left, top, CHECK, CHECK, 1, TEAL2, 0);
            if (on.getAsBoolean()) {
                int ink = active ? GREEN2 : INK3;
                // A check mark: two strokes, 2 px thick, on rows 3..7 so it sits in the middle of the key (its bottom
                // edge is darker, the mark looked high on rows 2..6)
                for (int d = 0; d < 2; d++) {
                    context.fill(left + 2 + d, top + 5 + d, left + 3 + d, top + 7 + d, ink);
                }
                for (int d = 0; d < 4; d++) {
                    context.fill(left + 4 + d, top + 6 - d, left + 5 + d, top + 8 - d, ink);
                }
            }
            context.drawText(font(), PagePaint.fit(font(), getMessage(), width - CHECK - 4), left + CHECK + 3, top + 2, active ? INK : INK3, false);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private Tool tool(int left, int top, String name, @Nullable String shortcut, Runnable action, BooleanSupplier lit) {
        Tool tool = new Tool(left, top, name, action, lit);
        MutableText tooltip = Text.translatable(KEY + "format." + name);
        if (shortcut != null) tooltip.append(Text.literal("  " + shortcut).formatted(Formatting.GRAY));
        tooltip.append("\n").append(Text.translatable(KEY + "format." + name + ".hint").formatted(Formatting.DARK_GRAY));
        tool.setTooltip(Tooltip.of(tooltip));
        tool.active = editor.canEdit();
        return editor.add(tool);
    }

    /** A tool of the description's toolbar: a paper key, yellow when its format is on. */
    private final class Tool extends PressableWidget {
        private final String name;
        private final Runnable action;
        private final BooleanSupplier lit;

        Tool(int left, int top, String name, Runnable action, BooleanSupplier lit) {
            super(left, top, TOOL, TOOL, Text.translatable(KEY + "format." + name));
            this.name = name;
            this.action = action;
            this.lit = lit;
        }

        @Override
        public void onPress() {
            if (editor.canEdit() && descriptionBox != null) action.run();
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = getX(), top = getY();
            boolean on = active && lit.getAsBoolean();
            ConsolePaint.box(context, left, top, TOOL, TOOL, Ramp.of(0x7e9192, 0xffffff, on ? 0xfff2a0 : 0xd6ebec, 0xc7dbdc), 1, 1);
            if (active && (isHovered() || isFocused())) ConsolePaint.highlight(context, left, top, TOOL, TOOL, 1, TEAL2, 0);
            int ink = active ? INK : INK3;
            switch (name) {
                case "bold" -> {
                    Text letter = Text.translatable(KEY + "format.bold.letter");
                    context.drawText(font(), letter, left + 4, top + 4, ink, false);
                    context.drawText(font(), letter, left + 5, top + 4, ink, false);
                }
                case "italic" -> {
                    for (int i = 0; i < 7; i++) PartyGui.pixel(context, left + 8 - i / 3, top + 3 + i, ink);
                }
                case "color" -> {
                    int[] stripes = {0xFFE8413C, 0xFFFFD83D, 0xFF3A9BFF};
                    for (int i = 0; i < 3; i++) context.fill(left + 3 + i * 3, top + 4, left + 6 + i * 3, top + 10, active ? stripes[i] : INK3);
                    int colour = descriptionBox == null ? 0 : descriptionBox.color();
                    if (colour > 0) context.fill(left + 3, top + 11, left + 12, top + 12, swatchColor((char) colour));
                }
                default -> {
                    context.drawText(font(), "T", left + 4, top + 4, ink, false);
                    for (int i = 0; i < 8; i++) PartyGui.pixel(context, left + 3 + i, top + 10 - i, active ? RED : INK3);
                }
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    /** After a tool, the typing goes on in the description. */
    public void afterClick(@Nullable Element focused) {
        if (focused == boldTool || focused == italicTool || focused == clearTool) editor.focus(descriptionBox);
    }

    // ------------------------------------------------------------------ the palette

    /** What a colour of the palette looks like (0: the text's own colour). */
    private static int swatchColor(char code) {
        Formatting formatting = code == 0 ? null : Formatting.byCode(code);
        return formatting != null && formatting.getColorValue() != null ? 0xFF000000 | formatting.getColorValue() : WHITE;
    }

    public boolean isPaletteOpen() {
        return palette;
    }

    /** Escape: the palette closes. @return true if it was open */
    public boolean closePalette() {
        if (!palette) return false;
        palette = false;
        return true;
    }

    private int paletteX() {
        return editor.left() + LX + FULL - PALETTE_WIDTH;
    }

    private int paletteY() {
        return editor.top() + TOOLBAR_Y + TOOL + 2;
    }

    /** The swatch of the palette under the mouse, -1 for none. */
    private int swatchAt(double mouseX, double mouseY) {
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = paletteX() + 3 + (i % PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP), sy = paletteY() + 3 + (i / PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP);
            if (HitArea.contains(mouseX, mouseY, sx, sy, SWATCH, SWATCH)) return i;
        }
        return -1;
    }

    /** The palette open: a swatch picks its colour, a click elsewhere closes it. */
    public void clickPalette(double mouseX, double mouseY, int button) {
        int swatch = swatchAt(mouseX, mouseY);
        palette = false;
        if (swatch >= 0 && descriptionBox != null && button == 0) {
            descriptionBox.setColor(PALETTE[swatch]);
            editor.focus(descriptionBox);
            editor.playClick();
        }
    }

    /** The palette, open under the toolbar: its swatches, the colour in use outlined. */
    public void drawPalette(DrawContext context, int mouseX, int mouseY) {
        if (descriptionBox == null) return;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        int px = paletteX(), py = paletteY();
        ConsolePaint.box(context, px, py, PALETTE_WIDTH, PALETTE_HEIGHT, CARD, 1, 1);
        int current = descriptionBox.color(), hovered = swatchAt(mouseX, mouseY);
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = px + 3 + (i % PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP), sy = py + 3 + (i / PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP);
            int c = swatchColor(PALETTE[i]);
            ConsolePaint.box(context, sx, sy, SWATCH, SWATCH, new Ramp(EDGE, PALETTE[i] == 0 ? WHITE : c, PALETTE[i] == 0 ? WHITE : c, PALETTE[i] == 0 ? PAPER3 : c), 1, 1);
            if (PALETTE[i] == 0) for (int d = 0; d < 8; d++) PartyGui.pixel(context, sx + 2 + d, sy + 9 - d, RED);
            if (i == hovered) context.drawBorder(sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, TEAL2);
            else if (PALETTE[i] == current) context.drawBorder(sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, 0xFFFFC52E);
        }
        if (hovered >= 0) {
            context.drawTooltip(font(), Text.translatable(KEY + "format.color." + (PALETTE[hovered] == 0 ? "none" : String.valueOf(PALETTE[hovered]))), mouseX, mouseY);
        }
        context.getMatrices().pop();
    }

    // ------------------------------------------------------------------ the picture

    private @Nullable MiniGamePageImage image(MiniGamePageData data) {
        return pendingImage != null ? pendingImage : data.image();
    }

    /** The server said what became of the picture sent. */
    public void forgetPendingImage() {
        pendingImage = null;
    }

    private void pickImage() {
        if (picking || !editor.canEdit()) return;
        picking = true;
        editor.setStatus(Text.translatable(KEY + "status.picking"), false);
        PageImagePicker.pick(this::onPicked);
    }

    /** A picture file dropped on the window. */
    public void filesDragged(List<Path> paths) {
        if (picking || !editor.canEdit() || paths.isEmpty()) return;
        picking = true;
        editor.setStatus(Text.translatable(KEY + "status.picking"), false);
        PageImagePicker.read(paths.get(0), this::onPicked);
    }

    private void onPicked(PageImagePicker.Result result) {
        picking = false;
        MinecraftClient client = editor.client();
        if (client == null || client.currentScreen != editor) return;
        if (result.cancelled()) {
            editor.setStatus(null, false);
            return;
        }
        if (result.bytes() == null || result.image() == null) {
            editor.setStatus(result.error(), true);
            return;
        }
        byte[] bytes = result.bytes();
        String hash = MiniGamePageImages.hash(bytes);
        MiniGamePageClient.putImage(hash, result.image());
        pendingImage = new MiniGamePageImage(hash, result.image().getWidth(), result.image().getHeight(), bytes.length, "", null);
        int total = (bytes.length + MiniGamePagePayloads.CHUNK_SIZE - 1) / MiniGamePagePayloads.CHUNK_SIZE;
        for (int index = 0; index < total; index++) {
            int from = index * MiniGamePagePayloads.CHUNK_SIZE;
            editor.send(new MiniGamePagePayloads.Upload(editor.hand(), editor.pageId(), index, total,
                    Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + MiniGamePagePayloads.CHUNK_SIZE))));
        }
        editor.setStatus(Text.translatable(KEY + "status.sending"), false);
    }

    // ------------------------------------------------------------------ drawing

    private FormatChips.Look look(MiniGamePageData data, int index) {
        return new FormatChips.Look(false, false, !data.hasPipesFor(data.formats().get(index)), false, 13);
    }

    /** Where the status line goes: under the formats' chips. */
    public int statusTop(MiniGamePageData data) {
        List<int[]> at = FormatChips.flow(font(), data.formats(), i -> look(data, i), CW, 3);
        return editor.top() + CHIPS_Y + (at.isEmpty() ? 0 : at.getLast()[1] + 13) + 3;
    }

    /** The lowest the status line may reach: above the zone's line. */
    public int statusBottom() {
        return editor.top() + ZONE_TEXT_Y - 2;
    }

    /** The format chip under the mouse, -1 for none. */
    private int chipAt(double mouseX, double mouseY) {
        MiniGamePageData data = editor.current();
        List<int[]> at = FormatChips.flow(font(), data.formats(), i -> look(data, i), CW, 3);
        for (int i = 0; i < at.size(); i++) {
            int cx = editor.left() + RX + at.get(i)[0], cy = editor.top() + CHIPS_Y + at.get(i)[1];
            if (HitArea.contains(mouseX, mouseY, cx, cy, at.get(i)[2], 13)) return i;
        }
        return -1;
    }

    private Text modifyLink() {
        return Text.translatable(KEY + "formats.modify");
    }

    /** Over « modifier », which leads to the Formats tab. */
    public boolean overModifyLink(double mouseX, double mouseY) {
        int w = font().getWidth(modifyLink());
        return HitArea.contains(mouseX, mouseY, editor.left() + RX + CW - w, editor.top() + FORMATS_Y - 1, w, 11);
    }

    /** Before the widgets are drawn: « Remove » follows the picture. */
    public void beforeRender(MiniGamePageData data) {
        if (removeButton != null) removeButton.active = editor.canEdit() && image(data) != null;
    }

    public void draw(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        int x = editor.left(), y = editor.top(), lx = x + LX, rx = x + RX, top = y + M;
        boolean canEdit = editor.canEdit();
        // ---- The picture, in its green frame
        ConsolePaint.box(context, lx, top, CW, 81, FRAME, 1, 1);
        MiniGamePageImage image = image(data);
        int px = lx + 3, py = top + 3, pw = CW - 6, ph = 75;
        if (image == null) {
            context.fill(px, py, px + pw, py + ph, 0xFFE6FFF4);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 0xCC / 255f);
            context.drawGuiTexture(EMPTY_PAGE, lx + (CW - 16) / 2, top + 22, 16, 16);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
            PagePaint.centred(context, font(), Text.translatable(KEY + "image.none"), lx + CW / 2, top + 44, GREEN2);
            if (canEdit) PagePaint.centred(context, font(), Text.translatable(KEY + "image.drop_hint"), lx + CW / 2, top + 56, INK3);
        } else {
            MiniGamePageClient.Picture picture = MiniGamePageClient.picture(image, pw, ph);
            if (picture != null) picture.draw(context, px, py, pw, ph, 0xFFFFFFFF);
            else {
                context.fill(px, py, px + pw, py + ph, 0xFFE6FFF4);
                PagePaint.centred(context, font(), Text.translatable(KEY + "image.loading"), lx + CW / 2, top + 34, INK3);
            }
        }

        // ---- The title, written on its line (highlighted while it is being written)
        context.drawText(font(), Text.translatable(KEY + "field.title"), rx, top + 2, INK2, false);
        boolean titleFocused = titleBox != null && titleBox.isFocused();
        if (titleFocused) context.fill(rx, top + 23, rx + CW, top + 27, HIGHLIGHT);
        context.fill(rx, top + 27, rx + CW, top + 28, titleFocused ? TEAL2 : EDGE);
        // ---- The formats: read only, « modifier » leads to their tab
        context.drawText(font(), Text.translatable(KEY + "tab.formats"), rx, y + FORMATS_Y, INK2, false);
        Text link = modifyLink();
        int lw = font().getWidth(link) - 1;
        boolean overLink = editor.popup() == null && overModifyLink(mouseX, mouseY);
        context.drawText(font(), link, rx + CW - lw, y + FORMATS_Y, overLink ? TEAL : TEAL2, false);
        context.fill(rx + CW - lw, y + FORMATS_Y + 8, rx + CW, y + FORMATS_Y + 9, overLink ? TEAL : TEAL2);
        FormatChips.drawFlow(context, font(), data.formats(), i -> look(data, i), rx, y + CHIPS_Y, CW, 3);
        // ---- The zone: its size, or none
        PageZone zone = data.zone();
        if (clearZoneButton != null) clearZoneButton.active = canEdit && zone != null;
        if (zone == null) {
            context.drawText(font(), Text.translatable(KEY + "zone.none"), rx, y + ZONE_TEXT_Y, INK3, false);
        } else {
            Text label = Text.translatable(KEY + "zone.label");
            context.drawText(font(), label, rx, y + ZONE_TEXT_Y, INK2, false);
            context.drawText(font(), PagePaint.fit(font(), PageZoneTool.size(zone.box()), CW - font().getWidth(label) - 3),
                    rx + font().getWidth(label) + 3, y + ZONE_TEXT_Y, INK, false);
        }

        // ---- The description, across the page: its label and toolbar, its ruled lines and margin, its counter on the last line
        context.drawText(font(), Text.translatable(KEY + "field.description"), lx, y + TOOLBAR_Y + 3, INK2, false);
        int ay = y + AREA_Y;
        boolean descriptionFocused = descriptionBox != null && descriptionBox.isFocused();
        for (int ly = ay + 9; ly < ay + AREA_H; ly += 10) context.fill(lx, ly, lx + FULL, ly + 1, descriptionFocused ? 0xFFA8D2D6 : RULE);
        context.fill(lx + 2, ay, lx + 3, ay + AREA_H, MARGIN_LINE);
        if (descriptionBox != null) {
            // The characters shown, out of how many: orange near the end, red at it (and when one was refused)
            int count = descriptionBox.visibleLength(), max = descriptionBox.maxVisible();
            boolean refused = Util.getMeasuringTimeMs() - descriptionBox.refusedAt() < 600;
            int color = count >= max || refused ? RED : count >= max * 9 / 10 ? ORANGE2 : INK3;
            String counter = count + "/" + max;
            context.drawText(font(), counter, lx + FULL - font().getWidth(counter), ay + (AREA_LINES - 1) * 10 + 2, color, false);
        }
    }

    /** Over everything: the tooltip of a format chip, or of « modifier ». */
    public void drawOverlay(DrawContext context, int mouseX, int mouseY) {
        int chip = chipAt(mouseX, mouseY);
        if (chip >= 0) PagePaint.formatTooltip(context, font(), editor.current(), chip, mouseX, mouseY);
        else if (overModifyLink(mouseX, mouseY)) context.drawTooltip(font(), Text.translatable(KEY + "formats.modify.hint"), mouseX, mouseY);
    }
}
