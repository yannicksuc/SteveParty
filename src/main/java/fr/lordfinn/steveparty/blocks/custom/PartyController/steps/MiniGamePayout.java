package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What a mini-game pays: the gains of its places, taken from the party's bank, and the results that show them. */
final class MiniGamePayout {
    /** What each player was paid by the last results ({coins, stars}), for their card. */
    private final Map<UUID, int[]> paid = new LinkedHashMap<>();

    /**
     * Pays the gains of the places, taken from the party's bank (see {@link PartyBank}): the 1st place first, then
     * the 2nd..., the participants last, in turn order within a place; when the bank runs short, a player gets what
     * is left and the next ones nothing. Nothing is created.
     *
     * @return the line telling everyone the gains were not all paid, null if they were
     */
    @Nullable Text payGains(PartyControllerEntity controller, MinecraftServer server, Map<UUID, Integer> places) {
        paid.clear();
        List<Map.Entry<UUID, Integer>> order = new ArrayList<>(places.entrySet());
        order.sort(Comparator.comparingInt(entry -> entry.getValue() <= 0 ? Integer.MAX_VALUE : entry.getValue()));
        Inventory bank = PartyBank.inventory(server, controller.getBank());
        boolean full = true;
        for (Map.Entry<UUID, Integer> entry : order) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) continue;
            // Holding a session inventory (he left the round and plays elsewhere, a visit...): what he would be paid
            // would go with it. Like one who is away, he is not paid: the bank keeps it
            if (ZoneBubbles.ofPlayer(player) != null) continue;
            PartyControllerEntity.Paid received = controller.payGains(player, entry.getValue(), bank);
            paid.put(entry.getKey(), new int[]{received.coins(), received.stars()});
            full &= received.full();
        }
        if (full) return null;
        return Text.translatable(bank == null ? "message.steveparty.minigame.results.no_bank" : "message.steveparty.minigame.results.bank_empty")
                .formatted(Formatting.RED);
    }

    /** What each player was paid by the last {@link #payGains}. */
    Map<UUID, int[]> paid() {
        return paid;
    }

    /** The results of the mini-game for these places, with what each player was paid (null: the gains of the places). */
    static MiniGameResults results(PartyControllerEntity controller, MinecraftServer server, Map<UUID, Integer> finalPlaces,
                                   @Nullable Map<UUID, int[]> received) {
        ItemStack stack = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        MiniGamePageData page = MiniGamePages.of(server, stack);
        String title = page != null && page.hasTitle() ? page.title() : stack.isEmpty() ? "" : stack.getName().getString();
        TeamDisposition teams = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN), star = controller.getCurrency(PartyCurrency.STAR);
        return MiniGameResults.of(title, coin, star, controller.getGains(), finalPlaces, teams, uuid -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) return player.getGameProfile().getName();
            return server.getUserCache() == null ? uuid.toString().substring(0, 8)
                    : server.getUserCache().getByUuid(uuid).map(GameProfile::getName).orElse(uuid.toString().substring(0, 8));
        }, received);
    }
}
