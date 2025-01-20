package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity.getDestinationsStatus;
import static fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent.DEFAULT_BOARD_SPACE_BEHAVIOR;

public abstract class AbstractBoardSpaceSelectorItem extends Item {
    public AbstractBoardSpaceSelectorItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getHand() == Hand.OFF_HAND) return ActionResult.PASS;
        World world = context.getWorld();
        if (isClientWorld(world)) return ActionResult.PASS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        BlockPos clickedPos = context.getBlockPos();

        ItemStack stack = context.getStack();
        ServerWorld serverWorld = (ServerWorld) world;

        BoardSpaceBehaviorComponent component = getBoardSpaceBehaviorComponent(stack);

        if (isInvalidWorld(component, serverWorld, player)) return ActionResult.PASS;

        return addOrRemoveDestination(component, clickedPos, player, stack, serverWorld) == null ? ActionResult.PASS : ActionResult.SUCCESS;
    }

    public BoardSpaceBehaviorComponent addOrRemoveDestination(BoardSpaceBehaviorComponent component, BlockPos clickedPos, PlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        List<BlockPos> destinations = new ArrayList<>(component.destinations());
        updateDestinations(destinations, clickedPos, player);

        BoardSpaceBehaviorComponent updatedComponent = new BoardSpaceBehaviorComponent(destinations, component.origin(), component.tileType(), getWorldName(serverWorld));
        stack.set(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, updatedComponent);

        return updatedComponent;
    }

    private boolean isClientWorld(World world) {
        return world.isClient;
    }

    protected static String getWorldName(ServerWorld serverWorld) {
        return serverWorld.getRegistryKey().getValue().toString();
    }

    private BoardSpaceBehaviorComponent getBoardSpaceBehaviorComponent(ItemStack stack) {
        return stack.getOrDefault(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, DEFAULT_BOARD_SPACE_BEHAVIOR);
    }

    private boolean isInvalidWorld(BoardSpaceBehaviorComponent component, ServerWorld serverWorld, PlayerEntity player) {
        if (!component.world().isEmpty() && !getWorldName(serverWorld).equals(component.world())) {
            player.sendMessage(Text.translatable("message.steveparty.invalid_world"), true);
            return true;
        }
        return false;
    }

    private void updateDestinations(List<BlockPos> destinations, BlockPos clickedPos, PlayerEntity player) {
        BlockPos blockAbove = clickedPos.add(0,1,0);
        if (destinations.contains(clickedPos) || destinations.contains(blockAbove)) {
            destinations.remove(clickedPos);
            destinations.remove(blockAbove);
            player.sendMessage(Text.translatable("message.steveparty.removed_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
            playCancelSound(clickedPos, player);
        } else {
            destinations.add(clickedPos);
            player.sendMessage(Text.translatable("message.steveparty.added_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
            playSelectSound(clickedPos, player);
        }
    }

    protected static void playSelectSound(BlockPos clickedPos, PlayerEntity player) {
        playSound(clickedPos, player, ModSounds.SELECT_SOUND_EVENT);
    }

    protected static void playCancelSound(BlockPos clickedPos, PlayerEntity player) {
        playSound(clickedPos, player, ModSounds.CANCEL_SOUND_EVENT);
    }

    protected static void playSound(BlockPos clickedPos, PlayerEntity player, SoundEvent category) {
        player.getWorld().playSound(null, clickedPos, category, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        BoardSpaceBehaviorComponent component = getBoardSpaceBehaviorComponent(stack);
        Entity holder = stack.getHolder();
        List<BoardSpaceDestination> tileDestinations = getDestinationsStatus(component.destinations(), holder == null ? null : holder.getWorld());

        if (!tileDestinations.isEmpty()) {
            addTooltipHeading(tooltip, component);
            addDestinationsToTooltip(tooltip, tileDestinations);
        } else {
            addNoDestinationsMessage(tooltip);
        }
    }

    private void addTooltipHeading(List<Text> tooltip, BoardSpaceBehaviorComponent component) {
        tooltip.add(Text.literal("Bound to: ").setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true))
                .append(Text.literal(component.world()).setStyle(Style.EMPTY.withColor(Formatting.WHITE).withBold(false))));
        tooltip.add(Text.literal("Destinations:")
                .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true)));
    }

    private void addDestinationsToTooltip(List<Text> tooltip, List<BoardSpaceDestination> tileDestinations) {
        for (BoardSpaceDestination destination : tileDestinations) {
            BlockPos pos = destination.position();
            tooltip.add(Text.literal(String.format("  - (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()))
                    .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
        }
    }

    private void addNoDestinationsMessage(List<Text> tooltip) {
        tooltip.add(Text.literal("No destinations set.")
                .setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(true)));
    }
}
