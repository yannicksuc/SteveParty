package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.payloads.custom.OpenTeleportSettingsPayload;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

/**
 * « Cartouche Téléportation »: a token landing on its tile is sent to another Teleport tile of the same network (its
 * colour: violet, green, orange or blue) on the same board, like warp pipes. Its settings are edited in its menu: right
 * click in the air with it, or on its Teleport tile with an empty hand.
 */
public class TeleportCartridgeItem extends CartridgeItem {
    public TeleportCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_TELEPORT;
    }

    public static TeleportSettingsComponent settings(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TELEPORT_SETTINGS, TeleportSettingsComponent.DEFAULT);
    }

    /** A Teleport Cartridge in {@code network} (for the creative tab, the test worlds...). */
    public static ItemStack withNetwork(ItemStack stack, TeleportNetwork network) {
        stack.set(ModComponents.TELEPORT_SETTINGS, settings(stack).withNetwork(network));
        return stack;
    }

    /** Right click in the air: its menu. */
    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (isTargetingBlock(player)) return super.use(world, player, hand);
        if (player instanceof ServerPlayerEntity serverPlayer) openMenu(serverPlayer, null);
        return ActionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- the menu (server side)

    /**
     * May {@code player} change the Teleport Cartridge of the tile at {@code pos} (null: the one in their hand)? Like
     * the other board edits: not a spectator, allowed to change blocks there, and in reach of the tile.
     */
    public static boolean mayEdit(ServerPlayerEntity player, @Nullable BlockPos pos) {
        if (player.isSpectator() || !player.canModifyBlocks()) return false;
        return pos == null || (player.getWorld().canPlayerModifyAt(player, pos) && ScreenHandlerChecks.isInReach(player, pos));
    }

    /** Opens the menu of the Teleport Cartridge of the tile at {@code pos} (null: the one in hand), if allowed. */
    public static void openMenu(ServerPlayerEntity player, @Nullable BlockPos pos) {
        if (!mayEdit(player, pos)) {
            player.sendMessage(Text.translatable("message.steveparty.teleport_cartridge.not_allowed").formatted(Formatting.RED), true);
            return;
        }
        ServerPlayNetworking.send(player, new OpenTeleportSettingsPayload(Optional.ofNullable(pos).map(BlockPos::toImmutable)));
    }

    /** The Teleport Cartridge in {@code player}'s hands (main hand first), or null. */
    public static @Nullable ItemStack inHand(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof TeleportCartridgeItem) return stack;
        }
        return null;
    }

    /**
     * Server side, from the menu: writes {@code settings} in the Teleport Cartridge of the tile at {@code pos} (its
     * active one) or in hand, if {@code player} may.
     *
     * @return true if written
     */
    public static boolean applyFromMenu(ServerPlayerEntity player, @Nullable BlockPos pos, TeleportSettingsComponent settings) {
        if (!mayEdit(player, pos)) return false;
        if (pos == null) {
            ItemStack stack = inHand(player);
            if (stack == null) return false;
            stack.set(ModComponents.TELEPORT_SETTINGS, settings);
            return true;
        }
        if (!(player.getWorld().getBlockEntity(pos) instanceof BoardSpaceBlockEntity tile)) return false;
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof TeleportCartridgeItem)) return false;
        apply(tile, cartridge, settings);
        return true;
    }

    /** Writes the settings of the tile's Teleport Cartridge, then saves and sends the tile (its colour follows). */
    public static void apply(BoardSpaceBlockEntity tile, ItemStack cartridge, TeleportSettingsComponent settings) {
        cartridge.set(ModComponents.TELEPORT_SETTINGS, settings);
        BoardLinks.sync(tile);
    }

    // ---------------------------------------------------------------- tooltip

    private static Text yesNo(boolean yes) {
        return Text.translatable(yes ? "tooltip.steveparty.teleport_cartridge.yes" : "tooltip.steveparty.teleport_cartridge.no");
    }

    /** The settings in plain words, one line each (the tooltip, the menu's summary). */
    public static List<Text> describe(TeleportSettingsComponent settings) {
        List<Text> lines = new java.util.ArrayList<>();
        lines.add(Text.translatable("tooltip.steveparty.teleport_cartridge.network", settings.network().displayName()));
        lines.add(Text.translatable(settings.push() ? "tooltip.steveparty.teleport_cartridge.arrival.push"
                : "tooltip.steveparty.teleport_cartridge.arrival.stay"));
        if (settings.push()) {
            lines.add(Text.translatable("tooltip.steveparty.teleport_cartridge.triggers", yesNo(settings.pushTriggers())));
        }
        lines.add(Text.translatable("tooltip.steveparty.teleport_cartridge.pick", Text.translatable(settings.cycle()
                ? "tooltip.steveparty.teleport_cartridge.pick.cycle" : "tooltip.steveparty.teleport_cartridge.pick.random")));
        return lines;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        for (Text line : describe(settings(stack))) tooltip.add(line.copy().formatted(Formatting.GRAY));
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.teleport_cartridge.controls"), Formatting.DARK_GRAY);
    }
}
