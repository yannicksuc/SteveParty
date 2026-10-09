package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.item.Items;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.AdvancedTileBlock;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.utils.InventoryChain;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.ToIntFunction;

import static fr.lordfinn.steveparty.components.ModComponents.*;

public class InventoryInteractorTileBehavior extends ABoardSpaceBehavior {

    /** Item tile colours (face and sides): orange while it has nothing to give or take, blue bonus, red malus. */
    public final static int NEUTRAL_COLOR = 0xFF9A1F;
    public final static int GOOD_COLOR = 0x1566E0;
    public final static int BAD_COLOR = 0xD42A2A;

    public InventoryInteractorTileBehavior() {
        super(BoardSpaceType.TILE_INVENTORY_INTERACTOR);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        if (AdvancedTileBlock.getBoardSpaceEntity(world, pos) instanceof BoardSpaceBlockEntity tileEntity &&
                tileEntity.getActiveCartridgeItemStack() instanceof ItemStack itemStack &&
                itemStack.getOrDefault(INVENTORY_COMPONENT, null) instanceof InventoryComponent cartridgeInventory &&
                CartridgeTransfers.getLinkedInventory(world, itemStack) instanceof Inventory connectedInventory) {

            int selectionState = InventoryCartridgeItem.getSelectionState(itemStack);
            switch (selectionState) {
                case 1 -> actionateAllSlots(cartridgeInventory, connectedInventory, token);
                case 2 -> actionateCycleSlot(cartridgeInventory, connectedInventory, token, boardSpaceEntity);
                default -> {
                    actionateRandomSlot(cartridgeInventory, connectedInventory, token);
                }
            }
        }
        super.onDestinationReached(world, pos, token, boardSpaceEntity, partyController);
        // nextStep() is called by BoardSpaceBlockEntity.onDestinationReached (calling it here too skipped a turn)
    }

    private void actionateAllSlots(InventoryComponent cartridgeInventory, Inventory connectedInventory, MobEntity token) {
        PlayerEntity player = getPlayerFromToken(token);
        if (player == null) return;

        for (ItemStack stack : cartridgeInventory.getItems()) {
            if (stack.isEmpty()) continue;
            handleTransfer(stack, connectedInventory, player);
        }
    }

    private void actionateRandomSlot(InventoryComponent cartridgeInventory, Inventory connectedInventory, MobEntity token) {
        PlayerEntity player = getPlayerFromToken(token);
        if (player == null) return;

        List<ItemStack> items = cartridgeInventory.getItems().stream().filter(stack -> !stack.isEmpty()).toList();
        if (!items.isEmpty()) {
            ItemStack randomStack = items.get(new Random().nextInt(items.size()));
            handleTransfer(randomStack, connectedInventory, player);
        }
    }

    private void actionateCycleSlot(InventoryComponent cartridgeInventory, Inventory connectedInventory, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity) {
        PlayerEntity player = getPlayerFromToken(token);
        if (player == null) return;

        List<ItemStack> items = cartridgeInventory.getItems().stream().filter(stack -> !stack.isEmpty()).toList();

        if (!items.isEmpty()) {
            // The cartridge content may have shrunk since the index was stored
            int cycleIndex = Math.floorMod(boardSpaceEntity.getCycleIndex(), items.size());
            ItemStack cycleStack = items.get(cycleIndex);
            handleTransfer(cycleStack, connectedInventory, player);

            // Met à jour l'indice cyclique
            boardSpaceEntity.setCycleIndex((cycleIndex + 1) % items.size());
        }
    }

    private PlayerEntity getPlayerFromToken(MobEntity token) {
        UUID owner = ((TokenizedEntityInterface) token).steveparty$getTokenOwner();
        if (owner == null) return null;
        return token.getWorld().getPlayerByUuid(owner);
    }

    private void handleTransfer(ItemStack stack, Inventory connectedInventory, PlayerEntity player) {
        boolean shouldTakeFromPlayer = Boolean.TRUE.equals(stack.get(IS_NEGATIVE));
        if (shouldTakeFromPlayer) {
            // What does not fit in the connected inventory stays in the player's inventory
            extractMatching(stack, player.getInventory(), toMove -> insertLinked(toMove, connectedInventory));
        } else {
            // The party's coins gained during a turn may be doubled (Double Coins power-up): taken from the same inventory
            ItemStack given = stack.copy();
            given.remove(IS_NEGATIVE);
            int gained = PowerUpService.itemsGained(player, given, stack.getCount());
            if (gained != stack.getCount()) stack = stack.copyWithCount(gained);
            // Power-ups past what the player may carry in the party stay in the chest
            int allowed = PowerUpLimit.allowed(player, given.copyWithCount(stack.getCount()));
            if (allowed < stack.getCount()) {
                PowerUpLimit.tellFull(player);
                if (allowed <= 0) return;
                stack = stack.copyWithCount(allowed);
            }
            // What does not fit in the player's inventory is dropped at the player's feet
            extractMatching(stack, connectedInventory, toMove -> {
                int count = toMove.getCount();
                player.getInventory().offerOrDrop(toMove);
                return count;
            });
            player.getInventory().markDirty();
        }
    }

    /**
     * Takes, in total, at most {@code template.getCount()} items matching {@code template} (item + components,
     * the cartridge-only IS_NEGATIVE flag excepted) out of {@code source}, and hands them to {@code sink}.
     * Only what the sink actually accepted is removed from the source.
     *
     * @param sink receives a copy (with the source components) of the items to move, returns how many it accepted
     * @return the number of items moved
     */
    public static int extractMatching(ItemStack template, Inventory source, ToIntFunction<ItemStack> sink) {
        if (template == null || template.isEmpty()) return 0;
        ItemStack pattern = template.copyWithCount(1);
        pattern.remove(IS_NEGATIVE);
        int remaining = template.getCount();
        int moved = 0;
        for (int i = 0; i < source.size() && remaining > 0; i++) {
            ItemStack sourceStack = source.getStack(i);
            if (sourceStack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(sourceStack, pattern)) continue;
            int wanted = Math.min(remaining, sourceStack.getCount());
            int accepted = Math.min(wanted, sink.applyAsInt(sourceStack.copyWithCount(wanted)));
            if (accepted <= 0) break; // target is full
            source.removeStack(i, accepted);
            remaining -= accepted;
            moved += accepted;
            if (accepted < wanted) break; // target is full
        }
        if (moved > 0) source.markDirty();
        return moved;
    }

    /**
     * Inserts into the containers of a cartridge: in their order, the first filled up before the next one (see
     * {@code CartridgeContainers#insertInOrder}). {@code stack} is decremented by what went in.
     *
     * @return the number of items inserted
     */
    public static int insertLinked(ItemStack stack, Inventory linked) {
        if (linked instanceof InventoryChain chain) {
            return CartridgeContainers.insertInOrder(stack, chain.inventories());
        }
        return insertIntoInventory(stack, linked);
    }

    /**
     * Inserts as much of {@code stack} as possible into {@code inventory} (merging first, then empty slots).
     * {@code stack} is decremented by what was inserted.
     *
     * @return the number of items inserted
     */
    public static int insertIntoInventory(ItemStack stack, Inventory inventory) {
        int initialCount = stack.getCount();
        // Fusionner avec un stack existant
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            ItemStack existingStack = inventory.getStack(i);
            if (existingStack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(existingStack, stack) || !inventory.isValid(i, stack)) continue;
            int max = Math.min(inventory.getMaxCount(stack), existingStack.getMaxCount());
            int transferableAmount = Math.min(stack.getCount(), max - existingStack.getCount());
            if (transferableAmount > 0) {
                existingStack.increment(transferableAmount);
                stack.decrement(transferableAmount);
            }
        }

        // Trouver un slot vide
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            if (inventory.getStack(i).isEmpty() && inventory.isValid(i, stack)) {
                int amount = Math.min(stack.getCount(), inventory.getMaxCount(stack));
                inventory.setStack(i, stack.split(amount));
            }
        }

        int inserted = initialCount - stack.getCount();
        if (inserted > 0) inventory.markDirty();
        return inserted;
    }

    /**
     * In game, each item as what it does: « +3 » given (« +0, empty » once its chests ran out), « -4 » taken; past
     * {@link #LISTED} items they circle the space instead. How it picks only when that changes something (several
     * items). Helmet: how many of each item given its chests hold.
     */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        InventoryComponent content = stack.get(INVENTORY_COMPONENT);
        if (content == null) return;
        List<ItemStack> items = content.getItems().stream().filter(item -> !item.isEmpty()).toList();
        if (items.isEmpty()) return;
        if (items.size() > 1) {
            int mode = InventoryCartridgeItem.getSelectionState(stack);
            info.line(mode == 1 ? TileInfo.Glyph.ALL : mode == 2 ? TileInfo.Glyph.CYCLE : TileInfo.Glyph.DICE,
                    TileInfo.value(TileInfo.line(mode == 1 ? "inventory.all" : mode == 2 ? "inventory.cycle" : "inventory.random")));
        }
        Inventory linked = CartridgeTransfers.getLinkedInventory(world, stack);
        boolean listed = items.size() <= LISTED, gives = false;
        for (ItemStack item : items) {
            boolean taken = Boolean.TRUE.equals(item.get(IS_NEGATIVE));
            int left = taken || linked == null ? 0 : countMatching(item, linked);
            if (!taken) gives = true;
            if (!listed) {
                info.item(item);
            } else if (taken) {
                info.line(item, TileInfo.bad("−" + item.getCount()));
            } else if (linked != null && left >= item.getCount()) {
                info.line(item, TileInfo.good("+" + item.getCount()));
            } else {
                info.line(item, TileInfo.bad(TileInfo.line("inventory.empty")));
            }
            // The stock, for the helmet; an item run out already says so
            if (!taken && linked != null && left >= item.getCount())
                info.detail(item, TileInfo.line("inventory.left", TileInfo.value(left)));
        }
        if (gives && linked == null) info.line(new ItemStack(Items.CHEST), TileInfo.bad(TileInfo.line("inventory.no_chest")));
    }

    /** Up to this many items, each is a line; more circle the space. */
    private static final int LISTED = 4;

    /** How many items matching {@code template} (item and components, the cartridge's IS_NEGATIVE flag aside) {@code inventory} holds. */
    private static int countMatching(ItemStack template, Inventory inventory) {
        ItemStack pattern = template.copyWithCount(1);
        pattern.remove(IS_NEGATIVE);
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack slot = inventory.getStack(i);
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, pattern)) count += slot.getCount();
        }
        return count;
    }

    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        Status status = getStatus(boardSpaceBlockEntity, stack);
        int color = NEUTRAL_COLOR;
        if (status == Status.BAD)
            color = BAD_COLOR;
        else if (status == Status.GOOD)
            color = GOOD_COLOR;
        setColor(boardSpaceBlockEntity, color);
    }

    @Override
    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        Status status = Status.NEUTRAL;
        InventoryComponent inventory = stack.get(INVENTORY_COMPONENT);
        if (inventory != null) {
            ItemStack item = inventory.getStack(0);
            if (item != null && !item.isEmpty()) {
                boolean isNegative = item.getOrDefault(IS_NEGATIVE, false);
                status = isNegative ? Status.BAD : Status.GOOD;
            }
        }
        return status;
    }

    /** Gain (blue) or loss (red) by what the cartridge gives or takes; an empty one: a neutral item tile. */
    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return switch (getStatus(boardSpaceEntity, stack)) {
            case GOOD -> TileFeedback.Landing.GOOD;
            case BAD -> TileFeedback.Landing.BAD;
            case NEUTRAL -> TileFeedback.Landing.ITEM;
        };
    }
}
