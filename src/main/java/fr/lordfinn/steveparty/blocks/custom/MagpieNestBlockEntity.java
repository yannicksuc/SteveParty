package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.board.LinkedParty;
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
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
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
 * On its own, it keeps them like a chest of {@link #SLOTS} slots, each holding up to {@link #SLOT_LIMIT} things of a
 * kind (whatever their stack size): a kind put in again goes onto its slot, a new kind (or a full slot's overflow)
 * into the first free slot, so its slots keep the order things were brought in. Its coins are the mod's coin, kept
 * among the rest. Nothing is ever made in it: what goes in came from somewhere, what comes out (to a hand, to a hopper
 * under it, or everything, the nest broken, in stacks of their usual size) leaves it.
 * <p>
 * Linked to a Common pot space (its cartridge links this nest, {@link PotCartridgeItem#linkedNest}; the space saved
 * here as {@link #getPot}), its whole content <em>is</em> the pot: its coins are the pot's coins, real items in its
 * slots, and its other things go to the winner too. What goes into the nest goes into the pot (coins up to its cap); the
 * winner of the pot takes it all and empties the nest. Nothing comes out of a pot's nest but by winning the pot (its
 * slots look empty to a hopper), or at the party's end (back to a storage, see CommonPots).
 * <p>
 * Things go in by hand ({@link MagpieNestBlock}), from a hopper, from a pot space's stakes, or in a wild Pie's beak.
 * A hopper fills its slots as a chest's, a kind it already holds going onto its slot (up to {@link #SLOT_LIMIT}).
 * <p>
 * The clients see what it holds, a stack per kind in its slots' order ({@link #getShownContents}, the aimed nest's
 * card), and how many things that is ({@link #getShownPile}). The loaded nests of each world are known
 * ({@link #loadedNests}) so the wild Pies find one without scanning blocks.
 * <p>
 * Saved before it had slots (its coins as a number, nine stacks of things), a nest moves them into its slots when
 * loaded, coins first; what would not fit falls at its first tick.
 */
public class MagpieNestBlockEntity extends SyncedBlockEntity implements Inventory {
    /** How many slots it keeps things in on its own. */
    public static final int SLOTS = 27;
    /** How many things of a kind a slot holds, whatever their stack size. */
    public static final int SLOT_LIMIT = 256;
    /** How many kinds the clients get at most (a pot's things can be more kinds than a nest's slots). */
    public static final int SHOWN_KINDS = 64;
    /** How often a linked nest checks its pot space is still a pot. */
    private static final int LINK_CHECK_INTERVAL = 40;
    /** How long the party of its pot space is kept before it is looked for again (ticks). */
    private static final int PARTY_REFRESH_TICKS = 20;
    private static final Map<RegistryKey<World>, Set<BlockPos>> LOADED = ServerMemory.forgetOnStop(new HashMap<>());

    private @Nullable BlockPos pot;
    /** The party of its pot space, as last found ({@link #potParty}). */
    private @Nullable PartyControllerEntity potParty;
    private long potPartyAt = Long.MIN_VALUE;
    /** Its own things (on its own, not linked), a kind a slot, up to {@link #SLOT_LIMIT} each. */
    private final DefaultedList<ItemStack> slots = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);
    /** What did not fit in its slots when an old nest was moved into them: falls at its first tick. */
    private final List<ItemStack> overflow = new ArrayList<>();
    /** How many things the clients see in it (server: as last sent). */
    private int shownPile;
    /** What the clients see in it, a stack per kind, its count maybe above its stack size (server: as last sent). */
    private List<ItemStack> shown = List.of();
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

    /** The pot cartridge of its linked space, null if on its own, the space no pot any more or its pot linked to another nest. */
    public @Nullable ItemStack potCartridge() {
        if (pot == null || world == null || !world.isChunkLoaded(pot)) return null;
        ItemStack cartridge = world.getBlockEntity(pot) instanceof BoardSpaceBlockEntity space ? CommonPots.potOf(space) : null;
        return cartridge != null && pos.equals(PotCartridgeItem.linkedNest(cartridge, world)) ? cartridge : null;
    }

    private void potChanged() {
        if (pot != null && world != null && world.getBlockEntity(pot) instanceof BoardSpaceBlockEntity space) space.update();
    }

    /** Linked to a pot space still holding its pot (or not loaded: kept). */
    public boolean isLinked() {
        if (pot == null || world == null) return false;
        return !world.isChunkLoaded(pot) || potCartridge() != null;
    }

    /** Server, by the pot space ({@link CommonPots}) whose cartridge links it: the nest is the pot of {@code space} now. */
    public void link(BlockPos space) {
        if (!space.equals(pot)) {
            pot = space.toImmutable();
            markDirty();
        }
    }

    /**
     * Server, now and then: a link to a space that is no pot any more (or whose pot links another nest) is dropped,
     * its content staying in it. What an old nest could not fit in its slots falls.
     */
    public static void tick(World world, BlockPos pos, BlockState state, MagpieNestBlockEntity nest) {
        if (!nest.overflow.isEmpty()) {
            for (ItemStack stack : nest.overflow) scatter(world, pos, stack);
            nest.overflow.clear();
            nest.markDirty();
        }
        if (nest.pot == null || (world.getTime() + pos.hashCode()) % LINK_CHECK_INTERVAL != 0) return;
        if (world.isChunkLoaded(nest.pot) && nest.potCartridge() == null) {
            nest.pot = null;
            nest.markDirty();
            nest.refresh();
        }
    }

    // ---------------------------------------------------------------- what it holds

    private static boolean sameKind(ItemStack a, ItemStack b) {
        return ItemStack.areItemsAndComponentsEqual(a, b);
    }

    /** Server: the coins it holds (the mod's coin, or its pot's party coin when linked). */
    public int getCoins() {
        int coins = 0;
        for (ItemStack slot : slots) if (!slot.isEmpty() && isCoin(slot)) coins += slot.getCount();
        return coins;
    }

    /** Server: how many things of the kind of {@code kind} it holds. */
    public int count(ItemStack kind) {
        int count = 0;
        for (ItemStack slot : slots) if (!slot.isEmpty() && sameKind(slot, kind)) count += slot.getCount();
        return count;
    }

    /** Server: its things that are not coins, in its slots' order (copies, a count up to {@link #SLOT_LIMIT}). */
    public List<ItemStack> getTreasures() {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack slot : slots) if (!slot.isEmpty() && !isCoin(slot)) copies.add(slot.copy());
        return copies;
    }

    /** Server: everything it holds, a stack per slot in their order, its count maybe above its stack size (copies). */
    public List<ItemStack> getContents() {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack slot : slots) if (!slot.isEmpty()) contents.add(slot.copy());
        return contents;
    }

    /** Server: how many things it holds, coins and the rest, one coin of its pile each. Client: {@link #getShownPile}. */
    public int getPileCount() {
        if (world != null && world.isClient) return shownPile;
        long count = 0;
        for (ItemStack slot : slots) count += slot.getCount();
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** Server: how many of its slots are full ({@link #SLOT_LIMIT} things). */
    public int getFullSlots() {
        int full = 0;
        for (ItemStack slot : slots) if (slot.getCount() >= SLOT_LIMIT) full++;
        return full;
    }

    /**
     * Server, the pot won: everything it holds comes out (copies, a stack per slot, its count maybe above its stack
     * size), the nest empty.
     */
    public List<ItemStack> takeAll() {
        List<ItemStack> all = getContents();
        for (int i = 0; i < SLOTS; i++) slots.set(i, ItemStack.EMPTY);
        changed();
        return all;
    }

    /** Server: takes out up to {@code count} things of the kind of {@code kind}, the last slots first. @return how many came out */
    public int take(ItemStack kind, int count) {
        int taken = 0;
        for (int i = SLOTS - 1; i >= 0 && taken < count; i--) {
            ItemStack slot = slots.get(i);
            if (slot.isEmpty() || !sameKind(slot, kind)) continue;
            int out = Math.min(slot.getCount(), count - taken);
            slot.decrement(out);
            if (slot.isEmpty()) slots.set(i, ItemStack.EMPTY);
            taken += out;
        }
        if (taken > 0) changed();
        return taken;
    }

    /** Saved, shown, and its pot space's info follows. */
    private void changed() {
        markDirty();
        potChanged();
    }

    /** How many things it holds, as the clients see it. */
    public int getShownPile() {
        return shownPile;
    }

    /** What it holds as the clients see it: a stack per slot in their order, its count maybe above its stack size. */
    public List<ItemStack> getShownContents() {
        return shown;
    }

    /** A coin for this nest: the mod's coin, or (linked to a pot) the coin of its pot space's party. */
    public boolean isCoin(ItemStack stack) {
        if (stack.isOf(ModItems.COIN)) return true;
        if (!isLinked() || stack.isEmpty()) return false;
        PartyControllerEntity party = potParty();
        return party != null && ItemStack.areItemsAndComponentsEqual(party.getCurrency(PartyCurrency.COIN), stack);
    }

    /**
     * The party of its pot space (the board element it is linked to, see {@link LinkedParty}): found again at most
     * every {@link #PARTY_REFRESH_TICKS}; null on its own, client side or for none.
     */
    private @Nullable PartyControllerEntity potParty() {
        if (pot == null || !(world instanceof ServerWorld serverWorld)) return null;
        long now = serverWorld.getTime();
        if (potParty == null || potParty.isRemoved() || now - potPartyAt >= PARTY_REFRESH_TICKS || now < potPartyAt) {
            potParty = LinkedParty.of(serverWorld, GlobalPos.create(serverWorld.getRegistryKey(), pot), false).playingParty();
            potPartyAt = now;
        }
        return potParty;
    }

    /** A shiny thing it takes (not a coin). */
    public boolean isTreasure(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(FrousseuxEntity.SHINY) && !isCoin(stack);
    }

    /** How many more things like {@code stack} its own slots take: the room on its kind's slots, then the free ones. */
    private int slotRoom(ItemStack stack) {
        long room = 0;
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) room += SLOT_LIMIT;
            else if (sameKind(slot, stack)) room += Math.max(0, SLOT_LIMIT - slot.getCount());
        }
        return (int) Math.min(Integer.MAX_VALUE, room);
    }

    /** {@code stack} into its own slots: onto its kind's slots, then into the first free ones. @return what is left */
    private ItemStack store(ItemStack stack) {
        ItemStack left = topUp(stack.copy());
        for (int i = 0; i < SLOTS && !left.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) slots.set(i, left.split(SLOT_LIMIT));
        }
        return left;
    }

    /** {@code stack} onto the slots already holding its kind (changed). @return what is left */
    private ItemStack topUp(ItemStack stack) {
        for (ItemStack held : slots) {
            if (stack.isEmpty()) break;
            if (!held.isEmpty() && sameKind(held, stack) && held.getCount() < SLOT_LIMIT) {
                int moved = Math.min(stack.getCount(), SLOT_LIMIT - held.getCount());
                held.increment(moved);
                stack.decrement(moved);
            }
        }
        return stack;
    }

    /** How many more things like {@code kind} it takes: coins as {@link #coinRoom}, anything else its slots' room. */
    public int room(ItemStack kind) {
        return isCoin(kind) ? coinRoom(kind) : slotRoom(kind);
    }

    /** Server: puts back up to {@code count} things like {@code kind}, as far as {@link #room} lets in. @return how many went in */
    public int putBack(ItemStack kind, int count) {
        if (isCoin(kind)) return addCoins(kind, count);
        int room = Math.min(Math.max(0, count), slotRoom(kind));
        if (room <= 0) return 0;
        return room - putTreasure(kind.copyWithCount(room)).getCount();
    }

    /** How many more coins like {@code coin} it takes (its slots' room, and a linked pot's cap). */
    public int coinRoom(ItemStack coin) {
        int room = slotRoom(coin);
        ItemStack cartridge = potCartridge();
        int cap = cartridge == null ? 0 : PotCartridgeItem.cap(cartridge);
        return cap > 0 ? Math.min(room, Math.max(0, cap - getCoins())) : room;
    }

    /** Server: puts in up to {@code count} coins like {@code coin} (as far as {@link #coinRoom} lets in). @return how many went in */
    public int addCoins(ItemStack coin, int count) {
        int added = Math.min(Math.max(0, count), coinRoom(coin));
        if (added <= 0) return 0;
        ItemStack left = store(coin.copyWithCount(added));
        added -= left.getCount();
        changed();
        return added;
    }

    /** Server: takes out up to {@code count} of its own coins, the last slots first (never a pot's). @return how many came out */
    public int takeCoins(int count) {
        if (isLinked()) return 0;
        int taken = 0;
        for (int i = SLOTS - 1; i >= 0 && taken < count; i--) {
            ItemStack slot = slots.get(i);
            if (!slot.isOf(ModItems.COIN)) continue;
            int out = Math.min(slot.getCount(), count - taken);
            slot.decrement(out);
            if (slot.isEmpty()) slots.set(i, ItemStack.EMPTY);
            taken += out;
        }
        if (taken > 0) {
            markDirty();
            refresh();
        }
        return taken;
    }

    /** Server: the clients see what it holds ({@link #getContents}, {@link #getPileCount}) when that changed. */
    public void refresh() {
        int now = getPileCount();
        List<ItemStack> contents = getContents();
        if (contents.size() > SHOWN_KINDS) contents = List.copyOf(contents.subList(0, SHOWN_KINDS));
        if (now != shownPile || !sameContents(contents, shown)) {
            shownPile = now;
            shown = contents;
            syncToClients();
            if (world != null) world.updateComparators(pos, getCachedState().getBlock());
        }
    }

    private static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getCount() != b.get(i).getCount() || !sameKind(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    /** Server: puts in {@code stack} (coins or a shiny thing), as much as it takes. @return what is left */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (isCoin(stack)) {
            int added = addCoins(stack, stack.getCount());
            ItemStack left = stack.copy();
            left.decrement(added);
            return left;
        }
        if (!isCoin(stack) && !isTreasure(stack)) return stack;
        return putTreasure(stack);
    }

    /** Server: {@code stack} among its things, whatever it is. @return what is left */
    public ItemStack putTreasure(ItemStack stack) {
        ItemStack left = store(stack);
        changed();
        return left;
    }

    /** Whether it takes some of {@code stack}. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isCoin(stack)) return coinRoom(stack) > 0;
        return isTreasure(stack) && slotRoom(stack) > 0;
    }

    /**
     * Server: a stack (of its usual size at most) of the last thing put in that is no coin, out of a nest on its own
     * (empty if none, or a pot's nest).
     */
    public ItemStack takeTreasure() {
        if (isLinked()) return ItemStack.EMPTY;
        for (int i = SLOTS - 1; i >= 0; i--) {
            ItemStack slot = slots.get(i);
            if (slot.isEmpty() || slot.isOf(ModItems.COIN)) continue;
            ItemStack taken = slot.split(slot.getMaxCount());
            if (slot.isEmpty()) slots.set(i, ItemStack.EMPTY);
            markDirty();
            refresh();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    /** {@code stack} falls at {@code at}, in stacks of its usual size. */
    private static void scatter(World world, BlockPos at, ItemStack stack) {
        ItemStack left = stack.copy();
        while (!left.isEmpty()) {
            ItemScatterer.spawn(world, at.getX(), at.getY(), at.getZ(), left.split(left.getMaxCount()));
        }
    }

    /** Server, the nest broken: everything it holds falls (a pot's too), in stacks of their usual size. */
    public void dropContents(World world, BlockPos at) {
        for (ItemStack stack : overflow) scatter(world, at, stack);
        overflow.clear();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack slot = slots.get(i);
            if (!slot.isEmpty()) scatter(world, at, slot);
            slots.set(i, ItemStack.EMPTY);
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

    // ---------------------------------------------------------------- a hopper's way in and out

    /**
     * Its slots, as a chest's ({@link #SLOT_LIMIT} things a slot). A pot's nest shows them empty: what it is given
     * goes into the pot, nothing comes out.
     */
    @Override
    public int size() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        if (isLinked()) return true;
        for (ItemStack slot : slots) if (!slot.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= SLOTS || isLinked()) return ItemStack.EMPTY;
        return slots.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot < 0 || slot >= SLOTS || isLinked()) return ItemStack.EMPTY;
        ItemStack taken = Inventories.splitStack(slots, slot, amount);
        if (!taken.isEmpty()) markDirty();
        return taken;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot < 0 || slot >= SLOTS || isLinked()) return ItemStack.EMPTY;
        ItemStack taken = Inventories.removeStack(slots, slot);
        if (!taken.isEmpty()) markDirty();
        return taken;
    }

    /**
     * {@code stack} in {@code slot}, a kind kept in one slot: given to a free slot while another slot holds its kind,
     * it goes onto that one first; given to a slot holding another kind (or to a pot's nest), it goes in as if put in.
     * What does not fit falls.
     */
    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) return;
        ItemStack left;
        if (isLinked()) {
            if (stack.isEmpty()) return;
            left = insert(stack);
        } else {
            ItemStack current = slots.get(slot);
            if (stack.isEmpty()) {
                slots.set(slot, ItemStack.EMPTY);
                left = ItemStack.EMPTY;
            } else if (current.isEmpty() || sameKind(current, stack)) {
                left = stack;
                if (current.isEmpty()) left = topUp(stack.copy());
                if (!left.isEmpty()) {
                    slots.set(slot, left);
                    left = left.getCount() > SLOT_LIMIT ? store(left.split(left.getCount() - SLOT_LIMIT)) : ItemStack.EMPTY;
                } else {
                    slots.set(slot, ItemStack.EMPTY);
                }
            } else {
                left = store(stack);
            }
            markDirty();
        }
        if (!left.isEmpty() && world != null && !world.isClient) {
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, left);
        }
    }

    @Override
    public int getMaxCountPerStack() {
        return SLOT_LIMIT;
    }

    @Override
    public int getMaxCount(ItemStack stack) {
        return SLOT_LIMIT;
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
        slots.clear();
    }

    /** Saved; on the server, shown again if what it holds changed (a hopper's doing too). */
    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null && !world.isClient) refresh();
    }

    // ---------------------------------------------------------------- save, sync

    /** {@code stacks} saved, each as a single item and its count apart (a slot holds more than an item stack's 99). */
    private static NbtList writeStacks(List<ItemStack> stacks, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) i);
            entry.put("Item", stack.copyWithCount(1).encode(registries));
            entry.putInt("Count", stack.getCount());
            list.add(entry);
        }
        return list;
    }

    /** The stacks of {@link #writeStacks}: each at its slot of {@code into}, or (null) in the list returned. */
    private static List<ItemStack> readStacks(NbtList list, @Nullable List<ItemStack> into, RegistryWrapper.WrapperLookup registries) {
        List<ItemStack> read = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            int count = entry.getInt("Count");
            ItemStack stack = entry.contains("Item") ? ItemStack.fromNbt(registries, entry.get("Item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            if (stack.isEmpty() || count <= 0) continue;
            stack.setCount(count);
            int slot = entry.getByte("Slot") & 255;
            if (into != null && slot < into.size()) into.set(slot, stack);
            else read.add(stack);
        }
        return read;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (pot != null) nbt.put("Pot", NbtHelper.fromBlockPos(pot));
        nbt.put("Nest", writeStacks(slots, registries));
        if (!overflow.isEmpty()) nbt.put("Overflow", writeStacks(overflow, registries));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Shown")) { // the clients' copy
            shownPile = nbt.getInt("Shown");
            shown = List.copyOf(readStacks(nbt.getList("Kinds", NbtElement.COMPOUND_TYPE), null, registries));
            return;
        }
        pot = NbtHelper.toBlockPos(nbt, "Pot").orElse(null);
        slots.clear();
        overflow.clear();
        if (nbt.contains("Nest")) {
            readStacks(nbt.getList("Nest", NbtElement.COMPOUND_TYPE), slots, registries);
            overflow.addAll(readStacks(nbt.getList("Overflow", NbtElement.COMPOUND_TYPE), null, registries));
            return;
        }
        // saved before it had slots: its coins as a number, nine stacks of things
        int coins = Math.max(0, nbt.getInt("Coins"));
        DefaultedList<ItemStack> old = DefaultedList.ofSize(9, ItemStack.EMPTY);
        Inventories.readNbt(nbt, old, registries);
        List<ItemStack> moving = new ArrayList<>();
        while (coins > 0) {
            int count = Math.min(coins, SLOT_LIMIT);
            moving.add(new ItemStack(ModItems.COIN, count));
            coins -= count;
        }
        moving.addAll(old);
        for (ItemStack stack : moving) {
            if (stack.isEmpty()) continue;
            ItemStack left = store(stack);
            if (!left.isEmpty()) overflow.add(left);
        }
    }

    /** The clients get what it holds: a stack per kind (its count apart) and how many things that is. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        if (world != null && !world.isClient) {
            shownPile = getPileCount();
            List<ItemStack> contents = getContents();
            shown = contents.size() > SHOWN_KINDS ? List.copyOf(contents.subList(0, SHOWN_KINDS)) : contents;
        }
        nbt.putInt("Shown", shownPile);
        nbt.put("Kinds", writeStacks(shown, registries));
        return nbt;
    }

    /** The nest at {@code pos}, or null. */
    public static @Nullable MagpieNestBlockEntity at(World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest ? nest : null;
    }
}
