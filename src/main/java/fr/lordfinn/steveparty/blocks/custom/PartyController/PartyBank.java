package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.items.custom.ChestCartridgeItem;
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

/**
 * The bank of a Party Controller: the chest its Chest Cartridge points to (the cartridge in the controller's bank
 * slot, Gains page). The gains of the party's mini-games are taken from it, never created: what is not in it is not
 * paid.
 * <p>
 * A bank is a storage container: a chest (a double chest counts whole, whichever half was chosen), a trapped chest, a
 * barrel or a shulker box; not a hopper, a dropper, a dispenser or a crafter (they move items by themselves). It is
 * only used while its chunk is loaded: an unloaded chest counts as missing (nothing is paid, nothing is loaded for it,
 * so that no item can be taken twice from a chunk that is not saved yet). A chest within two chunks of the controller
 * is always loaded during a mini-game (the controller's chunk is held, with its neighbours).
 */
public final class PartyBank {
    /** What the bank can pay. */
    public enum State {
        /** No Chest Cartridge in the controller (or one pointing nowhere). */
        NONE,
        /** The cartridge's chest is gone, is no storage container, or is not loaded. */
        MISSING,
        /** Enough for a whole mini-game. */
        OK,
        /** Not enough for a whole mini-game. */
        SHORT
    }

    /**
     * The bank as the dashboard shows it.
     *
     * @param coins the party's coins in the chest
     * @param stars the party's stars in the chest
     */
    public record Status(State state, int coins, int stars) {
        public static final Status NONE = new Status(State.NONE, 0, 0);
    }

    private PartyBank() {
    }

    /** Whether a block entity can be a bank: a storage container that does not move items by itself. */
    public static boolean isBank(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof LootableContainerBlockEntity && !(blockEntity instanceof HopperBlockEntity)
                && !(blockEntity instanceof DispenserBlockEntity) && !(blockEntity instanceof CrafterBlockEntity);
    }

    /** The chest a cartridge points to, as an inventory (a double chest whole), or null: none, gone, or not loaded. */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): an unloaded chest is not loaded for the bank
    public static @Nullable Inventory inventory(MinecraftServer server, ItemStack cartridge) {
        GlobalPos target = ChestCartridgeItem.target(cartridge);
        if (target == null) return null;
        ServerWorld world = server.getWorld(target.dimension());
        BlockPos pos = target.pos();
        if (world == null || !world.isChunkLoaded(pos)) return null;
        return inventory(world, pos);
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

    /** The bank of a controller, and whether it can pay a whole mini-game for {@code players} players. */
    public static Status status(PartyControllerEntity controller, MinecraftServer server, int players) {
        ItemStack cartridge = controller.getBank();
        if (ChestCartridgeItem.target(cartridge) == null) return Status.NONE;
        Inventory inventory = inventory(server, cartridge);
        if (inventory == null) return new Status(State.MISSING, 0, 0);
        int coins = InventoryUtils.count(inventory, controller.getCurrency(PartyCurrency.COIN));
        int stars = InventoryUtils.count(inventory, controller.getCurrency(PartyCurrency.STAR));
        MiniGameGains gains = controller.getGains();
        boolean enough = coins >= need(gains, PartyCurrency.COIN, players) && stars >= need(gains, PartyCurrency.STAR, players);
        return new Status(enough ? State.OK : State.SHORT, coins, stars);
    }
}
