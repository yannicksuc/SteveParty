package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResults;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

/** The end of a party: one winner, the most stars, then the most coins, then the better start roll. */
public class PartyResultsGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(2, 1, 2);

    private static ServerPlayerEntity player(TestContext context, String name, PartyControllerEntity controller, int stars, int coins) {
        ServerPlayerEntity player = TestPlayers.joined(context, "results", name, GameMode.SURVIVAL, 1, 2, 1);
        TestPlayers.removeAtEnd(context, player);
        player.getInventory().clear();
        if (stars > 0) player.getInventory().insertStack(controller.getCurrency(PartyCurrency.STAR).copyWithCount(stars));
        if (coins > 0) player.getInventory().insertStack(controller.getCurrency(PartyCurrency.COIN).copyWithCount(coins));
        return player;
    }

    private static void own(TestContext context, UUID token, ServerPlayerEntity owner) {
        ((TokenizedEntityInterface) context.getWorld().getEntity(token)).steveparty$setTokenOwner(owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theRankingHasOneWinnerTiesGoToTheStartRoll(TestContext context) {
        UUID a = TestBoards.token(context, new BlockPos(1, 1, 5), null).getUuid();
        UUID b = TestBoards.token(context, new BlockPos(3, 1, 5), null).getUuid();
        UUID c = TestBoards.token(context, new BlockPos(5, 1, 5), null).getUuid();
        // The turn order (start rolls): a, b, c
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, a, b, c);
        ServerPlayerEntity alice = player(context, "A", controller, 1, 10);
        ServerPlayerEntity bob = player(context, "B", controller, 1, 10);
        ServerPlayerEntity carol = player(context, "C", controller, 2, 0);
        own(context, a, alice);
        own(context, b, bob);
        own(context, c, carol);

        List<PartyResults.Place> places = PartyResults.ranking(controller, context.getWorld());
        context.assertEquals(places.size(), 3, "a place per player");
        context.assertEquals(places.get(0).owner(), carol.getUuid(), "the most stars wins, whatever the coins");
        context.assertEquals(places.get(1).owner(), alice.getUuid(), "same stars and coins: the better start roll first");
        context.assertEquals(places.get(2).owner(), bob.getUuid(), "then the other");
        context.assertEquals(places.stream().map(PartyResults.Place::place).toList(), List.of(1, 2, 3), "never two at the same place");
        context.assertTrue(!places.get(0).tied() && places.get(1).tied() && places.get(2).tied(), "the tie is known");

        MiniGameResults card = PartyResults.card(places, ItemStack.EMPTY, ItemStack.EMPTY);
        context.assertEquals(card.kind(), MiniGameResults.Kind.PARTY_END_TIE, "the card says the start roll decided");
        context.assertTrue(!card.test(), "nothing about unpaid gains");
        context.assertEquals(card.rows().get(0).stars(), 2, "what the winner has");

        // Without a tie: the plain end card
        bob.getInventory().insertStack(controller.getCurrency(PartyCurrency.COIN).copyWithCount(1));
        List<PartyResults.Place> untied = PartyResults.ranking(controller, context.getWorld());
        context.assertEquals(untied.get(1).owner(), bob.getUuid(), "one coin more: second");
        context.assertEquals(PartyResults.card(untied, ItemStack.EMPTY, ItemStack.EMPTY).kind(), MiniGameResults.Kind.PARTY_END, "no tie");
        controller.stopParty(Text.literal("Tester"));
        context.complete();
    }

    /** A player playing two pawns has one place. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void twoPawnsOnePlayerOnePlace(TestContext context) {
        UUID a1 = TestBoards.token(context, new BlockPos(1, 1, 5), null).getUuid();
        UUID a2 = TestBoards.token(context, new BlockPos(3, 1, 5), null).getUuid();
        UUID b = TestBoards.token(context, new BlockPos(5, 1, 5), null).getUuid();
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, a1, a2, b);
        ServerPlayerEntity alice = player(context, "A2", controller, 0, 3);
        ServerPlayerEntity bob = player(context, "B2", controller, 0, 5);
        own(context, a1, alice);
        own(context, a2, alice);
        own(context, b, bob);
        List<PartyResults.Place> places = PartyResults.ranking(controller, context.getWorld());
        context.assertEquals(places.stream().map(PartyResults.Place::owner).toList(), List.of(bob.getUuid(), alice.getUuid()), "one place each");
        controller.stopParty(Text.literal("Tester"));
        context.complete();
    }
}
