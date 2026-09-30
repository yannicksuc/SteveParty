package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import net.minecraft.util.Formatting;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Teleport Cartridge (see {@link TileTeleport}): a token landing here is warped to another Teleport tile
 * of its network, and the turn goes on once it has reappeared there, or once it has landed one space further (the
 * cartridge's "move on" option). Going over the tile does nothing.
 * <p>
 * Right click with an empty hand: the cartridge's menu (network, arrival). A dye of a network's colour switches the
 * tile to that network.
 */
public class TeleportTileBehavior extends ABoardSpaceBehavior {

    public TeleportTileBehavior() {
        super(BoardSpaceType.TILE_TELEPORT);
    }

    /** The turn goes on once the token has reappeared on the arrival (or has been pushed one space on). */
    @Override
    public boolean keepsTurn(MobEntity token) {
        return TileTeleport.isTeleporting(token);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     @Nullable PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        ItemStack cartridge = boardSpaceEntity.getActiveCartridgeItemStack();
        BlockPos target = TileTeleport.pick(serverWorld, boardSpaceEntity, cartridge);
        if (target == null) {
            // Alone in its network: an ordinary space, the party goes on right away (keepsTurn false)
            TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, TileFeedback.Landing.DEFAULT,
                    TileFeedback.Landing.DEFAULT.noticeKey());
            return;
        }
        // The notice « 🌀 X est téléporté ! » and the whirl
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, TileFeedback.Landing.TELEPORT,
                TileFeedback.Landing.TELEPORT.noticeKey());
        TeleportSettingsComponent settings = TileTeleport.settings(cartridge);
        PartyStep step = partyController == null ? null : partyController.getPartyData().getCurrentStep();
        // No second move during the warp (a dice rolled meanwhile would move it again)
        if (token instanceof TokenizedEntityInterface tokenized) {
            tokenized.steveparty$setStatus(TokenStatus.clearStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE));
        }
        TileTeleport.teleport(serverWorld, token, pos, target, settings.network().color(), () -> {
            // Pushed one space on: the turn goes on when it lands there (that space's landing, see BoardSpaceBlockEntity)
            if (settings.push() && !token.isRemoved() && TileTeleport.push(serverWorld, token, target, settings.pushTriggers())) return;
            if (partyController != null && !partyController.isRemoved() && step != null
                    && partyController.getPartyData().getCurrentStep() == step) {
                partyController.nextStep();
            }
        });
    }

    /** The teleport jingle and notice when it has somewhere to send the token, else a plain landing. */
    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        World world = boardSpaceEntity.getWorld();
        return world != null && !TileTeleport.partners(world, boardSpaceEntity.getPos()).isEmpty()
                ? TileFeedback.Landing.TELEPORT : TileFeedback.Landing.DEFAULT;
    }

    /** Always its network's colour. */
    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        setColor(boardSpaceBlockEntity, TileTeleport.settings(stack).network().color());
    }

    /** Empty hand: the tile's interface with its cartridge's menu, for a player who may edit the tile. */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.SUCCESS;
        if (!TeleportCartridgeItem.mayEdit(serverPlayer, pos)) {
            serverPlayer.sendMessage(Text.translatable("message.steveparty.cartridge_menu.not_allowed").formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        BlockState tileState = world.getBlockState(pos);
        if (tileState.getBlock() instanceof CartridgeContainer container) container.openContainerScreen(tileState, world, pos, player);
        return ActionResult.SUCCESS;
    }

    /** A dye of one of the networks' colours switches the tile to that network (not used up); other dyes do nothing. */
    @Override
    public ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (stack == null || !(stack.getItem() instanceof DyeItem dye)) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.SUCCESS;
        TeleportNetwork network = TeleportNetwork.ofDye(dye.getColor());
        if (network == null) {
            serverPlayer.sendMessage(Text.translatable("message.steveparty.teleport_cartridge.dye_none"), true);
            return ActionResult.SUCCESS;
        }
        BoardSpaceBlockEntity tile = getTileEntity(world, pos);
        if (tile == null || !TeleportCartridgeItem.mayEdit(serverPlayer, pos)) return ActionResult.SUCCESS;
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof TeleportCartridgeItem)) return ActionResult.SUCCESS;
        TeleportCartridgeItem.apply(tile, cartridge, TileTeleport.settings(cartridge).withNetwork(network));
        serverPlayer.sendMessage(Text.translatable("message.steveparty.teleport_cartridge.network", network.displayName()), true);
        world.playSound(null, pos, SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        return ActionResult.SUCCESS;
    }
}
