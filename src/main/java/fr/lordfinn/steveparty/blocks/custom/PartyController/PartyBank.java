package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.utils.InventoryChain;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The bank of a Party Controller: its own inventory (27 slots, opened from its dashboard, filled and emptied by
 * hoppers) first, then, optional, the containers of the Inventory Cartridge in its bank slot (Gains page), in their
 * order; the cartridge's item slots and transfer mode, for board spaces, play no part here. Everything the party pays
 * (mini-game gains, a star bought, the board spaces without a chest of their own...) is taken from it, the first
 * places first, never created: what is not in it is not paid; what the party takes (a star's price, stakes...) goes
 * in the first place with room, in the same order. Nobody uses it directly: everything goes through the party's one
 * source, {@link PartyResources#of(PartyControllerEntity)} (which may be the « Infinite bank » instead).
 * <p>
 * A bank container is a storage container: a chest (a double chest counts whole, whichever half was chosen, and once
 * even if both halves are in the list), a trapped chest, a barrel or a shulker box; not a hopper, a dropper, a
 * dispenser or a crafter (they move items by themselves). A container is only used while its chunk is loaded: an
 * unloaded one is skipped (nothing is loaded for it, so that no item can be taken twice from a chunk that is not saved
 * yet), as is one that is gone, no storage container, or in a mini-game zone in session; the others pay. A container
 * within two chunks of the controller is always loaded during a mini-game (the controller's chunk is held, with its
 * neighbours).
 */
public final class PartyBank {
    /** What the bank can pay. */
    public enum State {
        /** Its own inventory empty and no container linked (no Inventory Cartridge, or one without containers). */
        NONE,
        /** Its own inventory empty and none of its linked containers is there now (gone, no storage container, or not loaded). */
        MISSING,
        /** Enough for a whole mini-game, over the containers that are there. */
        OK,
        /** Not enough for a whole mini-game. */
        SHORT,
        /** The « Infinite bank » of the controller: it never runs out (its inventory and chests are not used). */
        INFINITE
    }

    /**
     * The bank as the dashboard shows it.
     *
     * @param coins    the party's coins in the containers that are there
     * @param stars    the party's stars in them
     * @param absent   containers of the list that are gone or no storage container (or in a zone in session)
     * @param unloaded containers of the list whose chunk is not loaded (skipped)
     */
    public record Status(State state, int coins, int stars, int absent, int unloaded) {
        public static final Status NONE = new Status(State.NONE, 0, 0, 0, 0);

        public Status(State state, int coins, int stars) {
            this(state, coins, stars, 0, 0);
        }
    }

    /** The containers of a bank cartridge looked at: those that pay, in order, and those skipped. */
    private record Resolved(List<Inventory> paying, int absent, int unloaded) {
    }

    private PartyBank() {
    }

    /** Hoppers (and any Fabric item storage user) fill and empty the controller's own bank, from every side. */
    public static void initialize() {
        ItemStorage.SIDED.registerForBlockEntity((controller, side) -> InventoryStorage.of(controller.getBankItems(), side),
                ModBlockEntities.PARTY_CONTROLLER_ENTITY);
    }

    /**
     * The places of {@code controller}'s bank that are there now, in order: its own inventory, then the containers of
     * its bank cartridge that pay (loaded, still storage containers, not in a zone in session).
     */
    public static List<Inventory> places(PartyControllerEntity controller) {
        List<Inventory> places = new ArrayList<>();
        places.add(controller.getBankItems());
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        if (server != null) places.addAll(resolve(server, controller.getBank()).paying());
        return places;
    }

    /** The linked containers of a bank cartridge that pay now, end to end in their order (a double chest whole); null for none. */
    public static @Nullable Inventory chests(MinecraftServer server, ItemStack cartridge) {
        List<Inventory> paying = resolve(server, cartridge).paying();
        return paying.isEmpty() ? null : paying.size() == 1 ? paying.getFirst() : new InventoryChain(paying);
    }

    /** Whether a block entity can be a bank: a storage container that does not move items by itself. */
    public static boolean isBank(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof LootableContainerBlockEntity && !(blockEntity instanceof HopperBlockEntity)
                && !(blockEntity instanceof DispenserBlockEntity) && !(blockEntity instanceof CrafterBlockEntity);
    }

    /**
     * The containers of a bank cartridge, in order, empty for none or another item. One saved before the dimension
     * was (by the Wrench) is in the overworld.
     */
    public static List<GlobalPos> targets(ItemStack cartridge) {
        if (!(cartridge.getItem() instanceof InventoryCartridgeItem)) return List.of();
        return CartridgeContainers.of(cartridge, World.OVERWORLD);
    }

    /** The first container of a bank cartridge, null for none. */
    public static @Nullable GlobalPos target(ItemStack cartridge) {
        List<GlobalPos> targets = targets(cartridge);
        return targets.isEmpty() ? null : targets.getFirst();
    }

    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): an unloaded container is skipped, not loaded
    private static Resolved resolve(MinecraftServer server, ItemStack cartridge) {
        List<Inventory> paying = new ArrayList<>();
        List<GlobalPos> seen = new ArrayList<>();
        int absent = 0, unloaded = 0;
        for (GlobalPos target : targets(cartridge)) {
            if (seen.contains(target)) continue;
            ServerWorld world = server.getWorld(target.dimension());
            BlockPos pos = target.pos();
            if (world == null) {
                absent++;
                continue;
            }
            if (!world.isChunkLoaded(pos)) {
                unloaded++;
                continue;
            }
            Inventory inventory = inventory(world, pos);
            if (inventory == null) {
                absent++;
                continue;
            }
            // A double chest is paid from once, whichever halves are in the list
            seen.add(target);
            BlockPos other = CartridgeContainers.otherHalf(world, pos);
            if (other != null) seen.add(GlobalPos.create(target.dimension(), other));
            paying.add(inventory);
        }
        return new Resolved(paying, absent, unloaded);
    }

    /** The container at {@code pos}, as an inventory (a double chest whole), or null if it is no bank. */
    public static @Nullable Inventory inventory(World world, BlockPos pos) {
        // in a mini-game zone in session or being put back: what it pays would come back with the zone
        if (ZoneBubbles.isInZone(world, pos)) return null;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!isBank(blockEntity)) return null;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            Inventory whole = ChestBlock.getInventory(chest, state, world, pos, true);
            if (whole != null) return whole;
        }
        return (Inventory) blockEntity;
    }

    /**
     * What one mini-game pays at most for {@code players} players: the four places to the first ones, the
     * participants' gain to the others.
     */
    public static int need(MiniGameGains gains, PartyCurrency currency, int players) {
        int need = 0;
        for (int place = 1; place <= players; place++) need += gains.forPlace(currency, place);
        return need;
    }

    /**
     * The bank of a controller: the coins and stars over all its containers that are there, whether that pays a whole
     * mini-game for {@code players} players, and how many containers are skipped (gone, not loaded).
     */
    public static Status status(PartyControllerEntity controller, MinecraftServer server, int players) {
        if (controller.isInfiniteBank()) return new Status(State.INFINITE, 0, 0);
        Resolved resolved = resolve(server, controller.getBank());
        // Its own inventory always pays; empty with no chest linked: none; its linked chests all missing: said
        if (controller.getBankItems().isEmpty() && resolved.paying().isEmpty()) {
            return targets(controller.getBank()).isEmpty() ? Status.NONE
                    : new Status(State.MISSING, 0, 0, resolved.absent(), resolved.unloaded());
        }
        List<Inventory> places = new ArrayList<>();
        places.add(controller.getBankItems());
        places.addAll(resolved.paying());
        Inventory inventory = new InventoryChain(places);
        int coins = InventoryUtils.count(inventory, controller.getCurrency(PartyCurrency.COIN));
        int stars = InventoryUtils.count(inventory, controller.getCurrency(PartyCurrency.STAR));
        MiniGameGains gains = controller.getGains();
        boolean enough = coins >= need(gains, PartyCurrency.COIN, players) && stars >= need(gains, PartyCurrency.STAR, players);
        return new Status(enough ? State.OK : State.SHORT, coins, stars, resolved.absent(), resolved.unloaded());
    }
}
