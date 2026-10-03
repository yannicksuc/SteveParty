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
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;

import java.util.*;

import static fr.lordfinn.steveparty.components.DestinationsComponent.DEFAULT;

public class DestinationsRenderer {

    private static final Map<BlockPos, GlowingCuboidRenderer.GradientType> DESTINATIONS = new HashMap<>();
    private static final ItemStack[] LAST_HELD_ITEM_STACKS = {ItemStack.EMPTY, ItemStack.EMPTY}; // Index 0: main hand, Index 1: offhand
    private static final List<Hand> HANDS = List.of(Hand.MAIN_HAND, Hand.OFF_HAND);

    public static void initialize() {
        // In the world's main pass, like the Wrench's overlay: at LAST the highlights were drawn after it, with a view
        // that no longer matched the world's (they lagged behind the blocks when walking or jumping)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            if (MinecraftClient.getInstance().player == null || context.matrixStack() == null) {
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

            if (!getDestinations().isEmpty()) renderDestinations(context);
            renderPagePipes(context);
        });
    }

    /** The linked pipe shown from the page's editor: it blinks until then (world time). */
    private static @Nullable GlobalPos located;
    private static long locatedUntil;

    /** Makes the highlight of a pipe linked to the page in hand blink for {@code ticks}, in another colour. */
    public static void locate(GlobalPos pipe, int ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        located = pipe;
        locatedUntil = client.world.getTime() + ticks;
    }

    /**
     * The pipes linked to the mini-game page in hand, highlighted like the destinations of a cartridge. A page only
     * carries its id: its pipes come from what this client knows of the page (asked to the server when needed, kept
     * up to date by it), so they are read every frame rather than when the held item changes.
     */
    private static void renderPagePipes(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        if (client.player == null || client.world == null) return;
        UUID page = MiniGamePages.idOf(client.player.getMainHandStack());
        if (page == null) page = MiniGamePages.idOf(client.player.getOffHandStack());
        MiniGamePageData data = page == null ? null : MiniGamePageClient.page(page);
        long time = client.world.getTime();
        if (located != null && time >= locatedUntil) located = null;
        if (data == null || (data.pipeLinks().isEmpty() && data.podiumLinks().isEmpty())) return;
        // Its podiums (the whole column) and its counters, like its pipes
        for (MiniGamePodiumLink link : data.podiumLinks()) {
            if (!link.pos().dimension().equals(client.world.getRegistryKey())) continue;
            BlockPos from = link.pos().pos(), to = from;
            if (link.kind() == MiniGamePodiumLink.Kind.PODIUM && PodiumBlock.isPodium(client.world.getBlockState(from))) {
                from = PodiumBlock.bottomOf(client.world, from);
                to = PodiumBlock.topOf(client.world, from);
            }
            boolean blink = link.pos().equals(located);
            boolean lit = time / 4 % 2 == 0;
            for (BlockPos pos = from; pos.getY() <= to.getY(); pos = pos.up()) {
                if (blink) GlowingCuboidRenderer.drawBlockBox(context.matrixStack(), vertexConsumers, pos, 1F, lit ? 1F : 0.2F, lit ? 1F : 0.6F, 0.75F);
                else GlowingCuboidRenderer.renderCuboids(context.matrixStack(), vertexConsumers, pos, GlowingCuboidRenderer.GradientType.RAINBOW);
            }
        }
        for (MiniGamePipeLink link : data.pipeLinks()) {
            if (!link.mouth().dimension().equals(client.world.getRegistryKey())) continue;
            if (link.mouth().equals(located)) {
                // The pipe looked for: white and pink in turn
                boolean lit = time / 4 % 2 == 0;
                GlowingCuboidRenderer.drawBlockBox(context.matrixStack(), vertexConsumers, link.mouth().pos(), 1F, lit ? 1F : 0.2F, lit ? 1F : 0.6F, 0.75F);
            } else {
                GlowingCuboidRenderer.renderCuboids(context.matrixStack(), vertexConsumers, link.mouth().pos(), GlowingCuboidRenderer.GradientType.RAINBOW);
            }
        }
        // With the world's projection, like the destinations
        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(RenderLayer.getDebugFilledBox());
    }

    private static boolean isHeldStackChanged(ItemStack heldStack, Hand hand) {
        return !heldStack.equals(getLastHeldItemStack(hand));
    }

    private static void handleHeldStackChange(ItemStack heldStack, Hand hand) {
        setLastHeldItemStack(heldStack, hand);
        if (hand == Hand.MAIN_HAND && heldStack.getItem() instanceof AbstractDestinationsSelectorItem) {
            DestinationsComponent component = heldStack.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DEFAULT);
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
        VertexConsumerProvider.Immediate vertexConsumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        getDestinations().forEach((pos, gradientType) -> GlowingCuboidRenderer.renderCuboids(matrixStack, vertexConsumers, pos, gradientType));
        // Draw now, with the world's projection: left in the shared buffers, they were drawn with the hand's
        // (its bobbing and sway when walking, running or jumping), off the blocks they mark
        vertexConsumers.draw(RenderLayer.getDebugFilledBox());
    }

    public static void addDestination(BlockPos pos, GlowingCuboidRenderer.GradientType gradientType) {
        DESTINATIONS.putIfAbsent(pos, gradientType);
    }

    public static void clearDestinations() {
        DESTINATIONS.clear();
    }

    /** Disconnected: nothing of the last world stays highlighted or blinking in the next one. */
    public static void clear() {
        DESTINATIONS.clear();
        LAST_HELD_ITEM_STACKS[0] = ItemStack.EMPTY;
        LAST_HELD_ITEM_STACKS[1] = ItemStack.EMPTY;
        located = null;
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
