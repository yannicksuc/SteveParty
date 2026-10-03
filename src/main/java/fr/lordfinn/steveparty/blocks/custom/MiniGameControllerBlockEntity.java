package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.minigame.MiniGameControllers;
import fr.lordfinn.steveparty.minigame.PageZone;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Mini-game Controller: the block of a mini-game's arena. It holds a Mini-game Page and is the home of that page's
 * mini-game: a page has one controller at most ({@link MiniGameControllers}). The zone of the mini-game is the page's.
 * A Zone Cartridge saved in a controller by an earlier version gives its zone to the page (if it has none) and is gone.
 * <ul>
 *     <li>Out of any party, its screen plays the mini-game with those near the page's pipes, for nothing
 *     ({@code MiniGameTest}).</li>
 *     <li>In a party, a mini-game whose page has a controller starts with a practice round (see
 *     {@code MiniGamePartyStep}); the players vote for the real round with a key, or on this screen.</li>
 * </ul>
 * The page is synced to the clients (what the block shows of its mini-game).
 */
public class MiniGameControllerBlockEntity extends SyncedBlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload> {
    public static final int SLOT_PAGE = 0, SLOTS = 1;
    /** How often a controller says again which page it holds (a copied controller gives its page back). */
    private static final int CLAIM_INTERVAL_TICKS = 40;

    private ItemStack page = ItemStack.EMPTY;
    /** The zone of the Zone Cartridge an earlier version saved in it: given to its page on its first tick. */
    private @Nullable PageZone oldZone;
    /** Its « adventure mode » option: the players of its mini-game play in adventure mode in its zone. */
    private boolean adventure;
    /** It said which page it holds since it was loaded. Not saved. */
    private boolean claimedOnce;
    /** Client side: the controllers the client has loaded (whose zones it may show). */
    private static final Set<MiniGameControllerBlockEntity> CLIENT_LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public MiniGameControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINI_GAME_CONTROLLER_ENTITY, pos, state);
    }

    @Override
    public void setWorld(net.minecraft.world.World world) {
        super.setWorld(world);
        if (world.isClient) CLIENT_LOADED.add(this);
    }

    /** Client side: the controllers loaded in {@code world}. */
    public static List<MiniGameControllerBlockEntity> clientLoaded(net.minecraft.world.World world) {
        synchronized (CLIENT_LOADED) {
            CLIENT_LOADED.removeIf(BlockEntity::isRemoved);
            return CLIENT_LOADED.stream().filter(controller -> controller.getWorld() == world).toList();
        }
    }

    public ItemStack getPage() {
        return page;
    }

    /** The id of the page it holds, null without page. */
    public @Nullable UUID getPageId() {
        return MiniGamePages.idOf(page);
    }

    private GlobalPos globalPos() {
        return GlobalPos.create(world.getRegistryKey(), pos);
    }

    public boolean isAdventure() {
        return adventure;
    }

    public void setAdventure(boolean adventure) {
        if (this.adventure == adventure) return;
        this.adventure = adventure;
        claim();
        markDirty();
    }

    /** Says again which page it holds, with its option. @return false if another controller is its home */
    private boolean claim() {
        UUID id = getPageId();
        if (id == null || !(world instanceof ServerWorld serverWorld)) return true;
        return MiniGameControllers.claim(serverWorld.getServer(), id, globalPos(), adventure);
    }

    /**
     * @return true if nothing of the controller may change now: a round is played in the zone it stands in, or the
     * player is of a round (what it holds then is a session inventory: a page taken would go with it)
     */
    public boolean isLockedFor(PlayerEntity player) {
        return world != null && (ZoneBubbles.of(world, pos) != null || ZoneBubbles.ofPlayer(player.getUuid()) != null);
    }

    /** Server side: the zone of its mini-game, its page's; empty without page, or for a page without zone. */
    public Optional<PageZone> getZone() {
        UUID id = getPageId();
        return id == null || !(world instanceof ServerWorld serverWorld) ? Optional.empty() : MiniGameControllers.zoneOf(serverWorld.getServer(), id);
    }

    /**
     * Whether a page can be put in: a mini-game page no other controller holds (a linked copy of a page that has its
     * controller is refused).
     */
    public boolean accepts(ItemStack stack) {
        if (!MiniGamePages.isPage(stack)) return false;
        UUID id = MiniGamePages.idOf(stack);
        if (id == null || !(world instanceof ServerWorld serverWorld)) return true;
        return MiniGameControllers.isFreeFor(serverWorld.getServer(), id, globalPos());
    }

    /** Puts a page in (an empty stack takes it out): nothing is given back, the caller moves the stacks. */
    public void setPage(ItemStack stack) {
        if (world instanceof ServerWorld serverWorld) {
            UUID before = getPageId();
            if (before != null) MiniGameControllers.release(serverWorld.getServer(), before, globalPos());
            if (!stack.isEmpty()) {
                MiniGameControllers.claim(serverWorld.getServer(), MiniGamePages.ensureId(stack), globalPos(), adventure);
                MiniGamePages.refresh(serverWorld.getServer(), stack);
            }
        }
        page = stack;
        markDirty();
        refreshState();
    }

    // ------------------------------------------------------------------ what the block shows

    /** How often the lamp looks at the mini-game of the page (the sessions have no event to listen to). */
    private static final int SIGNAL_INTERVAL_TICKS = 5;

    /** What the mini-game of the page is doing: a party's practice round, being played (a party's real round, or out of a party), or nothing. */
    public MiniGameControllerBlock.Signal signal() {
        UUID id = getPageId();
        if (id == null) return MiniGameControllerBlock.Signal.RED;
        java.util.Optional<fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity> party =
                fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity.getPartyPlayingPage(List.of(id));
        if (party.isPresent() && party.get().getPartyData().getCurrentStep()
                instanceof fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep step) {
            return step.isPractice() ? MiniGameControllerBlock.Signal.ORANGE : MiniGameControllerBlock.Signal.GREEN;
        }
        fr.lordfinn.steveparty.minigame.MiniGameTest played = fr.lordfinn.steveparty.minigame.MiniGameTest.of(id);
        return played != null && played.phase() != fr.lordfinn.steveparty.minigame.MiniGameTest.Phase.FINISHED
                ? MiniGameControllerBlock.Signal.GREEN : MiniGameControllerBlock.Signal.RED;
    }

    /** The block shows the page it holds and the lamp of its mini-game: changed only when they are not what it shows. */
    public void refreshState() {
        if (!(world instanceof ServerWorld) || isRemoved()) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MiniGameControllerBlock)) return;
        BlockState wanted = state.with(MiniGameControllerBlock.PAGE, !page.isEmpty()).with(MiniGameControllerBlock.SIGNAL, signal());
        if (wanted != state) world.setBlockState(pos, wanted, net.minecraft.block.Block.NOTIFY_LISTENERS);
    }

    /** The controller is broken: its page has no home any more, what it held falls. */
    public void onBroken() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        UUID id = getPageId();
        if (id != null) MiniGameControllers.release(serverWorld.getServer(), id, globalPos());
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, page);
        page = ItemStack.EMPTY;
    }

    public void serverTick(ServerWorld serverWorld) {
        if (serverWorld.getTime() % SIGNAL_INTERVAL_TICKS == 0) refreshState();
        // On its first tick too: a controller a mini-game zone just put back is the home of its page at once
        if (claimedOnce && serverWorld.getTime() % CLAIM_INTERVAL_TICKS != 0) return;
        claimedOnce = true;
        if (oldZone != null) {
            UUID id = getPageId();
            if (id != null) MiniGameControllers.adoptZone(serverWorld.getServer(), id, oldZone);
            oldZone = null;
            markDirty();
        }
        if (claim()) return;
        // Another controller is the home of this page (this one was copied): the page comes out
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, page);
        page = ItemStack.EMPTY;
        markDirty();
    }

    // ------------------------------------------------------------------ its slot, as an inventory

    /** The page as the slot of its screen. */
    public Inventory inventory() {
        return new Inventory() {
            @Override public int size() { return SLOTS; }
            @Override public boolean isEmpty() { return page.isEmpty(); }
            @Override public ItemStack getStack(int slot) { return slot == SLOT_PAGE ? page : ItemStack.EMPTY; }
            @Override public ItemStack removeStack(int slot, int amount) { return amount <= 0 ? ItemStack.EMPTY : removeStack(slot); }
            @Override public ItemStack removeStack(int slot) {
                ItemStack stack = getStack(slot);
                setStack(slot, ItemStack.EMPTY);
                return stack;
            }
            @Override public void setStack(int slot, ItemStack stack) {
                if (slot == SLOT_PAGE) setPage(stack);
            }
            @Override public int getMaxCountPerStack() { return 1; }
            @Override public void markDirty() { MiniGameControllerBlockEntity.this.markDirty(); }
            @Override public boolean canPlayerUse(PlayerEntity player) {
                return ScreenHandlerChecks.canUseBlockEntity(MiniGameControllerBlockEntity.this, player);
            }
            @Override public void clear() {
                setPage(ItemStack.EMPTY);
            }
        };
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        if (!page.isEmpty()) nbt.put("Page", page.toNbt(wrapper));
        if (adventure) nbt.putBoolean("Adventure", true);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        page = nbt.contains("Page") ? ItemStack.fromNbt(wrapper, nbt.get("Page")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        // The Zone Cartridge an earlier version kept here: an item that no longer exists, only its zone is read
        if (nbt.contains("ZoneCartridge", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) oldZone = oldZone(nbt.getCompound("ZoneCartridge"));
        adventure = nbt.getBoolean("Adventure");
    }

    /** The zone drawn on a saved Zone Cartridge (its {@code steveparty:zone-selection} component), null for none. */
    public static @Nullable PageZone oldZone(NbtCompound cartridge) {
        NbtCompound selection = cartridge.getCompound("components").getCompound("steveparty:zone-selection");
        net.minecraft.util.Identifier dimension = net.minecraft.util.Identifier.tryParse(selection.getString("dimension"));
        if (dimension == null || !selection.contains("box")) return null;
        return net.minecraft.util.math.BlockBox.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, selection.get("box")).result()
                .map(box -> new PageZone(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimension), box))
                .filter(zone -> !zone.tooBig()).orElse(null);
    }

    @Override
    public void markDirty() {
        syncToClients();
        super.markDirty();
    }

    // ------------------------------------------------------------------ screen

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity player) {
        return new BlockPosPayload(pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable(getCachedState().getBlock().getTranslationKey());
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new MiniGameControllerScreenHandler(syncId, playerInventory, this);
    }
}
