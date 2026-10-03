package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.utils.InventoryChain;
import fr.lordfinn.steveparty.utils.InventoryUtils;
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
 * The bank of a Party Controller: the containers of the Inventory Cartridge in its bank slot (Gains page), in their
 * order; the cartridge's item slots and transfer mode, for board spaces, play no part here. The gains of the party's
 * mini-games are taken from them, the first ones first, never created: what is not in them is not paid.
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
        /** No Inventory Cartridge in the controller (or one without containers). */
        NONE,
        /** None of its containers is there now (gone, no storage container, or not loaded). */
        MISSING,
        /** Enough for a whole mini-game, over the containers that are there. */
        OK,
        /** Not enough for a whole mini-game. */
        SHORT
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

    /**
     * The containers a cartridge pays from now, end to end in their order (a double chest whole), or null when none
     * is there: none, all gone, or none loaded.
     */
    public static @Nullable Inventory inventory(MinecraftServer server, ItemStack cartridge) {
        List<Inventory> paying = resolve(server, cartridge).paying();
        return paying.isEmpty() ? null : paying.size() == 1 ? paying.getFirst() : new InventoryChain(paying);
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
        ItemStack cartridge = controller.getBank();
        if (targets(cartridge).isEmpty()) return Status.NONE;
        Resolved resolved = resolve(server, cartridge);
        if (resolved.paying().isEmpty()) return new Status(State.MISSING, 0, 0, resolved.absent(), resolved.unloaded());
        Inventory inventory = new InventoryChain(resolved.paying());
        int coins = InventoryUtils.count(inventory, controller.getCurrency(PartyCurrency.COIN));
        int stars = InventoryUtils.count(inventory, controller.getCurrency(PartyCurrency.STAR));
        MiniGameGains gains = controller.getGains();
        boolean enough = coins >= need(gains, PartyCurrency.COIN, players) && stars >= need(gains, PartyCurrency.STAR, players);
        return new Status(enough ? State.OK : State.SHORT, coins, stars, resolved.absent(), resolved.unloaded());
    }
}
