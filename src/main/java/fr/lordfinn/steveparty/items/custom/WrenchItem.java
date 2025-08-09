package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock;
import fr.lordfinn.steveparty.components.BlockOriginComponent;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
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

public class WrenchItem extends AbstractDestinationsSelectorItem implements CartridgeContainerOpener {

    public WrenchItem(Settings settings) {
        super(settings);
    }

    @Override
    public DestinationsComponent addOrRemoveDestination(DestinationsComponent component, BlockPos clickedPos, PlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        BlockOriginComponent originComponent = stack.getOrDefault(ModComponents.BLOCK_ORIGIN_COMPONENT, BlockOriginComponent.DEFAULT_ORIGIN_COMPONENT);
        if (originComponent.origin().equals(BlockOriginComponent.DEFAULT_ORIGIN)) { //Clicked on a new tile not yet bounded
            return tryToBindTileAtPosFromWrench(clickedPos, player, stack, serverWorld);
        } else if (originComponent.origin().equals(clickedPos)) { //User clicked on the same tile already saved unbind it
            unbindTileAtPosFromWrench(clickedPos, (ServerPlayerEntity) player, stack, serverWorld);
            return null;
        }
        //Player want to add or remove a destination to the tile
        DestinationsComponent newComponent = super.addOrRemoveDestination(component, clickedPos, player, stack, serverWorld);
        if (!updateStackAtPos(newComponent, originComponent.origin(), serverWorld)) { //Try to update the tileBehavior at the registered Pos If no destination remove the bind from the wrench
            removeBinding(originComponent.origin(), stack, serverWorld);
            MessageUtils.sendToPlayer((ServerPlayerEntity) player, "The board space has changed and no longer matches the stored configuration in the wrench. It has been automatically unbound.", MessageUtils.MessageType.CHAT);
            return null;
        }
        displayLinks(serverWorld, originComponent);
        return newComponent;
    }

    private @Nullable DestinationsComponent tryToBindTileAtPosFromWrench(BlockPos clickedPos, PlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        BoardSpaceBlockEntity boardSpaceEntity = TileBlock.getBoardSpaceEntity(serverWorld, clickedPos);
        if (boardSpaceEntity == null) return null;

        ItemStack boardSpaceStoredBehavior = getOrCreateFromPlayerTileBehaviorStack(boardSpaceEntity, player);
        if (boardSpaceStoredBehavior == null) return null;

        // Composant comportement
        DestinationsComponent updatedComponent = boardSpaceStoredBehavior.getOrDefault(
                ModComponents.DESTINATIONS_COMPONENT,
                DestinationsComponent.DEFAULT
        );
        updatedComponent = new DestinationsComponent(updatedComponent.destinations(), updatedComponent.world());

        // Composant origine
        BlockOriginComponent originComponent = new BlockOriginComponent(clickedPos, getWorldName(serverWorld));

        // Mise à jour des composants
        stack.set(ModComponents.DESTINATIONS_COMPONENT, updatedComponent);
        stack.set(ModComponents.BLOCK_ORIGIN_COMPONENT, originComponent);

        boardSpaceStoredBehavior.set(ModComponents.DESTINATIONS_COMPONENT, updatedComponent);
        boardSpaceEntity.markDirty();

        // Message utilisateur
        MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.literal(
                "The wrench is now bound to a new board space at position X: " + clickedPos.getX() +
                        ", Y: " + clickedPos.getY() + ", Z: " + clickedPos.getZ() + "."
        ), MessageUtils.MessageType.ACTION_BAR);

        displayLinks(serverWorld, originComponent);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5F, 1.0F);
        return updatedComponent;
    }


    private static void unbindTileAtPosFromWrench(BlockPos clickedPos, ServerPlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        removeBinding(clickedPos, stack, serverWorld);
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
        MessageUtils.sendToPlayer(player, Text.literal("The wrench is no longer bound to the board space behavior stored at position X: "+ clickedPos.getX()+", Y: "+ clickedPos.getY()+", Z: "+ clickedPos.getZ()+"."), MessageUtils.MessageType.ACTION_BAR);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.5F, 1.0F);
    }

    private static void displayLinks(ServerWorld serverWorld, BlockOriginComponent originComponent) {
        BoardSpaceBlockEntity.hideDestinations(serverWorld, originComponent.origin());
        BoardSpaceBlockEntity.searchAndDisplayDestinations(serverWorld, originComponent.origin(), null);
    }

    private static void removeBinding(BlockPos pos, ItemStack stack, ServerWorld serverWorld) {
        BoardSpaceBlockEntity.hideDestinations(serverWorld, pos);
        stack.set(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT);
        stack.set(ModComponents.BLOCK_ORIGIN_COMPONENT, BlockOriginComponent.DEFAULT_ORIGIN_COMPONENT);
    }

    private boolean updateStackAtPos(DestinationsComponent component, BlockPos clickedPos, ServerWorld serverWorld) {
        BoardSpaceBlockEntity boardSpaceEntity = TileBlock.getBoardSpaceEntity(serverWorld, clickedPos);
        if (boardSpaceEntity == null) return false;
        ItemStack boardSpaceStoredBehavior = boardSpaceEntity.getActiveCartridgeItemStack();
        if (boardSpaceStoredBehavior == null) return false;
        boardSpaceStoredBehavior.set(ModComponents.DESTINATIONS_COMPONENT, component);
        boardSpaceEntity.markDirty();
        return true;
    }

    ItemStack getOrCreateFromPlayerTileBehaviorStack(BoardSpaceBlockEntity boardSpaceEntity, PlayerEntity player) {
        ItemStack boardSpaceStoredBehavior = boardSpaceEntity.getActiveCartridgeItemStack();
        if (boardSpaceStoredBehavior == null || boardSpaceStoredBehavior.isEmpty()) {

            //Item Stack doesn't exist in the tile create one by taking it from the offHand
            ItemStack offHandStack = player.getOffHandStack();
            if (!offHandStack.isEmpty() && offHandStack.getItem() instanceof CartridgeItem) {
                ItemStack newBehaviorItem = offHandStack.copyWithCount(1);
                if (!player.isCreative())
                    offHandStack.decrement(1);
                player.setStackInHand(Hand.OFF_HAND, offHandStack);
                boardSpaceEntity.setActiveCartridgeItemStack(newBehaviorItem);
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