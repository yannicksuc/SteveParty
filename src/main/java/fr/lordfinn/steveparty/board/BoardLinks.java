package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import net.minecraft.block.entity.BlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading and writing the links between board spaces. The save format does not change: the links of a board space
 * are the absolute positions of the {@code DESTINATIONS_COMPONENT} of its cartridge (the active one, or a chosen slot
 * of a 16-slot board space); everything here is a layer of tools above it.
 */
public final class BoardLinks {
    /** Colour of the link trail particles. */
    public static final int LINK_COLOR = 0x4CFF4C;
    public static final int CUT_COLOR = 0xFF4040;

    private BoardLinks() {
    }

    public static String worldName(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    /**
     * The board space or router at {@code pos} (a part of a large tile stands for its tile): the cartridge containers
     * whose links the Tile Linker Brush paints. Null for anything else.
     */
    public static @Nullable CartridgeContainerBlockEntity container(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(BoardSpaces.resolve(world, pos));
        if (blockEntity instanceof BoardSpaceBlockEntity || blockEntity instanceof BoardSpaceRedstoneRouterBlockEntity) {
            return (CartridgeContainerBlockEntity) blockEntity;
        }
        return null;
    }

    public static boolean isBoardSpace(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof ABoardSpaceBlock && world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity;
    }

    /**
     * The slot whose cartridge holds the links edited on {@code container}: {@code requested} on a board space with
     * several slots (if valid), else the active one (board spaces) or the only one (routers).
     */
    public static int slotOf(CartridgeContainerBlockEntity container, int requested) {
        if (container instanceof BoardSpaceBlockEntity boardSpace) {
            return requested >= 0 && requested < boardSpace.size() ? requested : boardSpace.getActiveSlot();
        }
        return 0;
    }

    public static ItemStack cartridge(CartridgeContainerBlockEntity container, int slot) {
        return container.getStack(slot);
    }

    /** The links held by the cartridge in {@code slot} (empty without cartridge). */
    public static List<BlockPos> links(CartridgeContainerBlockEntity container, int slot) {
        return links(container.getStack(slot));
    }

    public static List<BlockPos> links(ItemStack cartridge) {
        if (cartridge.isEmpty() || !(cartridge.getItem() instanceof CartridgeItem)) return List.of();
        return cartridge.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations();
    }

    /** Number of links of {@code links} leading to a board space. */
    public static int boardSpaceLinks(World world, List<BlockPos> links) {
        int count = 0;
        for (BlockPos link : links) if (isBoardSpace(world, link)) count++;
        return count;
    }

    /**
     * The dangling links around {@code center}: those of the board spaces and routers of the loaded chunks within
     * {@code radius} blocks (in the slot of {@code level}, see {@link #slotOf}) leading to a loaded cell without any
     * board space (a removed tile, or a cell planned with the brush). Each such cell, with the spaces linked to it.
     * Same answer on both sides: the brush shows them and aims at them as at tiles.
     */
    public static Map<BlockPos, List<BlockPos>> dangling(World world, Vec3d center, double radius, int level) {
        Map<BlockPos, List<BlockPos>> dangling = new LinkedHashMap<>();
        int minX = MathHelper.floor(center.x - radius), maxX = MathHelper.floor(center.x + radius);
        int minZ = MathHelper.floor(center.z - radius), maxZ = MathHelper.floor(center.z + radius);
        double radius2 = radius * radius;
        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof BoardSpaceBlockEntity) && !(blockEntity instanceof BoardSpaceRedstoneRouterBlockEntity)) continue;
                    BlockPos pos = blockEntity.getPos();
                    if (Vec3d.ofCenter(pos).squaredDistanceTo(center) > radius2) continue;
                    CartridgeContainerBlockEntity container = (CartridgeContainerBlockEntity) blockEntity;
                    for (BlockPos target : links(container, slotOf(container, level))) {
                        if (world.getChunkManager().getWorldChunk(ChunkSectionPos.getSectionCoord(target.getX()),
                                ChunkSectionPos.getSectionCoord(target.getZ())) == null || container(world, target) != null) continue;
                        dangling.computeIfAbsent(target, t -> new ArrayList<>()).add(pos.toImmutable());
                    }
                }
            }
        }
        return dangling;
    }

    /**
     * Writes the links of the cartridge in {@code slot}, saves the block entity and sends it to the clients (the board
     * view reads the links from there).
     *
     * @return false if there is no cartridge there
     */
    public static boolean setLinks(World world, CartridgeContainerBlockEntity container, int slot, List<BlockPos> links) {
        ItemStack cartridge = container.getStack(slot);
        if (cartridge.isEmpty() || !(cartridge.getItem() instanceof CartridgeItem)) return false;
        setLinks(cartridge, links, world);
        sync(container);
        return true;
    }

    /**
     * Writes {@code links} on {@code cartridge}; without any, the component goes entirely, so that a cartridge linked
     * then unlinked is the same as a new one again (and stacks with them).
     */
    public static void setLinks(ItemStack cartridge, List<BlockPos> links, World world) {
        if (links.isEmpty()) cartridge.remove(ModComponents.DESTINATIONS_COMPONENT);
        else cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(links), worldName(world)));
    }

    /** Saves {@code container} and sends it to the clients. */
    public static void sync(CartridgeContainerBlockEntity container) {
        if (container instanceof BoardSpaceBlockEntity boardSpace) {
            boardSpace.update();
            return;
        }
        container.markDirty();
        World world = container.getWorld();
        if (world != null && !world.isClient) {
            BlockState state = container.getCachedState();
            world.updateListeners(container.getPos(), state, state, Block.NOTIFY_ALL);
        }
    }

    // ---------------------------------------------------------------- cartridges supplied automatically

    /**
     * The cartridge in {@code slot}, inserting one first if the slot is empty: in creative a new one (of the type held
     * in the off hand, if any, else a plain Cartridge), in survival the one in the off hand, else the first plain
     * Cartridge of the inventory. The inserted cartridge never inherits links from the stack it comes from.
     *
     * @return the cartridge, or an empty stack if none could be found
     */
    public static ItemStack ensureCartridge(PlayerEntity player, CartridgeContainerBlockEntity container, int slot) {
        ItemStack current = container.getStack(slot);
        if (!current.isEmpty()) return current;
        ItemStack source = cartridgeSource(player);
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack inserted = source.copyWithCount(1);
        inserted.remove(ModComponents.DESTINATIONS_COMPONENT);
        if (!player.getAbilities().creativeMode) source.decrement(1);
        container.setStack(slot, inserted);
        sync(container);
        BlockPos pos = container.getPos();
        player.sendMessage(Text.translatable("message.steveparty.wrench.cartridge_stored",
                pos.getX(), pos.getY(), pos.getZ()).append(" (").append(inserted.getName()).append(")"), true);
        linkNearestChest(player, container, slot);
        return container.getStack(slot);
    }

    // ---------------------------------------------------------------- chests of inventory tiles

    /** How far an inventory tile looks for a chest when its cartridge has none. */
    public static final int CHEST_SEARCH_RADIUS = 8;

    /** A chest (any inventory block that is not a cartridge container) at {@code pos}. */
    public static boolean isChest(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof Inventory && !(blockEntity instanceof CartridgeContainerBlockEntity);
    }

    /**
     * An Inventory Cartridge without chest just put in a board space takes the nearest chest within
     * {@link #CHEST_SEARCH_RADIUS} blocks (a click on another chest with the Tile Linker Brush changes it).
     */
    public static void linkNearestChest(PlayerEntity player, CartridgeContainerBlockEntity container, int slot) {
        World world = container.getWorld();
        ItemStack cartridge = container.getStack(slot);
        if (world == null || !CartridgeContainers.linksContainers(cartridge)
                || !CartridgeContainers.isEmpty(cartridge)) return;
        BlockPos center = container.getPos();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(center.add(-CHEST_SEARCH_RADIUS, -4, -CHEST_SEARCH_RADIUS), center.add(CHEST_SEARCH_RADIUS, 4, CHEST_SEARCH_RADIUS))) {
            double distance = pos.getSquaredDistance(center);
            if (distance < best && isChest(world, pos)) {
                best = distance;
                nearest = pos.toImmutable();
            }
        }
        if (nearest == null) return;
        List<GlobalPos> linked = List.of(GlobalPos.create(world.getRegistryKey(), nearest));
        CartridgeContainers.set(cartridge, linked);
        sync(container);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            LinkHistory.record(serverPlayer, new LinkHistory.ChestChange(center.toImmutable(), slot, List.of(), linked));
        }
        player.sendMessage(Text.translatable("message.steveparty.wrench.chest.nearest", BoardText.pos(nearest)), false);
    }

    /**
     * How many cartridges can still be inserted in the tiles linked (see {@link #cartridgeSource}): the off-hand stack,
     * else the inventory's Cartridges of the kind picked on the brush (the plain one by default); -1 for unlimited
     * (creative).
     */
    public static int cartridgesLeft(PlayerEntity player) {
        ItemStack offHand = player.getOffHandStack();
        boolean creative = player.getAbilities().creativeMode;
        if (offHand.getItem() instanceof CartridgeItem) return creative ? -1 : offHand.getCount();
        if (creative) return -1;
        Item kind = cartridgeKind(player);
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(kind)) count += stack.getCount();
        }
        return count;
    }

    /**
     * The stack the next cartridge would come from (the off hand, a new one in creative, the first one of the
     * inventory), or an empty stack. Its kind: the one in the off hand, else the one picked on the Tile Linker Brush in
     * hand, else the plain Cartridge. Same on both sides: the brush HUD shows it.
     */
    public static ItemStack cartridgeSource(PlayerEntity player) {
        ItemStack offHand = player.getOffHandStack();
        if (offHand.getItem() instanceof CartridgeItem) return offHand;
        Item kind = cartridgeKind(player);
        if (player.getAbilities().creativeMode) return new ItemStack(kind);
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(kind)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** The kind of Cartridge put in new tiles: the one picked on the brush in hand, else the plain Cartridge. */
    public static Item cartridgeKind(PlayerEntity player) {
        Item picked = TileLinkerBrush.cartridge(player.getMainHandStack());
        return picked != null ? picked : ModItems.BOARD_SPACE_BEHAVIOR;
    }

    // ---------------------------------------------------------------- orientation

    /** The 8-step rotation (0 = north, 2 = east...) of a tile at {@code from} facing {@code to}, or -1 if above it. */
    public static int rotationToward(World world, BlockPos from, BlockPos to) {
        Vec3d a = BoardSpaces.standPos(world, from), b = BoardSpaces.standPos(world, to);
        double dx = b.x - a.x, dz = b.z - a.z;
        if (dx * dx + dz * dz < 1.0E-4) return -1;
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        return (int) Math.floorMod(Math.round(angle / 45.0), 8L);
    }

    /**
     * Turns the tile at {@code from} toward {@code to}.
     *
     * @return the previous rotation if it changed, else -1
     */
    public static int orient(World world, BlockPos from, BlockPos to) {
        BlockState state = world.getBlockState(from);
        if (!(state.getBlock() instanceof ATileBlock)) return -1;
        int rotation = rotationToward(world, from, to);
        int previous = state.get(ATileBlock.ROTATION_8);
        if (rotation < 0 || rotation == previous) return -1;
        world.setBlockState(from, state.with(ATileBlock.ROTATION_8, rotation), Block.NOTIFY_ALL);
        return previous;
    }

    // ---------------------------------------------------------------- feedback

    /** A trail of particles from {@code from} to {@code to} (seen by everyone around: useful when building together). */
    public static void trail(ServerWorld world, BlockPos from, BlockPos to, int color) {
        Vec3d a = BoardSpaces.standPos(world, from).add(0, 0.25, 0), b = BoardSpaces.standPos(world, to).add(0, 0.25, 0);
        Vec3d step = b.subtract(a);
        int count = Math.max(2, (int) Math.ceil(step.length() / 0.35));
        DustParticleEffect dust = new DustParticleEffect(Vec3d.unpackRgb(color).toVector3f(), 1.1F);
        for (int i = 0; i <= count; i++) {
            Vec3d p = a.add(step.multiply(i / (double) count));
            world.spawnParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
