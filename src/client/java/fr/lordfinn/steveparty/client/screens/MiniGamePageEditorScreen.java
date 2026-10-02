package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.client.minigame.PageImagePicker;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImage;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
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
 * per role, each with a mark in the colour of its pipe: dragging a card to another column gives it that role, a
 * right click unlinks it, a click shows where the pipe is (it blinks in the world for a few seconds). The small
 * button of a column says how its players are sent to its pipes: each in turn, or at random. Under the columns, what
 * is missing for the mini-game to be played in each of the ways it ticks; on the Page tab, the ways to play that miss
 * pipes carry a « ! ».
 * <p>
 * The « Podiums » tab lists the podiums and the goal pole bases linked to the page (a click on them, page in hand): the
 * podiums are the places of the mini-game (the taller the column, the better the place), the bases its counters. A
 * click on a card shows where it is, a right click unlinks it. Without any podium the mini-game names no winner (every
 * player is a « participant »): the tab says so and carries a « ! ».
 */
public class MiniGamePageEditorScreen extends Screen {
    private static final String KEY = "gui.steveparty.mini_game_page.";
    private static final int WIDTH = 320, HEIGHT = 228;
    private static final int MARGIN = 8, COLUMN = 148, RIGHT_X = MARGIN + COLUMN + MARGIN;
    private static final int TOP = 18;
    private static final int PICTURE_WIDTH = 144, PICTURE_HEIGHT = 81;
    private static final int FIELD_BODY = 0xFF3B4247, PICTURE_BODY = 0xFF1E2327;
    private static final int ROW = 16;
    /** Width of the tooltips drawn here: about forty characters. */
    private static final int TOOLTIP_WIDTH = 200;

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
    /** The « Podiums » tab (never with {@link #pipesTab}). */
    private boolean podiumsTab;
    private PartyButton pageTabButton, pipesTabButton, podiumsTabButton;
    /** The podium cards: two columns, scrolled by row. */
    private static final int PODIUM_CARD_WIDTH = 150, PODIUM_CARD = 13, PODIUM_ROWS = 8, PODIUMS_TOP = 64;
    private int podiumScroll;
    /** What is typed in the fields, kept while the other tab is shown. */
    private String titleValue, descriptionValue;
    /** First card shown in each column. */
    private final int[] scroll = new int[COLUMNS.length];
    /** The card held by the mouse, and whether it moved (a drag) since it was pressed. */
    private @Nullable MiniGamePipeLink held;
    private boolean dragged;
    private double pressX, pressY;
    /** What the tooltips of the ways to play were made for (the pipes and the ways ticked). */
    private int modeTooltipsKey;
    /** Ticks a located pipe blinks in the world. */
    private static final int LOCATE_TICKS = 100;
    private static final int ORDER_BUTTON = 9;
    /** The codes of the description's formatting buttons: bold, italic, four colours, back to plain. */
    private static final String[] FORMAT_CODES = {"&l", "&o", "&c", "&6", "&a", "&b", "&r"};

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
        pageTabButton = addDrawableChild(new PartyButton(x + WIDTH - 8 - 156, y - 7, 44, 16, Text.translatable(KEY + "tab.page"), b -> showTab(false, false)));
        pipesTabButton = addDrawableChild(new PartyButton(x + WIDTH - 8 - 110, y - 7, 50, 16, Text.translatable(KEY + "tab.pipes"), b -> showTab(true, false)));
        pipesTabButton.setTooltip(Tooltip.of(Text.translatable(KEY + "tab.pipes.hint")));
        podiumsTabButton = addDrawableChild(new PartyButton(x + WIDTH - 8 - 58, y - 7, 58, 16, Text.translatable(KEY + "tab.podiums"), b -> showTab(false, true)));
        podiumsTabButton.setTooltip(Tooltip.of(Text.translatable(KEY + "tab.podiums.hint")));
        pageTabButton.setSelected(!pipesTab && !podiumsTab);
        pipesTabButton.setSelected(pipesTab);
        podiumsTabButton.setSelected(podiumsTab);
        addDrawableChild(new PartyButton(rx, y + HEIGHT - 26, COLUMN, 18, ScreenTexts.DONE, b -> close()).style(PartyButton.Style.PRIMARY));
        if (pipesTab || podiumsTab) {
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

        // The description's formatting: each button writes its code where the cursor is
        for (int i = 0; i < FORMAT_CODES.length; i++) {
            String code = FORMAT_CODES[i];
            Formatting formatting = Formatting.byCode(code.charAt(1));
            PartyButton button = new PartyButton(rx + COLUMN - (FORMAT_CODES.length - i) * 10 + 1, y + TOP + 30, 9, 9, Text.empty(), b -> insertCode(code));
            button.content((context, renderer, centerX, centerY, color) -> {
                if (formatting != null && formatting.isColor() && formatting.getColorValue() != null) {
                    context.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFF000000 | formatting.getColorValue());
                } else {
                    Text letter = Text.translatable(KEY + "format." + code.charAt(1) + ".letter").styled(style -> formatting == null || formatting == Formatting.RESET ? style : style.withFormatting(formatting));
                    context.drawText(renderer, letter, centerX - renderer.getWidth(letter) / 2, centerY - 4, color, false);
                }
            });
            button.setTooltip(Tooltip.of(Text.translatable(KEY + "format." + code.charAt(1)).append("\n")
                    .append(Text.translatable(KEY + "format.hint", code).formatted(Formatting.GRAY))));
            button.active = canEdit;
            addDrawableChild(button);
        }

        // ---- Right: type of mini-game (several can be ticked)
        int modesY = y + TOP + 112;
        for (MiniGameMode mode : MiniGameMode.values()) {
            int i = mode.ordinal();
            PartyButton button = new PartyButton(rx + (i % 2) * 75, modesY + (i / 2) * (ROW + 2), 73, ROW, mode.text(), b -> toggle(mode));
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

        modeTooltipsKey = 0;
        if (canEdit && !opened && saved.title().isEmpty()) setInitialFocus(titleField);

        if (!opened && client != null && client.player != null) {
            opened = true;
            client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
        }
    }

    /** Writes a formatting code in the description, where its cursor is. */
    private void insertCode(String code) {
        if (descriptionBox == null || !canEdit) return;
        setFocused(descriptionBox);
        for (char c : code.toCharArray()) descriptionBox.charTyped(c, 0);
    }

    /**
     * The tooltips of the ways to play: what each is, the pipes it needs and those that are missing. Made again only
     * when the pipes or the ways ticked change.
     */
    private void refreshModeTooltips(MiniGamePageData data) {
        int key = 31 * data.pipeLinks().hashCode() + modes.hashCode() + 1;
        if (key == modeTooltipsKey || modeButtons[0] == null) return;
        modeTooltipsKey = key;
        for (MiniGameMode mode : MiniGameMode.values()) {
            net.minecraft.text.MutableText text = Text.translatable(mode.translationKey() + ".hint").append("\n")
                    .append(Text.translatable(mode.translationKey() + ".pipes").formatted(Formatting.GRAY));
            List<MiniGamePipeRole> missing = data.missing(mode);
            if (!missing.isEmpty()) {
                net.minecraft.text.MutableText roles = Text.empty();
                for (int i = 0; i < missing.size(); i++) roles.append(i == 0 ? "" : ", ").append(missing.get(i).text());
                text.append("\n").append(Text.translatable(KEY + "field.type.missing", roles).formatted(Formatting.RED));
            }
            text.append("\n").append(Text.translatable(KEY + "field.type.hint").formatted(Formatting.DARK_GRAY));
            modeButtons[mode.ordinal()].setTooltip(Tooltip.of(text));
        }
    }

    /** A « ! » on the corner of each way to play whose pipes are missing: red when it is ticked, grey otherwise. */
    private void drawModeWarnings(DrawContext context, MiniGamePageData data) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 100);
        for (MiniGameMode mode : MiniGameMode.values()) {
            PartyButton button = modeButtons[mode.ordinal()];
            if (button == null || data.missing(mode).isEmpty()) continue;
            int left = button.getX() + button.getWidth() - 6, top = button.getY() - 2;
            int body = modes.contains(mode) ? 0xFFD8323F : 0xFF8A8A8A;
            context.fill(left + 1, top, left + 6, top + 9, 0xFF000000);
            context.fill(left, top + 1, left + 7, top + 8, 0xFF000000);
            context.fill(left + 1, top + 1, left + 6, top + 8, body);
            context.fill(left + 3, top + 2, left + 4, top + 5, 0xFFFFFFFF);
            context.fill(left + 3, top + 6, left + 4, top + 7, 0xFFFFFFFF);
        }
        context.getMatrices().pop();
    }

    private void keepTexts() {
        if (titleField != null) titleValue = titleField.getText();
        if (descriptionBox != null) descriptionValue = descriptionBox.getText();
    }

    private void showTab(boolean pipes, boolean podiums) {
        if (pipes == pipesTab && podiums == podiumsTab) return;
        keepTexts();
        pipesTab = pipes;
        podiumsTab = podiums;
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
        if (picking || !canEdit || paths.isEmpty() || pipesTab || podiumsTab) return;
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
        if (!pipesTab && !podiumsTab) refreshModeTooltips(data);
        super.render(context, mouseX, mouseY, delta);
        if (pipesTab) drawPipesOverlay(context, mouseX, mouseY);
        else if (podiumsTab) drawPodiumsOverlay(context, mouseX, mouseY);
        else drawModeWarnings(context, data);
        // No podium: the mini-game names no winner. Said on the tab itself
        if (!data.hasPodium() && podiumsTabButton != null) {
            int left = podiumsTabButton.getX() + podiumsTabButton.getWidth() - 6, top = podiumsTabButton.getY() - 3;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 100);
            context.fill(left + 1, top, left + 6, top + 9, 0xFF6B4300);
            context.fill(left, top + 1, left + 7, top + 8, 0xFF6B4300);
            context.fill(left + 1, top + 1, left + 6, top + 8, 0xFFF0A020);
            context.fill(left + 3, top + 2, left + 4, top + 5, 0xFFFFFFFF);
            context.fill(left + 3, top + 6, left + 4, top + 7, 0xFFFFFFFF);
            context.getMatrices().pop();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        PartyGui.panel(context, x, y, WIDTH, HEIGHT, PartyGui.PANEL);
        // On the left: the tabs sit on the right of the top edge
        PartyGui.titlePlate(context, textRenderer, x + 80, y - 12, 0, title, PartyGui.FLAG_RED);
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
        if (podiumsTab) {
            drawPodiums(context, data, mouseX, mouseY);
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
            int right = cx + COLUMN_WIDTH - 3;
            if (role.hasOrder()) {
                // How its players are sent to its pipes: each in turn, or at random
                drawOrderButton(context, cx + COLUMN_WIDTH - 1 - ORDER_BUTTON, cy + 1, data.isRandom(role), orderButtonAt(mouseX, mouseY) == column);
                right -= ORDER_BUTTON + 1;
            }
            context.drawText(textRenderer, fit(role.text(), right - cx - 5 - textRenderer.getWidth(count)), cx + 3, cy + 2, textOn(role.color()), false);
            context.drawText(textRenderer, count, right - textRenderer.getWidth(count), cy + 2, textOn(role.color()), false);
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

    /** The column whose order button is under the mouse, -1 for none. */
    private int orderButtonAt(double mouseX, double mouseY) {
        for (int column = 0; column < COLUMNS.length; column++) {
            if (!COLUMNS[column].hasOrder()) continue;
            int left = columnX(column) + COLUMN_WIDTH - 1 - ORDER_BUTTON, top = columnY(column) + 1;
            if (mouseX >= left && mouseX < left + ORDER_BUTTON && mouseY >= top && mouseY < top + ORDER_BUTTON + 1) return column;
        }
        return -1;
    }

    /** The small square button of a column: three steps for « each in turn », a dice face for « at random ». */
    private void drawOrderButton(DrawContext context, int left, int top, boolean random, boolean hovered) {
        PartyGui.Theme theme = hovered && canEdit ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON;
        PartyGui.button(context, left, top, ORDER_BUTTON, ORDER_BUTTON + 1, theme, false);
        int ink = 0xFF2E2E2E;
        if (random) {
            // The five of a dice
            for (int[] dot : new int[][]{{2, 2}, {6, 2}, {4, 4}, {2, 6}, {6, 6}}) PartyGui.pixel(context, left + dot[0], top + dot[1], ink);
        } else {
            // First, second, third
            context.fill(left + 2, top + 2, left + 4, top + 3, ink);
            context.fill(left + 2, top + 4, left + 6, top + 5, ink);
            context.fill(left + 2, top + 6, left + 7, top + 7, ink);
        }
    }

    /** The colour of the pipe a card stands for (0xRRGGBB): the pipe's, whatever its role; its role's when not known. */
    private static int pipeColor(MiniGamePipeLink link) {
        if (link.kind() == fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.GLASS) return 0xD6ECF2;
        if (link.color() == MiniGamePipeLink.UNKNOWN_COLOR) return link.role().color();
        net.minecraft.util.DyeColor dye = net.minecraft.util.DyeColor.byName(fr.lordfinn.steveparty.blocks.ModBlocks.COLORS[link.color()], null);
        return dye == null ? link.role().color() : dye.getEntityColor() & 0xFFFFFF;
    }

    private void drawCard(DrawContext context, MiniGamePipeLink link, int left, int top, boolean hovered) {
        PartyGui.Theme theme = hovered ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON;
        PartyGui.button(context, left, top, COLUMN_WIDTH - 5, CARD - 1, theme, false);
        // The mark of its pipe: plain for plastic, a window in a windowed pipe, half see-through for glass
        int color = 0xFF000000 | pipeColor(link);
        int markLeft = left + 2, markTop = top + 2, markRight = left + 6, markBottom = top + CARD - 3;
        context.fill(markLeft, markTop, markRight, markBottom, color);
        switch (link.kind()) {
            case WINDOWED -> context.fill(markLeft + 1, markTop + 2, markRight - 1, markBottom - 2, 0xFFEAF6FA);
            case GLASS, STAINED_GLASS -> {
                int half = markTop + (markBottom - markTop) / 2;
                for (int py = half; py < markBottom; py++) {
                    for (int px = markLeft; px < markRight; px++) {
                        PartyGui.pixel(context, px, py, ((px - markLeft) + (py - half)) % 2 == 0 ? 0xFFFFFFFF : 0xFFB9B9B9);
                    }
                }
            }
            default -> {
            }
        }
        // « x y z », or « x z » when that is too long for the card (the tooltip has it all)
        Text text = cardText(link);
        if (textRenderer.getWidth(text) > COLUMN_WIDTH - 16) text = Text.literal(link.mouth().pos().getX() + " " + link.mouth().pos().getZ());
        context.drawText(textRenderer, fit(text, COLUMN_WIDTH - 16), left + 8, top + 2, 0xFF2E2E2E, false);
    }

    /** A tooltip whose long lines are cut (about forty characters wide). */
    private void drawWrappedTooltip(DrawContext context, List<Text> lines, int mouseX, int mouseY) {
        List<OrderedText> wrapped = new java.util.ArrayList<>();
        for (Text line : lines) wrapped.addAll(textRenderer.wrapLines(line, TOOLTIP_WIDTH));
        context.drawOrderedTooltip(textRenderer, wrapped, mouseX, mouseY);
    }

    /** Over everything: the card being dragged, or the tooltip of what is under the mouse. */
    private void drawPipesOverlay(DrawContext context, int mouseX, int mouseY) {
        if (held != null && dragged) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            drawCard(context, held, mouseX - (COLUMN_WIDTH - 5) / 2, mouseY - CARD / 2, true);
            context.getMatrices().pop();
            return;
        }
        int order = orderButtonAt(mouseX, mouseY);
        if (order >= 0) {
            boolean random = current().isRandom(COLUMNS[order]);
            List<Text> lines = new java.util.ArrayList<>();
            lines.add(Text.translatable(KEY + (random ? "pipes.order.random" : "pipes.order.in_turn")).formatted(Formatting.GOLD));
            lines.add(Text.translatable(KEY + (random ? "pipes.order.random.hint" : "pipes.order.in_turn.hint")).formatted(Formatting.GRAY));
            if (canEdit) lines.add(Text.translatable(KEY + "pipes.order.change").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            drawWrappedTooltip(context, lines, mouseX, mouseY);
            return;
        }
        MiniGamePipeLink link = cardAt(mouseX, mouseY);
        if (link == null) return;
        List<Text> lines = new java.util.ArrayList<>();
        lines.add(Text.empty().append(link.role().text()).styled(style -> style.withColor(link.role() == MiniGamePipeRole.ENTRY ? 0xB8B8B8 : link.role().color())));
        lines.add(Text.translatable(KEY + "pipes.card.position", cardText(link), link.mouth().dimension().getValue().getPath()).formatted(Formatting.GRAY));
        lines.add(Text.translatable(link.role().translationKey() + ".hint").formatted(Formatting.GRAY));
        lines.add(Text.translatable(KEY + (canEdit ? "pipes.card.hint" : "pipes.card.hint.read_only")).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        drawWrappedTooltip(context, lines, mouseX, mouseY);
    }

    /** Shows where a linked pipe is: it blinks in the world for a few seconds (seen through the menu, and once it is closed). */
    private void locate(MiniGamePipeLink link) {
        DestinationsRenderer.locate(link.mouth(), LOCATE_TICKS);
        if (client != null && client.player != null) client.player.playSound(fr.lordfinn.steveparty.sounds.ModSounds.SELECT_SOUND_EVENT, 1.0F, 1.4F);
        boolean here = client != null && client.world != null && client.world.getRegistryKey().equals(link.mouth().dimension());
        setStatus(Text.translatable(KEY + (here ? "status.located" : "status.located_elsewhere"), cardText(link)), false);
    }

    private void setRole(MiniGamePipeLink link, @Nullable MiniGamePipeRole role) {
        if (!canEdit || role == link.role()) return;
        send(new MiniGamePagePayloads.PipeRole(hand, page, link.mouth(), role == null ? -1 : role.ordinal()));
        if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
    }

    // ------------------------------------------------------------------ the « Podiums » tab

    private static Text podiumText(MiniGamePodiumLink link) {
        net.minecraft.util.math.BlockPos pos = link.pos().pos();
        return Text.translatable(KEY + (link.kind() == MiniGamePodiumLink.Kind.PODIUM ? "podiums.card.podium" : "podiums.card.counter"),
                pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private int podiumCardX(int index) {
        return x + MARGIN + (index % 2) * (PODIUM_CARD_WIDTH + 4);
    }

    private int podiumCardY(int index) {
        return y + PODIUMS_TOP + (index / 2 - podiumScroll) * PODIUM_CARD;
    }

    /** The linked podium (or goal pole base) whose card is under the mouse, null for none. */
    private @Nullable MiniGamePodiumLink podiumAt(double mouseX, double mouseY) {
        List<MiniGamePodiumLink> links = current().podiumLinks();
        for (int index = podiumScroll * 2; index < links.size() && index < (podiumScroll + PODIUM_ROWS) * 2; index++) {
            int left = podiumCardX(index), top = podiumCardY(index);
            if (mouseX >= left && mouseX < left + PODIUM_CARD_WIDTH && mouseY >= top && mouseY < top + PODIUM_CARD - 1) return links.get(index);
        }
        return null;
    }

    private void drawPodiums(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        int lx = x + MARGIN;
        List<OrderedText> guide = textRenderer.wrapLines(Text.translatable(KEY + "podiums.guide"), WIDTH - 2 * MARGIN);
        for (int i = 0; i < Math.min(4, guide.size()); i++) {
            context.drawText(textRenderer, guide.get(i), lx, y + TOP + i * 10, PartyGui.TEXT_SOFT, false);
        }
        List<MiniGamePodiumLink> links = data.podiumLinks();
        int rows = (links.size() + 1) / 2;
        podiumScroll = Math.max(0, Math.min(podiumScroll, rows - PODIUM_ROWS));
        PartyGui.inset(context, lx - 2, y + PODIUMS_TOP - 2, WIDTH - 2 * MARGIN + 4, PODIUM_ROWS * PODIUM_CARD + 3, FIELD_BODY, false, false);
        MiniGamePodiumLink hovered = podiumAt(mouseX, mouseY);
        for (int index = podiumScroll * 2; index < links.size() && index < (podiumScroll + PODIUM_ROWS) * 2; index++) {
            MiniGamePodiumLink link = links.get(index);
            int left = podiumCardX(index), top = podiumCardY(index);
            PartyGui.button(context, left, top, PODIUM_CARD_WIDTH, PODIUM_CARD - 1, link == hovered ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON, false);
            // A gold step for a podium, a red flag for a counter
            if (link.kind() == MiniGamePodiumLink.Kind.PODIUM) {
                context.fill(left + 3, top + 6, left + 9, top + 9, 0xFFB5761A);
                context.fill(left + 5, top + 3, left + 9, top + 6, 0xFFFFC52E);
            } else {
                context.fill(left + 4, top + 2, left + 5, top + 10, 0xFF4A4A4A);
                context.fill(left + 5, top + 2, left + 9, top + 6, 0xFFD8323F);
            }
            context.drawText(textRenderer, fit(podiumText(link), PODIUM_CARD_WIDTH - 16), left + 12, top + 2, 0xFF2E2E2E, false);
        }
        if (podiumScroll > 0) context.drawText(textRenderer, "▲", x + WIDTH - MARGIN - 8, y + PODIUMS_TOP, 0xFFB8C0C6, false);
        if (podiumScroll + PODIUM_ROWS < rows) {
            context.drawText(textRenderer, "▼", x + WIDTH - MARGIN - 8, y + PODIUMS_TOP + PODIUM_ROWS * PODIUM_CARD - 9, 0xFFB8C0C6, false);
        }
        // Without any podium the mini-game names no winner
        int top = y + PODIUMS_TOP + PODIUM_ROWS * PODIUM_CARD + 6;
        if (!data.hasPodium()) {
            int lines = 0;
            for (OrderedText line : textRenderer.wrapLines(Text.translatable(KEY + "podiums.none"), WIDTH - 2 * MARGIN)) {
                if (lines < 3) context.drawText(textRenderer, line, lx, top + 10 * lines++, 0xFFB36200, false);
            }
        } else {
            long podiums = links.stream().filter(link -> link.kind() == MiniGamePodiumLink.Kind.PODIUM).count();
            PartyGui.statusIcon(context, lx, top, true);
            context.drawText(textRenderer, fit(Text.translatable(KEY + "podiums.complete", podiums, links.size() - podiums), WIDTH - 2 * MARGIN - 10),
                    lx + 10, top, PartyGui.TEXT_OK, false);
        }
    }

    private void drawPodiumsOverlay(DrawContext context, int mouseX, int mouseY) {
        MiniGamePodiumLink link = podiumAt(mouseX, mouseY);
        if (link == null) return;
        List<Text> lines = new java.util.ArrayList<>();
        boolean podium = link.kind() == MiniGamePodiumLink.Kind.PODIUM;
        lines.add(podiumText(link).copy().formatted(Formatting.GOLD));
        lines.add(Text.translatable(KEY + "pipes.card.position", link.pos().pos().getX() + " " + link.pos().pos().getY() + " " + link.pos().pos().getZ(),
                link.pos().dimension().getValue().getPath()).formatted(Formatting.GRAY));
        lines.add(Text.translatable(KEY + (podium ? "podiums.card.podium.hint" : "podiums.card.counter.hint")).formatted(Formatting.GRAY));
        lines.add(Text.translatable(KEY + (canEdit ? "podiums.card.hint" : "pipes.card.hint.read_only")).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        drawWrappedTooltip(context, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (podiumsTab) {
            MiniGamePodiumLink link = podiumAt(mouseX, mouseY);
            if (link != null && button == 1) {
                if (canEdit) {
                    send(new MiniGamePagePayloads.PodiumUnlink(hand, page, link.pos()));
                    if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                }
                return true;
            }
            if (link != null && button == 0) {
                DestinationsRenderer.locate(link.pos(), LOCATE_TICKS);
                if (client != null && client.player != null) client.player.playSound(fr.lordfinn.steveparty.sounds.ModSounds.SELECT_SOUND_EVENT, 1.0F, 1.4F);
                boolean here = client != null && client.world != null && client.world.getRegistryKey().equals(link.pos().dimension());
                net.minecraft.util.math.BlockPos pos = link.pos().pos();
                setStatus(Text.translatable(KEY + (here ? "status.located" : "status.located_elsewhere"), pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
                return true;
            }
        }
        if (pipesTab) {
            int order = orderButtonAt(mouseX, mouseY);
            if (order >= 0 && button == 0) {
                if (canEdit) {
                    MiniGamePipeRole role = COLUMNS[order];
                    send(new MiniGamePagePayloads.PipeOrder(hand, page, role.ordinal(), !current().isRandom(role)));
                    if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
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
                // A click: where the pipe is
                locate(link);
            }
            dragged = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (podiumsTab && verticalAmount != 0) {
            podiumScroll = Math.max(0, podiumScroll - (int) Math.signum(verticalAmount));
            return true;
        }
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
