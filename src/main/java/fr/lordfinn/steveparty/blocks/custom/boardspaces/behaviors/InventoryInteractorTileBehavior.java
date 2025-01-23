package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.Tile;
import fr.lordfinn.steveparty.components.PersistentInventoryComponent;
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

import static fr.lordfinn.steveparty.components.ModComponents.*;

public class InventoryInteractorTileBehavior extends ABoardSpaceBehavior {

    public InventoryInteractorTileBehavior() {
        super(BoardSpaceType.TILE_INVENTORY_INTERACTOR);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        if (Tile.getBoardSpaceEntity(world, pos) instanceof BoardSpaceBlockEntity tileEntity &&
                tileEntity.getActiveTileBehaviorItemStack() instanceof ItemStack itemStack &&
                itemStack.getOrDefault(INVENTORY_CARTRIDGE_COMPONENT, null) instanceof PersistentInventoryComponent cartridgeInventory &&
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
        partyController.nextStep();
    }

    private void actionateAllSlots(PersistentInventoryComponent cartridgeInventory, Inventory connectedInventory, MobEntity token) {
        PlayerEntity player = getPlayerFromToken(token);
        if (player == null) return;

        for (ItemStack stack : cartridgeInventory.getItems()) {
            handleTransfer(stack, connectedInventory, player);
        }
    }

    private void actionateRandomSlot(PersistentInventoryComponent cartridgeInventory, Inventory connectedInventory, MobEntity token) {
        PlayerEntity player = getPlayerFromToken(token);
        if (player == null) return;

        List<ItemStack> items = cartridgeInventory.getItems().stream().filter(stack -> !stack.isEmpty()).toList();
        if (!items.isEmpty()) {
            ItemStack randomStack = items.get(new Random().nextInt(items.size()));
            handleTransfer(randomStack, connectedInventory, player);
        }
    }

    private void actionateCycleSlot(PersistentInventoryComponent cartridgeInventory, Inventory connectedInventory, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity) {
        PlayerEntity player = getPlayerFromToken(token);
        if (player == null) return;

        int cycleIndex = boardSpaceEntity.getCycleIndex();
        List<ItemStack> items = cartridgeInventory.getItems().stream().filter(stack -> !stack.isEmpty()).toList();

        if (!items.isEmpty()) {
            ItemStack cycleStack = items.get(cycleIndex);
            handleTransfer(cycleStack, connectedInventory, player);

            // Met à jour l'indice cyclique
            boardSpaceEntity.setCycleIndex((cycleIndex + 1) % items.size());
        }
    }

    private PlayerEntity getPlayerFromToken(MobEntity token) {
        return token.getWorld().getPlayerByUuid(((TokenizedEntityInterface) token).steveparty$getTokenOwner());
    }

    private void transferItem(ItemStack stack, Inventory sourceInventory, Inventory targetInventory) {
        for (int i = 0; i < sourceInventory.size(); i++) {
            ItemStack sourceStack = sourceInventory.getStack(i);
            if (ItemStack.areItemsEqual(sourceStack, stack)) {
                int transferableAmount = Math.min(sourceStack.getCount(), stack.getCount());
                ItemStack toTransfer = new ItemStack(stack.getItem(), transferableAmount);
                if (insertIntoInventory(toTransfer, targetInventory)) {
                    sourceStack.decrement(transferableAmount);
                }
            }
        }
    }

    private void handleTransfer(ItemStack stack, Inventory connectedInventory, PlayerEntity player) {
        boolean shouldTakeFromPlayer = Boolean.TRUE.equals(stack.get(IS_NEGATIVE));
        if (shouldTakeFromPlayer) {
            transferItem(stack, player.getInventory(), connectedInventory);
        } else {
            transferItem(stack, connectedInventory, player.getInventory());
        }
    }

    private boolean insertIntoInventory(ItemStack stack, Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack existingStack = inventory.getStack(i);

            // Fusionner avec un stack existant
            if (!existingStack.isEmpty() && canCombine(existingStack, stack)) {
                int transferableAmount = Math.min(stack.getCount(), inventory.getMaxCount(stack) - existingStack.getCount());
                if (transferableAmount > 0) {
                    existingStack.increment(transferableAmount);
                    stack.decrement(transferableAmount);
                    inventory.markDirty();
                    if (stack.isEmpty()) {
                        return true; // Tout a été inséré
                    }
                }
            }
        }

        // Trouver un slot vide
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) {
                inventory.setStack(i, stack.copy());
                inventory.markDirty();
                stack.setCount(0);
                return true;
            }
        }

        return false; // Pas assez d'espace
    }

    private boolean canCombine(ItemStack stack1, ItemStack stack2) {
        return stack1.getItem() == stack2.getItem() &&
                ItemStack.areItemsEqual(stack1, stack2);
    }

}
