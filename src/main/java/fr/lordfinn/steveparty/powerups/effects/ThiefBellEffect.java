package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The effect of the Thief Bell power-up ({@code powerup_thief_bell}) and of the Golden Thief Bell
 * ({@code powerup_golden_thief_bell}): the player who rings it picks another player of the party and steals from them.
 * <ul>
 *     <li>the Thief Bell steals {@value #MIN_COINS} to {@value #MAX_COINS} coins at random, never more than the target
 *     holds;</li>
 *     <li>the Golden Thief Bell steals one star, if the target has one.</li>
 * </ul>
 * Coins and stars are the party's currencies ({@link PartyControllerEntity#getCurrency}): items in the players'
 * inventories. What is stolen is taken from the target and given to the thief (what does not fit falls at their feet).
 * Every theft is announced in the chat of the party's audience, the thief and the target.
 * <p>
 * Entry points, server thread only:
 * <ul>
 *     <li>{@link #use}: the whole power-up, picker included (the {@link DicePrompts} list of the swap face; no answer:
 *     {@link #defaultTarget});</li>
 *     <li>{@link #steal}: the theft itself, once the target is known.</li>
 * </ul>
 * Nothing here consumes the power-up item: the caller does ({@code ThiefBellPowerUp}: only when something was stolen
 * or a Padlock parried the theft).
 */
public final class ThiefBellEffect {
    /** Fewest coins a Thief Bell steals (if the target has them). */
    public static final int MIN_COINS = 5;
    /** Most coins a Thief Bell steals. */
    public static final int MAX_COINS = 15;

    /** The two bells. */
    public enum Variant {
        /** Steals coins. */
        THIEF("powerup_thief_bell", PartyCurrency.COIN),
        /** Steals one star. */
        GOLDEN("powerup_golden_thief_bell", PartyCurrency.STAR);

        private final String itemId;
        private final PartyCurrency currency;

        Variant(String itemId, PartyCurrency currency) {
            this.itemId = itemId;
            this.currency = currency;
        }

        /** The id of its item (namespace steveparty). */
        public String itemId() {
            return itemId;
        }

        /** What it steals. */
        public PartyCurrency currency() {
            return currency;
        }

        /** Its name, as its item is called. */
        public Text displayName() {
            return Text.translatable("item.steveparty." + itemId);
        }
    }

    /** How a theft ended. */
    public enum Outcome {
        /** Something was stolen ({@link Result#amount()} > 0). */
        STOLEN,
        /** The target had nothing of the kind to steal. */
        NOTHING,
        /** The target was protected (Padlock): nothing stolen. */
        PROTECTED,
        /** No other player to steal from (or the target was not a valid one). */
        NO_TARGET
    }

    /**
     * What a theft did.
     *
     * @param target null for {@link Outcome#NO_TARGET}
     * @param amount coins or stars stolen (0 unless {@link Outcome#STOLEN})
     */
    public record Result(Outcome outcome, Variant variant, UUID thief, @Nullable UUID target, int amount) {
    }

    /**
     * The hook of the Padlock power-up. Asked once per theft, before anything is taken, and only when something would
     * be taken (the target holds some of it).
     */
    @FunctionalInterface
    public interface Protection {
        /** No protection: every theft goes through. */
        Protection NONE = (thief, target, variant) -> false;

        /**
         * The Padlock: the target's token protected ({@link PowerUpProtection}) parries the theft and its protection
         * is used up (announced by the Padlock: « Y's Padlock blocked the Thief Bell! »).
         */
        static Protection padlock(PartyControllerEntity party) {
            return (thief, target, variant) -> PowerUpProtection.consume(party,
                    PowerUpProtection.tokenOf(party, target.getUuid()), PowerUpProtection.Attack.THIEF_BELL);
        }

        /**
         * PADLOCK: whether {@code target} is protected against this theft. An implementation that answers true also
         * consumes the protection (the Padlock is used up); the theft is then cancelled and announced as such.
         */
        boolean blocks(ServerPlayerEntity thief, ServerPlayerEntity target, Variant variant);
    }

    private ThiefBellEffect() {
    }

    // ---------------------------------------------------------------- targets

    /**
     * The players {@code thief} may steal from: the other players of the party (owners of its tokens), connected, in
     * play order.
     */
    public static List<ServerPlayerEntity> candidates(PartyControllerEntity party, ServerPlayerEntity thief) {
        MinecraftServer server = thief.getServer();
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        if (server == null) return candidates;
        for (UUID id : party.getPlayersInOrder()) {
            if (id.equals(thief.getUuid())) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) candidates.add(player);
        }
        return candidates;
    }

    /** How many items of the currency {@code player} holds, by the party's template. */
    public static int holdings(PartyControllerEntity party, ServerPlayerEntity player, PartyCurrency currency) {
        return InventoryUtils.count(player.getInventory(), party.getCurrency(currency));
    }

    /**
     * The target picked when the thief does not answer: the candidate with the most of what the bell steals (coins
     * for the Thief Bell, stars for the Golden one), ties broken by the other currency, then by play order. Null when
     * there is no other player.
     */
    public static @Nullable ServerPlayerEntity defaultTarget(PartyControllerEntity party, ServerPlayerEntity thief, Variant variant) {
        return defaultTarget(party, candidates(party, thief), variant);
    }

    /** {@link #defaultTarget(PartyControllerEntity, ServerPlayerEntity, Variant)} among {@code candidates}. */
    public static @Nullable ServerPlayerEntity defaultTarget(PartyControllerEntity party, List<ServerPlayerEntity> candidates, Variant variant) {
        PartyCurrency first = variant.currency();
        // max() keeps the first of equal elements: the first in play order
        return candidates.stream()
                .max(Comparator.<ServerPlayerEntity>comparingInt(player -> holdings(party, player, first))
                        .thenComparingInt(player -> holdings(party, player, first.other())))
                .orElse(null);
    }

    // ---------------------------------------------------------------- use

    /**
     * Uses the bell: asks {@code thief} which player to steal from (a list of the {@link #candidates}, with what each
     * holds), then {@link #steal}s. Unanswered after {@code timeoutTicks}, or if the thief gets another prompt, the
     * {@link #defaultTarget} is robbed. With no other player, nothing is asked: the result is {@link Outcome#NO_TARGET}
     * right away (announced).
     *
     * @param timeoutTicks time given to pick ({@link DicePrompts#TIMEOUT_TICKS} usually)
     * @param then         called once with the result (on the server thread, maybe later)
     */
    public static void use(PartyControllerEntity party, ServerPlayerEntity thief, Variant variant, Random random,
                           Protection protection, int timeoutTicks, Consumer<Result> then) {
        List<ServerPlayerEntity> candidates = candidates(party, thief);
        if (candidates.isEmpty()) {
            then.accept(steal(party, thief, null, variant, random, protection));
            return;
        }
        ask(party, thief, variant, candidates, timeoutTicks,
                target -> then.accept(steal(party, thief, target, variant, random, protection)));
    }

    /**
     * Asks {@code thief} which of {@code candidates} (not empty) to steal from: a list showing what each holds. The
     * answer, or the {@link #defaultTarget} once {@code timeoutTicks} are over (or if the thief gets another prompt),
     * goes to {@code pick}, exactly once.
     */
    public static void ask(PartyControllerEntity party, ServerPlayerEntity thief, Variant variant,
                           List<ServerPlayerEntity> candidates, int timeoutTicks, Consumer<ServerPlayerEntity> pick) {
        ServerPlayerEntity fallback = defaultTarget(party, candidates, variant);
        List<DicePrompts.Option> options = new ArrayList<>();
        for (ServerPlayerEntity candidate : candidates) {
            options.add(new DicePrompts.Option(PowerUp.headOf(candidate), Text.translatable("gui.steveparty.thief_bell.option",
                    candidate.getDisplayName(), holdings(party, candidate, variant.currency()),
                    party.getCurrency(variant.currency()).getName()).formatted(Formatting.WHITE)));
        }
        Text title = Text.translatable("gui.steveparty.thief_bell.prompt." + variant.name().toLowerCase(java.util.Locale.ROOT));
        DicePrompts.ask(thief, title, DicePrompts.Layout.LIST, options, timeoutTicks, Math.max(0, candidates.indexOf(fallback)),
                index -> pick.accept(candidates.get(index)));
    }

    // ---------------------------------------------------------------- theft

    /**
     * Steals from {@code target} for {@code thief}, and announces it. The Thief Bell takes
     * {@code random.nextBetween(MIN_COINS, MAX_COINS)} coins, at most what the target holds; the Golden one takes one
     * star if the target has one. Nothing is taken from a target that is the thief, null, disconnected or protected;
     * the protection is only asked when the target holds something to take (an empty-handed target keeps its Padlock).
     *
     * @param target     the robbed player, null to report that there was nobody to rob
     * @param protection the Padlock hook ({@link Protection#NONE} for none)
     */
    public static Result steal(PartyControllerEntity party, ServerPlayerEntity thief, @Nullable ServerPlayerEntity target,
                               Variant variant, Random random, Protection protection) {
        if (target == null || target == thief || target.isRemoved() || target.isDisconnected()) {
            Result result = new Result(Outcome.NO_TARGET, variant, thief.getUuid(), null, 0);
            announce(party, thief, null, result);
            return result;
        }

        int held = holdings(party, target, variant.currency());
        // PADLOCK: the target's Padlock is checked (and consumed) here, once a theft would really happen, before
        // anything is taken: theft cancelled.
        if (held > 0 && protection.blocks(thief, target, variant)) {
            Result result = new Result(Outcome.PROTECTED, variant, thief.getUuid(), target.getUuid(), 0);
            announce(party, thief, target, result);
            return result;
        }

        ItemStack template = party.getCurrency(variant.currency());
        int wanted = variant == Variant.GOLDEN ? 1 : random.nextBetween(MIN_COINS, MAX_COINS);
        int taken = held <= 0 ? 0 : InventoryUtils.take(target.getInventory(), template, Math.min(wanted, held));
        if (taken > 0) InventoryUtils.giveOrDrop(thief, template, taken);
        Result result = new Result(taken > 0 ? Outcome.STOLEN : Outcome.NOTHING, variant, thief.getUuid(), target.getUuid(), taken);
        announce(party, thief, target, result);
        return result;
    }

    // ---------------------------------------------------------------- announce

    private static void announce(PartyControllerEntity party, ServerPlayerEntity thief, @Nullable ServerPlayerEntity target, Result result) {
        Text bell = result.variant().displayName().copy().formatted(Formatting.GOLD);
        Text thiefName = thief.getDisplayName();
        Text targetName = target == null ? Text.empty() : target.getDisplayName();
        Text currency = party.getCurrency(result.variant().currency()).getName();
        MutableText message = switch (result.outcome()) {
            case STOLEN -> Text.translatable("message.steveparty.thief_bell.stolen", thiefName, bell, result.amount(), currency, targetName)
                    .formatted(Formatting.YELLOW);
            case NOTHING -> Text.translatable("message.steveparty.thief_bell.nothing", thiefName, bell, targetName, currency)
                    .formatted(Formatting.GRAY);
            // The Padlock announces it (« Y's Padlock blocked the Thief Bell! »)
            case PROTECTED -> null;
            case NO_TARGET -> Text.translatable("message.steveparty.thief_bell.nobody", thiefName, bell)
                    .formatted(Formatting.GRAY);
        };
        List<ServerPlayerEntity> audience = new ArrayList<>(party.getPartyAudience());
        if (!audience.contains(thief)) audience.add(thief);
        if (target != null && !audience.contains(target)) audience.add(target);
        if (message != null) MessageUtils.sendToPlayers(audience, message, MessageUtils.MessageType.CHAT);

        ServerWorld world = thief.getServerWorld();
        world.playSound(null, thief.getX(), thief.getY(), thief.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1f,
                result.variant() == Variant.GOLDEN ? 1.4f : 1f);
        if (result.outcome() == Outcome.STOLEN) {
            world.playSound(null, thief.getX(), thief.getY(), thief.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1.2f);
            if (target != null)
                target.getServerWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_VILLAGER_NO,
                        SoundCategory.PLAYERS, 0.6f, 1f);
        }
    }
}
