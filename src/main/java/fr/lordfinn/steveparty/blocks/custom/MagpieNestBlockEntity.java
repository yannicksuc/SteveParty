package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.service.CommonPots;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a Magpie Nest holds: coins and other things (by hand or in a wild Pie's beak: shiny things, the item tag
 * {@code steveparty:shiny}, {@link FrousseuxEntity#SHINY}). Every one of them, coin or not, is one more coin drawn on
 * its pile ({@link #getPileCount}, {@link MagpieNestPile}).
 * <p>
 * Linked to a Common pot space ({@link CommonPots#nestOf}, the link saved here as {@link #getPot}), its whole content
 * <em>is</em> the pot: its coins are the pot's coins and its other things the pot's items ({@link PotCartridgeItem#coins},
 * {@link PotCartridgeItem#items}, with what the thieving Pie stole), kept in the pot cartridge only, so a pot saved
 * before nests held anything keeps its amount. What goes into the nest goes into the pot (coins up to its cap); the
 * winner of the pot takes it all and empties the nest. What a nest held on its own goes into the pot when it gets
 * linked. On its own, the nest keeps its things itself (its coins are the mod's coin), dropped when broken. Nothing
 * comes out of a pot's nest but by winning the pot; on its own, an empty hand takes its things back.
 * <p>
 * Things go in by hand ({@link MagpieNestBlock}), from a hopper (a slot that is always empty, taking what it is given:
 * nothing comes out of it), from a pot space's stakes, or in a wild Pie's beak.
 * <p>
 * The clients only see how many things it holds ({@link #getShownPile}). The loaded nests of each world are known
 * ({@link #loadedNests}) so the wild Pies find one without scanning blocks.
 */
public class MagpieNestBlockEntity extends SyncedBlockEntity implements Inventory {
    /** How many stacks of things it keeps on its own. */
    public static final int TREASURE_SLOTS = 9;
    /** How many stacks of things a pot holds at most (what the thieving Pie steals aside). */
    public static final int POT_ITEM_STACKS = 27;
    /** How often a linked nest checks its pot space is still a pot. */
    private static final int LINK_CHECK_INTERVAL = 40;
    private static final Map<RegistryKey<World>, Set<BlockPos>> LOADED = ServerMemory.forgetOnStop(new HashMap<>());

    private @Nullable BlockPos pot;
    /** Its own coins (on its own, not linked). */
    private int coins;
    /** Its own things (on its own, not linked). */
    private final DefaultedList<ItemStack> treasures = DefaultedList.ofSize(TREASURE_SLOTS, ItemStack.EMPTY);
    /** How many things the clients see in it (server: as last sent). */
    private int shownPile;
    /** The client's drawing of the pile, kept by its renderer. */
    public @Nullable Object renderCache;

    public MagpieNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGPIE_NEST_ENTITY, pos, state);
    }

    // ---------------------------------------------------------------- the loaded nests

    /** The loaded nests of {@code world} (server). */
    public static Set<BlockPos> loadedNests(World world) {
        return LOADED.getOrDefault(world.getRegistryKey(), Set.of());
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (!world.isClient) LOADED.computeIfAbsent(world.getRegistryKey(), key -> new HashSet<>()).add(pos.toImmutable());
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        if (world != null && !world.isClient) LOADED.computeIfAbsent(world.getRegistryKey(), key -> new HashSet<>()).add(pos.toImmutable());
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (world != null && !world.isClient) {
            Set<BlockPos> nests = LOADED.get(world.getRegistryKey());
            if (nests != null) nests.remove(pos);
        }
    }

    // ---------------------------------------------------------------- the pot

    /** The pot space it is linked to (null: on its own). */
    public @Nullable BlockPos getPot() {
        return pot;
    }

    /** The pot cartridge of its linked space, null if it is on its own or the space is no pot any more. */
    private @Nullable ItemStack potCartridge() {
        if (pot == null || world == null || !world.isChunkLoaded(pot)) return null;
        return world.getBlockEntity(pot) instanceof BoardSpaceBlockEntity space ? CommonPots.potOf(space) : null;
    }

    private void potChanged() {
        if (pot != null && world != null && world.getBlockEntity(pot) instanceof BoardSpaceBlockEntity space) space.update();
    }

    /** Linked to a pot space still holding its pot (or not loaded: kept). */
    public boolean isLinked() {
        if (pot == null || world == null) return false;
        return !world.isChunkLoaded(pot) || potCartridge() != null;
    }

    /**
     * Server, by the pot space ({@link CommonPots}): the nest is the pot of {@code space} now; what it held on its own
     * goes into the pot. Shows the pot.
     */
    public void link(BlockPos space) {
        if (!space.equals(pot)) {
            pot = space.toImmutable();
            markDirty();
        }
        ItemStack cartridge = potCartridge();
        if (cartridge != null && (coins > 0 || !treasures.stream().allMatch(ItemStack::isEmpty))) {
            PotCartridgeItem.setCoins(cartridge, PotCartridgeItem.coins(cartridge) + coins);
            coins = 0;
            List<ItemStack> items = PotCartridgeItem.items(cartridge);
            for (int i = 0; i < TREASURE_SLOTS; i++) {
                merge(items, treasures.get(i), Integer.MAX_VALUE);
                treasures.set(i, ItemStack.EMPTY);
            }
            PotCartridgeItem.setItems(cartridge, items);
            markDirty();
            potChanged();
        }
        refresh();
    }

    /** Server, now and then: a link to a space that is no pot any more is dropped (its content stays in the cartridge). */
    public static void tick(World world, BlockPos pos, BlockState state, MagpieNestBlockEntity nest) {
        if (nest.pot == null || (world.getTime() + pos.hashCode()) % LINK_CHECK_INTERVAL != 0) return;
        if (world.isChunkLoaded(nest.pot) && nest.potCartridge() == null) {
            nest.pot = null;
            nest.markDirty();
            nest.refresh();
        }
    }

    // ---------------------------------------------------------------- what it holds

    /** Server: the coins it holds (its pot's when linked). */
    public int getCoins() {
        ItemStack cartridge = potCartridge();
        return cartridge != null ? PotCartridgeItem.coins(cartridge) : coins;
    }

    /** Server: its things that are not coins (copies; its pot's items when linked). */
    public List<ItemStack> getTreasures() {
        ItemStack cartridge = potCartridge();
        if (cartridge != null) return PotCartridgeItem.items(cartridge);
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : treasures) if (!stack.isEmpty()) copies.add(stack.copy());
        return copies;
    }

    /**
     * Server: how many things it holds, coins and the rest, one coin of its pile each (as last seen while its pot's
     * chunk is unloaded). Client: {@link #getShownPile}.
     */
    public int getPileCount() {
        if (world != null && world.isClient) return shownPile;
        if (pot != null && world != null && !world.isChunkLoaded(pot)) return shownPile;
        long count = getCoins();
        for (ItemStack stack : getTreasures()) count += stack.getCount();
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** How many things it holds, as the clients see it. */
    public int getShownPile() {
        return shownPile;
    }

    /** A coin for this nest: the mod's coin, or (linked to a pot) the coin of the nearest party. */
    public boolean isCoin(ItemStack stack) {
        if (stack.isOf(ModItems.COIN)) return true;
        if (!isLinked() || world == null || stack.isEmpty()) return false;
        return PartyControllerEntity.getClosestSteppablePartyControllerEntity(world, pos, PartyBellBlockEntity.RANGE, false)
                .map(party -> ItemStack.areItemsAndComponentsEqual(party.getCurrency(PartyCurrency.COIN), stack))
                .orElse(false);
    }

    /** A shiny thing it takes (not a coin). */
    public boolean isTreasure(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(FrousseuxEntity.SHINY) && !isCoin(stack);
    }

    /** How many more coins it takes (a linked pot's cap). */
    public int coinRoom() {
        ItemStack cartridge = potCartridge();
        if (cartridge == null) return Integer.MAX_VALUE - coins;
        int cap = PotCartridgeItem.cap(cartridge);
        return cap > 0 ? Math.max(0, cap - PotCartridgeItem.coins(cartridge)) : Integer.MAX_VALUE;
    }

    /** Server: puts in up to {@code count} coins (into the pot when linked). @return how many went in */
    public int addCoins(int count) {
        int added = Math.min(Math.max(0, count), coinRoom());
        if (added <= 0) return 0;
        ItemStack cartridge = potCartridge();
        if (cartridge != null) {
            PotCartridgeItem.setCoins(cartridge, PotCartridgeItem.coins(cartridge) + added);
            potChanged();
        } else {
            coins += added;
            markDirty();
        }
        refresh();
        return added;
    }

    /** Server: takes out up to {@code count} of its own coins (never a pot's). @return how many came out */
    public int takeCoins(int count) {
        if (isLinked()) return 0;
        int taken = Math.min(coins, Math.max(0, count));
        if (taken > 0) {
            coins -= taken;
            markDirty();
            refresh();
        }
        return taken;
    }

    /** Server: the clients see {@link #getPileCount} (after it changed, the pot's too). */
    public void refresh() {
        int now = getPileCount();
        if (now != shownPile) {
            shownPile = now;
            syncToClients();
            if (world != null) world.updateComparators(pos, getCachedState().getBlock());
        }
    }

    /** Server: puts in {@code stack} (coins or a shiny thing), as much as it takes. @return what is left */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (isCoin(stack)) {
            int added = addCoins(stack.getCount());
            ItemStack left = stack.copy();
            left.decrement(added);
            return left;
        }
        if (!isTreasure(stack)) return stack;
        return putTreasure(stack);
    }

    /** Server: {@code stack} among its things (the pot's when linked), whatever it is. @return what is left */
    public ItemStack putTreasure(ItemStack stack) {
        ItemStack cartridge = potCartridge();
        ItemStack left = stack.copy();
        if (cartridge != null) {
            List<ItemStack> items = PotCartridgeItem.items(cartridge);
            left = merge(items, left, POT_ITEM_STACKS);
            PotCartridgeItem.setItems(cartridge, items);
            potChanged();
        } else {
            for (int i = 0; i < TREASURE_SLOTS && !left.isEmpty(); i++) {
                ItemStack slot = treasures.get(i);
                if (slot.isEmpty()) {
                    treasures.set(i, left);
                    left = ItemStack.EMPTY;
                } else if (ItemStack.areItemsAndComponentsEqual(slot, left) && slot.getCount() < slot.getMaxCount()) {
                    int moved = Math.min(left.getCount(), slot.getMaxCount() - slot.getCount());
                    slot.increment(moved);
                    left.decrement(moved);
                }
            }
            markDirty();
        }
        refresh();
        return left;
    }

    /** {@code added} into {@code items}: onto stacks like it, then as new stacks while there are fewer than {@code maxStacks}. */
    private static ItemStack merge(List<ItemStack> items, ItemStack added, int maxStacks) {
        ItemStack left = added.copy();
        for (ItemStack item : items) {
            if (left.isEmpty()) break;
            if (ItemStack.areItemsAndComponentsEqual(item, left) && item.getCount() < item.getMaxCount()) {
                int moved = Math.min(left.getCount(), item.getMaxCount() - item.getCount());
                item.increment(moved);
                left.decrement(moved);
            }
        }
        while (!left.isEmpty() && items.size() < maxStacks) {
            items.add(left.split(left.getMaxCount()));
        }
        return left;
    }

    /** Whether it takes some of {@code stack}. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isCoin(stack)) return coinRoom() > 0;
        if (!isTreasure(stack)) return false;
        ItemStack cartridge = potCartridge();
        List<ItemStack> held = cartridge != null ? PotCartridgeItem.items(cartridge) : treasures;
        int stacks = 0;
        for (ItemStack slot : held) {
            if (slot.isEmpty()) continue;
            stacks++;
            if (ItemStack.areItemsAndComponentsEqual(slot, stack) && slot.getCount() < slot.getMaxCount()) return true;
        }
        return stacks < (cartridge != null ? POT_ITEM_STACKS : TREASURE_SLOTS);
    }

    /** Server: the last thing put in, out of a nest on its own (empty if none, or a pot's nest). */
    public ItemStack takeTreasure() {
        if (isLinked()) return ItemStack.EMPTY;
        for (int i = TREASURE_SLOTS - 1; i >= 0; i--) {
            ItemStack slot = treasures.get(i);
            if (slot.isEmpty()) continue;
            treasures.set(i, ItemStack.EMPTY);
            markDirty();
            refresh();
            return slot;
        }
        return ItemStack.EMPTY;
    }

    /** Server, the nest broken: its own coins and things fall (a pot's stay in the pot). */
    public void dropContents(World world, BlockPos at) {
        if (isLinked()) return;
        int left = coins;
        while (left > 0) {
            int count = Math.min(left, ModItems.COIN.getMaxCount());
            ItemScatterer.spawn(world, at.getX(), at.getY(), at.getZ(), new ItemStack(ModItems.COIN, count));
            left -= count;
        }
        coins = 0;
        for (int i = 0; i < TREASURE_SLOTS; i++) {
            ItemStack slot = treasures.get(i);
            if (!slot.isEmpty()) ItemScatterer.spawn(world, at.getX(), at.getY(), at.getZ(), slot);
            treasures.set(i, ItemStack.EMPTY);
        }
    }

    /** The comparator: 0 empty, then 1 to 15 with what it holds (15 from 64). */
    public int comparatorOutput() {
        int count = getPileCount();
        return count <= 0 ? 0 : 1 + Math.min(14, count * 14 / MagpieNestPile.MAX_DRAWN);
    }

    // ---------------------------------------------------------------- a Pie on it

    public Direction getFacing() {
        BlockState state = getCachedState();
        return state.contains(MagpieNestBlock.FACING) ? state.get(MagpieNestBlock.FACING) : Direction.NORTH;
    }

    /** Where a Pie stands on it: in the middle of an empty nest, on the free corner of its rim beside a pile. */
    public Vec3d perch() {
        return MagpieNestPile.perch(pos, getFacing(), getPileCount());
    }

    public float perchYaw() {
        return MagpieNestPile.perchYaw(getFacing(), getPileCount());
    }

    // ---------------------------------------------------------------- a hopper's way in

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (world == null || world.isClient) return;
        ItemStack left = insert(stack);
        if (!left.isEmpty()) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, left);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return world != null && !world.isClient && accepts(stack);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {
    }

    // ---------------------------------------------------------------- save, sync

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (pot != null) nbt.put("Pot", NbtHelper.fromBlockPos(pot));
        nbt.putInt("Coins", coins);
        Inventories.writeNbt(nbt, treasures, registries);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Shown")) { // the clients' copy
            shownPile = nbt.getInt("Shown");
            return;
        }
        pot = NbtHelper.toBlockPos(nbt, "Pot").orElse(null);
        coins = Math.max(0, nbt.getInt("Coins"));
        treasures.clear();
        Inventories.readNbt(nbt, treasures, registries);
    }

    /** The clients only get how many things it holds. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        if (world != null && !world.isClient) shownPile = getPileCount();
        nbt.putInt("Shown", shownPile);
        return nbt;
    }

    /** The nest at {@code pos}, or null. */
    public static @Nullable MagpieNestBlockEntity at(World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest ? nest : null;
    }
}
