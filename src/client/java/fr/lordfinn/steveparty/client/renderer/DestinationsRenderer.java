package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.*;

import static fr.lordfinn.steveparty.components.DestinationsComponent.DEFAULT_BOARD_SPACE_BEHAVIOR;

public class DestinationsRenderer {

    private static final Map<BlockPos, GlowingCuboidRenderer.GradientType> DESTINATIONS = new HashMap<>();
    private static final ItemStack[] LAST_HELD_ITEM_STACKS = {ItemStack.EMPTY, ItemStack.EMPTY}; // Index 0: main hand, Index 1: offhand
    private static final List<Hand> HANDS = List.of(Hand.MAIN_HAND, Hand.OFF_HAND);

    public static void initialize() {
        WorldRenderEvents.LAST.register(context -> {
            if (MinecraftClient.getInstance().player == null) {
                return;
            }

            boolean cleared = false;

            for (Hand hand : HANDS) {
                ItemStack heldStack = MinecraftClient.getInstance().player.getStackInHand(hand);
                if (isHeldStackChanged(heldStack, hand)) {
                    if (!cleared) {
                        clearDestinations();
                        cleared = true;
                    }
                    handleHeldStackChange(heldStack, hand);
                }
            }

            if (getDestinations().isEmpty()) {
                return;
            }

            renderDestinations(context);
        });
    }

    private static boolean isHeldStackChanged(ItemStack heldStack, Hand hand) {
        return !heldStack.equals(getLastHeldItemStack(hand));
    }

    private static void handleHeldStackChange(ItemStack heldStack, Hand hand) {
        setLastHeldItemStack(heldStack, hand);
        if (hand == Hand.MAIN_HAND && heldStack.getItem() instanceof AbstractDestinationsSelectorItem) {
            DestinationsComponent component = heldStack.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DEFAULT_BOARD_SPACE_BEHAVIOR);
            List<BlockPos> destinations = component.destinations();

            if (!destinations.isEmpty()) {
                destinations.forEach(pos -> addDestination(pos, GlowingCuboidRenderer.GradientType.RAINBOW));
            }
        }

        if (hand == Hand.OFF_HAND && heldStack.getItem() instanceof InventoryCartridgeItem) {
            BlockPos savedPos = ((InventoryCartridgeItem) heldStack.getItem()).getSavedInventoryPos(heldStack);

            if (savedPos != null) {
                addDestination(savedPos, GlowingCuboidRenderer.GradientType.SOLID_COLOR);
            }
        }
    }

    private static void renderDestinations(WorldRenderContext context) {
        MatrixStack matrixStack = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();

        if (vertexConsumers != null) {
            getDestinations().forEach((pos, gradientType) -> GlowingCuboidRenderer.renderCuboids(matrixStack, vertexConsumers, pos, gradientType));
        }
    }

    public static void addDestination(BlockPos pos, GlowingCuboidRenderer.GradientType gradientType) {
        DESTINATIONS.putIfAbsent(pos, gradientType);
    }

    public static void clearDestinations() {
        DESTINATIONS.clear();
    }

    public static Map<BlockPos, GlowingCuboidRenderer.GradientType> getDestinations() {
        return DESTINATIONS;
    }

    public static ItemStack getLastHeldItemStack(Hand hand) {
        return LAST_HELD_ITEM_STACKS[hand.ordinal()];
    }

    public static void setLastHeldItemStack(ItemStack stack, Hand hand) {
        LAST_HELD_ITEM_STACKS[hand.ordinal()] = stack;
    }
}
