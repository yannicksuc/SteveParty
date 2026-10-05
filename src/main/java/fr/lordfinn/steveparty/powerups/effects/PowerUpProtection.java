package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * The Padlock power-up's effect, and the reference for every « is this player protected? » check.
 *
 * <p>A player (identified, like everywhere in a party, by the UUID of their <b>token</b>) who uses a Padlock is
 * protected until the start of their next turn against the other players' attacks: the Thief Bell (coins or star
 * stolen) and the Traps laid by the others. A protection <b>parries one attack, then it is used up</b>: an attack
 * calls {@link #consume}, which removes it and announces « X's Padlock blocked ... ». Consumed rather than lasting
 * the whole round, so one cheap power-up can't make a player immune to everything played until their next turn,
 * and so laying a second Trap or ringing the Bell again after a blocked one is still worth it.
 *
 * <p>Using a second Padlock while protected changes nothing (one protection at most, not stacked): {@link #protect}
 * returns false and the power-up should then not be spent.
 *
 * <p>The protection expires at the start of the player's next turn ({@link #expire}, called by
 * {@link TokenTurnPartyStep#start}; the extra turn given by a Replay tile does not end it). It is stored in the
 * party's state ({@link fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData#getProtectedTokens}) and
 * saved with it in the party controller's NBT, so it survives a save and a reload. It is shown in the party HUD's
 * standings, in the « bonus » column next to the player's name (the Padlock's icon, see {@link #icon}).
 *
 * <p>All methods are server side and accept a null or unknown token (then nothing is protected).
 */
public final class PowerUpProtection {
    /** Id of the Padlock power-up's item, drawn as the protection's sign in the HUD. */
    public static final String PADLOCK_ITEM_ID = "powerup_padlock";

    /** The attacks a Padlock parries, each with its words in « X's Padlock blocked ... ». */
    public enum Attack {
        /** The Thief Bell, stealing coins or a star. */
        THIEF_BELL("message.steveparty.padlock.attack.thief_bell", "the Thief Bell"),
        /** A Trap laid by another player. */
        TRAP("message.steveparty.padlock.attack.trap", "a Trap");

        private final String key;
        private final String fallback;

        Attack(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        /** What was blocked, as said in the announcement (« the Thief Bell », « a Trap »). */
        public Text text() {
            return Text.translatableWithFallback(key, fallback);
        }
    }

    private PowerUpProtection() {
    }

    /**
     * Protects a player until the start of their next turn, and announces it to the party (« X is protected »).
     *
     * @param controller the party
     * @param token      the player's token
     * @return true if the player is now protected; false if they already were (nothing changes: don't spend the
     * power-up) or if the token does not play in this party
     */
    public static boolean protect(PartyControllerEntity controller, @Nullable UUID token) {
        if (controller == null || token == null || !controller.getPartyData().getTokens().contains(token)) return false;
        if (!protections(controller).add(token)) return false;
        announce(controller, Text.translatableWithFallback("message.steveparty.padlock.protected",
                "%s is protected by a Padlock until their next turn!", name(controller, token)).formatted(Formatting.AQUA));
        playAt(controller, token, true);
        refresh(controller);
        return true;
    }

    /**
     * @param controller the party
     * @param token      the player's token
     * @return true if the player is protected (a Padlock used since the start of their last turn, not used up yet)
     */
    public static boolean isProtected(@Nullable PartyControllerEntity controller, @Nullable UUID token) {
        return controller != null && token != null && protections(controller).contains(token);
    }

    /**
     * Uses up the player's protection to parry an attack, without saying which one (« X's Padlock blocked an
     * attack »). Prefer {@link #consume(PartyControllerEntity, UUID, Attack)}.
     *
     * @return true if a protection was consumed: the attack must then be cancelled
     */
    public static boolean consume(PartyControllerEntity controller, @Nullable UUID token) {
        return consume(controller, token, Text.translatableWithFallback("message.steveparty.padlock.attack.any", "an attack"));
    }

    /**
     * Uses up the player's protection to parry an attack, announced as « X's Padlock blocked the Thief Bell! ».
     *
     * @param controller the party
     * @param token      the attacked player's token
     * @param attack     what is blocked
     * @return true if a protection was consumed: the attack must then be cancelled; false if the player was not
     * protected (the attack goes on)
     */
    public static boolean consume(PartyControllerEntity controller, @Nullable UUID token, Attack attack) {
        return consume(controller, token, attack.text());
    }

    /**
     * Uses up the player's protection to parry an attack described by {@code blocked}.
     *
     * @param controller the party
     * @param token      the attacked player's token
     * @param blocked    what is blocked, as said in « X's Padlock blocked %s! »
     * @return true if a protection was consumed: the attack must then be cancelled
     */
    public static boolean consume(PartyControllerEntity controller, @Nullable UUID token, Text blocked) {
        if (!isProtected(controller, token) || !protections(controller).remove(token)) return false;
        announce(controller, Text.translatableWithFallback("message.steveparty.padlock.blocked",
                "%1$s's Padlock blocked %2$s!", name(controller, token), blocked).formatted(Formatting.GOLD));
        playAt(controller, token, false);
        refresh(controller);
        return true;
    }

    /**
     * Ends the player's protection, silently: called at the start of each of their turns (see
     * {@link TokenTurnPartyStep#start}). The party is synced only if something changed.
     *
     * @return true if a protection was removed
     */
    public static boolean expire(PartyControllerEntity controller, @Nullable UUID token) {
        if (!isProtected(controller, token) || !protections(controller).remove(token)) return false;
        controller.markDirty();
        return true;
    }

    /**
     * The token whose player is the given one, in this party (the Thief Bell knows players, the protection tokens).
     *
     * @param controller the party
     * @param player     the player's UUID
     * @return their token, or null if they play no token in this party
     */
    public static @Nullable UUID tokenOf(PartyControllerEntity controller, @Nullable UUID player) {
        if (controller == null || player == null) return null;
        for (UUID token : controller.getPartyData().getTokens()) {
            if (controller.isTokenOwnedBy(token, player)) return token;
        }
        return null;
    }

    /** The protection's sign in the HUD: the Padlock power-up (a tripwire hook until that item exists). */
    public static ItemStack icon() {
        Item padlock = Registries.ITEM.get(Steveparty.id(PADLOCK_ITEM_ID));
        return new ItemStack(padlock == Items.AIR ? Items.TRIPWIRE_HOOK : padlock);
    }

    // ------------------------------------------------------------------ internals

    private static Set<UUID> protections(PartyControllerEntity controller) {
        return controller.getPartyData().getProtectedTokens();
    }

    private static Text name(PartyControllerEntity controller, UUID token) {
        return controller.getWorld() instanceof ServerWorld world ? controller.getTokenDisplayName(world, token)
                : Text.literal(token.toString().substring(0, 8));
    }

    private static void announce(PartyControllerEntity controller, Text message) {
        MessageUtils.sendToPlayers(controller.getPartyAudience(), message, MessageUtils.MessageType.CHAT);
    }

    /** A lock's click on the pawn: closing when protected, a shield's parry when an attack is blocked. */
    private static void playAt(PartyControllerEntity controller, UUID token, boolean locked) {
        if (!(controller.getWorld() instanceof ServerWorld world) || !(world.getEntity(token) instanceof Entity entity)
                || !(entity instanceof TokenizedEntityInterface)) return;
        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                locked ? SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE : SoundEvents.ITEM_SHIELD_BLOCK,
                SoundCategory.PLAYERS, 0.8F, locked ? 1.4F : 1.0F);
    }

    /** Saves the party and refreshes the HUDs (the standings' bonus column). */
    private static void refresh(PartyControllerEntity controller) {
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
    }
}
