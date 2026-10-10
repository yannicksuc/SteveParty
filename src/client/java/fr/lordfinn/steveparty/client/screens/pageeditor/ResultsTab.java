package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * The « Results » tab: the podiums, goal pole bases and step controllers linked to the page, with their block's name and
 * where they are. A click shows where a card's block is, a right click unlinks it.
 */
public final class ResultsTab {
    /** The cards: full width, scrolled by card. */
    private static final int RESULT_PITCH = 18, RESULT_H = 16, RESULTS_TOP = M + 18, RESULT_ROWS = 9;

    private final PageEditor editor;
    private int scroll;

    public ResultsTab(PageEditor editor) {
        this.editor = editor;
    }

    private TextRenderer font() {
        return editor.font();
    }

    /**
     * A linked block's name: the block there (« Podium d'or ») when it is loaded here, else its kind's block (« Socle
     * de mât d'arrivée », « Contrôleur de pas », « Podium »).
     */
    private Text name(MiniGamePodiumLink link) {
        if (loadedHere(link)) {
            Block block = editor.client().world.getBlockState(link.pos().pos()).getBlock();
            boolean expected = switch (link.kind()) {
                case PODIUM -> block instanceof PodiumBlock;
                case COUNTER -> block == ModBlocks.GOAL_POLE_BASE;
                default -> block == ModBlocks.STEP_CONTROLLER;
            };
            if (expected) return block.getName();
        }
        return switch (link.kind()) {
            case PODIUM -> Text.translatable("block.steveparty.podium");
            case COUNTER -> ModBlocks.GOAL_POLE_BASE.getName();
            default -> ModBlocks.STEP_CONTROLLER.getName();
        };
    }

    private boolean loadedHere(MiniGamePodiumLink link) {
        MinecraftClient client = editor.client();
        return client != null && client.world != null && client.world.getRegistryKey().equals(link.pos().dimension())
                && client.world.isChunkLoaded(link.pos().pos());
    }

    /** A podium's height (half blocks), -1 when it is not loaded here. */
    private int height(MiniGamePodiumLink link) {
        if (link.kind() != MiniGamePodiumLink.Kind.PODIUM || !loadedHere(link)) return -1;
        MinecraftClient client = editor.client();
        if (!PodiumBlock.isPodium(client.world.getBlockState(link.pos().pos()))) return -1;
        return PodiumBlock.heightOf(client.world, link.pos().pos());
    }

    /** A podium's place among the page's (0 when not known). */
    private int placeOf(MiniGamePodiumLink link, List<MiniGamePodiumLink> links) {
        int height = height(link);
        if (height < 0) return 0;
        TreeSet<Integer> taller = new TreeSet<>();
        for (MiniGamePodiumLink other : links) {
            int h = height(other);
            if (h > height) taller.add(h);
        }
        return taller.size() + 1;
    }

    /** What a card's block does: a podium's place (the taller, the better), « counter », « ends the game ». */
    private Text role(MiniGamePodiumLink link, List<MiniGamePodiumLink> links) {
        if (link.kind() != MiniGamePodiumLink.Kind.PODIUM) return Text.translatable(KEY + "podiums.card.sub." + link.kind().key());
        int place = placeOf(link, links);
        if (place <= 0) return Text.translatable(KEY + "podiums.card.sub.podium");
        return place == 1 ? Text.translatable(KEY + "podiums.card.sub.first") : Text.translatable(KEY + "podiums.card.sub.place", place);
    }

    private static String position(MiniGamePodiumLink link) {
        BlockPos pos = link.pos().pos();
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private int cardY(int index) {
        return editor.top() + RESULTS_TOP + (index - scroll) * RESULT_PITCH;
    }

    /** The linked block whose card is under the mouse, null for none. */
    private @Nullable MiniGamePodiumLink podiumAt(double mouseX, double mouseY) {
        List<MiniGamePodiumLink> links = editor.current().podiumLinks();
        for (int index = scroll; index < links.size() && index < scroll + RESULT_ROWS; index++) {
            if (HitArea.contains(mouseX, mouseY, editor.left() + LX, cardY(index), FULL, RESULT_H)) return links.get(index);
        }
        return null;
    }

    public void draw(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        List<MiniGamePodiumLink> links = data.podiumLinks();
        int x = editor.left(), y = editor.top(), lx = x + LX;
        // The first line: what is linked (without any podium the mini-game names no winner)
        if (!data.hasPodium()) {
            UiText.line(context, font(), Text.translatable(KEY + "podiums.none"), lx, y + M + 2, FULL - 16, ORANGE2, false);
        } else {
            long podiums = links.stream().filter(link -> link.kind() == MiniGamePodiumLink.Kind.PODIUM).count();
            long counters = links.stream().filter(link -> link.kind() == MiniGamePodiumLink.Kind.COUNTER).count();
            UiText.line(context, font(), Text.translatable(KEY + "podiums.complete", podiums, counters, links.size() - podiums - counters),
                    lx, y + M + 2, FULL - 16, INK2, false);
        }
        scroll = Math.max(0, Math.min(scroll, links.size() - RESULT_ROWS));
        MiniGamePodiumLink hovered = podiumAt(mouseX, mouseY);
        for (int index = scroll; index < links.size() && index < scroll + RESULT_ROWS; index++) {
            MiniGamePodiumLink link = links.get(index);
            int top = cardY(index);
            ConsolePaint.box(context, lx, top, FULL, RESULT_H, CARD, 1, 1);
            if (link == hovered) ConsolePaint.highlight(context, lx, top, FULL, RESULT_H, 1, TEAL2, 0);
            Text role = role(link, links);
            int place = link.kind() == MiniGamePodiumLink.Kind.PODIUM ? placeOf(link, links) : 0;
            int colour = place == 1 ? 0xFFFFD83D : place == 2 ? 0xFFC9D3DA : place == 3 ? 0xFFD98A4A
                    : link.kind() == MiniGamePodiumLink.Kind.COUNTER ? 0xFF3A9BFF : link.kind() == MiniGamePodiumLink.Kind.STEP_CONTROLLER ? 0xFFA35CFF : 0xFFFFD83D;
            context.fill(lx + 2, top + 2, lx + 6, top + 14, colour);
            // Its block's name (bold), what it does, where it is (greyed, at the right). Too long together, the name keeps
            // at most two thirds of their room and each scrolls in its part
            String where = position(link);
            int whereWidth = font().getWidth(where) - 1;
            int room = FULL - 6 - whereWidth - 6 - 10 + 1;
            Text name = name(link);
            int nameWidth = font().getWidth(name), roleWidth = font().getWidth(role);
            if (nameWidth + 6 + roleWidth > room) nameWidth = Math.min(nameWidth, Math.max(room * 2 / 3, room - 6 - roleWidth));
            UiText.line(context, font(), name, lx + 10, top + 5, nameWidth, INK, false);
            UiText.line(context, font(), name, lx + 11, top + 5, nameWidth, INK, false);
            if (room - nameWidth - 6 > 0) UiText.line(context, font(), role, lx + 10 + nameWidth + 6, top + 5, room - nameWidth - 6, INK2, false);
            UiText.line(context, font(), where, lx + FULL - 6 - whereWidth, top + 5, whereWidth + 1, INK3, false);
        }
        if (scroll > 0) UiText.line(context, font(), "▲", x + PW - M - 8, y + RESULTS_TOP - 9, 8, INK3, false);
        if (scroll + RESULT_ROWS < links.size()) UiText.line(context, font(), "▼", x + PW - M - 8, y + RESULTS_TOP + RESULT_ROWS * RESULT_PITCH, 8, INK3, false);
    }

    /** Over everything: the tooltip of the first line (no podium), or of a card. */
    public void drawOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!editor.current().hasPodium() && HitArea.contains(mouseX, mouseY, editor.left() + LX, editor.top() + M, PW - M - 16 - LX, 12)) {
            PagePaint.tooltip(context, font(), List.of(Text.translatable(KEY + "podiums.none").formatted(Formatting.GOLD)), mouseX, mouseY);
            return;
        }
        MiniGamePodiumLink link = podiumAt(mouseX, mouseY);
        if (link == null) return;
        List<Text> lines = new ArrayList<>();
        lines.add(name(link).copy().formatted(Formatting.GOLD));
        lines.add(Text.translatable(KEY + "podiums.card." + link.kind().key() + ".hint").formatted(Formatting.GRAY));
        lines.add(Text.translatable(KEY + (editor.canEdit() ? "podiums.card.hint" : "pipes.card.hint.read_only")).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        PagePaint.tooltip(context, font(), lines, mouseX, mouseY);
    }

    /** A right click on a card unlinks its block, a left one shows where it is. @return true if the click was a card's */
    public boolean click(double mouseX, double mouseY, int button) {
        MiniGamePodiumLink link = podiumAt(mouseX, mouseY);
        if (link != null && button == 1) {
            if (editor.canEdit()) {
                editor.send(new MiniGamePagePayloads.PodiumUnlink(editor.hand(), editor.pageId(), link.pos()));
                editor.playClick();
            }
            return true;
        }
        if (link != null && button == 0) {
            DestinationsRenderer.locate(link.pos(), LOCATE_TICKS);
            MinecraftClient client = editor.client();
            if (client != null && client.player != null) client.player.playSound(ModSounds.SELECT_SOUND_EVENT, 1.0F, 1.4F);
            boolean here = client != null && client.world != null && client.world.getRegistryKey().equals(link.pos().dimension());
            editor.setStatus(Text.translatable(KEY + (here ? "status.located" : "status.located_elsewhere"), position(link)), false);
            return true;
        }
        return false;
    }

    /** The wheel scrolls the cards. */
    public void scroll(double verticalAmount) {
        scroll = Math.max(0, scroll - (int) Math.signum(verticalAmount));
    }
}
