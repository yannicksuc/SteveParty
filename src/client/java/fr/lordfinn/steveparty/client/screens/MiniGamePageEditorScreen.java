package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.client.screens.pageeditor.BinderPage;
import fr.lordfinn.steveparty.client.screens.pageeditor.BinderTab;
import fr.lordfinn.steveparty.client.screens.pageeditor.FormatsTab;
import fr.lordfinn.steveparty.client.screens.pageeditor.PageEditor;
import fr.lordfinn.steveparty.client.screens.pageeditor.PagePaint;
import fr.lordfinn.steveparty.client.screens.pageeditor.PagePopup;
import fr.lordfinn.steveparty.client.screens.pageeditor.PageTab;
import fr.lordfinn.steveparty.client.screens.pageeditor.PipesTab;
import fr.lordfinn.steveparty.client.screens.pageeditor.ResultsTab;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * The editor of a mini-game page, opened by right-clicking the page in hand: « the binder page » (the art sources
 * build_page_editor_v2.py, build_page_editor_formats_v3.py). The page's paper with its punched holes, its fields
 * written on it; four dividers on its right edge (Page, Formats, Pipes, Results), an icon each, popping out to show
 * their name while hovered ({@link BinderPage}).
 * <ul>
 *     <li><b>Page</b> ({@link PageTab}): its picture (picked on the player's computer, or dropped on the window), its
 *     title, its formats (read only: pawn chips, « modifier » leads to the Formats tab), its description across the page
 *     (formatted as it is shown, on ruled lines, scrolling), Copy / Unlink. What is written is sent when the editor
 *     closes; the picture as soon as it is picked. A player who may not build only reads the page.</li>
 *     <li><b>Formats</b> ({@link FormatsTab}): the ways the mini-game can be played ({@link MiniGameFormat}), as chips
 *     (×, a red « ! » when the page misses their pipes). « + » opens the gallery of ready formats, a click on a chip its
 *     editor: both in a popup anchored to their chip, the rest dimmed (Escape or a click outside closes it).</li>
 *     <li><b>Pipes</b> ({@link PipesTab}): the pipes linked to the page (a click on a pipe mouth, page in hand) as cards
 *     in a column per role: dragging a card to another column gives it that role, a right click unlinks it, a click shows
 *     where the pipe is. The small button of a column says how its players are sent to its pipes: each in turn, or at
 *     random.</li>
 *     <li><b>Results</b> ({@link ResultsTab}): the podiums, goal pole bases and step controllers linked to the page, with
 *     their block's name and where they are. A click shows where a card's block is, a right click unlinks it.</li>
 * </ul>
 * This screen holds the page edited, the « Test » and « Done » buttons, the status line and the popup; each tab draws
 * and handles its own content.
 */
public class MiniGamePageEditorScreen extends Screen implements PageEditor {
    private final Hand hand;
    private final UUID page;
    private final boolean canEdit;
    /** What the server has (the texts and settings as they were sent last). */
    private MiniGamePageData saved;

    private int x, y;
    private BinderTab tab = BinderTab.PAGE;
    /** The formats as edited (sent when the editor closes). */
    private final List<MiniGameFormat> formats;
    /**
     * The « Test » button, on every tab: plays the mini-game out of any party with those near its pipes, or stops the
     * test being played. Whether it can is asked to the server every second ({@link #onTestStatus}).
     */
    private ConsoleButton testButton;
    private MiniGameTest.@Nullable Status testStatus;
    private int testPlayers, testFormat = -1, testPoll;
    private int[] testShortfall = new int[0];

    private @Nullable Text status;
    private boolean statusIsError;
    private boolean opened;
    /** The popup open over the page (a format's editor, the gallery), null for none. */
    private @Nullable PagePopup popup;

    private final BinderPage binder = new BinderPage(this);
    private final PageTab pageTab;
    private final FormatsTab formatsTab = new FormatsTab(this);
    private final PipesTab pipesTab = new PipesTab(this);
    private final ResultsTab resultsTab = new ResultsTab(this);

    public MiniGamePageEditorScreen(Hand hand, MiniGamePageData data, boolean canEdit, boolean linked, @Nullable String status) {
        super(Text.translatable(KEY + "title"));
        this.hand = hand;
        this.page = data.id();
        this.canEdit = canEdit && !MiniGamePageData.NO_ID.equals(data.id());
        this.saved = data;
        this.formats = new ArrayList<>(data.formats());
        this.pageTab = new PageTab(this, data, linked);
        if (status != null) this.status = Text.literal(status);
        else if (!this.canEdit) this.status = Text.translatable(KEY + "status.read_only");
    }

    // ------------------------------------------------------------------ the editor, as its tabs see it

    @Override
    public TextRenderer font() {
        return textRenderer;
    }

    @Override
    public @Nullable MinecraftClient client() {
        return client;
    }

    @Override
    public int left() {
        return x;
    }

    @Override
    public int top() {
        return y;
    }

    @Override
    public int screenWidth() {
        return width;
    }

    @Override
    public int screenHeight() {
        return height;
    }

    @Override
    public Hand hand() {
        return hand;
    }

    @Override
    public UUID pageId() {
        return page;
    }

    @Override
    public boolean canEdit() {
        return canEdit;
    }

    /** The page as the server has it now (its picture may change while the editor is open), with the formats edited here. */
    @Override
    public MiniGamePageData current() {
        MiniGamePageData known = MiniGamePageClient.page(page);
        return (known != null ? known : saved).withFormats(formats);
    }

    @Override
    public List<MiniGameFormat> formats() {
        return formats;
    }

    @Override
    public <T extends Element & Drawable & Selectable> T add(T widget) {
        return addDrawableChild(widget);
    }

    @Override
    public void focus(@Nullable Element element) {
        setFocused(element);
    }

    @Override
    public void send(CustomPayload payload) {
        if (ClientPlayNetworking.canSend(payload.getId())) ClientPlayNetworking.send(payload);
    }

    @Override
    public void playClick() {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
    }

    @Override
    public void setStatus(@Nullable Text text, boolean error) {
        status = text;
        statusIsError = error;
    }

    @Override
    public @Nullable PagePopup popup() {
        return popup;
    }

    @Override
    public void openPopup(@Nullable PagePopup popup) {
        this.popup = popup;
    }

    @Override
    public void showTab(BinderTab tab) {
        if (tab == this.tab) return;
        pageTab.keepTexts();
        this.tab = tab;
        pipesTab.drop();
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.2F));
        clearAndInit();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init() {
        // The page and its dividers at rest, centred; a popped divider stays on the screen (the same place on every tab)
        x = Math.max(2, Math.min((width - PW - BinderPage.REST_OUT) / 2, width - 2 - PW - BinderPage.maxOut(textRenderer)));
        y = Math.max(2, (height - PH) / 2);
        // init() runs again on every resize and tab change: keep what the player already typed
        pageTab.keepTexts();
        popup = null;

        // ---- The bottom row: « Test » and « Done » on the right edge of the content
        int right = x + PW - M;
        testButton = addDrawableChild(new ConsoleButton(right - 124, y + BOTTOM_Y, 60, BOTTOM_H, Text.translatable(KEY + "test"),
                ConsoleButton.Kind.PAPER_TEAL, null, this::clickTest));
        addDrawableChild(new ConsoleButton(right - 60, y + BOTTOM_Y, 60, BOTTOM_H, ScreenTexts.DONE, ConsoleButton.Kind.PAPER_GREEN, null, this::close));
        refreshTestButton();
        if (testStatus == null) queryTest();
        if (tab != BinderTab.PAGE) {
            pageTab.clear();
            return;
        }
        pageTab.init();
        if (canEdit && !opened && saved.title().isEmpty()) setInitialFocus(pageTab.titleBox());
        if (!opened && client != null && client.player != null) {
            opened = true;
            client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && popup != null) {
            popup.cancel();
            popup = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && pageTab.closePalette()) return true;
        if (popup != null) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return popup == null && super.charTyped(chr, modifiers);
    }

    /** A picture file dropped on the window. */
    @Override
    public void filesDragged(List<Path> paths) {
        if (tab == BinderTab.PAGE) pageTab.filesDragged(paths);
    }

    // ------------------------------------------------------------------ test

    private void queryTest() {
        if (!MiniGamePageData.NO_ID.equals(page)) send(new MiniGamePagePayloads.TestQuery(hand, page));
    }

    @Override
    public void tick() {
        super.tick();
        if (++testPoll % 20 == 0) queryTest();
    }

    /** The server says whether the page can be tested now, with how many players, in which format, or what it misses. */
    public void onTestStatus(UUID about, int status, int players, int format, int[] shortfall) {
        if (!about.equals(page)) return;
        MiniGameTest.Status[] values = MiniGameTest.Status.values();
        testStatus = status >= 0 && status < values.length ? values[status] : null;
        testPlayers = players;
        testFormat = format;
        testShortfall = shortfall;
        refreshTestButton();
    }

    private boolean testRunning() {
        return testStatus == MiniGameTest.Status.RUNNING;
    }

    /** « Test », or « Stop the test » while one is played; greyed, with why in its tooltip, when the page can't be tested. */
    @Override
    public void refreshTestButton() {
        if (testButton == null) return;
        boolean ready = testStatus == MiniGameTest.Status.READY;
        testButton.setMessage(Text.translatable(KEY + (testRunning() ? "test.stop" : "test")));
        testButton.active = canEdit && (ready || testRunning());
        Text why;
        MiniGamePageData data = current();
        if (!canEdit) why = Text.translatable(KEY + "status.read_only");
        else if (testStatus == null) why = Text.translatable(KEY + "test.tooltip.unknown");
        else if (ready) {
            MiniGameFormat format = data.format(testFormat);
            why = Text.translatable(KEY + "test.tooltip.ready", testPlayers, format == null ? Text.empty() : format.name());
        } else if (testStatus == MiniGameTest.Status.NOT_ENOUGH && testShortfall.length == 5) {
            why = Text.translatable(KEY + "test.tooltip.not_enough.format", FormatChips.shortfallText(data, testShortfall));
        } else why = Text.translatable(KEY + "test.tooltip." + testStatus.name().toLowerCase(Locale.ROOT));
        testButton.setTooltip(Tooltip.of(ready || testRunning() ? why : why.copy().formatted(Formatting.RED)));
    }

    private void clickTest() {
        if (!canEdit) return;
        if (testRunning()) {
            send(new MiniGamePagePayloads.TestAction(hand, page, false));
        } else {
            // What is written is the mini-game tested
            save();
            send(new MiniGamePagePayloads.TestAction(hand, page, true));
        }
        close();
    }

    /** The server's answer to the last request. */
    public void onStatus(UUID about, MiniGamePagePayloads.Status.Code code) {
        if (!about.equals(page)) return;
        boolean error = code == MiniGamePagePayloads.Status.Code.IMAGE_REFUSED || code == MiniGamePagePayloads.Status.Code.COPY_NEEDS_PAPER
                || code == MiniGamePagePayloads.Status.Code.NOT_ALLOWED;
        if (code == MiniGamePagePayloads.Status.Code.IMAGE_SAVED || code == MiniGamePagePayloads.Status.Code.IMAGE_REFUSED) pageTab.forgetPendingImage();
        setStatus(Text.translatable(KEY + "status." + code.name().toLowerCase(Locale.ROOT)), error);
    }

    /** The editor is about to be opened again on the same hand: saves, and gives its status line to the next one. */
    public @Nullable String handOver() {
        save();
        return status == null || statusIsError ? null : status.getString();
    }

    // ------------------------------------------------------------------ saving

    /** Sends the texts and formats if they changed. */
    @Override
    public void save() {
        if (!canEdit) return;
        pageTab.keepTexts();
        MiniGamePageData edited = saved.withTexts(pageTab.title(), pageTab.description()).withFormats(formats);
        if (edited.title().equals(saved.title()) && edited.description().equals(saved.description()) && edited.formats().equals(saved.formats())) return;
        saved = edited;
        send(new MiniGamePagePayloads.Edit(hand, page, edited.title(), edited.description(), edited.formats()));
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
        pageTab.beforeRender(current());
        // Under a popup nothing is hovered
        int mx = popup != null ? -1000 : mouseX, my = popup != null ? -1000 : mouseY;
        super.render(context, mx, my, delta);
        if (binder.overInfo(mx, my) && !pageTab.isPaletteOpen()) {
            PagePaint.tooltip(context, textRenderer, List.of(Text.translatable(KEY + "tab." + tab.key()).formatted(Formatting.GOLD),
                    Text.translatable(KEY + tab.key() + ".info").formatted(Formatting.GRAY)), mouseX, mouseY);
        } else {
            switch (tab) {
                case PIPES -> pipesTab.drawOverlay(context, mx, my);
                case RESULTS -> resultsTab.drawOverlay(context, mx, my);
                case FORMATS -> formatsTab.drawOverlay(context, mx, my);
                default -> pageTab.drawOverlay(context, mx, my);
            }
        }
        if (pageTab.isPaletteOpen()) pageTab.drawPalette(context, mouseX, mouseY);
        if (popup != null) popup.render(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        binder.draw(context, tab, popup == null ? binder.tabAt(mouseX, mouseY) : null);
        MiniGamePageData data = current();
        switch (tab) {
            case PIPES -> pipesTab.draw(context, data, mouseX, mouseY);
            case RESULTS -> resultsTab.draw(context, data, mouseX, mouseY);
            case FORMATS -> formatsTab.draw(context, data, mouseX, mouseY);
            default -> pageTab.draw(context, data, mouseX, mouseY);
        }
        ConsolePaint.infoButton(context, binder.infoX(), y + M);
        // The status line: on the Page tab under the formats, else at the bottom left
        if (status != null) {
            int colour = statusIsError ? RED : GREEN2;
            if (tab == BinderTab.PAGE) {
                // Between the formats and the zone
                int top = pageTab.statusTop(data), below = pageTab.statusBottom() - top - 8;
                List<OrderedText> lines = statusLines(status, below < 0 ? 0 : Math.min(2, below / 10 + 1));
                for (int i = 0; i < lines.size(); i++)
                    UiText.line(context, textRenderer, lines.get(i), x + RX, top + i * 10, CW + 1, colour, false);
            } else {
                // Up to the « Test » button, 5 px before it
                UiText.line(context, textRenderer, status, x + LX, y + BOTTOM_Y + 4, FULL - 124 - 5, colour, false);
            }
        }
    }

    /** The status on lines of the right column, at most {@code max}: what does not fit goes on the last one, which scrolls. */
    private List<OrderedText> statusLines(Text text, int max) {
        List<OrderedText> lines = UiText.wrap(textRenderer, text, CW + 1);
        if (lines.size() <= max) return lines;
        if (max <= 0) return List.of();
        List<OrderedText> shown = new ArrayList<>(lines.subList(0, max - 1)), rest = new ArrayList<>();
        for (OrderedText line : lines.subList(max - 1, lines.size())) {
            if (!rest.isEmpty()) rest.add(OrderedText.styledForwardsVisitedString(" ", Style.EMPTY));
            rest.add(line);
        }
        shown.add(OrderedText.concat(rest));
        return shown;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (popup != null) return popup.click(mouseX, mouseY);
        if (pageTab.isPaletteOpen()) {
            pageTab.clickPalette(mouseX, mouseY, button);
            return true;
        }
        BinderTab divider = binder.tabAt(mouseX, mouseY);
        if (divider != null && button == 0) {
            showTab(divider);
            return true;
        }
        if (tab == BinderTab.PAGE) {
            if (button == 0 && pageTab.overModifyLink(mouseX, mouseY)) {
                playClick();
                showTab(BinderTab.FORMATS);
                return true;
            }
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            pageTab.afterClick(getFocused());
            return handled;
        }
        if (tab == BinderTab.FORMATS && formatsTab.click(mouseX, mouseY, button)) return true;
        if (tab == BinderTab.RESULTS && resultsTab.click(mouseX, mouseY, button)) return true;
        if (tab == BinderTab.PIPES && pipesTab.click(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (popup != null) return true;
        return pipesTab.drag(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return pipesTab.release(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (popup != null) return popup.scroll(mouseX, mouseY, verticalAmount);
        if (tab == BinderTab.RESULTS && verticalAmount != 0) {
            resultsTab.scroll(verticalAmount);
            return true;
        }
        if (tab == BinderTab.PIPES && pipesTab.scroll(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
