package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.ToIntFunction;

import static fr.lordfinn.steveparty.components.ModComponents.*;

public class InventoryInteractorTileBehavior extends ABoardSpaceBehavior {

    public final static int NEUTRAL_COLOR = 0x951CAE;
    public final static int GOOD_COLOR = 0x0083DF;
    public final static int BAD_COLOR = 0xC41C24;

    public InventoryInteractorTileBehavior() {
        super(BoardSpaceType.TILE_INVENTORY_INTERACTOR);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        if (TileBlock.getBoardSpaceEntity(world, pos) instanceof BoardSpaceBlockEntity tileEntity &&
                tileEntity.getActiveCartridgeItemStack() instanceof ItemStack itemStack &&
                itemStack.getOrDefault(INVENTORY_COMPONENT, null) instanceof InventoryComponent cartridgeInventory &&
                itemStack.get(INVENTORY_POS) instanceof BlockPos connectedInventoryPos  && world.getBlockEntity(connectedInventoryPos) instanceof Inventory connectedInventory) {

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
            extractMatching(stack, player.getInventory(), toMove -> insertIntoInventory(toMove, connectedInventory));
        } else {
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
}
