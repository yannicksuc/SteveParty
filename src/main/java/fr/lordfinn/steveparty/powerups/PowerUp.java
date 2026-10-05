package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A power-up (bonus): an item bought in a shop that a player uses at the start of their turn, before their roll and
 * on top of it (a die replaces the roll, a power-up adds to the turn). Using one is always the same
 * ({@link PowerUpService#use}): right-click during your own turn of a running party, before the roll counts; one
 * power-up per turn; the item is consumed and the use is announced to the party.
 * <p>
 * What a power-up says about itself:
 * <ul>
 *     <li>{@link #target()}: what it needs to aim at (nothing, a player, a board space);</li>
 *     <li>{@link #refusal}: when it cannot be used, on top of the rules above (null: it can);</li>
 *     <li>{@link #apply}: what it does right away, when used;</li>
 *     <li>{@link #modifyRoll} and {@link #modifyCoinsGained}: what it changes until the end of the turn. The power-up
 *     used is remembered in the turn ({@link PowerUpTurn}, saved with the party), so these are asked for as long as
 *     the turn lasts; what they need to remember goes in {@link PowerUpTurn#data()}.</li>
 * </ul>
 * Everything does nothing by default: a power-up overrides what it needs. Server side only, except the tooltip.
 * <p>
 * A new power-up: one class extending this one, and one line in {@link PowerUps}
 * ({@code register(new MyPowerUp())}). See {@code docs/powerups.md} for the assets and lang keys it needs.
 */
public abstract class PowerUp {
    /** What a power-up aims at when used. */
    public enum Target {
        /** Nothing: right-click anywhere. */
        NONE,
        /** Another player of the party: they are picked in a prompt after the right-click ({@link PowerUpUse#targetPlayer}). */
        PLAYER,
        /** A board space: right-click it ({@link PowerUpUse#targetTile}). */
        TILE
    }

    private final String id;
    private final int defaultPrice;
    private final Formatting color;

    /**
     * @param id           its id: the item is {@code powerup_<id>}
     * @param defaultPrice its price in coins on a Trading Stall whose two price slots are left empty
     * @param color        its colour: its keywords in the tooltip, its announcement
     */
    protected PowerUp(String id, int defaultPrice, Formatting color) {
        this.id = id;
        this.defaultPrice = Math.max(1, defaultPrice);
        this.color = color;
    }

    public final String id() {
        return id;
    }

    public final int defaultPrice() {
        return defaultPrice;
    }

    public final Formatting color() {
        return color;
    }

    /** Path of its item: {@code powerup_<id>}. */
    public final String itemPath() {
        return "powerup_" + id;
    }

    /** Its item (AIR if it is not registered). */
    public final Item item() {
        return Registries.ITEM.get(Steveparty.id(itemPath()));
    }

    /** Its name (the item's). */
    public Text name() {
        return Text.translatable("item.steveparty." + itemPath());
    }

    // ------------------------------------------------------------------ using it

    public Target target() {
        return Target.NONE;
    }

    /**
     * Its own conditions, checked after the common ones (its player's turn, before the roll, one per turn) and once
     * the target is known.
     *
     * @return why it cannot be used now (shown to the player, nothing consumed), null if it can
     */
    public @Nullable Text refusal(PowerUpUse use) {
        return null;
    }

    /** It is used (the item is already consumed and the use announced): what it does right away. */
    public void apply(PowerUpUse use) {
    }

    /** The announcement to the party: {@code powerup.steveparty.<id>.announce}, {@code %s} being the player. */
    public MutableText announcement(PowerUpUse use) {
        return Text.translatable("powerup.steveparty." + id + ".announce", use.player().getDisplayName());
    }

    // ------------------------------------------------------------------ until the end of the turn

    /**
     * The roll of the turn is final (not a blank roll: those are rolled again and never reach here). Called before
     * it is announced and before the token moves; a change of its steps is shown in the announcement.
     *
     * @param turn the power-up state of the turn: {@link PowerUpTurn#data()} remembers what this roll spent
     */
    public DiceOutcome modifyRoll(DiceOutcome outcome, PowerUpTurn turn) {
        return outcome;
    }

    /**
     * The player of the turn gains {@code coins} (> 0) coins of the party: a coin face, an item space... Losses never
     * come here.
     *
     * @return the coins they really gain
     */
    public int modifyCoinsGained(int coins, PowerUpTurn turn) {
        return coins;
    }

    // ------------------------------------------------------------------ tooltip

    /**
     * What it does, for its tooltip: {@code powerup.steveparty.<id>.desc} by default, its arguments
     * ({@link #descriptionArgs}) in its colour.
     */
    public List<Text> effectLines() {
        Object[] args = descriptionArgs();
        for (int i = 0; i < args.length; i++) args[i] = keyword(args[i]);
        return List.of(Text.translatable("powerup.steveparty." + id + ".desc", args).formatted(Formatting.GRAY));
    }

    /** The arguments of {@code powerup.steveparty.<id>.desc}: the key words, shown in its colour. */
    protected Object[] descriptionArgs() {
        return new Object[0];
    }

    /** A key word of its tooltip, in its colour. */
    protected final Text keyword(Object word) {
        MutableText text = word instanceof Text t ? t.copy() : Text.literal(String.valueOf(word));
        return text.formatted(color);
    }

    @Override
    public String toString() {
        return "PowerUp[" + id + "]";
    }
}
