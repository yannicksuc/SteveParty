package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ItemStackBackedInventory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGamesCatalogueScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.CURRENT_MINIGAME;
import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

public class MiniGamesCatalogueItem extends Item {
    public MiniGamesCatalogueItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            return ActionResult.PASS;
        }

        // Prevent opening the screen if the player is targeting a block
        if (isTargetingBlock(player)) {
            return ActionResult.PASS;
        }

        // Open the mini-game screen
        if (player instanceof ServerPlayerEntity serverPlayer) {
            openInventoryScreen(serverPlayer, hand);
        }

        return ActionResult.SUCCESS;
    }

    public static void openInventoryScreen(ServerPlayerEntity player) {
        openInventoryScreen(player, Hand.MAIN_HAND);
    }

    public static void openInventoryScreen(ServerPlayerEntity player, Hand hand) {
        ItemStack catalogue = player.getStackInHand(hand);
        if (catalogue.isEmpty() || !(catalogue.getItem() instanceof MiniGamesCatalogueItem)) return;
        ItemStackBackedInventory inventory = InventoryComponent.getInventoryFromStack(catalogue, MiniGamesCatalogueScreenHandler.SIZE);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory1, playerEntity) -> new MiniGamesCatalogueScreenHandler(syncId, inventory1, inventory),
                Text.translatable("title.steveparty.mini_game_catalogue")
        ));
    }
    public static List<ItemStack> getStoredPages(ItemStack catalogue) {
        if (catalogue.contains(ModComponents.INVENTORY_COMPONENT)) {
            InventoryComponent inventory = catalogue.get(ModComponents.INVENTORY_COMPONENT);
            if (inventory != null) {
                // Copies: callers can't alter the stored component
                return inventory.getItems();
            }
        }
        return List.of();
    }


    public static void setCurrentMiniGamePage(ItemStack catalogue, ItemStack miniGamePage) {
        catalogue.set(CURRENT_MINIGAME, miniGamePage);
    }


    public static ItemStack getCurrentMiniGame(ItemStack catalogue) {
        return catalogue.getOrDefault(CURRENT_MINIGAME, ItemStack.EMPTY);
    }

    public static void setCurrentMiniGameTeamDisposition(ItemStack catalogue, TeamDisposition chosenDisposition) {
        catalogue.set(ModComponents.TEAM_DISPOSITION, chosenDisposition);
    }

    public static TeamDisposition getCurrentMiniGameTeamDisposition(ItemStack catalogue) {
        return catalogue.getOrDefault(ModComponents.TEAM_DISPOSITION, null);
    }
}
