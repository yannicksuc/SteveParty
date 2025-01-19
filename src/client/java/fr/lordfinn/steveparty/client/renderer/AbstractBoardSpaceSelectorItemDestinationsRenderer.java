package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.AbstractBoardSpaceSelectorItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent.DEFAULT_BOARD_SPACE_BEHAVIOR;


public class AbstractBoardSpaceSelectorItemDestinationsRenderer {

    private static final List<BlockPos> DESTINATIONS = new ArrayList<>();
    private static ItemStack LAST_HELD_ITEM_STACK = ItemStack.EMPTY;

    public static void initialize() {
        WorldRenderEvents.LAST.register(context -> {
            if (MinecraftClient.getInstance().player == null)
                return;

            ItemStack heldStack = MinecraftClient.getInstance().player.getMainHandStack();
            boolean hasHeldStackChanged = !heldStack.equals(getLastHeldItemStack());

            if (hasHeldStackChanged) {

                // Set the last held stack to the current held stack
                setLastHeldItemStack(heldStack);

                // Clear the destinations if the held stack has changed
                clearDestinations();

                // Return if the held stack is not an AbstractBoardSpaceSelectorItem
                if (!(heldStack.getItem() instanceof AbstractBoardSpaceSelectorItem))
                    return;

                // Get the destinations from the held stack if it is an AbstractBoardSpaceSelectorItem
                BoardSpaceBehaviorComponent component = heldStack.getOrDefault(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, DEFAULT_BOARD_SPACE_BEHAVIOR);
                List<BlockPos> destinations = component.destinations();
                if (!destinations.isEmpty() && getDestinations().isEmpty()) {
                    for (BlockPos pos : destinations) {
                        addDestination(pos);
                    }
                }
            }

            if (getDestinations().isEmpty())
                return;

            // Get the matrix stack for rendering
            MatrixStack matrixStack = context.matrixStack();
            VertexConsumerProvider vertexConsumers = context.consumers();

            if (vertexConsumers == null)
                return;

            // Render the cuboids for each destination
            for (BlockPos pos : getDestinations()) {
                GlowingCuboidRenderer.renderCuboids(matrixStack, vertexConsumers, pos);
            }
        });
    }

    public static void addDestination(BlockPos pos) {
        if (!DESTINATIONS.contains(pos)) {
            DESTINATIONS.add(pos);
        }
    }

    public static void clearDestinations() {
        DESTINATIONS.clear();
    }

    public static List<BlockPos> getDestinations() {
        return DESTINATIONS;
    }

    public static ItemStack getLastHeldItemStack() {
        return LAST_HELD_ITEM_STACK;
    }

    public static void setLastHeldItemStack(ItemStack stack) {
        LAST_HELD_ITEM_STACK = stack;
    }
}



