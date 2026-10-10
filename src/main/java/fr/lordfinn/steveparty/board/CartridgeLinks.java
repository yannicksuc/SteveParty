package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.HopSwitchBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.switchable.Switchables;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.service.MarkerResidents;
import org.jetbrains.annotations.Nullable;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The kinds of links the mod's cartridges store, as the Tile Linker Brush paints them (see {@link BrushLinks}):
 * <ul>
 *     <li>{@link BoardPaths}: the destinations of a board space's or router's cartridge, to board spaces (the board's
 *     paths; a cartridge is supplied to an empty slot);</li>
 *     <li>{@link Switches}: the destinations of a Hop Switch's cartridge, to the blocks it switches;</li>
 *     <li>{@link Containers}: the containers of an Inventory, Trichaudron or Shop Cartridge (board spaces, Looting Box,
 *     Piggy Bank, the Party Controller's bank), at most {@link CartridgeContainers#MAX};</li>
 *     <li>{@link SpawnPoint}: the Spawn Marker of a mob space's cartridge, where its mob appears (one at most).</li>
 * </ul>
 * The target decides: a board space is a destination, a container a container, a Spawn Marker the spawn point; a
 * target the cartridge takes nothing of is refused with a word (see TileLinkerBrush).
 * Each writes what a click of the cartridge on the target writes, with the same feedback, recorded for undo.
 */
public final class CartridgeLinks {
    /**
     * Colour of the links to a cartridge's storage, its containers or the Party Controller's bank (also the chest
     * trail): the green of the « Storage » line of its menu.
     */
    public static final int CONTAINER_COLOR = 0x7CE06A;
    /** Colour of a router's links to the board spaces it powers: redstone. */
    public static final int ROUTER_COLOR = 0xE03030;
    /** Colour of a Hop Switch's links to the blocks it switches. */
    public static final int SWITCH_COLOR = 0xE070FF;
    /** Colour of the link to a Spawn Marker. */
    public static final int SPAWN_COLOR = 0x40E0D0;

    private CartridgeLinks() {
    }

    static final List<BrushLinks.Provider> PROVIDERS = List.of(
            (world, holder, held, out) -> {
                if (BrushLinks.isBoardHolder(holder)) out.add(new BoardPaths((CartridgeContainerBlockEntity) holder, held.slot()));
            },
            (world, holder, held, out) -> {
                if (holder instanceof HopSwitchBlockEntity && BrushLinks.isCartridge(held.cartridge())) out.add(new Switches(held));
            },
            (world, holder, held, out) -> {
                if (BrushLinks.isInventoryCartridge(held.cartridge()) && BrushLinks.usesContainers(holder)) out.add(new Containers(held));
            },
            (world, holder, held, out) -> {
                if (holder instanceof BoardSpaceBlockEntity && CartridgeSpawnMarker.spawnsMobs(held.cartridge())) out.add(new SpawnPoint(held));
            });

    // ---------------------------------------------------------------- board paths

    /** The links between board spaces: a board space's or a router's cartridge to board spaces. */
    public record BoardPaths(CartridgeContainerBlockEntity container, int slot) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return container.getPos();
        }

        @Override
        public int color() {
            return container instanceof BoardSpaceBlockEntity ? BoardLinks.LINK_COLOR : ROUTER_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return BoardLinks.container(world, target) instanceof BoardSpaceBlockEntity;
        }

        @Override
        public List<BlockPos> targets(World world) {
            return BoardLinks.links(container, slot);
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            BlockPos from = holder();
            if (!WrenchActions.addLink(player, world, from, container, slot, target)) return false;
            WrenchActions.say(player, Text.translatable("message.steveparty.tile_linker_brush.linked", BoardText.pos(from), BoardText.pos(target)));
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, 1.2f);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            WrenchActions.removeLink(player, world, container, slot, target);
            TileLinkerBrush.erased(player, world, holder(), target);
            return true;
        }

        /** A board space's paths are the board view's; a router's are not paths (drawn by the brush overlay). */
        @Override
        public boolean drawnByBoardView() {
            return container instanceof BoardSpaceBlockEntity;
        }
    }

    // ---------------------------------------------------------------- Hop Switch

    /** The blocks a Hop Switch switches: the destinations of its cartridge, added as a click of the cartridge adds them. */
    public record Switches(BrushLinks.Held held) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return held.pos();
        }

        @Override
        public int color() {
            return SWITCH_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return Switchables.isSwitchable(world.getBlockState(target)) && !BrushLinks.isHolder(world, target);
        }

        @Override
        public List<BlockPos> targets(World world) {
            return BoardLinks.links(held.cartridge());
        }

        /** As a click: a destination is also removed by a click on what it stands on. */
        @Override
        public boolean linked(World world, BlockPos target) {
            List<BlockPos> targets = targets(world);
            return targets.contains(target) || targets.contains(target.up());
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            return !linked(world, target) && toggle(player, world, target);
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            return linked(world, target) && toggle(player, world, target);
        }

        private boolean toggle(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            ItemStack cartridge = held.cartridge();
            if (!(cartridge.getItem() instanceof AbstractDestinationsSelectorItem item)) return false;
            DestinationsComponent component = cartridge.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT);
            if (!component.world().isEmpty() && !component.world().equals(BoardLinks.worldName(world))) {
                player.sendMessage(Text.translatable("message.steveparty.invalid_world"), true);
                return false;
            }
            boolean adding = !linked(world, target);
            List<BlockPos> before = List.copyOf(targets(world));
            item.addOrRemoveDestination(component, target, player, cartridge, world);
            held.sync().run();
            LinkHistory.record(player, new LinkHistory.LinksChange(held.pos(), held.slot(), before, List.copyOf(targets(world))));
            BoardLinks.trail(world, held.pos(), target, adding ? SWITCH_COLOR : BoardLinks.CUT_COLOR);
            return true;
        }
    }

    // ---------------------------------------------------------------- Inventory Cartridge

    /** The containers of an Inventory Cartridge, added as a click of the cartridge on them adds them (at most 8). */
    public record Containers(BrushLinks.Held held) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return held.pos();
        }

        @Override
        public int color() {
            return CONTAINER_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return CartridgeContainers.accepts(world, target);
        }

        @Override
        public List<BlockPos> targets(World world) {
            return CartridgeContainers.in(held.cartridge(), world);
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            return !linked(world, target) && toggle(player, world, target);
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            return linked(world, target) && toggle(player, world, target);
        }

        private boolean toggle(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            ItemStack cartridge = held.cartridge();
            List<GlobalPos> before = CartridgeContainers.of(cartridge, world.getRegistryKey());
            CartridgeContainers.Toggle toggle = InventoryCartridgeItem.choose(cartridge, world, target, player);
            if (toggle == CartridgeContainers.Toggle.FULL) return false;
            held.sync().run();
            LinkHistory.record(player, new LinkHistory.ChestChange(held.pos(), held.slot(), before,
                    CartridgeContainers.of(cartridge, world.getRegistryKey())));
            BoardLinks.trail(world, held.pos(), target, toggle == CartridgeContainers.Toggle.ADDED ? CONTAINER_COLOR : BoardLinks.CUT_COLOR);
            return true;
        }
    }

    // ---------------------------------------------------------------- Spawn Marker

    /** The Spawn Marker of a mob space's cartridge: one at most, a new one replaces it. */
    public record SpawnPoint(BrushLinks.Held held) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return held.pos();
        }

        @Override
        public int color() {
            return SPAWN_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return CartridgeSpawnMarker.accepts(world, target);
        }

        @Override
        public List<BlockPos> targets(World world) {
            BlockPos marker = CartridgeSpawnMarker.linked(held.cartridge(), world);
            return marker == null ? List.of() : List.of(marker);
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            if (linked(world, target)) return false;
            set(player, world, target);
            WrenchActions.say(player, Text.translatable("message.steveparty.tile_linker_brush.spawn_marker", BoardText.pos(holder()), BoardText.pos(target)));
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.45f, 1.4f);
            BoardLinks.trail(world, holder(), target, SPAWN_COLOR);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            if (!linked(world, target)) return false;
            set(player, world, null);
            TileLinkerBrush.erased(player, world, holder(), target);
            BoardLinks.trail(world, holder(), target, BoardLinks.CUT_COLOR);
            return true;
        }

        private void set(ServerPlayerEntity player, ServerWorld world, @Nullable BlockPos marker) {
            ItemStack cartridge = held.cartridge();
            GlobalPos before = cartridge.get(ModComponents.SPAWN_MARKER);
            CartridgeSpawnMarker.set(cartridge, world, marker);
            if (marker != null) CartridgeSpawnMarker.own(world, marker, holder());
            held.sync().run();
            LinkHistory.record(player, new LinkHistory.SpawnChange(held.pos(), held.slot(), before, cartridge.get(ModComponents.SPAWN_MARKER)));
            MarkerResidents.refresh(world, before == null ? marker : before.pos());
        }
    }
}
