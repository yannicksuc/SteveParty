package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileBehaviorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.TileBehaviorComponent.DEFAULT_TILE_BEHAVIOR;

public class TileBehavior extends Item {
    public TileBehavior(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        BlockPos clickedPos = context.getBlockPos();
        BlockPos blockAbove = clickedPos.up();

        ItemStack stack = context.getStack();
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
        // Get or create the TileBehaviorComponent
        TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, DEFAULT_TILE_BEHAVIOR);

        // Check if clickedPos or blockAbove is already in destinations
        List<BlockPos> destinations = new ArrayList<>(component.destinations());
        if (destinations.contains(clickedPos) || destinations.contains(blockAbove)) {
            // Remove the block position if already exists
            destinations.remove(clickedPos);
            destinations.remove(blockAbove);
            player.sendMessage(Text.translatable("message.steveparty.removed_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
        } else {
            // Add the block position
            destinations.add(clickedPos);
            player.sendMessage(Text.translatable("message.steveparty.added_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
        }

        TileBehaviorComponent updatedComponent = new TileBehaviorComponent(destinations, component.tileType());

        stack.set(ModComponents.TILE_BEHAVIOR_COMPONENT, updatedComponent);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, DEFAULT_TILE_BEHAVIOR);
        List<BlockPos> destinations = component.destinations();

        if (!destinations.isEmpty()) {
            // Add a heading for the destinations with a different color
            tooltip.add(Text.literal("Destinations:")
                    .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true)));

            for (BlockPos pos : destinations) {
                // Format each BlockPos with its coordinates in a distinct color
                tooltip.add(Text.literal(String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()))
                        .setStyle(Style.EMPTY.withColor(Formatting.GREEN)));
            }
        } else {
            // Display a message when there are no destinations
            tooltip.add(Text.literal("No destinations set.")
                    .setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(true)));
        }
    }
}
