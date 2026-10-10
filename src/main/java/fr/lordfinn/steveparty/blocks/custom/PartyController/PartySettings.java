package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.dice.AllowedDice;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rules a Party Controller plays its parties by (the dashboard's Settings and Gains pages): the currencies, the
 * gains of the mini-games and their bank, the practice rounds, the power-up limit and the allowed dice. Saved in the
 * controller's own compound. The setters say whether something changed: the controller then marks itself dirty.
 */
public final class PartySettings {
    /** The items counted as stars and coins by this party (one of each, never empty): see {@link PartyCurrency}. */
    private ItemStack starItem = PartyCurrency.STAR.defaultStack();
    private ItemStack coinItem = PartyCurrency.COIN.defaultStack();
    /** What the party pays at the end of each mini-game, by place (Gains page). */
    private MiniGameGains gains = MiniGameGains.DEFAULT;
    /** The Inventory Cartridge whose chest the gains are taken from (Gains page), empty for none: see {@link PartyBank}. */
    private ItemStack bank = ItemStack.EMPTY;
    /** A practice round before each mini-game whose page has a Mini-game Controller (Settings page). */
    private boolean practiceRound = true;
    /** The power-ups a player may carry during a party, dice carrying the Power-up module included (0: no limit). */
    private int maxPowerUps = PowerUpLimit.DEFAULT;
    /** Only the allowed dice may be thrown during a party (Settings page); off: every die. */
    private boolean restrictDice;
    /**
     * « Infinite bank »: the party's bank never runs out, whatever is asked of it is given and whatever is put in it is
     * absorbed (see {@link PartyResources#of(PartyControllerEntity)}). Only a player in creative mode or an operator
     * switches it.
     */
    private boolean infiniteBank;
    /**
     * The dice a player may throw during a party while {@link #restrictDice} is on (empty: every die), one of each, in
     * the order they were picked; {@link AllowedDice#defaults} at first.
     */
    private final List<ItemStack> allowedDice = new ArrayList<>(AllowedDice.defaults());

    void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        nbt.put(PartyCurrency.STAR.nbtKey(), starItem.encode(wrapper));
        nbt.put(PartyCurrency.COIN.nbtKey(), coinItem.encode(wrapper));
        nbt.put("MiniGameGains", gains.toNbt());
        if (!bank.isEmpty()) nbt.put("BankCartridge", bank.encode(wrapper));
        nbt.putBoolean("PracticeRound", practiceRound);
        nbt.putInt("MaxPowerUps", maxPowerUps);
        nbt.putBoolean("RestrictDice", restrictDice);
        if (infiniteBank) nbt.putBoolean("InfiniteBank", true);
        // Saved even empty (every die): only a controller without it gets the default dice
        NbtList diceNbt = new NbtList();
        allowedDice.forEach(die -> diceNbt.add(die.encode(wrapper)));
        nbt.put("AllowedDice", diceNbt);
    }

    void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        starItem = readCurrency(nbt, wrapper, PartyCurrency.STAR);
        coinItem = readCurrency(nbt, wrapper, PartyCurrency.COIN);
        gains = MiniGameGains.fromNbt(nbt.getCompound("MiniGameGains"));
        NbtElement bankElement = nbt.get("BankCartridge");
        bank = bankElement == null ? ItemStack.EMPTY : ItemStack.fromNbt(wrapper, bankElement).orElse(ItemStack.EMPTY);
        practiceRound = !nbt.contains("PracticeRound") || nbt.getBoolean("PracticeRound");
        maxPowerUps = nbt.contains("MaxPowerUps")
                ? Math.clamp(nbt.getInt("MaxPowerUps"), 0, PowerUpLimit.MAX)
                : PowerUpLimit.DEFAULT;
        restrictDice = nbt.getBoolean("RestrictDice");
        infiniteBank = nbt.getBoolean("InfiniteBank");
        allowedDice.clear();
        if (!nbt.contains("AllowedDice")) allowedDice.addAll(AllowedDice.defaults());
        for (NbtElement element : nbt.getList("AllowedDice", NbtElement.COMPOUND_TYPE)) {
            if (allowedDice.size() >= PartyControllerEntity.MAX_ALLOWED_DICE) break;
            ItemStack.fromNbt(wrapper, element).filter(AllowedDice::isDie).ifPresent(allowedDice::add);
        }
    }

    private static ItemStack readCurrency(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper, PartyCurrency currency) {
        NbtElement element = nbt.get(currency.nbtKey());
        ItemStack stack = element == null ? ItemStack.EMPTY : ItemStack.fromNbt(wrapper, element).orElse(ItemStack.EMPTY);
        return currency.template(stack);
    }

    /** The item counted as this currency (a copy, count 1). */
    public ItemStack getCurrency(PartyCurrency currency) {
        return (currency == PartyCurrency.STAR ? starItem : coinItem).copy();
    }

    /** @return false if refused: the other currency already uses that very item */
    boolean setCurrency(PartyCurrency currency, ItemStack picked) {
        ItemStack template = currency.template(picked);
        if (ItemStack.areItemsAndComponentsEqual(template, currency == PartyCurrency.STAR ? coinItem : starItem)) return false;
        if (currency == PartyCurrency.STAR) starItem = template;
        else coinItem = template;
        return true;
    }

    public MiniGameGains getGains() {
        return gains;
    }

    /** @return true if they changed */
    boolean setGains(MiniGameGains gains) {
        if (gains == null || gains.equals(this.gains)) return false;
        this.gains = gains;
        return true;
    }

    public ItemStack getBank() {
        return bank;
    }

    void setBank(ItemStack bank) {
        this.bank = bank == null ? ItemStack.EMPTY : bank;
    }

    public boolean hasPracticeRound() {
        return practiceRound;
    }

    /** @return true if it changed */
    boolean setPracticeRound(boolean practiceRound) {
        if (this.practiceRound == practiceRound) return false;
        this.practiceRound = practiceRound;
        return true;
    }

    public int getMaxPowerUps() {
        return maxPowerUps;
    }

    /** @return true if it changed */
    boolean setMaxPowerUps(int maxPowerUps) {
        int clamped = Math.clamp(maxPowerUps, 0, PowerUpLimit.MAX);
        if (this.maxPowerUps == clamped) return false;
        this.maxPowerUps = clamped;
        return true;
    }

    public boolean isInfiniteBank() {
        return infiniteBank;
    }

    /** @return true if it changed */
    boolean setInfiniteBank(boolean infiniteBank) {
        if (this.infiniteBank == infiniteBank) return false;
        this.infiniteBank = infiniteBank;
        return true;
    }

    public boolean isRestrictDice() {
        return restrictDice;
    }

    /** @return true if it changed */
    boolean setRestrictDice(boolean restrictDice) {
        if (this.restrictDice == restrictDice) return false;
        this.restrictDice = restrictDice;
        return true;
    }

    /** Read-only. */
    public List<ItemStack> getAllowedDice() {
        return Collections.unmodifiableList(allowedDice);
    }

    /** @return false if it is not a die, is already listed elsewhere, or the list is full */
    boolean setAllowedDie(int index, ItemStack die) {
        if (!AllowedDice.isDie(die) || index < 0) return false;
        for (ItemStack allowed : allowedDice) {
            if (AllowedDice.sameDie(allowed, die)) return false;
        }
        if (index < allowedDice.size()) allowedDice.set(index, die.copyWithCount(1));
        else if (allowedDice.size() < PartyControllerEntity.MAX_ALLOWED_DICE) allowedDice.add(die.copyWithCount(1));
        else return false;
        return true;
    }

    /** @return false if there is none at {@code index} */
    boolean removeAllowedDie(int index) {
        if (index < 0 || index >= allowedDice.size()) return false;
        allowedDice.remove(index);
        return true;
    }
}
