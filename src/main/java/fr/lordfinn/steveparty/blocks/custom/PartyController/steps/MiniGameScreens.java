package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static fr.lordfinn.steveparty.utils.SoundsUtils.playSoundToPlayers;

/**
 * What the players of a mini-game and its audience see of it: the card of the mini-game drawn (and its countdown),
 * the practice chip, the big title when it starts, and the results card with the same lines in the chat.
 */
final class MiniGameScreens {
    private final MiniGamePartyStep step;
    /** What the practice chip last showed, and to whom: sent again only when it changes. Not saved. */
    private @Nullable Object practiceShown;
    /** The results of the mini-game once it is over (as sent to the players), null before. Not saved. */
    private @Nullable MiniGameResults lastResults;

    MiniGameScreens(MiniGamePartyStep step) {
        this.step = step;
    }

    @Nullable MiniGameResults lastResults() {
        return lastResults;
    }

    /** Those who see the card: the party's audience and the players of the mini-game. */
    List<ServerPlayerEntity> previewAudience(PartyControllerEntity controller) {
        List<ServerPlayerEntity> audience = new ArrayList<>(controller.getInterestedPlayersEntities());
        for (ServerPlayerEntity player : step.getOnlineParticipants(controller)) {
            if (!audience.contains(player)) audience.add(player);
        }
        return audience;
    }

    // ---------------------------------------------------------------- the card of the mini-game

    /**
     * Shows the card of the mini-game drawn (picture, title, how it is played) to the audience.
     *
     * @param countdown seconds before the departure, 0 while the countdown has not started
     */
    void showPreview(PartyControllerEntity controller, int countdown) {
        if (controller.getWorld() == null || controller.getWorld().getServer() == null) return;
        ItemStack page = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        if (page.isEmpty()) return;
        MiniGamePageData data = MiniGamePages.of(controller.getWorld().getServer(), page);
        if (data == null) data = MiniGamePageData.empty(MiniGamePageData.NO_ID);
        if (!data.hasTitle()) data = data.withTexts(page.getName().getString(), data.description());
        int format = data.formatFor(MiniGameRoulette.counts(MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue)));
        MiniGamePagePayloads.Preview payload = new MiniGamePagePayloads.Preview(true, data, format, countdown);
        for (ServerPlayerEntity player : previewAudience(controller)) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Preview.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    /** The card goes away (the players leave for the mini-game, or it is called off). */
    void hidePreview(PartyControllerEntity controller) {
        if (controller.getWorld() == null || controller.getWorld().getServer() == null) return;
        MiniGamePagePayloads.Preview payload = new MiniGamePagePayloads.Preview(false, MiniGamePageData.empty(MiniGamePageData.NO_ID), 0, 0);
        for (ServerPlayerEntity player : previewAudience(controller)) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Preview.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    // ---------------------------------------------------------------- the practice chip

    /** The practice chip of the players and the audience: « Practice — [key] Ready 2/4 », and who is ready. */
    void showPractice(PartyControllerEntity controller) {
        if (!step.isPractice() || controller.getWorld() == null || controller.getWorld().getServer() == null) return;
        ItemStack stack = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        MiniGamePageData page = MiniGamePages.of(controller.getWorld().getServer(), stack);
        String title = page != null && page.hasTitle() ? page.title() : stack.isEmpty() ? "" : stack.getName().getString();
        MiniGamePagePayloads.Practice payload = new MiniGamePagePayloads.Practice(true, title, step.voters(controller));
        List<ServerPlayerEntity> audience = previewAudience(controller);
        Object shown = List.of(payload, audience.stream().map(ServerPlayerEntity::getUuid).toList());
        if (shown.equals(practiceShown)) return;
        practiceShown = shown;
        for (ServerPlayerEntity player : audience) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Practice.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    void hidePractice(PartyControllerEntity controller) {
        if (practiceShown == null && step.getPhase() != MiniGamePartyStep.Phase.PRACTICE) return;
        practiceShown = null;
        MiniGamePagePayloads.Practice payload = new MiniGamePagePayloads.Practice(false, "", List.of());
        for (ServerPlayerEntity player : previewAudience(controller)) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Practice.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    // ---------------------------------------------------------------- the start

    /**
     * The mini-game starts: its title in big on the screen (« Go! » under it), and in the chat its title and, when
     * it has one, its description, for the players and the audience.
     */
    void announce(PartyControllerEntity controller, @Nullable MiniGamePageData page, boolean inChat) {
        ItemStack stack = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        String name = page != null && page.hasTitle() ? page.title() : stack.isEmpty() ? "" : stack.getName().getString();
        announceStart(name, page, previewAudience(controller), inChat);
    }

    /** See {@link MiniGamePartyStep#announceStart(String, MiniGamePageData, Collection, boolean)}. */
    static void announceStart(String name, @Nullable MiniGamePageData page, Collection<ServerPlayerEntity> audience, boolean inChat) {
        Text go = Text.translatableWithFallback("message.steveparty.minigame.go", "Go!").styled(style -> style.withColor(0x55FF55).withBold(true));
        Text title = name.isEmpty() ? go : Text.literal(name).styled(style -> style.withColor(0xFFC52E).withBold(true));
        for (ServerPlayerEntity player : audience) {
            if (!name.isEmpty()) player.networkHandler.sendPacket(new SubtitleS2CPacket(go));
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
            if (name.isEmpty() || !inChat) continue;
            player.sendMessage(Text.translatable("message.steveparty.minigame.title", title), false);
            if (page != null && !MiniGameText.strip(page.description()).isBlank()) {
                player.sendMessage(MiniGameText.parse(page.description(), Style.EMPTY.withColor(Formatting.GRAY)), false);
            }
        }
    }

    // ---------------------------------------------------------------- the results

    /** Shows results to the players and the audience: the card, and the same lines in the chat ({@code note}: a last line). */
    void tellResults(PartyControllerEntity controller, MiniGameResults results, @Nullable Text note) {
        String title = results.title();
        ItemStack coin = results.coinItem(), star = results.starItem();
        lastResults = results;
        MiniGamePagePayloads.Results payload = new MiniGamePagePayloads.Results(results);
        List<ServerPlayerEntity> audience = previewAudience(controller);
        for (ServerPlayerEntity player : controller.getPartyAudience()) if (!audience.contains(player)) audience.add(player);
        for (ServerPlayerEntity player : audience) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Results.ID)) ServerPlayNetworking.send(player, payload);
        }
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(title.isEmpty() ? "message.steveparty.minigame.results" : "message.steveparty.minigame.results.of", title)
                .styled(style -> style.withColor(0xFFC52E).withBold(true)));
        for (MiniGameResults.Row row : results.rows()) lines.add(resultLine(row, coin, star));
        if (note != null) lines.add(note);
        for (Text line : lines) MessageUtils.sendToPlayers(audience, line, MessageUtils.MessageType.CHAT);
        playSoundToPlayers(step.getOnlineParticipants(controller), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
    }

    /** See {@link MiniGamePartyStep#resultLine}. */
    static Text resultLine(MiniGameResults.Row row, ItemStack coin, ItemStack star) {
        MutableText line = Text.empty();
        line.append((row.place() == 0 ? Text.translatable("message.steveparty.minigame.results.participant") : Podiums.placeText(row.place()))
                .styled(style -> style.withColor(row.place() == 1 ? 0xFFD700 : row.place() == 0 ? 0xA0A0A0 : 0xFFFFFF).withBold(row.place() == 1)));
        line.append(Text.translatable("message.steveparty.minigame.results.separator"));
        if (row.team() >= 0) line.append(Podiums.teamText(row.team())).append(" (");
        line.append(String.join(", ", row.names()));
        if (row.team() >= 0) line.append(")");
        if (row.coins() > 0) line.append(Text.literal("  +" + row.coins() + " ").append(coin.getName()).formatted(Formatting.GREEN));
        if (row.stars() > 0) line.append(Text.literal("  +" + row.stars() + " ").append(star.getName()).formatted(Formatting.YELLOW));
        return line;
    }
}
