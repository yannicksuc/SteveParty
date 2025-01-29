package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.screen_handlers.CartridgeInventoryScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.MiniGamesCatalogueScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

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

        // Prevent opening the screen if the player is targeting a block or an entity
        if (isTargetingBlock(player)) {
            return ActionResult.PASS;
        }

        // Open the mini-game screen
        if (player instanceof ServerPlayerEntity serverPlayer) {
            openInventoryScreen(serverPlayer);
        }

        return ActionResult.SUCCESS;
    }

    public static void openInventoryScreen(ServerPlayerEntity player) {
        ItemStack stackMainHand = player.getMainHandStack();
        InventoryComponent inventory = InventoryComponent.getInventoryFromStack(stackMainHand, 91);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory1, playerEntity) -> new MiniGamesCatalogueScreenHandler(syncId, inventory1, inventory),
                Text.empty()
        ));
    }
}
