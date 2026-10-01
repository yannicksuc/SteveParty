package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.client.minigame.PageImagePicker;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImage;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The editor of a mini-game page, opened by right-clicking the page in hand. On the left its picture (picked on the
 * player's computer, or dropped on the window) and its copies; on the right its title, its description, the team
 * layouts the mini-game accepts and its numbers of players. What is written is sent when the editor closes; the
 * picture is sent as soon as it is picked. A player who may not build only reads the page.
 * <p>
 * The « Pipes » tab shows the pipes linked to the page (a click on a pipe mouth, page in hand) as cards in a column
 * per role: a click on a card gives it the next role, dragging it to another column that role, a right click unlinks
 * it. Under the columns, what is missing for the mini-game to be played in each of the ways it ticks.
 */
public class MiniGamePageEditorScreen extends Screen {
    private static final String KEY = "gui.steveparty.mini_game_page.";
    private static final int WIDTH = 320, HEIGHT = 228;
    private static final int MARGIN = 8, COLUMN = 148, RIGHT_X = MARGIN + COLUMN + MARGIN;
    private static final int TOP = 18;
    private static final int PICTURE_WIDTH = 144, PICTURE_HEIGHT = 81;
    private static final int FIELD_BODY = 0xFF3B4247, PICTURE_BODY = 0xFF1E2327;
    private static final int ROW = 16;

    private final Hand hand;
    private final UUID page;
    private final boolean canEdit;
    private final boolean linked;
    /** What the server has (the texts and settings as they were sent last). */
    private MiniGamePageData saved;

    private int x, y;
    private TextFieldWidget titleField;
    private EditBoxWidget descriptionBox;
    private final EnumSet<MiniGameMode> modes;
    private int minPlayers, maxPlayers;
    private final PartyButton[] modeButtons = new PartyButton[MiniGameMode.values().length];
    private PartyButton removeButton;

    /** The picture just picked, shown until the server says what became of it. */
    private @Nullable MiniGamePageImage pendingImage;
    private boolean picking;
    private @Nullable Text status;
    private boolean statusIsError;
    private boolean opened;

    // ---- The « Pipes » tab
    /** The columns: two rows of four. */
    private static final MiniGamePipeRole[] COLUMNS = {MiniGamePipeRole.PLAYERS, MiniGamePipeRole.TEAM_A, MiniGamePipeRole.TEAM_B,
            MiniGamePipeRole.SPECTATORS, MiniGamePipeRole.TEAM_C, MiniGamePipeRole.TEAM_D, MiniGamePipeRole.ENTRY, MiniGamePipeRole.EXIT};
    private static final int COLUMN_WIDTH = 74, COLUMN_GAP = 2, COLUMNS_TOP = 50, HEADER = 11, CARD = 12, CARDS_SHOWN = 3;
    private static final int COLUMN_HEIGHT = HEADER + CARDS_SHOWN * CARD + 2, ROW_GAP = 3;
    private boolean pipesTab;
    private PartyButton pageTabButton, pipesTabButton;
    /** What is typed in the fields, kept while the other tab is shown. */
    private String titleValue, descriptionValue;
    /** First card shown in each column. */
    private final int[] scroll = new int[COLUMNS.length];
    /** The card held by the mouse, and whether it moved (a drag) since it was pressed. */
    private @Nullable MiniGamePipeLink held;
    private boolean dragged;
    private double pressX, pressY;

    public MiniGamePageEditorScreen(Hand hand, MiniGamePageData data, boolean canEdit, boolean linked, @Nullable String status) {
        super(Text.translatable(KEY + "title"));
        this.hand = hand;
        this.page = data.id();
        this.canEdit = canEdit && !MiniGamePageData.NO_ID.equals(data.id());
        this.linked = linked;
        this.saved = data;
        this.modes = EnumSet.copyOf(data.modes());
        this.minPlayers = data.minPlayers();
        this.maxPlayers = data.maxPlayers();
        this.titleValue = data.title();
        this.descriptionValue = data.description();
        if (status != null) this.status = Text.literal(status);
        else if (!this.canEdit) this.status = Text.translatable(KEY + "status.read_only");
    }

    /** The page as the server has it now (its picture may change while the editor is open). */
    private MiniGamePageData current() {
        MiniGamePageData known = MiniGamePageClient.page(page);
        return known != null ? known : saved;
    }

    @Override
    protected void init() {
        x = (width - WIDTH) / 2;
        y = Math.max(12, (height - HEIGHT) / 2 + 4);
        // init() runs again on every resize and tab change: keep what the player already typed
        keepTexts();
        String title = titleValue, description = descriptionValue;
        int lx = x + MARGIN, rx = x + RIGHT_X;

        // ---- Tabs, sitting on the top edge
        pageTabButton = addDrawableChild(new PartyButton(x + WIDTH - 8 - 104, y - 7, 50, 16, Text.translatable(KEY + "tab.page"), b -> showTab(false)));
        pipesTabButton = addDrawableChild(new PartyButton(x + WIDTH - 8 - 52, y - 7, 52, 16, Text.translatable(KEY + "tab.pipes"), b -> showTab(true)));
        pipesTabButton.setTooltip(Tooltip.of(Text.translatable(KEY + "tab.pipes.hint")));
        pageTabButton.setSelected(!pipesTab);
        pipesTabButton.setSelected(pipesTab);
        addDrawableChild(new PartyButton(rx, y + HEIGHT - 26, COLUMN, 18, ScreenTexts.DONE, b -> close()).style(PartyButton.Style.PRIMARY));
        if (pipesTab) {
            titleField = null;
            descriptionBox = null;
            removeButton = null;
            return;
        }

        // ---- Left: picture
        int pictureButtons = y + TOP + PICTURE_HEIGHT + 4 + 4;
        PartyButton choose = addDrawableChild(new PartyButton(lx, pictureButtons, 96, ROW, Text.translatable(KEY + "image.choose"), b -> pickImage()));
        choose.setTooltip(Tooltip.of(Text.translatable(KEY + "image.choose.hint", MiniGamePageImages.MAX_WIDTH, MiniGamePageImages.MAX_HEIGHT)));
        choose.active = canEdit;
        removeButton = addDrawableChild(new PartyButton(lx + 100, pictureButtons, COLUMN - 100, ROW, Text.translatable(KEY + "image.remove"), b -> {
            pendingImage = null;
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.CLEAR_IMAGE));
        }));

        // ---- Left: copies
        int copies = y + 176;
        PartyButton copy = addDrawableChild(new PartyButton(lx, copies, 72, ROW, Text.translatable(KEY + "copy"), b -> {
            save();
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.COPY));
        }));
        copy.setTooltip(Tooltip.of(Text.translatable(KEY + "copy.hint")));
        copy.active = canEdit;
        PartyButton unlink = addDrawableChild(new PartyButton(lx + 76, copies, 72, ROW, Text.translatable(KEY + "unlink"), b -> {
            save();
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.UNLINK));
        }));
        unlink.setTooltip(Tooltip.of(Text.translatable(KEY + "unlink.hint")));
        unlink.active = canEdit && linked;

        // ---- Right: texts
        titleField = new TextFieldWidget(textRenderer, rx + 5, y + TOP + 10 + 5, COLUMN - 8, 10, Text.translatable(KEY + "field.title"));
        titleField.setDrawsBackground(false);
        titleField.setMaxLength(MiniGamePageData.MAX_TITLE_LENGTH);
        titleField.setText(title);
        titleField.setPlaceholder(Text.translatable(KEY + "field.title.placeholder").formatted(Formatting.DARK_GRAY));
        titleField.setEditable(canEdit);
        addDrawableChild(titleField);

        descriptionBox = new InsetEditBox(textRenderer, rx, y + TOP + 41, COLUMN, 56,
                Text.translatable(KEY + "field.description.placeholder"), Text.translatable(KEY + "field.description"));
        descriptionBox.setMaxLength(MiniGamePageData.MAX_DESCRIPTION_LENGTH);
        descriptionBox.setText(description);
        descriptionBox.active = canEdit;
        addDrawableChild(descriptionBox);

        // ---- Right: type of mini-game (several can be ticked)
        int modesY = y + TOP + 112;
        for (MiniGameMode mode : MiniGameMode.values()) {
            int i = mode.ordinal();
            PartyButton button = new PartyButton(rx + (i % 2) * 75, modesY + (i / 2) * (ROW + 2), 73, ROW, mode.text(), b -> toggle(mode));
            button.setTooltip(Tooltip.of(Text.translatable(mode.translationKey() + ".hint").append("\n")
                    .append(Text.translatable(KEY + "field.type.hint").formatted(Formatting.GRAY))));
            button.setSelected(modes.contains(mode));
            button.active = canEdit;
            modeButtons[i] = addDrawableChild(button);
        }

        // ---- Right: players
        int playersY = y + TOP + 162;
        stepper(rx + 22, playersY, () -> minPlayers, value -> {
            minPlayers = value;
            if (maxPlayers < minPlayers) maxPlayers = minPlayers;
        });
        stepper(rx + 100, playersY, () -> maxPlayers, value -> {
            maxPlayers = value;
            if (minPlayers > maxPlayers) minPlayers = maxPlayers;
        });

        if (canEdit && !opened && saved.title().isEmpty()) setInitialFocus(titleField);

        if (!opened && client != null && client.player != null) {
            opened = true;
            client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
        }
    }

    private void keepTexts() {
        if (titleField != null) titleValue = titleField.getText();
        if (descriptionBox != null) descriptionValue = descriptionBox.getText();
    }

    private void showTab(boolean pipes) {
        if (pipes == pipesTab) return;
        keepTexts();
        pipesTab = pipes;
        held = null;
        clearAndInit();
    }

    /** « − value + » : the two buttons of a number of players. */
    private void stepper(int left, int top, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        PartyButton minus = addDrawableChild(new PartyButton(left, top, 14, ROW, Text.literal("−"), b -> {
            int step = hasShiftDown() ? 4 : 1;
            setter.accept(Math.max(MiniGamePageData.MIN_PLAYERS, getter.getAsInt() - step));
        }));
        PartyButton plus = addDrawableChild(new PartyButton(left + 32, top, 14, ROW, Text.literal("+"), b -> {
            int step = hasShiftDown() ? 4 : 1;
            setter.accept(Math.min(MiniGamePageData.MAX_PLAYERS, getter.getAsInt() + step));
        }));
        minus.active = plus.active = canEdit;
    }

    private void toggle(MiniGameMode mode) {
        // At least one way to play
        if (modes.contains(mode)) {
            if (modes.size() > 1) modes.remove(mode);
        } else {
            modes.add(mode);
        }
        for (MiniGameMode each : MiniGameMode.values()) modeButtons[each.ordinal()].setSelected(modes.contains(each));
    }

    // ------------------------------------------------------------------ picture

    private void pickImage() {
        if (picking || !canEdit) return;
        picking = true;
        setStatus(Text.translatable(KEY + "status.picking"), false);
        PageImagePicker.pick(this::onPicked);
    }

    /** A picture file dropped on the window. */
    @Override
    public void filesDragged(List<Path> paths) {
        if (picking || !canEdit || paths.isEmpty() || pipesTab) return;
        picking = true;
        setStatus(Text.translatable(KEY + "status.picking"), false);
        PageImagePicker.read(paths.get(0), this::onPicked);
    }

    private void onPicked(PageImagePicker.Result result) {
        picking = false;
        if (client == null || client.currentScreen != this) return;
        if (result.cancelled()) {
            setStatus(null, false);
            return;
        }
        if (result.bytes() == null || result.image() == null) {
            setStatus(result.error(), true);
            return;
        }
        byte[] bytes = result.bytes();
        String hash = MiniGamePageImages.hash(bytes);
        MiniGamePageClient.putImage(hash, result.image());
        pendingImage = new MiniGamePageImage(hash, result.image().getWidth(), result.image().getHeight(), bytes.length, "", null);
        int total = (bytes.length + MiniGamePagePayloads.CHUNK_SIZE - 1) / MiniGamePagePayloads.CHUNK_SIZE;
        for (int index = 0; index < total; index++) {
            int from = index * MiniGamePagePayloads.CHUNK_SIZE;
            send(new MiniGamePagePayloads.Upload(hand, page, index, total,
                    java.util.Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + MiniGamePagePayloads.CHUNK_SIZE))));
        }
        setStatus(Text.translatable(KEY + "status.sending"), false);
    }

    /** The server's answer to the last request. */
    public void onStatus(UUID about, MiniGamePagePayloads.Status.Code code) {
        if (!about.equals(page)) return;
        boolean error = code == MiniGamePagePayloads.Status.Code.IMAGE_REFUSED || code == MiniGamePagePayloads.Status.Code.COPY_NEEDS_PAPER
                || code == MiniGamePagePayloads.Status.Code.NOT_ALLOWED;
        if (code == MiniGamePagePayloads.Status.Code.IMAGE_SAVED || code == MiniGamePagePayloads.Status.Code.IMAGE_REFUSED) pendingImage = null;
        setStatus(Text.translatable(KEY + "status." + code.name().toLowerCase(java.util.Locale.ROOT)), error);
    }

    private void setStatus(@Nullable Text text, boolean error) {
        status = text;
        statusIsError = error;
    }

    /** The editor is about to be opened again on the same hand: saves, and gives its status line to the next one. */
    public @Nullable String handOver() {
        save();
        return status == null || statusIsError ? null : status.getString();
    }

    // ------------------------------------------------------------------ saving

    private static void send(net.minecraft.network.packet.CustomPayload payload) {
        if (ClientPlayNetworking.canSend(payload.getId())) ClientPlayNetworking.send(payload);
    }

    /** Sends the texts and settings if they changed. */
    private void save() {
        if (!canEdit) return;
        keepTexts();
        MiniGamePageData edited = saved.withTexts(titleValue, descriptionValue).withModes(modes).withPlayers(minPlayers, maxPlayers);
        if (edited.title().equals(saved.title()) && edited.description().equals(saved.description())
                && edited.modes().equals(saved.modes()) && edited.minPlayers() == saved.minPlayers() && edited.maxPlayers() == saved.maxPlayers()) return;
        saved = edited;
        send(new MiniGamePagePayloads.Edit(hand, page, edited.title(), edited.description(), MiniGameMode.toMask(edited.modes()),
                edited.minPlayers(), edited.maxPlayers()));
    }

    @Override
    public void removed() {
        save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MiniGamePageData data = current();
        MiniGamePageImage image = pendingImage != null ? pendingImage : data.image();
        if (removeButton != null) removeButton.active = canEdit && image != null;
        super.render(context, mouseX, mouseY, delta);
        if (pipesTab) drawPipesOverlay(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        PartyGui.panel(context, x, y, WIDTH, HEIGHT, PartyGui.PANEL);
        // On the left: the tabs sit on the right of the top edge
        PartyGui.titlePlate(context, textRenderer, x + 104, y - 12, 0, title, PartyGui.FLAG_RED);
        int lx = x + MARGIN, rx = x + RIGHT_X;
        MiniGamePageData data = current();

        // ---- Status
        if (status != null) {
            List<OrderedText> statusLines = textRenderer.wrapLines(status, COLUMN);
            int top = y + HEIGHT - 26 + (statusLines.size() > 1 ? -1 : 5);
            for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
                context.drawText(textRenderer, statusLines.get(i), lx, top + i * 10, statusIsError ? PartyGui.TEXT_ERROR : PartyGui.TEXT_OK, false);
            }
        }
        if (pipesTab) {
            drawPipes(context, data, mouseX, mouseY);
            return;
        }

        // ---- Picture
        PartyGui.inset(context, lx, y + TOP, COLUMN, PICTURE_HEIGHT + 4, PICTURE_BODY, false, false);
        MiniGamePageImage image = pendingImage != null ? pendingImage : data.image();
        int px = lx + 2, py = y + TOP + 2;
        if (image == null) {
            centered(context, Text.translatable(KEY + "image.none"), lx + COLUMN / 2, py + PICTURE_HEIGHT / 2 - (canEdit ? 10 : 4), 0xFFB8C0C6);
            if (canEdit) centered(context, Text.translatable(KEY + "image.drop_hint"), lx + COLUMN / 2, py + PICTURE_HEIGHT / 2 + 2, 0xFF7C868D);
        } else {
            MiniGamePageClient.Picture picture = MiniGamePageClient.picture(image, PICTURE_WIDTH, PICTURE_HEIGHT);
            if (picture != null) picture.draw(context, px, py, PICTURE_WIDTH, PICTURE_HEIGHT, 0xFFFFFFFF);
            else centered(context, Text.translatable(KEY + "image.loading"), lx + COLUMN / 2, py + PICTURE_HEIGHT / 2 - 4, 0xFF7C868D);
        }
        if (data.image() != null && pendingImage == null && !data.image().uploader().isEmpty()) {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "image.by", data.image().uploader()), COLUMN),
                    lx, y + TOP + PICTURE_HEIGHT + 4 + 4 + ROW + 4, PartyGui.TEXT_SOFT, false);
        }

        // ---- Copies
        context.drawText(textRenderer, Text.translatable(KEY + "link"), lx, y + 143, PartyGui.TEXT_DARK, false);
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(KEY + (linked ? "link.linked" : "link.single")), COLUMN);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            context.drawText(textRenderer, lines.get(i), lx, y + 154 + i * 10, linked ? 0xFF1F6F8B : PartyGui.TEXT_SOFT, false);
        }

        // ---- Texts
        context.drawText(textRenderer, Text.translatable(KEY + "field.title"), rx, y + TOP, PartyGui.TEXT_DARK, false);
        PartyGui.inset(context, rx, y + TOP + 10, COLUMN, ROW + 2, FIELD_BODY, titleField != null && titleField.isFocused(), false);
        context.drawText(textRenderer, Text.translatable(KEY + "field.description"), rx, y + TOP + 31, PartyGui.TEXT_DARK, false);
        context.drawText(textRenderer, Text.translatable(KEY + "field.type"), rx, y + TOP + 102, PartyGui.TEXT_DARK, false);

        // ---- Players
        int playersY = y + TOP + 162;
        context.drawText(textRenderer, Text.translatable(KEY + "field.players"), rx, playersY - 10, PartyGui.TEXT_DARK, false);
        context.drawText(textRenderer, Text.translatable(KEY + "field.players.min"), rx, playersY + 4, PartyGui.TEXT_SOFT, false);
        context.drawText(textRenderer, Text.translatable(KEY + "field.players.max"), rx + 78, playersY + 4, PartyGui.TEXT_SOFT, false);
        centered(context, Text.literal(String.valueOf(minPlayers)), rx + 22 + 23, playersY + 4, PartyGui.TEXT_DARK);
        centered(context, Text.literal(String.valueOf(maxPlayers)), rx + 100 + 23, playersY + 4, PartyGui.TEXT_DARK);
    }

    // ------------------------------------------------------------------ the « Pipes » tab

    private int columnX(int column) {
        return x + MARGIN + (column % 4) * (COLUMN_WIDTH + COLUMN_GAP);
    }

    private int columnY(int column) {
        return y + COLUMNS_TOP + (column / 4) * (COLUMN_HEIGHT + ROW_GAP);
    }

    /** The column under the mouse, -1 for none. */
    private int columnAt(double mouseX, double mouseY) {
        for (int column = 0; column < COLUMNS.length; column++) {
            int cx = columnX(column), cy = columnY(column);
            if (mouseX >= cx && mouseX < cx + COLUMN_WIDTH && mouseY >= cy && mouseY < cy + COLUMN_HEIGHT) return column;
        }
        return -1;
    }

    /** The card under the mouse, null for none. */
    private @Nullable MiniGamePipeLink cardAt(double mouseX, double mouseY) {
        int column = columnAt(mouseX, mouseY);
        if (column < 0) return null;
        List<MiniGamePipeLink> pipes = current().pipes(COLUMNS[column]);
        int row = (int) Math.floor((mouseY - (columnY(column) + HEADER + 1)) / CARD);
        int index = row + scroll[column];
        return row >= 0 && row < CARDS_SHOWN && index < pipes.size() ? pipes.get(index) : null;
    }

    private static Text cardText(MiniGamePipeLink link) {
        net.minecraft.util.math.BlockPos pos = link.mouth().pos();
        return Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private static int textOn(int rgb) {
        int luminance = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        return luminance > 150 ? 0xFF2E2E2E : 0xFFFFFFFF;
    }

    private void drawPipes(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        int lx = x + MARGIN;
        // How to link pipes
        List<OrderedText> guide = textRenderer.wrapLines(Text.translatable(KEY + "pipes.guide"), WIDTH - 2 * MARGIN);
        for (int i = 0; i < Math.min(3, guide.size()); i++) {
            context.drawText(textRenderer, guide.get(i), lx, y + TOP + i * 10, PartyGui.TEXT_SOFT, false);
        }

        int hovered = held != null && dragged ? columnAt(mouseX, mouseY) : -1;
        for (int column = 0; column < COLUMNS.length; column++) {
            MiniGamePipeRole role = COLUMNS[column];
            List<MiniGamePipeLink> pipes = data.pipes(role);
            int cx = columnX(column), cy = columnY(column);
            scroll[column] = Math.max(0, Math.min(scroll[column], pipes.size() - CARDS_SHOWN));
            PartyGui.inset(context, cx, cy, COLUMN_WIDTH - 1, COLUMN_HEIGHT - 1, column == hovered ? 0xFF55606A : FIELD_BODY, false, false);
            // Header: the role, in the colour of its pipes
            int color = 0xFF000000 | role.color();
            context.fill(cx + 1, cy + 1, cx + COLUMN_WIDTH - 1, cy + HEADER, color);
            String count = pipes.isEmpty() ? "" : String.valueOf(pipes.size());
            context.drawText(textRenderer, fit(role.text(), COLUMN_WIDTH - 6 - textRenderer.getWidth(count)), cx + 3, cy + 2, textOn(role.color()), false);
            context.drawText(textRenderer, count, cx + COLUMN_WIDTH - 3 - textRenderer.getWidth(count), cy + 2, textOn(role.color()), false);
            for (int row = 0; row < CARDS_SHOWN && row + scroll[column] < pipes.size(); row++) {
                MiniGamePipeLink link = pipes.get(row + scroll[column]);
                if (link.equals(held) && dragged) continue;
                int top = cy + HEADER + 1 + row * CARD;
                boolean over = held == null && link.equals(cardAt(mouseX, mouseY));
                drawCard(context, link, cx + 2, top, over);
            }
            // More cards than shown: marks
            if (scroll[column] > 0) context.drawText(textRenderer, "▲", cx + COLUMN_WIDTH - 9, cy + HEADER + 2, 0xFFB8C0C6, false);
            if (scroll[column] + CARDS_SHOWN < pipes.size()) {
                context.drawText(textRenderer, "▼", cx + COLUMN_WIDTH - 9, cy + COLUMN_HEIGHT - 10, 0xFFB8C0C6, false);
            }
        }

        // What is missing for each way to play ticked on the page
        int top = columnY(4) + COLUMN_HEIGHT + 4;
        MiniGamePageData edited = data.withModes(modes);
        int lines = 0;
        if (data.pipeLinks().isEmpty()) {
            for (OrderedText line : textRenderer.wrapLines(Text.translatable(KEY + "pipes.none"), WIDTH - 2 * MARGIN)) {
                if (lines < 3) context.drawText(textRenderer, line, lx, top + 10 * lines++, PartyGui.TEXT_ERROR, false);
            }
            return;
        }
        for (MiniGameMode mode : MiniGameMode.values()) {
            if (!modes.contains(mode)) continue;
            List<MiniGamePipeRole> missing = edited.missing(mode);
            if (missing.isEmpty() || lines >= 3) continue;
            net.minecraft.text.MutableText roles = Text.empty();
            for (int i = 0; i < missing.size(); i++) roles.append(i == 0 ? "" : ", ").append(missing.get(i).text());
            context.drawText(textRenderer, fit(Text.translatable(KEY + "pipes.missing", mode.text(), roles), WIDTH - 2 * MARGIN - 10),
                    lx + 10, top + 10 * lines, PartyGui.TEXT_ERROR, false);
            PartyGui.statusIcon(context, lx, top + 10 * lines, false);
            lines++;
        }
        if (lines == 0) {
            PartyGui.statusIcon(context, lx, top, true);
            context.drawText(textRenderer, fit(Text.translatable(KEY + "pipes.complete"), WIDTH - 2 * MARGIN - 10), lx + 10, top, PartyGui.TEXT_OK, false);
        }
    }

    private void drawCard(DrawContext context, MiniGamePipeLink link, int left, int top, boolean hovered) {
        PartyGui.Theme theme = hovered && canEdit ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON;
        PartyGui.button(context, left, top, COLUMN_WIDTH - 5, CARD - 1, theme, false);
        context.fill(left + 2, top + 2, left + 5, top + CARD - 3, 0xFF000000 | link.role().color());
        // « x y z », or « x z » when that is too long for the card (the tooltip has it all)
        Text text = cardText(link);
        if (textRenderer.getWidth(text) > COLUMN_WIDTH - 15) text = Text.literal(link.mouth().pos().getX() + " " + link.mouth().pos().getZ());
        context.drawText(textRenderer, fit(text, COLUMN_WIDTH - 15), left + 7, top + 2, 0xFF2E2E2E, false);
    }

    /** Over everything: the card being dragged, or the tooltip of the card under the mouse. */
    private void drawPipesOverlay(DrawContext context, int mouseX, int mouseY) {
        if (held != null && dragged) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            drawCard(context, held, mouseX - (COLUMN_WIDTH - 5) / 2, mouseY - CARD / 2, true);
            context.getMatrices().pop();
            return;
        }
        MiniGamePipeLink link = cardAt(mouseX, mouseY);
        if (link == null) return;
        List<Text> lines = new java.util.ArrayList<>();
        lines.add(Text.empty().append(link.role().text()).styled(style -> style.withColor(link.role() == MiniGamePipeRole.ENTRY ? 0xB8B8B8 : link.role().color())));
        lines.add(Text.translatable(KEY + "pipes.card.position", cardText(link), link.mouth().dimension().getValue().getPath()).formatted(Formatting.GRAY));
        lines.add(Text.translatable(link.role().translationKey() + ".hint").formatted(Formatting.GRAY));
        if (canEdit) lines.add(Text.translatable(KEY + "pipes.card.hint").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private void setRole(MiniGamePipeLink link, @Nullable MiniGamePipeRole role) {
        if (!canEdit || role == link.role()) return;
        send(new MiniGamePagePayloads.PipeRole(hand, page, link.mouth(), role == null ? -1 : role.ordinal()));
        if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pipesTab && canEdit) {
            MiniGamePipeLink link = cardAt(mouseX, mouseY);
            if (link != null && button == 1) {
                setRole(link, null);
                return true;
            }
            if (link != null && button == 0) {
                held = link;
                dragged = false;
                pressX = mouseX;
                pressY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (held != null && (Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3)) dragged = true;
        return held != null || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (held != null && button == 0) {
            MiniGamePipeLink link = held;
            held = null;
            if (dragged) {
                // Dropped on a column: that role
                int column = columnAt(mouseX, mouseY);
                if (column >= 0) setRole(link, COLUMNS[column]);
            } else {
                // A click: the next role (Shift: the one before), in the order of the columns
                int column = java.util.Arrays.asList(COLUMNS).indexOf(link.role());
                setRole(link, COLUMNS[Math.floorMod(column + (hasShiftDown() ? -1 : 1), COLUMNS.length)]);
            }
            dragged = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int column = pipesTab ? columnAt(mouseX, mouseY) : -1;
        if (column >= 0) {
            scroll[column] = Math.max(0, scroll[column] - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void centered(DrawContext context, Text text, int centerX, int top, int color) {
        OrderedText line = fit(text, COLUMN - 8);
        context.drawText(textRenderer, line, centerX - textRenderer.getWidth(line) / 2, top, color, false);
    }

    private OrderedText fit(Text text, int width) {
        if (textRenderer.getWidth(text) <= width) return text.asOrderedText();
        return net.minecraft.util.Language.getInstance().reorder(net.minecraft.text.StringVisitable.concat(
                textRenderer.trimToWidth(text, Math.max(0, width - textRenderer.getWidth("…"))), net.minecraft.text.StringVisitable.plain("…")));
    }

    /** The vanilla multi-line box, in the sunken box of the mod's screens. */
    private static final class InsetEditBox extends EditBoxWidget {
        InsetEditBox(TextRenderer textRenderer, int x, int y, int width, int height, Text placeholder, Text message) {
            super(textRenderer, x, y, width, height, placeholder, message);
        }

        @Override
        protected void drawBox(DrawContext context, int x, int y, int width, int height) {
            PartyGui.inset(context, x, y, width, height, FIELD_BODY, isFocused(), false);
        }
    }
}
