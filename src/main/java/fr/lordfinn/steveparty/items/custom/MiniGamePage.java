package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

public class MiniGamePage extends AbstractDestinationsSelectorItem {
    public MiniGamePage(Settings settings) {
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
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, playerEntity) -> ModScreensHandlers.MINI_GAME_SCREEN_HANDLER.create(syncId, inventory),
                    Text.empty())
            );
        }

        return ActionResult.SUCCESS;
    }
}
