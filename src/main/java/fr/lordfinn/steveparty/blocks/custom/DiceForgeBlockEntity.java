package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.ForgeCoreEntity;
import fr.lordfinn.steveparty.utils.GravityPull;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.UUID;

/**
 * Dice Forge.
 * <p>
 * Layout: 12 face slots around the vortex, the center slot (gravity core input while the forge is not activated;
 * afterwards the GUI draws the core there as the FORGE button), 4 star fragment slots around the center, the blank
 * faces slot and the output slot.
 * <p>
 * Faces are not consumed: the count of a face slot is the weight of that face on the die (a face placed 10 times
 * comes up 10 times more often than a face placed once). Each die consumes one blank face per face slot used,
 * whatever its weight, and one fragment of every non-black fragment slot.
 * <p>
 * The core floats above the forge, higher with more fragments ({@link #getCoreAltitude}), and pulls what is around it
 * like a small planet ({@link GravityPull}): the higher, the stronger and the farther (32 blocks at the top). Hitting
 * it ({@link ForgeCoreEntity}) blows it up, flinging everything away: the way out of its pull when nothing else works.
 * <p>
 * Core: inserted by right-clicking the forge with it or through the center slot; taken back with sneak +
 * right-click (empty hand) once the insertion animation is over, see {@link #removeCore}.
 * <p>
 * Production: the FORGE button (or a redstone rising edge) starts a loop. Each craft ({@link #CRAFT_TIME} ticks)
 * consumes the blank faces and 1 fragment of every non-black fragment slot, and outputs a die carrying the faces
 * currently in the ring (changing them changes the next die). The loop stops when toggled off or when something runs
 * out. The fragments and blank faces in use are remembered: missing ones are shown as ghosts in the GUI.
 * <p>
 * It fills like any container, by hand or by hoppers (any valid item in any free slot; blank faces only in their
 * own slot); hoppers below take the dice out.
 * <p>
 * Redstone: powered = production enabled. Rising edge starts production (like pressing FORGE), falling edge
 * stops it. While powered, a loop stopped by a shortage resumes by itself once refilled, unless the player
 * stopped it with the button (manual stop wins until the next rising edge).
 */
public class DiceForgeBlockEntity extends LootableContainerBlockEntity implements GeoBlockEntity, TickableBlockEntity, SidedInventory {
    // ---- slots
    public static final int FACE_SLOTS = 12;
    public static final int CENTER_SLOT = 12;
    public static final int FIRST_FRAGMENT_SLOT = 13;
    public static final int FRAGMENT_SLOTS = 4;
    /** Blank dice faces consumed by each craft (one per face slot used). */
    public static final int BLANK_SLOT = FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS;
    public static final int OUTPUT_SLOT = BLANK_SLOT + 1;
    public static final int SIZE = OUTPUT_SLOT + 1;
    /**
     * Layout indices: the face slots, the fragment slots, then the blank faces slot. Only the fragments and the blank
     * faces are remembered (faces are not consumed, nothing to refill).
     */
    public static final int LAYOUT_SIZE = FACE_SLOTS + FRAGMENT_SLOTS + 1;
    private static final int BLANK_LAYOUT_INDEX = FACE_SLOTS + FRAGMENT_SLOTS;

    // ---- tuning
    public static final int CRAFT_TIME = 100;
    /** Block event: a die was just forged (the client animates it falling into the forge). */
    public static final int FORGED_EVENT = 1;
    /** A die may have a single face (it then always rolls that face). */
    public static final int MIN_FACES = 1;
    /** Duration of the "core_insert" animation (must match the animation JSON: 3 s). */
    public static final int CORE_INSERT_TICKS = 60;
    /** Fragments counted for the core altitude: 256 (4 full stacks) lift it {@link #MAX_CORE_ALTITUDE} blocks. */
    public static final int MAX_ALTITUDE_FRAGMENTS = 256;
    public static final float MAX_CORE_ALTITUDE = 16f;
    /** A black fragment is never consumed: it counts as a full stack. */
    public static final int BLACK_FRAGMENT_WORTH = 64;
    /** Height of the core above the plate once risen, before the fragments lift it (blocks). */
    public static final float CORE_BASE_LIFT = 0.5f;
    /** Height of the core center above the forge block when resting in the plate (blocks). */
    public static final float CORE_REST_HEIGHT = 1f;
    /** Reach (blocks) and pull (blocks per tick²) of the core at its highest; both grow with its altitude. */
    public static final double PULL_RANGE = 32, PULL_STRENGTH = 0.3;
    /** Radius of the orbit what the core pulls ends up circling on (blocks): within reach to hit it. */
    public static final double PULL_ORBIT = 2.5;
    /** The core blowing up: explosion power (an end crystal is 6), then everything within the radius is flung away. */
    public static final float CORE_EXPLOSION_POWER = 2f;
    public static final double CORE_BLAST_RADIUS = 8, CORE_BLAST_SPEED = 4;

    // ---- synced properties (PropertyDelegate)
    public static final int PROP_PROGRESS = 0;
    public static final int PROP_CRAFT_TIME = 1;
    public static final int PROP_FLAGS = 2;
    public static final int PROP_STATUS = 3;
    public static final int PROP_FIRST_GHOST = 4;
    public static final int PROPERTY_COUNT = PROP_FIRST_GHOST + LAYOUT_SIZE;
    public static final int FLAG_RUNNING = 1;
    public static final int FLAG_ACTIVATED = 1 << 1;
    public static final int FLAG_BLOCKED = 1 << 2;
    public static final int FLAG_POWERED = 1 << 3;

    /** 3: faces are weights, blank faces slot, die output moved from the center slot to its own slot. */
    private static final int FORMAT_VERSION = 3;
    private static final int[] DOWN_SLOTS = {OUTPUT_SLOT};
    private static final int[] OTHER_SLOTS = IntStream.range(0, SIZE).toArray();

    /** Why production can't run (synced as an ordinal). */
    public enum Status {
        OK, NOT_ACTIVATED, NOT_ENOUGH_FACES, MISSING_FRAGMENT, DUPLICATE_FRAGMENT, OUTPUT_BLOCKED,
        NOT_ENOUGH_BLANK_FACES;

        public static Status byId(int id) {
            Status[] values = values();
            return id >= 0 && id < values.length ? values[id] : OK;
        }

        /** Output full is not a reason to stop: the craft simply waits at 100%. */
        public boolean allowsRunning() {
            return this == OK || this == OUTPUT_BLOCKED;
        }
    }

    // ---- animations
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected static final RawAnimation FLOATING = RawAnimation.begin().thenLoop("floating");
    protected static final RawAnimation CORE_INSERT = RawAnimation.begin().thenPlay("core_insert").thenLoop("floating");
    protected static final RawAnimation CRAFTING = RawAnimation.begin().thenLoop("crafting");

    // ---- state
    private DefaultedList<ItemStack> inventory;
    /** Fragment and blank faces remembered for the ghosts (null = nothing remembered; faces are never remembered). */
    private final Item[] layout = new Item[LAYOUT_SIZE];
    private boolean running = false;
    private int progress = 0;
    private boolean powered = false;
    /** Production stopped with the button while powered: no auto-resume until the next rising edge. */
    private boolean manualStop = false;
    private long activationTime = Long.MIN_VALUE / 2;
    /** Items that must leave the forge (legacy power star, extra cores): dropped on the next tick. */
    private final List<ItemStack> pendingDrops = new ArrayList<>();
    private float rotationTicks = 0f; // client only
    private long forgedTime = -1; // client only
    // Core altitude smoothly following the fragments (client only, blocks above the plate)
    private float coreAltitude = 0f;
    private float prevCoreAltitude = 0f;
    /** The hitbox entity of the core in the sky (server only, never saved: respawned as needed). */
    @Nullable
    private UUID coreEntityId;
    /** Redstone input read once after placement/load (the block entity does not exist yet in onBlockAdded). */
    private boolean powerChecked = false;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PROP_PROGRESS -> progress;
                case PROP_CRAFT_TIME -> CRAFT_TIME;
                case PROP_FLAGS -> getFlags();
                case PROP_STATUS -> getStatus().ordinal();
                default -> {
                    int layoutIndex = index - PROP_FIRST_GHOST;
                    if (layoutIndex < 0 || layoutIndex >= LAYOUT_SIZE || layout[layoutIndex] == null) yield 0;
                    yield Registries.ITEM.getRawId(layout[layoutIndex]);
                }
            };
        }

        @Override
        public void set(int index, int value) {
            // Server authoritative: nothing is writable from the client
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public DiceForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DICE_FORGE_ENTITY, pos, state);
        this.inventory = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    }

    // =================================================================== NBT

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!this.writeLootTable(nbt)) {
            Inventories.writeNbt(nbt, this.inventory, registries);
        }
        nbt.putInt("ForgeVersion", FORMAT_VERSION);
        nbt.putBoolean("Running", running);
        nbt.putInt("Progress", progress);
        nbt.putBoolean("Powered", powered);
        nbt.putBoolean("ManualStop", manualStop);
        nbt.putLong("ActivationTime", activationTime);
        NbtList layoutNbt = new NbtList();
        for (Item item : layout) {
            layoutNbt.add(NbtString.of(item == null ? "" : Registries.ITEM.getId(item).toString()));
        }
        nbt.put("Layout", layoutNbt);
        if (!pendingDrops.isEmpty()) {
            NbtList drops = new NbtList();
            for (ItemStack stack : pendingDrops) {
                if (!stack.isEmpty()) drops.add(stack.toNbt(registries));
            }
            nbt.put("PendingDrops", drops);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.inventory = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
        if (!this.readLootTable(nbt)) {
            Inventories.readNbt(nbt, this.inventory, registries);
        }
        pendingDrops.clear();
        if (nbt.contains("PendingDrops", NbtElement.LIST_TYPE)) {
            NbtList drops = nbt.getList("PendingDrops", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < drops.size(); i++) {
                ItemStack.fromNbt(registries, drops.getCompound(i)).ifPresent(pendingDrops::add);
            }
        }
        int version = nbt.getInt("ForgeVersion");
        ItemStack center = inventory.get(CENTER_SLOT);
        if (!center.isEmpty()) {
            if (version < 2) {
                // Legacy forge (power star in slot 12): give the star back
                pendingDrops.add(center);
            } else if (inventory.get(OUTPUT_SLOT).isEmpty()) {
                // Version 2 kept the forged dice in the center slot: they move to the output slot
                inventory.set(OUTPUT_SLOT, center);
            } else {
                pendingDrops.add(center);
            }
            inventory.set(CENTER_SLOT, ItemStack.EMPTY);
        }
        this.running = nbt.getBoolean("Running");
        this.progress = Math.max(0, Math.min(CRAFT_TIME, nbt.getInt("Progress")));
        this.powered = nbt.getBoolean("Powered");
        this.manualStop = nbt.getBoolean("ManualStop");
        this.activationTime = nbt.contains("ActivationTime") ? nbt.getLong("ActivationTime") : Long.MIN_VALUE / 2;
        Arrays.fill(layout, null);
        if (nbt.contains("Layout", NbtElement.LIST_TYPE)) {
            NbtList layoutNbt = nbt.getList("Layout", NbtElement.STRING_TYPE);
            // Faces saved by older versions are forgotten (they are no longer remembered)
            for (int i = FACE_SLOTS; i < Math.min(LAYOUT_SIZE, layoutNbt.size()); i++) {
                Identifier id = Identifier.tryParse(layoutNbt.getString(i));
                if (id == null) continue;
                Item item = Registries.ITEM.get(id);
                layout[i] = item == Items.AIR ? null : item;
            }
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    public void sync() {
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        sync();
    }

    // =================================================================== tick

    @Override
    public void tick() {
        if (world == null) return;
        updateCoreAltitude();
        pullAround();
        if (world.isClient) {
            rotationTicks++; // client-side rotation for rendering
            if (running && progress < CRAFT_TIME) progress++; // client prediction, resynced on each craft
            return;
        }
        syncCoreEntity((ServerWorld) world);

        if (!powerChecked) {
            powerChecked = true;
            onRedstoneChanged(world.isReceivingRedstonePower(pos));
        }

        if (!pendingDrops.isEmpty()) {
            for (ItemStack stack : pendingDrops) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
            }
            pendingDrops.clear();
            markDirty();
        }

        if (!running) {
            // Automation: while powered, resume a loop stopped by a shortage as soon as it is refilled
            if (powered && !manualStop && world.getTime() % 10 == 0 && getStatus() == Status.OK) start();
            return;
        }

        Status status = getStatus();
        if (!status.allowsRunning()) {
            stop(false);
            return;
        }
        if (progress < CRAFT_TIME) {
            progress++;
            world.markDirty(pos); // saved, but no client sync needed: the client predicts the progress
        }
        if (progress >= CRAFT_TIME && status == Status.OK) {
            completeCraft();
            progress = 0;
            if (!getStatus().allowsRunning()) {
                // A slot ran out: stop the loop, the missing items show as ghosts
                running = false;
            }
            markDirty();
        }
    }

    // =================================================================== production

    /** Toggles production (CRAFT button). */
    public void toggleProduction() {
        if (running) {
            stop(powered);
        } else {
            start();
        }
    }

    /** Starts production with the current layout. @return true if started. */
    public boolean start() {
        if (world == null || running) return false;
        if (!getStatus().allowsRunning()) return false;
        snapshotLayout();
        running = true;
        progress = 0;
        manualStop = false;
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.6f, 1.6f);
        markDirty();
        return true;
    }

    /** Stops production. @param manual stopped by the player while powered (prevents auto-resume). */
    public void stop(boolean manual) {
        boolean wasRunning = running;
        running = false;
        progress = 0;
        manualStop = manual;
        if (wasRunning && world != null && !world.isClient) {
            world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.5f, 1.6f);
        }
        markDirty();
    }

    /** Redstone input (called by the block on neighbour updates / placement). */
    public void onRedstoneChanged(boolean isPowered) {
        if (isPowered == powered) return;
        powered = isPowered;
        manualStop = false;
        if (powered) {
            start();
        } else if (running) {
            stop(false);
        }
        markDirty();
    }

    /** Remembers the fragments and blank faces in use (their ghosts show once they run out). */
    private void snapshotLayout() {
        for (int i = FACE_SLOTS; i < LAYOUT_SIZE; i++) {
            ItemStack stack = inventory.get(layoutSlot(i));
            layout[i] = stack.isEmpty() ? null : stack.getItem();
        }
    }

    /** @return the inventory slot of a layout index (faces 0..11, fragments 13..16, then the blank faces slot). */
    public static int layoutSlot(int layoutIndex) {
        if (layoutIndex < FACE_SLOTS) return layoutIndex;
        if (layoutIndex == BLANK_LAYOUT_INDEX) return BLANK_SLOT;
        return FIRST_FRAGMENT_SLOT + (layoutIndex - FACE_SLOTS);
    }

    /** @return the layout index of an inventory slot, or -1 for the center and output slots. */
    public static int layoutIndex(int slot) {
        if (slot < FACE_SLOTS) return slot;
        if (slot >= FIRST_FRAGMENT_SLOT && slot < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS) return FACE_SLOTS + slot - FIRST_FRAGMENT_SLOT;
        if (slot == BLANK_SLOT) return BLANK_LAYOUT_INDEX;
        return -1;
    }

    /** @return the first reason preventing a craft, or {@link Status#OK} */
    public Status getStatus() {
        if (!isActivated()) return Status.NOT_ACTIVATED;
        Status resources = checkResources(this);
        if (resources != Status.OK) return resources;
        return canAcceptOutput(createDie()) ? Status.OK : Status.OUTPUT_BLOCKED;
    }

    /** Resource checks shared with the client screen (faces count, fragments, blank faces). */
    public static Status checkResources(Inventory inventory) {
        int faces = countFaces(inventory);
        if (faces < MIN_FACES) return Status.NOT_ENOUGH_FACES;
        Set<Item> colours = new java.util.HashSet<>();
        for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!isStarFragment(stack)) return Status.MISSING_FRAGMENT;
            if (!isInfiniteFragment(stack) && !colours.add(stack.getItem())) return Status.DUPLICATE_FRAGMENT;
        }
        if (!isBlankFace(inventory.getStack(BLANK_SLOT)) || inventory.getStack(BLANK_SLOT).getCount() < faces) {
            return Status.NOT_ENOUGH_BLANK_FACES;
        }
        return Status.OK;
    }

    /** @return the number of face slots used, i.e. the blank faces one die consumes. */
    public static int countFaces(Inventory inventory) {
        int faces = 0;
        for (int i = 0; i < FACE_SLOTS; i++) if (DiceFace.isFace(inventory.getStack(i))) faces++;
        return faces;
    }

    /** @return the die the current face slots would produce (EMPTY if not enough faces). */
    public ItemStack createDie() {
        return createDie(this);
    }

    public static ItemStack createDie(Inventory inventory) {
        List<ItemStack> faces = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < FACE_SLOTS; i++) {
            ItemStack stack = inventory.getStack(i);
            if (DiceFace.isFace(stack)) {
                faces.add(stack);
                count++;
            }
        }
        if (count < MIN_FACES) return ItemStack.EMPTY;
        return DiceFacesComponent.createDie(faces);
    }

    private boolean canAcceptOutput(ItemStack die) {
        if (die.isEmpty()) return false;
        ItemStack output = inventory.get(OUTPUT_SLOT);
        if (output.isEmpty()) return true;
        return ItemStack.areItemsAndComponentsEqual(output, die)
                && output.getCount() + die.getCount() <= output.getMaxCount();
    }

    private void completeCraft() {
        ItemStack die = createDie();
        if (die.isEmpty()) return;
        // Faces are kept (they are the die's weights): one blank face per face slot used is consumed instead
        inventory.get(BLANK_SLOT).decrement(countFaces(this));
        for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
            ItemStack stack = inventory.get(i);
            // Black fragments are never consumed
            if (!stack.isEmpty() && !isInfiniteFragment(stack)) stack.decrement(1);
        }
        ItemStack output = inventory.get(OUTPUT_SLOT);
        if (output.isEmpty()) {
            inventory.set(OUTPUT_SLOT, die);
        } else {
            output.increment(die.getCount());
        }
        if (world != null) {
            world.addSyncedBlockEvent(pos, getCachedState().getBlock(), FORGED_EVENT, 0); // client animation
            world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.4f, 1.8f);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.8f, 1.2f);
        }
    }

    // =================================================================== activation

    public boolean isActivated() {
        BlockState state = getCachedState();
        return state.contains(DiceForgeBlock.ACTIVATED) && state.get(DiceForgeBlock.ACTIVATED);
    }

    public void activate() {
        if (world == null || isActivated()) return;
        activationTime = world.getTime();
        world.setBlockState(pos, getCachedState().with(DiceForgeBlock.ACTIVATED, true));
        world.playSound(null, pos, SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.7f, 1.2f);
        markDirty();
    }

    /** @return true if the gravity core can be taken back: forge activated and the insertion animation over. */
    public boolean canRemoveCore() {
        return world != null && isActivated() && !isInsertingCore(0f);
    }

    /**
     * Takes the gravity core back out of the forge (sneak + right-click with an empty hand).
     * <p>
     * A running craft is stopped without losing anything: items are only consumed when a craft completes.
     * The core is handed to the player, or dropped when their inventory is full / there is no player; the forged
     * dice stay in the output slot. The remembered layout (ghosts) is kept, and the forge goes back to its static
     * idle state, so re-inserting the core plays "core_insert" again.
     *
     * @return true if the core was removed
     */
    public boolean removeCore(@Nullable PlayerEntity player) {
        if (world == null || world.isClient || !canRemoveCore()) return false;
        if (running) {
            // No manual stop flag: under redstone power, production resumes once the core is back
            stop(false);
        }
        activationTime = Long.MIN_VALUE / 2;
        world.setBlockState(pos, getCachedState().with(DiceForgeBlock.ACTIVATED, false));
        giveOrDrop(player, new ItemStack(ModBlocks.GRAVITY_CORE));
        world.playSound(null, pos, SoundEvents.BLOCK_HEAVY_CORE_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        markDirty();
        return true;
    }

    private void giveOrDrop(@Nullable PlayerEntity player, ItemStack stack) {
        if (player != null) {
            player.getInventory().offerOrDrop(stack);
        } else if (world != null) {
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        }
    }

    /** @return world time at which the gravity core was inserted (drives the insertion animation). */
    public long getActivationTime() {
        return activationTime;
    }

    /** @return true while the core insertion animation plays (client). */
    public boolean isInsertingCore(float partialTick) {
        if (world == null || !isActivated()) return false;
        long elapsed = world.getTime() - activationTime;
        return elapsed >= 0 && elapsed + partialTick < CORE_INSERT_TICKS;
    }

    public static boolean isGravityCore(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(ModBlocks.GRAVITY_CORE.asItem());
    }

    // =================================================================== item rules

    public static boolean isStarFragment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == ModItems.BLUE_STAR_FRAGMENT || item == ModItems.PURPLE_STAR_FRAGMENT
                || item == ModItems.RED_STAR_FRAGMENT || item == ModItems.YELLOW_STAR_FRAGMENT
                || item == ModItems.GREEN_STAR_FRAGMENT || item == ModItems.BLACK_STAR_FRAGMENT;
    }

    public static boolean isBlankFace(ItemStack stack) {
        return !stack.isEmpty() && DiceFace.fromItem(stack.getItem())
                .map(face -> face.kind() == DiceFacesComponent.Kind.BLANK).orElse(false);
    }

    // ---- core altitude

    /** @return fragments counted for the altitude: every fragment, a black one worth a full stack (max 256). */
    public static int countAltitudeFragments(Inventory inventory) {
        int total = 0;
        for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!isStarFragment(stack)) continue;
            total += isInfiniteFragment(stack) ? BLACK_FRAGMENT_WORTH * stack.getCount() : stack.getCount();
        }
        return Math.min(MAX_ALTITUDE_FRAGMENTS, total);
    }

    /** @return blocks the fragments lift the core above its base height (0 to {@link #MAX_CORE_ALTITUDE}). */
    public static float getTargetAltitude(Inventory inventory) {
        return MAX_CORE_ALTITUDE * countAltitudeFragments(inventory) / MAX_ALTITUDE_FRAGMENTS;
    }

    private void updateCoreAltitude() {
        prevCoreAltitude = coreAltitude;
        // The core rises out of the plate once the insertion animation is over
        float target = isActivated() && !isInsertingCore(0f) ? CORE_BASE_LIFT + getTargetAltitude(this) : 0f;
        float delta = target - coreAltitude;
        float step = Math.signum(delta) * Math.min(Math.abs(delta), Math.max(0.02f, Math.abs(delta) * 0.06f));
        coreAltitude += step;
    }

    /** @return the center of the core, in the world. */
    public Vec3d getCoreCenter() {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + CORE_REST_HEIGHT + coreAltitude, pos.getZ() + 0.5);
    }

    /** The risen core pulls what is around it: the higher, the stronger and the farther. */
    private void pullAround() {
        float lift = coreAltitude - CORE_BASE_LIFT;
        if (world == null || !isActivated() || lift <= 0) return;
        double height = Math.min(1, lift / MAX_CORE_ALTITUDE);
        GravityPull.pullAround(world, getCoreCenter(), PULL_RANGE * height, PULL_STRENGTH * height, true, PULL_ORBIT);
    }

    /** Keeps the hitbox entity on the risen core (and removes it when there is no core). */
    private void syncCoreEntity(ServerWorld world) {
        Entity current = coreEntityId == null ? null : world.getEntity(coreEntityId);
        if (!isActivated() || isInsertingCore(0f)) {
            if (current != null) current.discard();
            coreEntityId = null;
            return;
        }
        Vec3d center = getCoreCenter();
        if (current == null || current.isRemoved()) {
            ForgeCoreEntity core = new ForgeCoreEntity(ModEntities.FORGE_CORE, world);
            core.setForgePos(pos);
            core.setPosition(center.x, center.y - core.getHeight() / 2, center.z);
            world.spawnEntity(core);
            coreEntityId = core.getUuid();
        } else {
            current.setPosition(center.x, center.y - current.getHeight() / 2, center.z);
        }
    }

    /**
     * The core, hit in the sky, blows up: a small explosion, then everything around it is flung away. The core is
     * lost and the forge goes back to its idle state (nothing else is lost).
     */
    public void explodeCore(@Nullable Entity cause) {
        if (!(world instanceof ServerWorld serverWorld) || !isActivated()) return;
        Vec3d center = getCoreCenter();
        if (running) stop(false);
        activationTime = Long.MIN_VALUE / 2;
        world.setBlockState(pos, getCachedState().with(DiceForgeBlock.ACTIVATED, false));
        coreAltitude = prevCoreAltitude = 0f;
        // Hurts, but breaks no block (the forge right under a low core included)
        serverWorld.createExplosion(cause, center.x, center.y, center.z, CORE_EXPLOSION_POWER, World.ExplosionSourceType.NONE);
        Box blast = new Box(center, center).expand(CORE_BLAST_RADIUS);
        for (Entity entity : serverWorld.getOtherEntities(null, blast, e -> !e.isSpectator() && !(e instanceof ForgeCoreEntity))) {
            Vec3d away = entity.getBoundingBox().getCenter().subtract(center);
            double distance = away.length();
            if (distance > CORE_BLAST_RADIUS) continue;
            Vec3d direction = distance < 1.0E-3 ? new Vec3d(0, 1, 0) : away.multiply(1 / distance);
            entity.setVelocity(entity.getVelocity().add(direction.multiply(CORE_BLAST_SPEED * (1 - distance / CORE_BLAST_RADIUS))));
            entity.velocityModified = true;
        }
        markDirty();
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (world instanceof ServerWorld serverWorld && coreEntityId != null) {
            Entity core = serverWorld.getEntity(coreEntityId);
            if (core != null) core.discard();
        }
    }

    /** @return height of the core above the plate (blocks), smoothed. */
    public float getCoreAltitude(float partialTick) {
        return prevCoreAltitude + (coreAltitude - prevCoreAltitude) * partialTick;
    }

    /** Black star fragments power the forge forever and may be used in several slots. */
    public static boolean isInfiniteFragment(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(ModItems.BLACK_STAR_FRAGMENT);
    }

    /**
     * Slot rules, shared by the block entity, the GUI slots (also client side, with a plain inventory) and hoppers.
     */
    public static boolean isValidForSlot(Inventory inventory, int slot, ItemStack stack, boolean activated) {
        if (stack.isEmpty()) return true;
        if (slot >= 0 && slot < FACE_SLOTS) return DiceFace.isFace(stack);
        if (slot == CENTER_SLOT) return !activated && isGravityCore(stack);
        if (slot == BLANK_SLOT) return isBlankFace(stack);
        if (slot == OUTPUT_SLOT) return false; // filled by the forge only
        if (slot >= FIRST_FRAGMENT_SLOT && slot < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS) {
            if (!isStarFragment(stack)) return false;
            if (isInfiniteFragment(stack)) return true;
            // Non-black colours must differ from one slot to another
            for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
                if (i != slot && inventory.getStack(i).isOf(stack.getItem())) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return isValidForSlot(this, slot, stack, isActivated());
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == CENTER_SLOT && !isActivated() && isGravityCore(stack) && world != null && !world.isClient) {
            // Inserting the core in the center slot activates the forge: the core goes into the forge itself
            if (stack.getCount() > 1) pendingDrops.add(stack.copyWithCount(stack.getCount() - 1));
            inventory.set(CENTER_SLOT, ItemStack.EMPTY);
            activate();
            return;
        }
        super.setStack(slot, stack);
    }

    // ---- hoppers / automation

    @Override
    public int[] getAvailableSlots(Direction side) {
        return side == Direction.DOWN ? DOWN_SLOTS : OTHER_SLOTS;
    }

    /**
     * Like any container: any valid item in any slot (merging is up to the hopper). Blank faces only go to their own
     * slot: automation never turns them into faces of the die.
     */
    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (dir == Direction.DOWN || !isValid(slot, stack)) return false;
        return slot >= FACE_SLOTS || !isBlankFace(stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT_SLOT && dir == Direction.DOWN;
    }

    // =================================================================== misc

    public int getComparatorOutput() {
        ItemStack output = inventory.get(OUTPUT_SLOT);
        if (output.isEmpty()) return 0;
        return 1 + (int) (14f * output.getCount() / output.getMaxCount());
    }

    /** Items to drop when the forge is broken, on top of its inventory (core, legacy items). */
    public List<ItemStack> getExtraDrops() {
        List<ItemStack> drops = new ArrayList<>(pendingDrops);
        pendingDrops.clear();
        if (isActivated()) drops.add(new ItemStack(ModBlocks.GRAVITY_CORE));
        return drops;
    }

    private int getFlags() {
        int flags = 0;
        if (running) flags |= FLAG_RUNNING;
        if (isActivated()) flags |= FLAG_ACTIVATED;
        if (running && progress >= CRAFT_TIME) flags |= FLAG_BLOCKED;
        if (powered) flags |= FLAG_POWERED;
        return flags;
    }

    public PropertyDelegate getProperties() {
        return properties;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "main", 10, this::mainAnimController));
    }

    /** idle (static, no core) → core_insert → floating loop; crafting loop while producing. */
    private PlayState mainAnimController(AnimationState<DiceForgeBlockEntity> state) {
        if (!isActivated()) {
            // Core removed (or never inserted): next insertion must replay core_insert from its start
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }
        if (running) return state.setAndContinue(CRAFTING);
        if (isInsertingCore(state.getPartialTick())) return state.setAndContinue(CORE_INSERT);
        return state.setAndContinue(FLOATING);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    protected DefaultedList<ItemStack> getHeldStacks() {
        return this.inventory;
    }

    @Override
    protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
        this.inventory = inventory;
    }

    @Override
    protected Text getContainerName() {
        return Text.translatable("block.steveparty.dice_forge");
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new DiceForgeScreenHandler(syncId, playerInventory, this, this.properties);
    }

    public DefaultedList<ItemStack> getInventory() {
        return this.inventory;
    }

    /** @return the remembered item of a layout index (null if none). */
    public @Nullable Item getLayoutItem(int layoutIndex) {
        return layout[layoutIndex];
    }

    /** Block event sent when a die is forged: the client plays the falling die animation. */
    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (type != FORGED_EVENT) return super.onSyncedBlockEvent(type, data);
        if (world != null && world.isClient) forgedTime = world.getTime();
        return true;
    }

    /** @return client world time at which the last die was forged, or -1 */
    public long getForgedTime() {
        return forgedTime;
    }

    public float getRotationTicks() {
        return rotationTicks;
    }

    public float getCraftProgress(float partialTick) {
        if (!running) return 0f;
        float value = progress < CRAFT_TIME ? progress + partialTick : progress;
        return Math.min(1f, Math.max(0f, value / CRAFT_TIME));
    }

    public boolean isCrafting() {
        return running;
    }

    public boolean isPowered() {
        return powered;
    }
}
