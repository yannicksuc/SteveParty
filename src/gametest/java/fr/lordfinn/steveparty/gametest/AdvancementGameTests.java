package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.advancement.ModCriteria;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;

import java.util.List;

/** The advancements load, and the {@code steveparty:event} trigger grants them on the right event, value and creature. */
public class AdvancementGameTests implements SteveGameTest {

    /** A few advancements of every tab, the new ones included: they must all load (an unknown trigger drops one). */
    private static final List<String> EXPECTED = List.of(
            "main/root", "main/party/start", "main/party/win", "main/dice/thirty", "main/minigames/one_vs_all",
            "main/spaces/pot", "main/spaces/star", "main/board/resident", "main/party/piggy_bank_payout",
            "workshop/root", "workshop/bubbles/ride", "workshop/bubbles/cloud_nine", "workshop/plastic/looting_box",
            "stars/root", "stars/burst_black", "stars/full_house",
            "wild_cards/root", "wild_cards/spin_doctor", "wild_cards/floating_bomb",
            "creatures/root", "creatures/glandouille/tower", "creatures/frousseux/all_colours",
            "creatures/boomcart/hot_potato", "creatures/magpie/delivery", "creatures/trichaudron/crew",
            "creatures/mistigri/summon");

    private static AdvancementEntry advancement(TestContext context, String path) {
        AdvancementEntry entry = context.getWorld().getServer().getAdvancementLoader().get(Steveparty.id(path));
        if (entry == null) throw new AssertionError("advancement " + path + " did not load");
        return entry;
    }

    private static boolean done(ServerPlayerEntity player, AdvancementEntry entry) {
        return player.getAdvancementTracker().getProgress(entry).isDone();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theAdvancementsOfEveryTabLoad(TestContext context) {
        for (String path : EXPECTED) advancement(context, path);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anEventGrantsItsAdvancement(TestContext context) {
        ServerPlayerEntity player = TestPlayers.joined(context, "adv", "Pot", GameMode.SURVIVAL, 1, 2, 1);
        AdvancementEntry pot = advancement(context, "main/spaces/pot");
        context.assertFalse(done(player, pot), "the pot is not won yet");
        ModCriteria.fire(player, "pot_lost");
        context.assertFalse(done(player, pot), "another event grants nothing");
        ModCriteria.fire(player, "pot_won", 12);
        context.assertTrue(done(player, pot), "winning the pot grants Jackpot!");
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theValueMustBeInRange(TestContext context) {
        ServerPlayerEntity player = TestPlayers.joined(context, "adv", "Dice", GameMode.SURVIVAL, 1, 2, 1);
        AdvancementEntry thirty = advancement(context, "main/dice/thirty");
        ModCriteria.fire(player, "roll_3", 29);
        ModCriteria.fire(player, "roll_2", 30);
        context.assertFalse(done(player, thirty), "29 with three dice, or 30 with two, is not Thirty Steps to Glory");
        ModCriteria.fire(player, "roll_3", 30);
        context.assertTrue(done(player, thirty), "30 with three dice is");
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theCreatureMustMatch(TestContext context) {
        ServerPlayerEntity player = TestPlayers.joined(context, "adv", "Bomb", GameMode.SURVIVAL, 1, 2, 1);
        AdvancementEntry bomb = advancement(context, "wild_cards/floating_bomb");
        AdvancementEntry trouble = advancement(context, "workshop/bubbles/bubbled");
        Entity zombie = EntityType.ZOMBIE.create(context.getWorld());
        Entity creeper = EntityType.CREEPER.create(context.getWorld());
        ModCriteria.fire(player, "bubbled_hit", zombie);
        context.assertTrue(done(player, trouble), "any creature counts for Bubble Trouble");
        context.assertFalse(done(player, bomb), "a zombie is not a creeper");
        ModCriteria.fire(player, "bubbled_hit", creeper);
        context.assertTrue(done(player, bomb), "a bubbled creeper is a Floating Bomb");
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyValueOfAnAllAdvancementIsNeeded(TestContext context) {
        ServerPlayerEntity player = TestPlayers.joined(context, "adv", "Acorn", GameMode.SURVIVAL, 1, 2, 1);
        AdvancementEntry harvest = advancement(context, "creatures/glandouille/all_variants");
        for (int variant = 0; variant < 3; variant++) ModCriteria.fire(player, "glandouille_hatched", variant);
        context.assertFalse(done(player, harvest), "three kinds out of four");
        ModCriteria.fire(player, "glandouille_hatched", 3);
        context.assertTrue(done(player, harvest), "the four kinds");
        TestPlayers.remove(context, player);
        context.complete();
    }
}
