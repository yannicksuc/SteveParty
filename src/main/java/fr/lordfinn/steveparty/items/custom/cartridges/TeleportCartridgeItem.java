package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportTargetsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

/**
 * « Cartouche Téléportation »: a token landing on its tile is sent to one of its arrival spaces (warp pipe). The
 * arrivals are linked with the Wrench's Teleport mode, apart from the path links. Right click in the air: random pick or
 * in turn; sneaking: whether the arrival counts as a landing there.
 */
public class TeleportCartridgeItem extends CartridgeItem {
    public TeleportCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_TELEPORT;
    }

    public static TeleportTargetsComponent settings(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TELEPORT_TARGETS, TeleportTargetsComponent.DEFAULT);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (isTargetingBlock(player)) return super.use(world, player, hand);
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack stack = player.getStackInHand(hand);
        TeleportTargetsComponent settings = settings(stack);
        Text message;
        if (player.isSneaking()) {
            settings = settings.withLandOnTarget(!settings.landOnTarget());
            message = Text.translatable("message.steveparty.teleport_cartridge.arrival", arrivalText(settings));
        } else {
            settings = settings.withCycle(!settings.cycle());
            message = Text.translatable("message.steveparty.teleport_cartridge.pick", pickText(settings));
        }
        stack.set(ModComponents.TELEPORT_TARGETS, settings);
        player.sendMessage(message, true);
        world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.5F, 1.4F);
        return ActionResult.SUCCESS;
    }

    public static Text pickText(TeleportTargetsComponent settings) {
        return Text.translatable(settings.cycle() ? "tooltip.steveparty.teleport_cartridge.pick.cycle"
                : "tooltip.steveparty.teleport_cartridge.pick.random").formatted(Formatting.LIGHT_PURPLE);
    }

    public static Text arrivalText(TeleportTargetsComponent settings) {
        return Text.translatable(settings.landOnTarget() ? "tooltip.steveparty.teleport_cartridge.arrival.land"
                : "tooltip.steveparty.teleport_cartridge.arrival.end").formatted(Formatting.LIGHT_PURPLE);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        TeleportTargetsComponent settings = settings(stack);
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.teleport_cartridge.link"), Formatting.DARK_GRAY);
        if (settings.targets().isEmpty()) {
            tooltip.add(Text.translatable("tooltip.steveparty.teleport_cartridge.no_target").formatted(Formatting.RED, Formatting.ITALIC));
        } else {
            tooltip.add(Text.translatable("tooltip.steveparty.teleport_cartridge.targets", settings.targets().size()).formatted(Formatting.LIGHT_PURPLE));
            for (BlockPos target : settings.targets()) tooltip.add(Text.literal(" ").append(BoardText.pos(target)).formatted(Formatting.WHITE));
        }
        tooltip.add(Text.translatable("tooltip.steveparty.teleport_cartridge.pick", pickText(settings)).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.teleport_cartridge.arrival", arrivalText(settings)).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.teleport_cartridge.controls").formatted(Formatting.DARK_GRAY));
    }
}
