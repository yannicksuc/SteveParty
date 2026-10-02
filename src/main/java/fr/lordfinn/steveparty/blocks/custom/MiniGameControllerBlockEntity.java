package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.minigame.MiniGameControllers;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
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
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Mini-game Controller: the block of a mini-game's arena. It holds a Mini-game Page (and a Zone Cartridge) and is
 * the home of that page's mini-game: a page has one controller at most ({@link MiniGameControllers}).
 * <ul>
 *     <li>Out of any party, its screen plays the mini-game with those near the page's pipes, for nothing
 *     ({@code MiniGameTest}).</li>
 *     <li>In a party, a mini-game whose page has a controller starts with a practice round (see
 *     {@code MiniGamePartyStep}); the players vote for the real round with a key, or on this screen.</li>
 * </ul>
 * The page and the cartridge are synced to the clients (what the block shows of its mini-game).
 */
public class MiniGameControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload> {
    public static final int SLOT_PAGE = 0, SLOT_ZONE = 1, SLOTS = 2;
    /** How often a controller says again which page it holds (a copied controller gives its page back). */
    private static final int CLAIM_INTERVAL_TICKS = 40;

    private ItemStack page = ItemStack.EMPTY;
    private ItemStack cartridge = ItemStack.EMPTY;

    public MiniGameControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINI_GAME_CONTROLLER_ENTITY, pos, state);
    }

    public ItemStack getPage() {
        return page;
    }

    public ItemStack getCartridge() {
        return cartridge;
    }

    /** The id of the page it holds, null without page. */
    public @Nullable UUID getPageId() {
        return MiniGamePages.idOf(page);
    }

    private GlobalPos globalPos() {
        return GlobalPos.create(world.getRegistryKey(), pos);
    }

    /** @return true if the stack is a Zone Cartridge (what its second slot takes). */
    public static boolean isZoneCartridge(ItemStack stack) {
        return false;
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
                MiniGameControllers.claim(serverWorld.getServer(), MiniGamePages.ensureId(stack), globalPos());
                MiniGamePages.refresh(serverWorld.getServer(), stack);
            }
        }
        page = stack;
        markDirty();
    }

    /** Puts a Zone Cartridge in (an empty stack takes it out). */
    public void setCartridge(ItemStack stack) {
        cartridge = stack;
        markDirty();
    }

    /** The controller is broken: its page has no home any more, what it held falls. */
    public void onBroken() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        UUID id = getPageId();
        if (id != null) MiniGameControllers.release(serverWorld.getServer(), id, globalPos());
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, page);
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cartridge);
        page = ItemStack.EMPTY;
        cartridge = ItemStack.EMPTY;
    }

    public void serverTick(ServerWorld serverWorld) {
        if (serverWorld.getTime() % CLAIM_INTERVAL_TICKS != 0) return;
        UUID id = getPageId();
        if (id == null || MiniGameControllers.claim(serverWorld.getServer(), id, globalPos())) return;
        // Another controller is the home of this page (this one was copied): the page comes out
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, page);
        page = ItemStack.EMPTY;
        markDirty();
    }

    // ------------------------------------------------------------------ the two slots, as an inventory

    /** The page and the cartridge as the slots of its screen. */
    public Inventory inventory() {
        return new Inventory() {
            @Override public int size() { return SLOTS; }
            @Override public boolean isEmpty() { return page.isEmpty() && cartridge.isEmpty(); }
            @Override public ItemStack getStack(int slot) { return slot == SLOT_PAGE ? page : cartridge; }
            @Override public ItemStack removeStack(int slot, int amount) { return amount <= 0 ? ItemStack.EMPTY : removeStack(slot); }
            @Override public ItemStack removeStack(int slot) {
                ItemStack stack = getStack(slot);
                setStack(slot, ItemStack.EMPTY);
                return stack;
            }
            @Override public void setStack(int slot, ItemStack stack) {
                if (slot == SLOT_PAGE) setPage(stack);
                else setCartridge(stack);
            }
            @Override public int getMaxCountPerStack() { return 1; }
            @Override public void markDirty() { MiniGameControllerBlockEntity.this.markDirty(); }
            @Override public boolean canPlayerUse(PlayerEntity player) {
                return ScreenHandlerChecks.canUseBlockEntity(MiniGameControllerBlockEntity.this, player);
            }
            @Override public void clear() {
                setPage(ItemStack.EMPTY);
                setCartridge(ItemStack.EMPTY);
            }
        };
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        if (!page.isEmpty()) nbt.put("Page", page.toNbt(wrapper));
        if (!cartridge.isEmpty()) nbt.put("ZoneCartridge", cartridge.toNbt(wrapper));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        page = nbt.contains("Page") ? ItemStack.fromNbt(wrapper, nbt.get("Page")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        cartridge = nbt.contains("ZoneCartridge") ? ItemStack.fromNbt(wrapper, nbt.get("ZoneCartridge")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void markDirty() {
        if (world instanceof ServerWorld) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
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
