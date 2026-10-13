package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Outcome;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.powerups.effects.TrapKind;
import fr.lordfinn.steveparty.service.MistigriSentences;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;

/**
 * The party's texts name its currencies by their items (translatable, so each client reads its own language): a party
 * counting gold nuggets and nether stars says so, outside a party the mod's Coin and Party Star are named.
 */
public class CurrencyTextGameTests implements SteveGameTest {
    private static final String NUGGET = "item.minecraft.gold_nugget", NETHER_STAR = "item.minecraft.nether_star";

    /** Every translation key in {@code text}: its own, its arguments' and its siblings', deep. */
    private static List<String> keys(Text text) {
        List<String> keys = new ArrayList<>();
        collect(text, keys);
        return keys;
    }

    private static void collect(Object value, List<String> keys) {
        if (!(value instanceof Text text)) return;
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            keys.add(translatable.getKey());
            for (Object arg : translatable.getArgs()) collect(arg, keys);
        }
        for (Text sibling : text.getSiblings()) collect(sibling, keys);
    }

    private static PartyControllerEntity customParty(TestContext context, ServerPlayerEntity player) {
        MobEntity token = token(context, new BlockPos(1, 1, 1), player.getUuid());
        PartyControllerEntity party = party(context, player.getUuid(), token);
        context.assertTrue(party.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_NUGGET)), "coins: gold nuggets");
        context.assertTrue(party.setCurrency(PartyCurrency.STAR, new ItemStack(Items.NETHER_STAR)), "stars: nether stars");
        return party;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void amountsNameTheDefaultItemsOutsideAParty(TestContext context) {
        Text coins = PartyCurrencyText.amount(null, PartyCurrency.COIN, 5);
        context.assertTrue(coins.getContent() instanceof TranslatableTextContent t && t.getKey().equals(PartyCurrencyText.AMOUNT_KEY)
                && t.getArgs()[0].equals(5), "« 5 × … »: " + keys(coins));
        context.assertEquals(keys(coins), List.of(PartyCurrencyText.AMOUNT_KEY, "item.steveparty.coin"), "the mod's coin");
        context.assertEquals(keys(PartyCurrencyText.name(null, PartyCurrency.STAR)), List.of("item.steveparty.party_star"), "the Party Star");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void amountsNameThePartysItems(TestContext context) {
        PartyControllerEntity party = customParty(context, player(context));
        context.assertTrue(keys(PartyCurrencyText.amount(party, PartyCurrency.COIN, 3)).contains(NUGGET), "coins: the gold nugget's name");
        context.assertTrue(keys(PartyCurrencyText.amount(party, PartyCurrency.STAR, 1)).contains(NETHER_STAR), "stars: the nether star's name");
        // A custom-named item keeps its name, as typed
        ItemStack ecu = new ItemStack(Items.GOLD_NUGGET);
        ecu.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Écu"));
        context.assertTrue(party.setCurrency(PartyCurrency.COIN, ecu), "coins: renamed nuggets");
        context.assertEquals(PartyCurrencyText.name(party, PartyCurrency.COIN).getString(), "Écu", "the custom name");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diceTrapAndMistigriTextsNameThePartysItems(TestContext context) {
        PartyControllerEntity party = customParty(context, player(context));
        ItemStack coin = party.getCurrency(PartyCurrency.COIN), star = party.getCurrency(PartyCurrency.STAR);
        List<String> dice = keys(new DiceOutcome(0, 5, true, false, false).describe(coin));
        context.assertTrue(dice.contains(NUGGET) && !dice.contains("item.steveparty.coin"), "a coin face: " + dice);
        context.assertTrue(keys(new TrapSetupComponent.Effect(TrapKind.COINS, 10).describe(coin)).contains(NUGGET), "a trap");
        List<String> lose = keys(new Outcome(Kind.LOSE_STARS, Target.LANDED, 1, false, 1).text(null, coin, star));
        context.assertTrue(lose.contains(NETHER_STAR), "a Mistigri star theft: " + lose);
        List<String> share = keys(new Outcome(Kind.SHARE_COINS, Target.ALL, 50, true, 1).text(null, coin, star));
        context.assertTrue(share.contains(NUGGET) && share.contains("hud.steveparty.mistigri.effect.share_coins.percent"), "a share: " + share);
        context.complete();
    }

    /** A sentence passed in a party tells the coins taken in its item: « LordFinn −3 × Gold Nugget ». */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mistigriChatNamesThePartysCoin(TestContext context) {
        ServerPlayerEntity player = player(context);
        atEnd(context, () -> player.removeCommandTag(CursedRolls.TAG));
        PartyControllerEntity party = customParty(context, player);
        player.getInventory().clear();
        InventoryUtils.giveOrDrop(player, new ItemStack(Items.GOLD_NUGGET), 10);
        Text told = MistigriSentences.apply(context.getWorld(), party, player, new Outcome(Kind.LOSE_COINS, Target.LANDED, 3, false, 1),
                Random.create(1), MistigriSentences.Moves.NONE);
        context.assertTrue(told != null, "something is told");
        List<String> keys = keys(told);
        context.assertTrue(keys.contains("message.steveparty.mistigri_space.delta") && keys.contains(NUGGET)
                && keys.contains(PartyCurrencyText.AMOUNT_KEY), "the delta names the nugget: " + keys);
        context.complete();
    }
}
