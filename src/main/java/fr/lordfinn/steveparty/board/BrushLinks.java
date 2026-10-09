package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.HopSwitchBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Everything the Tile Linker Brush links: the blocks holding a cartridge (board spaces of every role, routers, Hop
 * Switches, Looting Boxes, Piggy Banks, the Party Controller's bank) and, for each, the kinds of links their cartridge
 * stores ({@link BrushLinkable}), given by {@link Provider}s: the built-in ones are in {@link CartridgeLinks}, another
 * holder or cartridge adds its own with {@link #register}. Same answers on both sides (the cartridges of the holders are
 * sent to the clients).
 */
public final class BrushLinks {
    /** The slot of the Party Controller's bank (its Inventory Cartridge, not in an inventory). */
    public static final int BANK = -2;

    private BrushLinks() {
    }

    /**
     * A cartridge held by a block: where, in which slot, how its change is saved and sent, and who may change it.
     *
     * @param cartridge the stack itself (changed in place), empty if the slot is
     */
    public record Held(BlockPos pos, int slot, ItemStack cartridge, Runnable sync, Predicate<PlayerEntity> editors) {
        public boolean canEdit(PlayerEntity player) {
            return editors.test(player);
        }
    }

    /** Adds the kinds of links of the cartridge {@code held} by {@code holder} to {@code out}. */
    @FunctionalInterface
    public interface Provider {
        void provide(World world, BlockEntity holder, Held held, List<BrushLinkable> out);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>(CartridgeLinks.PROVIDERS);

    /** Another kind of links the brush paints (an addon's holder or cartridge). */
    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    // ---------------------------------------------------------------- holders

    /** Whether the block at {@code pos} holds a cartridge the brush can link from (a part of a large tile stands for its tile). */
    public static boolean isHolder(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(BoardSpaces.resolve(world, pos));
        return blockEntity instanceof CartridgeContainerBlockEntity || blockEntity instanceof PartyControllerEntity;
    }

    /**
     * The cartridge the brush edits on the holder at {@code pos}: on a board space, the slot of {@code level} (see
     * {@link BoardLinks#slotOf}); the only slot of the other containers; the Party Controller's bank. Null if no holder.
     */
    public static @Nullable Held holder(World world, BlockPos pos, int level) {
        BlockPos resolved = BoardSpaces.resolve(world, pos);
        BlockEntity blockEntity = world.getBlockEntity(resolved);
        if (blockEntity instanceof CartridgeContainerBlockEntity container) return held(world, resolved, BoardLinks.slotOf(container, level));
        if (blockEntity instanceof PartyControllerEntity) return held(world, resolved, BANK);
        return null;
    }

    /** The cartridge in {@code slot} of the holder at {@code pos} ({@link #BANK} for a Party Controller's), or null. */
    public static @Nullable Held held(World world, BlockPos pos, int slot) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof CartridgeContainerBlockEntity container) {
            if (slot < 0 || slot >= container.size()) return null;
            BlockPos at = pos.toImmutable();
            return new Held(at, slot, container.getStack(slot), () -> BoardLinks.sync(container),
                    player -> ScreenHandlerChecks.canBuildAt(player, at));
        }
        if (blockEntity instanceof PartyControllerEntity controller && slot == BANK) {
            BlockPos at = pos.toImmutable();
            return new Held(at, BANK, controller.getBank(), () -> {
                controller.markDirty();
                BlockState state = controller.getCachedState();
                if (!world.isClient) world.updateListeners(at, state, state, Block.NOTIFY_ALL);
            }, controller::canEdit);
        }
        return null;
    }

    /** The kinds of links of the holder at {@code pos} (the slot of {@code level}), empty for anything else. */
    public static List<BrushLinkable> of(World world, @Nullable BlockPos pos, int level) {
        if (pos == null) return List.of();
        Held held = holder(world, pos, level);
        if (held == null) return List.of();
        BlockEntity blockEntity = world.getBlockEntity(held.pos());
        List<BrushLinkable> kinds = new ArrayList<>(2);
        for (Provider provider : PROVIDERS) provider.provide(world, blockEntity, held, kinds);
        return kinds;
    }

    /**
     * The kind of {@code kinds} reaching {@code target} edits: the one it is a target of (removed), else the first that
     * accepts it (added); null for none.
     */
    public static @Nullable BrushLinkable kindFor(List<BrushLinkable> kinds, World world, BlockPos target) {
        for (BrushLinkable kind : kinds) if (kind.linked(world, target)) return kind;
        for (BrushLinkable kind : kinds) if (kind.accepts(world, target)) return kind;
        return null;
    }

    /** Whether {@code target} (not a holder) is something the holder at {@code from} links or could link: the brush aims at it. */
    public static boolean aims(World world, @Nullable BlockPos from, int level, BlockPos target) {
        return from != null && !target.equals(from) && kindFor(of(world, from, level), world, target) != null;
    }

    /** What reaching {@code to} from the holder at {@code from} does, as the client previews it. */
    public enum Outcome { LINK, ERASE, NOTHING }

    public static Outcome outcome(World world, @Nullable BlockPos from, int level, BlockPos to) {
        if (from == null || from.equals(to)) return Outcome.NOTHING;
        BrushLinkable kind = kindFor(of(world, from, level), world, to);
        if (kind == null) return Outcome.NOTHING;
        return kind.linked(world, to) ? Outcome.ERASE : Outcome.LINK;
    }

    /**
     * The kinds of links of the holders of the loaded chunks within {@code radius} of {@code center} (at {@code level}):
     * the brush overlay draws those the board view does not.
     */
    public static List<BrushLinkable> around(World world, Vec3d center, double radius, int level) {
        List<BrushLinkable> kinds = new ArrayList<>();
        int minX = MathHelper.floor(center.x - radius), maxX = MathHelper.floor(center.x + radius);
        int minZ = MathHelper.floor(center.z - radius), maxZ = MathHelper.floor(center.z + radius);
        double radius2 = radius * radius;
        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof CartridgeContainerBlockEntity) && !(blockEntity instanceof PartyControllerEntity)) continue;
                    if (Vec3d.ofCenter(blockEntity.getPos()).squaredDistanceTo(center) > radius2) continue;
                    kinds.addAll(of(world, blockEntity.getPos(), level));
                }
            }
        }
        return kinds;
    }

    // ---------------------------------------------------------------- what the built-in kinds need

    static boolean isBoardHolder(BlockEntity blockEntity) {
        return blockEntity instanceof BoardSpaceBlockEntity || blockEntity instanceof BoardSpaceRedstoneRouterBlockEntity;
    }

    /** The holders whose Inventory Cartridge's containers mean nothing (a router routes power, a Hop Switch switches blocks). */
    static boolean usesContainers(BlockEntity blockEntity) {
        return !(blockEntity instanceof BoardSpaceRedstoneRouterBlockEntity) && !(blockEntity instanceof HopSwitchBlockEntity);
    }

    static boolean isCartridge(ItemStack stack) {
        return stack.getItem() instanceof CartridgeItem;
    }

    /** A cartridge linked to containers (Inventory, Trichaudron). */
    static boolean isInventoryCartridge(ItemStack stack) {
        return CartridgeContainers.linksContainers(stack);
    }

    static boolean isShopCartridge(ItemStack stack) {
        return stack.getItem() instanceof ShopCartridgeItem;
    }
}
