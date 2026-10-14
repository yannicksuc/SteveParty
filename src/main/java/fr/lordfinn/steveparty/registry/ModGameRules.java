package fr.lordfinn.steveparty.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

/** The Steve Party game rules. */
public final class ModGameRules {
    /** Seconds a player gets to shop at a shop stop (see ShopStops). */
    public static final int DEFAULT_SHOP_STOP_SECONDS = 60;
    public static final GameRules.Key<GameRules.IntRule> SHOP_STOP_SECONDS = GameRuleRegistry.register(
            "stevepartyShopStopSeconds", GameRules.Category.MISC, GameRuleFactory.createIntRule(DEFAULT_SHOP_STOP_SECONDS, 5, 600));
    /** Lets the Tokenizer Wand take the Wither too (off by default: shrinking or controlling it can be exploited). */
    public static final GameRules.Key<GameRules.BooleanRule> TOKENIZE_BOSSES = GameRuleRegistry.register(
            "stevepartyTokenizeBosses", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
    /** Lets the Tokenizer Wand turn players into player pawns (on by default). */
    public static final GameRules.Key<GameRules.BooleanRule> TOKENIZE_PLAYERS = GameRuleRegistry.register(
            "stevepartyTokenizePlayers", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));
    /** Lets the Tokenizer Wand take hostile mobs (monsters: zombies, creepers...; on by default). */
    public static final GameRules.Key<GameRules.BooleanRule> TOKENIZE_HOSTILE_MOBS = GameRuleRegistry.register(
            "stevepartyTokenizeHostileMobs", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

    private ModGameRules() {
    }

    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }
}
