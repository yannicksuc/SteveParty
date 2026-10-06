package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ContainersModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity.getDestinationsStatus;
import static fr.lordfinn.steveparty.components.DestinationsComponent.DEFAULT;
import static fr.lordfinn.steveparty.components.ModComponents.*;

public class InventoryCartridgeItem extends CartridgeItem {

    public InventoryCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_INVENTORY_INTERACTOR;
    }

    /**
     * A click decides by what it hits, the same in either hand:
     * <ul>
     *     <li>a container ({@link CartridgeContainers#accepts}): added to the cartridge's containers, or removed if it
     *     is one; it does not open. With the cartridge in the off hand and nothing in the main hand, too;</li>
     *     <li>a board space or a router, sneaking: added to the destinations, or removed ({@link #useOnBlock}). Not
     *     sneaking, the board space opens its interface, where cartridges are put in, as for every cartridge;</li>
     *     <li>anything else: its menu (see {@link CartridgeItem#useOnBlock}).</li>
     * </ul>
     */
    public static void initialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            // The off hand's cartridge with nothing in the main hand: the main hand's turn would open the container
            if (hand == Hand.MAIN_HAND && stack.isEmpty() && player.getOffHandStack().getItem() instanceof InventoryCartridgeItem) {
                stack = player.getOffHandStack();
            }
            if (!(stack.getItem() instanceof InventoryCartridgeItem) || player.isSpectator()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos().toImmutable();
            if (!CartridgeContainers.accepts(world, pos)) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            choose(stack, world, pos, player);
            return ActionResult.SUCCESS;
        });
    }

    /** Adds the container at {@code pos} to the cartridge's, or removes it if it is one; the player is told. */
    public static CartridgeContainers.Toggle choose(ItemStack stack, World world, BlockPos pos, @Nullable PlayerEntity player) {
        CartridgeContainers.Toggle toggle = CartridgeContainers.toggle(stack, world, pos);
        if (player == null) return toggle;
        int count = CartridgeContainers.of(stack, world.getRegistryKey()).size();
        switch (toggle) {
            case ADDED -> {
                ModSounds.playSelect(world, pos);
                player.sendMessage(Text.translatable("message.steveparty.inventory_cartridge.added", count, pos.getX(), pos.getY(), pos.getZ()), true);
            }
            case REMOVED -> {
                ModSounds.playCancel(world, pos);
                player.sendMessage(Text.translatable("message.steveparty.inventory_cartridge.removed", pos.getX(), pos.getY(), pos.getZ(), count), true);
            }
            case FULL -> {
                ModSounds.playCancel(world, pos);
                player.sendMessage(Text.translatable("message.steveparty.inventory_cartridge.full", CartridgeContainers.MAX), true);
            }
        }
        return toggle;
    }

    /**
     * Reached when the block did not take the click (sneaking, or a block without a use): a board space or a router
     * is added to the destinations (or removed), in either hand; anything else as every cartridge (its menu).
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (fr.lordfinn.steveparty.board.BoardLinks.container(world, context.getBlockPos()) == null) return super.useOnBlock(context);
        return toggleDestination(context);
    }

    /** The position of its first container in its dimension, null for none (see {@link CartridgeContainers}). */
    public static @Nullable BlockPos getSavedInventoryPos(ItemStack stack) {
        List<GlobalPos> containers = CartridgeContainers.of(stack, World.OVERWORLD);
        return containers.isEmpty() ? null : containers.getFirst().pos();
    }

    public static void setSelectionState(ItemStack stack, int state) {
        stack.set(SELECTION_STATE, state);
    }

    public static int getSelectionState(ItemStack stack) {
        int state = stack.getOrDefault(SELECTION_STATE, RANDOM);
        return state >= RANDOM && state <= CYCLE ? state : RANDOM;
    }

    // ---------------------------------------------------------------- the menu

    private static final String K = MENU_KEY + "inventory.";
    public static final int LABEL_COLOR = 0xB95D05;
    /** Selection states: which ghost slots are used when a token lands. */
    public static final int RANDOM = 0, ALL = 1, CYCLE = 2;

    private static final List<CartridgeModule> MODULES = List.of(
            new GhostSlotsModule("items", K + "items"),
            // No title: its hint row says what it is, and it fits beside a tile with all its rows
            new ContainersModule("chests", null),
            new ChoiceModule("mode", K + "mode",
                    List.of(new ChoiceModule.Option(K + "random", -1, "message.steveparty.button_state.random"),
                            new ChoiceModule.Option(K + "all", -1, "message.steveparty.button_state.all"),
                            new ChoiceModule.Option(K + "cycle", -1, "message.steveparty.button_state.cycle")),
                    InventoryCartridgeItem::getSelectionState,
                    (edit, value) -> setSelectionState(edit.stack(), value)));

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return LABEL_COLOR;
    }

    // ========================
    //   TOOLTIP / LORE
    // ========================
    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type); // the stamp, if any
        // --- Controls ---
        tooltip.add(Text.translatable("tooltip.steveparty.controls")
                .setStyle(Style.EMPTY.withBold(true).withColor(0xfcb017)));

        tooltip.add(Text.translatable("tooltip.steveparty.controls.select_container",
                Text.translatable("tooltip.steveparty.controls.container_click")
                        .setStyle(Style.EMPTY.withColor(0xfcb017))));

        tooltip.add(Text.translatable("tooltip.steveparty.controls.select_destination",
                Text.translatable("tooltip.steveparty.controls.board_space_click")
                        .setStyle(Style.EMPTY.withColor(0xfcb017))));

        tooltip.add(Text.translatable("tooltip.steveparty.controls.open_config",
                Text.translatable("tooltip.steveparty.controls.right_air")
                        .setStyle(Style.EMPTY.withColor(0xfcb017))));
        tooltip.add(Text.empty());

        // --- Container info ---
        Entity viewer = stack.getHolder();
        List<GlobalPos> containers = CartridgeContainers.of(stack, viewer == null ? World.OVERWORLD : viewer.getWorld().getRegistryKey());
        if (!containers.isEmpty()) {
            tooltip.add(Text.translatable("tooltip.steveparty.linked_containers", containers.size(), CartridgeContainers.MAX)
                    .setStyle(Style.EMPTY.withColor(0x167abf).withBold(true))); // Aqua
            for (int i = 0; i < containers.size(); i++) {
                BlockPos pos = containers.get(i).pos();
                tooltip.add(Text.translatable("tooltip.steveparty.container_entry_indexed", i + 1, pos.getX(), pos.getY(), pos.getZ())
                        .setStyle(Style.EMPTY.withColor(0xFFFFFF))); // White
            }
            tooltip.add(Text.translatable("tooltip.steveparty.containers_order").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.translatable("tooltip.steveparty.no_container")
                    .setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(true)));
        }

        // --- Destinations info (reuse AbstractDestinationsSelectorItem methods) ---
        DestinationsComponent component = stack.getOrDefault(DESTINATIONS_COMPONENT, DEFAULT);
        Entity holder = stack.getHolder();
        List<BoardSpaceDestination> tileDestinations =
                getDestinationsStatus(component.destinations(), holder == null ? null : holder.getWorld());

        if (!tileDestinations.isEmpty()) {
            this.addTooltipHeading(tooltip, component);
            this.addDestinationsToTooltip(tooltip, tileDestinations, component, holder == null ? null : holder.getWorld());
        } else {
            this.addNoDestinationsMessage(tooltip);
        }
    }
}
