package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.items.custom.PowerUpItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where the power-ups ({@link PowerUp}) meet the party: their use during a turn, and the hooks the rest of the mod
 * calls so that the power-up of the turn has its say ({@link #onRollFinished} from the dice, {@link #coinsGained} from
 * whatever gives coins). Server side only.
 */
public final class PowerUpService {
    private PowerUpService() {
    }

    /** A party at the turn of a player. */
    public record Turn(PartyControllerEntity controller, TokenTurnPartyStep step) {
        public PowerUpTurn state() {
            return step.getPowerUps();
        }
    }

    /**
     * The turn being played by {@code player}: the current step of a running party, in progress, whose token they own
     * and is not waited for (absent).
     */
    public static @Nullable Turn turnOf(@Nullable UUID player) {
        if (player == null) return null;
        for (PartyControllerEntity controller : PartyControllerEntity.getActivePartyControllers()) {
            if (controller.isRemoved() || !controller.getPartyData().isStarted()) continue;
            if (controller.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep step
                    && step.getStatus() == PartyStep.Status.IN_PROGRESS && !step.isWaitingForAbsentToken()
                    && player.equals(step.getOwnerUUID()))
                return new Turn(controller, step);
        }
        return null;
    }

    // ---------------------------------------------------------------- using one

    /** The longest a player is given to pick a target: the prompt's time, and a margin. */
    private static final int PICK_HOLD_TICKS = DicePrompts.TIMEOUT_TICKS + 40;

    /**
     * {@code player} uses the power-up held in {@code hand} (a right-click), aiming at {@code tile} if they clicked a
     * board space. Refused, with a message and nothing consumed, outside their own turn, once their roll counts,
     * after another power-up this turn, while one is still at work, without the target it needs, or when the power-up
     * itself says no (before or when applied). Else the power-up is applied, then the item consumed (not in creative)
     * and the use announced to the party. A power-up aiming at a player first asks which one
     * ({@link PowerUp#pickPlayer}, the turn held meanwhile).
     *
     * @return false if it was refused (true while a player is being picked)
     */
    public static boolean use(ServerPlayerEntity player, Hand hand, PowerUp powerUp, @Nullable BlockPos tile) {
        Turn turn = turnOf(player.getUuid());
        Text refusal = commonRefusal(turn);
        if (refusal == null && powerUp.target() == PowerUp.Target.TILE && tile == null)
            refusal = Text.translatable("message.steveparty.powerup.needs_tile");
        if (refusal != null) {
            refuse(player, refusal);
            return false;
        }
        ServerWorld world = (ServerWorld) turn.controller().getWorld();
        if (powerUp.target() != PowerUp.Target.PLAYER) {
            return finish(new PowerUpUse(world, player, turn.controller(), turn.step(), powerUp, null,
                    powerUp.target() == PowerUp.Target.TILE ? tile : null), hand);
        }
        List<ServerPlayerEntity> others = otherPlayers(turn.controller(), world, player);
        if (others.isEmpty()) {
            refuse(player, Text.translatable("message.steveparty.powerup.no_player"));
            return false;
        }
        TokenTurnPartyStep step = turn.step();
        // No roll and no other power-up while the player picks
        Runnable release = hold(world, step, PICK_HOLD_TICKS);
        boolean[] picked = {false};
        powerUp.pickPlayer(new PowerUpUse(world, player, turn.controller(), step, powerUp, null, null), others, target -> {
            if (picked[0]) return;
            picked[0] = true;
            release.run();
            // The turn may have moved on while the player was choosing
            Turn now = turnOf(player.getUuid());
            if (now == null || now.step() != step || target == null) return;
            Text late = commonRefusal(now);
            if (late != null) {
                refuse(player, late);
                return;
            }
            finish(new PowerUpUse(world, player, now.controller(), step, powerUp, target.getUuid(), null), hand);
        });
        return true;
    }

    /** Why nobody may use a power-up now, null if the turn allows one. */
    private static @Nullable Text commonRefusal(@Nullable Turn turn) {
        if (turn == null) return Text.translatable("message.steveparty.powerup.not_your_turn");
        if (turn.step().hasRolled()) return Text.translatable("message.steveparty.powerup.after_roll");
        if (turn.state().hasUsed()) return Text.translatable("message.steveparty.powerup.already_used");
        if (isHeld(turn)) return Text.translatable("message.steveparty.powerup.wait");
        return null;
    }

    /**
     * The power-up's own conditions, then it is applied: if it had its effect, it is consumed, remembered and
     * announced; else the turn forgets it and the player keeps it.
     */
    private static boolean finish(PowerUpUse use, Hand hand) {
        Text refusal = use.powerUp().refusal(use);
        if (refusal == null && !holds(use.player(), hand, use.powerUp()))
            refusal = Text.translatable("message.steveparty.powerup.not_held");
        if (refusal != null) {
            refuse(use.player(), refusal);
            return false;
        }
        use.state().use(use.powerUp());
        PowerUp.Result result = use.powerUp().apply(use);
        if (!result.applied()) {
            use.state().forget();
            refuse(use.player(), result.refusal());
            return false;
        }
        consume(use.player(), hand, use.powerUp());
        announce(use);
        use.controller().markDirty();
        use.controller().sendPacketToInterestedPlayers();
        return true;
    }

    /** Whether the player still has one of the power-up (always in creative): in the hand that used it, or anywhere. */
    private static boolean holds(ServerPlayerEntity player, Hand hand, PowerUp powerUp) {
        return player.isInCreativeMode() || find(player, hand, powerUp) != null;
    }

    /** One of the power-up, from the hand that used it, else from anywhere in the inventory (moved meanwhile). */
    private static void consume(ServerPlayerEntity player, Hand hand, PowerUp powerUp) {
        if (player.isInCreativeMode()) return;
        ItemStack stack = find(player, hand, powerUp);
        if (stack != null) stack.decrement(1);
    }

    private static @Nullable ItemStack find(ServerPlayerEntity player, Hand hand, PowerUp powerUp) {
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof PowerUpItem item && item.powerUp() == powerUp) return held;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof PowerUpItem item && item.powerUp() == powerUp) return stack;
        }
        return null;
    }

    // ---------------------------------------------------------------- holding the turn

    /**
     * Holds {@code step} (no roll, no other power-up) for {@code maxTicks} at the latest.
     *
     * @return what releases it (once; harmless if the turn moved on)
     */
    static Runnable hold(ServerWorld world, TokenTurnPartyStep step, int maxTicks) {
        PowerUpTurn state = step.getPowerUps();
        state.hold(world.getTime() + Math.max(1, maxTicks));
        boolean[] released = {false};
        return () -> {
            if (released[0]) return;
            released[0] = true;
            state.release();
        };
    }

    private static boolean isHeld(Turn turn) {
        return turn.state().isHeld(turn.controller().getWorld().getTime());
    }

    /**
     * Why {@code player} may not throw a die now, null if they may: a power-up of their turn is still at work (a
     * player being picked, a warp). Asked by the dice items.
     */
    public static @Nullable Text rollRefusal(PlayerEntity player) {
        return rollRefusal(player, ItemStack.EMPTY);
    }

    /**
     * Why {@code player} may not throw {@code die} now, null if they may: see {@link #rollRefusal(PlayerEntity)}; a die
     * carrying the Power-up module is a power-up: not after another one this turn.
     */
    public static @Nullable Text rollRefusal(PlayerEntity player, ItemStack die) {
        Turn turn = turnOf(player.getUuid());
        if (turn == null) return null;
        if (isHeld(turn)) return Text.translatable("message.steveparty.powerup.wait");
        if (DiceModules.isPowerUp(die) && turn.state().hasUsed()) return Text.translatable("message.steveparty.powerup.already_used");
        return null;
    }

    /**
     * The dice items' check: true (and the player told why, in the action bar) if {@code player} may not throw
     * {@code die} now (see {@link #rollRefusal(PlayerEntity, ItemStack)}).
     */
    public static boolean refusesRoll(PlayerEntity player, ItemStack die) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;
        Text why = rollRefusal(serverPlayer, die);
        if (why == null) return false;
        refuse(serverPlayer, why);
        return true;
    }

    /**
     * {@code player} just threw {@code die}: one carrying the Power-up module, thrown during their turn before their
     * roll counts, is the power-up of the turn (no other one after it).
     */
    public static void onDieThrown(PlayerEntity player, ItemStack die) {
        if (player.getWorld().isClient || !DiceModules.isPowerUp(die)) return;
        Turn turn = turnOf(player.getUuid());
        if (turn == null || turn.step().hasRolled() || turn.state().hasUsed()) return;
        turn.state().use(PowerUps.DIE);
        turn.controller().markDirty();
    }

    /** The other players of the party who are online: those a power-up may aim at. */
    private static List<ServerPlayerEntity> otherPlayers(PartyControllerEntity controller, ServerWorld world, ServerPlayerEntity self) {
        List<ServerPlayerEntity> others = new ArrayList<>();
        for (PlayerEntity owner : controller.getPartyData().getOwners(world)) {
            if (owner instanceof ServerPlayerEntity other && !other.getUuid().equals(self.getUuid()) && !others.contains(other))
                others.add(other);
        }
        return others;
    }

    private static void refuse(ServerPlayerEntity player, @Nullable Text why) {
        if (why != null)
            MessageUtils.sendToPlayer(player, why.copy().formatted(Formatting.RED), MessageUtils.MessageType.ACTION_BAR);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.8f);
    }

    /** In the action bar and the chat of the party's audience (and of its player), like the landings: in its colour. */
    private static void announce(PowerUpUse use) {
        MutableText announcement = use.powerUp().announcement(use);
        if (announcement == null) return;
        MutableText notice = Text.literal("✦ ").append(announcement).formatted(use.powerUp().color());
        List<ServerPlayerEntity> audience = new ArrayList<>(use.controller().getPartyAudience());
        if (!audience.contains(use.player())) audience.add(use.player());
        MessageUtils.sendToPlayers(audience, notice, MessageUtils.MessageType.ACTION_BAR);
        MessageUtils.sendToPlayers(audience, notice, MessageUtils.MessageType.CHAT);
        ServerPlayerEntity player = use.player();
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.5f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 1.2f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.6, 0.4, 0.0);
    }

    // ---------------------------------------------------------------- hooks: until the end of the turn

    /**
     * What a roll does once the power-up of its roller's turn had its say.
     *
     * @param outcome the outcome to apply and announce
     * @param note    what the power-up changed, for the announcement ("(4 +3 Mushroom)"), null if nothing
     */
    public record Roll(DiceOutcome outcome, @Nullable Text note) {
    }

    /**
     * A roll by {@code roller} is final (after the modules of the die): the power-up used during their turn changes it.
     * A blank roll (rolled again) is left alone, so the next one gets the power-up.
     */
    public static Roll onRollFinished(@Nullable UUID roller, DiceOutcome outcome) {
        boolean blank = outcome.steps() == 0 && !outcome.isSpecial() && !outcome.zero();
        Turn turn = blank ? null : turnOf(roller);
        PowerUp used = turn == null ? null : turn.state().used();
        if (used == null) return new Roll(outcome, null);
        DiceOutcome changed = used.modifyRoll(outcome, turn.state());
        turn.controller().markDirty();
        if (changed.steps() == outcome.steps()) return new Roll(changed, null);
        Text note = Text.translatable("message.steveparty.powerup.roll_note", Integer.toString(outcome.steps()),
                DiceOutcome.signed(changed.steps() - outcome.steps()), used.name()).formatted(used.color());
        return new Roll(changed, note);
    }

    /**
     * {@code player} is about to gain {@code coins} coins of their party: the power-up of their turn may change it.
     * Losses (and anything outside their turn) are left as they are.
     *
     * @return the coins they really gain
     */
    public static int coinsGained(@Nullable UUID player, int coins) {
        if (coins <= 0) return coins;
        Turn turn = turnOf(player);
        PowerUp used = turn == null ? null : turn.state().used();
        return used == null ? coins : Math.max(0, used.modifyCoinsGained(coins, turn.state()));
    }

    /**
     * {@code player} is about to receive {@code count} of {@code given} (an item space, a bank...): if it is the coin of
     * the party they are playing their turn in, {@link #coinsGained} decides how many.
     */
    public static int itemsGained(PlayerEntity player, ItemStack given, int count) {
        Turn turn = turnOf(player.getUuid());
        if (turn == null || count <= 0) return count;
        ItemStack coin = turn.controller().getCurrency(PartyCurrency.COIN);
        if (!ItemStack.areItemsAndComponentsEqual(coin, given)) return count;
        return coinsGained(player.getUuid(), count);
    }
}
