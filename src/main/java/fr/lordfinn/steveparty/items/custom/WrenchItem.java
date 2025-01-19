package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.Tile;
import fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.tilebehaviors.BoardSpaceBehaviorItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class WrenchItem extends AbstractBoardSpaceSelectorItem implements TileOpener {

    public WrenchItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceBehaviorComponent addOrRemoveDestination(BoardSpaceBehaviorComponent component, BlockPos clickedPos, PlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        if (!component.isOriginSet()) { //Clicked on a new tile not yet bounded
            return tryToBindTileAtPosFromWrench(clickedPos, player, stack, serverWorld);
        } else if (component.origin().equals(clickedPos)) { //User clicked on the same tile already saved unbind it
            unbindTileAtPosFromWrench(clickedPos, (ServerPlayerEntity) player, stack, serverWorld);
            return null;
        }
        //Player want to add or remove a destination to the tile
        BoardSpaceBehaviorComponent newComponent = super.addOrRemoveDestination(component, clickedPos, player, stack, serverWorld);
        if (!updateStackAtPos(newComponent, component.origin(), serverWorld)) { //Try to update the tileBehavior at the registered Pos If no destination remove the bind from the wrench
            removeBinding(component.origin(), stack, serverWorld);
            MessageUtils.sendToPlayer((ServerPlayerEntity) player, "The board space has changed and no longer matches the stored configuration in the wrench. It has been automatically unbound.", MessageUtils.MessageType.CHAT);
            return null;
        }
        displayLinks(serverWorld, newComponent);
        return newComponent;
    }

    private @Nullable BoardSpaceBehaviorComponent tryToBindTileAtPosFromWrench(BlockPos clickedPos, PlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        BoardSpaceEntity boardSpaceEntity = Tile.getBoardSpaceEntity(serverWorld, clickedPos);
        if (boardSpaceEntity == null) return null;
        ItemStack boardSpaceStoredBehavior = getOrCreateFromPlayerTileBehaviorStack(boardSpaceEntity, player);
        if (boardSpaceStoredBehavior == null) return null;
        //Get BOARD_SPACE_BEHAVIOR_COMPONENT and add origin
        BoardSpaceBehaviorComponent updatedComponent = boardSpaceStoredBehavior.getOrDefault(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, BoardSpaceBehaviorComponent.DEFAULT_BOARD_SPACE_BEHAVIOR);
        updatedComponent = new BoardSpaceBehaviorComponent(updatedComponent.destinations(), clickedPos, updatedComponent.tileType(), updatedComponent.world());
        //Save the new component to wrench and behavior Stack
        stack.set(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, updatedComponent);
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        boardSpaceStoredBehavior.set(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, updatedComponent);
        MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.literal("The wrench is now bound to a new board space behavior stored at position X: "+ clickedPos.getX()+", Y: "+ clickedPos.getY()+", Z: "+ clickedPos.getZ()+"."), MessageUtils.MessageType.ACTION_BAR);
        displayLinks(serverWorld, updatedComponent);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5F, 1.0F);
        return updatedComponent;
    }

    private static void unbindTileAtPosFromWrench(BlockPos clickedPos, ServerPlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        removeBinding(clickedPos, stack, serverWorld);
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
        MessageUtils.sendToPlayer(player, Text.literal("The wrench is no longer bound to the board space behavior stored at position X: "+ clickedPos.getX()+", Y: "+ clickedPos.getY()+", Z: "+ clickedPos.getZ()+"."), MessageUtils.MessageType.ACTION_BAR);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.5F, 1.0F);
    }

    private static void displayLinks(ServerWorld serverWorld, BoardSpaceBehaviorComponent updatedComponent) {
        BoardSpaceEntity.hideDestinations(serverWorld, updatedComponent.origin());
        BoardSpaceEntity.searchAndDisplayDestinations(serverWorld, updatedComponent.origin(), null);
    }

    private static void removeBinding(BlockPos pos, ItemStack stack, ServerWorld serverWorld) {
        BoardSpaceEntity.hideDestinations(serverWorld, pos);
        stack.set(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, BoardSpaceBehaviorComponent.DEFAULT_BOARD_SPACE_BEHAVIOR);
    }

    private boolean updateStackAtPos(BoardSpaceBehaviorComponent component, BlockPos clickedPos, ServerWorld serverWorld) {
        BoardSpaceEntity boardSpaceEntity = Tile.getBoardSpaceEntity(serverWorld, clickedPos);
        if (boardSpaceEntity == null) return false;
        ItemStack boardSpaceStoredBehavior = boardSpaceEntity.getActiveTileBehaviorItemStack();
        if (boardSpaceStoredBehavior == null) return false;
        boardSpaceStoredBehavior.set(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, component);
        return true;
    }

    ItemStack getOrCreateFromPlayerTileBehaviorStack(BoardSpaceEntity boardSpaceEntity, PlayerEntity player) {
        ItemStack boardSpaceStoredBehavior = boardSpaceEntity.getActiveTileBehaviorItemStack();
        if (boardSpaceStoredBehavior == null || boardSpaceStoredBehavior.isEmpty()) {

            //Item Stack doesn't exist in the tile create one by taking it from the offHand
            ItemStack offHandStack = player.getOffHandStack();
            if (!offHandStack.isEmpty() && offHandStack.getItem() instanceof BoardSpaceBehaviorItem) {
                ItemStack newBehaviorItem = offHandStack.copyWithCount(1);
                if (!player.isCreative())
                    offHandStack.decrement(1);
                player.setStackInHand(Hand.OFF_HAND, offHandStack);
                boardSpaceEntity.setActiveTileBehaviorItemStack(newBehaviorItem);
                boardSpaceEntity.markDirty();
                boardSpaceStoredBehavior = newBehaviorItem;
                MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.literal("New space behavior stored at position X: "+boardSpaceEntity.getPos().getX()+", Y: "+boardSpaceEntity.getPos().getY()+", Z: "+boardSpaceEntity.getPos().getZ()+"."), MessageUtils.MessageType.ACTION_BAR);
            } else {
                MessageUtils.sendToPlayer((ServerPlayerEntity) player, "No behavior is assigned to this board space, and it can't be filled from your off-hand.", MessageUtils.MessageType.CHAT);
                return null;
            }
        }
        return boardSpaceStoredBehavior;
    }
}
//, () -> player.isHolding(this) && holders.contains(player.getUuid()), () -> holders.remove(player.getUuid())