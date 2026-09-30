package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.utils.MessageUtils;
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

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        Hand hand = context.getHand();
        if (!world.isClient && player != null && hand == Hand.OFF_HAND) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof InventoryCartridgeItem) {
                BlockPos blockPos = context.getBlockPos();
                if (world.getBlockEntity(blockPos) instanceof Inventory) {
                    if (blockPos.equals(getSavedInventoryPos(stack))) {
                        playCancelSound(blockPos, player);
                        saveBlockPos(stack, null);
                    } else {
                        playSelectSound(blockPos, player);
                        saveBlockPos(stack, blockPos);
                    }
                    return ActionResult.SUCCESS;
                } else {
                    MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.translatable("message.steveparty.block_not_inventory"), MessageUtils.MessageType.ACTION_BAR);
                    return ActionResult.FAIL;
                }
            }
        }
        return super.useOnBlock(context);
    }

    private void saveBlockPos(ItemStack stack, BlockPos pos) {
        stack.set(INVENTORY_POS, pos);
    }

    public BlockPos getSavedInventoryPos(ItemStack stack) {
        return stack.getOrDefault(INVENTORY_POS, null);
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
            new InfoModule("chest", K + "chest", 2, InventoryCartridgeItem::chest, stack -> new ItemStack(Items.CHEST)),
            new ChoiceModule("mode", K + "mode",
                    List.of(new ChoiceModule.Option(K + "random", -1, "message.steveparty.button_state.random"),
                            new ChoiceModule.Option(K + "all", -1, "message.steveparty.button_state.all"),
                            new ChoiceModule.Option(K + "cycle", -1, "message.steveparty.button_state.cycle")),
                    InventoryCartridgeItem::getSelectionState,
                    (edit, value) -> setSelectionState(edit.stack(), value)));

    /** The chest it gives from / takes to, and how to link one. */
    private static List<InfoModule.Line> chest(InfoModule.Context context) {
        BlockPos pos = context.stack().getOrDefault(INVENTORY_POS, null);
        return List.of(pos != null
                        ? InfoModule.Line.of(Text.translatable(K + "chest.at", pos.getX(), pos.getY(), pos.getZ()))
                        : new InfoModule.Line(Text.translatable(K + "chest.none"), InfoModule.Tone.BAD),
                new InfoModule.Line(Text.translatable(K + "chest.hint"), InfoModule.Tone.SOFT));
    }

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
                Text.translatable("tooltip.steveparty.controls.left_hand")
                        .setStyle(Style.EMPTY.withColor(0xfcb017))));

        tooltip.add(Text.translatable("tooltip.steveparty.controls.select_destination",
                Text.translatable("tooltip.steveparty.controls.right_hand")
                        .setStyle(Style.EMPTY.withColor(0xfcb017))));

        tooltip.add(Text.translatable("tooltip.steveparty.controls.open_config",
                Text.translatable("tooltip.steveparty.controls.right_air")
                        .setStyle(Style.EMPTY.withColor(0xfcb017))));
        tooltip.add(Text.empty());

        // --- Container info ---
        BlockPos pos = getSavedInventoryPos(stack);
        if (pos != null) {
            tooltip.add(Text.translatable("tooltip.steveparty.linked_container")
                    .setStyle(Style.EMPTY.withColor(0x167abf).withBold(true))); // Aqua
            tooltip.add(Text.translatable("tooltip.steveparty.container_entry",
                            pos.getX(), pos.getY(), pos.getZ())
                    .setStyle(Style.EMPTY.withColor(0xFFFFFF))); // White
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
            this.addDestinationsToTooltip(tooltip, tileDestinations);
        } else {
            this.addNoDestinationsMessage(tooltip);
        }
    }
}
