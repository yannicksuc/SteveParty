package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The end of a party: who won. One place per player (his pawns together), the most stars first, the most coins
 * between equal stars, and between equal stars and coins the better start roll (the turn order): never two winners.
 * Told to everyone of the party on the results card and in the chat.
 */
public final class PartyResults {
    private PartyResults() {
    }

    /**
     * A place of the ranking.
     *
     * @param place 1 for the winner, 2, 3...
     * @param owner the player, null for a pawn nobody plays
     * @param name  the player's name (the pawn's when nobody plays it)
     * @param tied  same stars and coins as the place before or after it: the start roll decided
     */
    public record Place(int place, @Nullable UUID owner, String name, int stars, int coins, boolean tied, @Nullable UUID token) {
        public Place(int place, @Nullable UUID owner, String name, int stars, int coins, boolean tied) {
            this(place, owner, name, stars, coins, tied, null);
        }

        /** As the clients get it (the end panel over the podiums): no pawn. */
        public net.minecraft.nbt.NbtCompound toNbt() {
            net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
            nbt.putInt("Place", place);
            if (owner != null) nbt.putUuid("Owner", owner);
            nbt.putString("Name", name);
            nbt.putInt("Stars", stars);
            nbt.putInt("Coins", coins);
            if (tied) nbt.putBoolean("Tied", true);
            return nbt;
        }

        public static Place fromNbt(net.minecraft.nbt.NbtCompound nbt) {
            return new Place(nbt.getInt("Place"), nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null, nbt.getString("Name"),
                    nbt.getInt("Stars"), nbt.getInt("Coins"), nbt.getBoolean("Tied"));
        }
    }

    /** The ranking of the party of {@code controller} (its pawns still in it), the winner first. */
    public static List<Place> ranking(PartyControllerEntity controller, ServerWorld world) {
        // One entry per player, in the turn order (the start rolls'): the sort below is stable
        Map<Object, PartyLiveData.Standing> players = new LinkedHashMap<>();
        for (UUID token : controller.getPartyData().getTokens()) {
            PartyLiveData.Standing standing = PartyLiveData.standingOf(controller, world, token);
            players.putIfAbsent(standing.owner().isPresent() ? standing.owner().get() : token, standing);
        }
        List<PartyLiveData.Standing> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.comparingInt(PartyLiveData.Standing::stars).thenComparingInt(PartyLiveData.Standing::coins).reversed());
        // Ranked by arrival (an End space): who arrived first, in their order, then the others by stars
        int arrived = 0;
        if (controller.getPartyData().getEndRanking() == fr.lordfinn.steveparty.items.custom.cartridges.EndCartridgeItem.Ranking.ARRIVAL.ordinal()) {
            List<PartyLiveData.Standing> first = new ArrayList<>();
            for (UUID token : controller.getPartyData().getArrivals()) {
                for (PartyLiveData.Standing standing : sorted) {
                    if (standing.token().equals(token) && !first.contains(standing)) first.add(standing);
                }
            }
            sorted.removeAll(first);
            sorted.addAll(0, first);
            arrived = first.size();
        }
        List<Place> places = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            PartyLiveData.Standing standing = sorted.get(i);
            boolean tied = i >= arrived && (i > arrived && same(sorted.get(i - 1), standing) || i + 1 < sorted.size() && same(sorted.get(i + 1), standing));
            String name = standing.ownerName().isEmpty() ? standing.tokenName() : standing.ownerName();
            places.add(new Place(i + 1, standing.owner().orElse(null), name, standing.stars(), standing.coins(), tied, standing.token()));
        }
        return places;
    }

    private static boolean same(PartyLiveData.Standing a, PartyLiveData.Standing b) {
        return a.stars() == b.stars() && a.coins() == b.coins();
    }

    /** The ranking as the results card shows it: the places, with the stars and coins each has. */
    public static MiniGameResults card(List<Place> places, ItemStack coinItem, ItemStack starItem) {
        List<MiniGameResults.Row> rows = new ArrayList<>(places.size());
        for (Place place : places)
            rows.add(new MiniGameResults.Row(place.place(), -1, new ArrayList<>(List.of(place.name())), place.coins(), place.stars()));
        boolean tieBroken = places.stream().anyMatch(Place::tied);
        return new MiniGameResults("", coinItem, starItem, rows,
                tieBroken ? MiniGameResults.Kind.PARTY_END_TIE : MiniGameResults.Kind.PARTY_END);
    }

    /** The party is over: everyone of it sees who won (the results card, the same lines in the chat). */
    public static void announce(PartyControllerEntity controller, ServerWorld world) {
        List<Place> places = ranking(controller, world);
        if (places.isEmpty()) return;
        ItemStack coin = PartyCurrencyText.item(controller, PartyCurrency.COIN), star = PartyCurrencyText.item(controller, PartyCurrency.STAR);
        MiniGamePagePayloads.Results payload = new MiniGamePagePayloads.Results(card(places, coin, star));
        List<ServerPlayerEntity> audience = controller.getPartyAudience();
        for (ServerPlayerEntity player : audience) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Results.ID)) ServerPlayNetworking.send(player, payload);
            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
        }
        for (Text line : lines(places, coin, star)) MessageUtils.sendToPlayers(audience, line, MessageUtils.MessageType.CHAT);
    }

    /** The ranking as the chat tells it: who won, a line per place, and how a tie was broken. */
    public static List<Text> lines(List<Place> places, ItemStack coin, ItemStack star) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatableWithFallback("message.steveparty.party.results", "Party over: %s wins!", places.getFirst().name())
                .styled(style -> style.withColor(0xFFC52E).withBold(true)));
        for (Place place : places) lines.add(line(place, coin, star));
        if (places.stream().anyMatch(Place::tied))
            lines.add(Text.translatableWithFallback("message.steveparty.party.results.tie", "Same stars and coins: the better start roll wins.")
                    .formatted(Formatting.GRAY));
        return lines;
    }

    private static Text line(Place place, ItemStack coin, ItemStack star) {
        MutableText line = Text.empty();
        line.append(Podiums.placeText(place.place())
                .styled(style -> style.withColor(place.place() == 1 ? 0xFFD700 : 0xFFFFFF).withBold(place.place() == 1)));
        line.append(Text.translatable("message.steveparty.minigame.results.separator"));
        line.append(place.name());
        // Coins then stars, as on the card and in the mini-game results
        line.append(Text.literal("  ").append(PartyCurrencyText.amount(coin, place.coins())).formatted(Formatting.GOLD));
        line.append(Text.literal("  ").append(PartyCurrencyText.amount(star, place.stars())).formatted(Formatting.YELLOW));
        return line;
    }
}
