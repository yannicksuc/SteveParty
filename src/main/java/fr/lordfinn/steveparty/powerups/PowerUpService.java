package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.items.custom.PowerUpItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

    /**
     * {@code player} uses the power-up held in {@code hand} (a right-click), aiming at {@code tile} if they clicked a
     * board space. Refused, with a message and nothing consumed, outside their own turn, once their roll counts,
     * after another power-up this turn, without the target it needs, or when the power-up itself says no. Else the item
     * is consumed (not in creative), the use announced to the party, and the power-up applied; a power-up aiming at a
     * player first asks which one.
     *
     * @return false if it was refused
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
        List<DicePrompts.Option> options = new ArrayList<>();
        for (ServerPlayerEntity other : others) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.PROFILE, new ProfileComponent(other.getGameProfile()));
            options.add(new DicePrompts.Option(head, other.getDisplayName()));
        }
        TokenTurnPartyStep step = turn.step();
        DicePrompts.ask(player, Text.translatable("gui.steveparty.powerup.pick_player", powerUp.name()), DicePrompts.Layout.LIST,
                options, DicePrompts.TIMEOUT_TICKS, 0, index -> {
                    // The turn may have moved on while the player was choosing
                    Turn now = turnOf(player.getUuid());
                    if (now == null || now.step() != step) return;
                    Text late = commonRefusal(now);
                    if (late != null) {
                        refuse(player, late);
                        return;
                    }
                    finish(new PowerUpUse(world, player, now.controller(), step, powerUp, others.get(index).getUuid(), null), hand);
                });
        return true;
    }

    /** Why nobody may use a power-up now, null if the turn allows one. */
    private static @Nullable Text commonRefusal(@Nullable Turn turn) {
        if (turn == null) return Text.translatable("message.steveparty.powerup.not_your_turn");
        if (turn.step().hasRolled()) return Text.translatable("message.steveparty.powerup.after_roll");
        if (turn.state().hasUsed()) return Text.translatable("message.steveparty.powerup.already_used");
        return null;
    }

    /** The power-up's own conditions, then: consumed, remembered, announced, applied. */
    private static boolean finish(PowerUpUse use, Hand hand) {
        Text refusal = use.powerUp().refusal(use);
        if (refusal != null) {
            refuse(use.player(), refusal);
            return false;
        }
        if (!consume(use.player(), hand, use.powerUp())) return false;
        use.state().use(use.powerUp());
        announce(use);
        use.powerUp().apply(use);
        use.controller().markDirty();
        use.controller().sendPacketToInterestedPlayers();
        return true;
    }

    /** One of the power-up, from the hand that used it, else from anywhere in the inventory (moved meanwhile). */
    private static boolean consume(ServerPlayerEntity player, Hand hand, PowerUp powerUp) {
        if (player.isInCreativeMode()) return true;
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof PowerUpItem item && item.powerUp() == powerUp) {
            held.decrement(1);
            return true;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof PowerUpItem item && item.powerUp() == powerUp) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
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

    private static void refuse(ServerPlayerEntity player, Text why) {
        MessageUtils.sendToPlayer(player, why.copy().formatted(Formatting.RED), MessageUtils.MessageType.ACTION_BAR);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.8f);
    }

    /** In the action bar and the chat of the party's audience (and of its player), like the landings: in its colour. */
    private static void announce(PowerUpUse use) {
        MutableText notice = Text.literal("✦ ").append(use.powerUp().announcement(use)).formatted(use.powerUp().color());
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
