package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.ToolWheelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Tile Linker Brush's wheel, in redstone: the outer ring is the palette of the levels (the powered slot at the top,
 * then 0 to 15 in the colour of redstone dust at that power), the inner ring the kind of Cartridge put in new tiles
 * (the plain one, and each kind found in the inventory) with undo / redo at the top.
 */
final class BrushWheel implements ToolWheel.Provider {
    /** Different kinds of Cartridges shown at most. */
    private static final int MAX_CARTRIDGES = 10;
    private static final ToolWheel.Theme THEME = new ToolWheel.Theme(0xD01A0606, 0xF0300A0A, 0xFF5A1414,
            0xFFFFD27A, 0xFFF2D6D6, 0xE0200808);
    private static final List<ToolWheel.Ring> RINGS = List.of(new ToolWheel.Ring(30, 70), new ToolWheel.Ring(74, 118));
    private static final ItemStack TORCH = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack DUST = new ItemStack(Items.REDSTONE);
    private static final ItemStack UNDO = new ItemStack(Items.REPEATER);
    private static final ItemStack REDO = new ItemStack(Items.COMPARATOR);

    @Override
    public boolean handles(ItemStack stack) {
        return TileLinkerBrush.isBrush(stack);
    }

    @Override
    public ToolWheel.Layout layout(MinecraftClient client, ItemStack brush) {
        ClientPlayerEntity player = client.player;
        int level = TileLinkerBrush.level(brush);

        // Outer ring: the powered slot (top), then 0-15 clockwise
        List<ToolWheel.Sector> levels = new ArrayList<>();
        levels.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.powered"),
                Text.translatable("wheel.steveparty.brush.powered.description"),
                0xE0B02A10, item(TORCH), level == TileLinkerBrush.POWERED, true,
                () -> send(ToolWheelPayload.Action.BRUSH_LEVEL, TileLinkerBrush.POWERED)));
        for (int power = 0; power <= 15; power++) {
            int value = power;
            levels.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.level", power),
                    Text.translatable(power == 0 ? "wheel.steveparty.brush.level.description_zero" : "wheel.steveparty.brush.level.description", power),
                    0xE0000000 | RedstoneWireBlock.getWireColor(power), number(power), level == power, true,
                    () -> send(ToolWheelPayload.Action.BRUSH_LEVEL, value)));
        }
        float half = 360f / levels.size() / 2;

        // Inner ring: undo / redo on top, the Cartridges around
        List<ToolWheel.Sector> history = List.of(
                new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.undo"), Text.translatable("wheel.steveparty.brush.undo.description"),
                        0xE0503030, item(UNDO), false, true, () -> send(ToolWheelPayload.Action.BRUSH_UNDO, 0)),
                new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.redo"), Text.translatable("wheel.steveparty.brush.redo.description"),
                        0xE0503030, item(REDO), false, true, () -> send(ToolWheelPayload.Action.BRUSH_REDO, 0)));
        List<ToolWheel.Sector> cartridges = new ArrayList<>();
        Item picked = TileLinkerBrush.cartridge(brush);
        if (picked == null) picked = ModItems.BOARD_SPACE_BEHAVIOR;
        boolean creative = player != null && player.getAbilities().creativeMode;
        boolean offHand = player != null && player.getOffHandStack().getItem() instanceof CartridgeItem;
        for (Map.Entry<Item, Integer> kind : cartridgeKinds(player).entrySet()) {
            Item item = kind.getKey();
            ItemStack shown = new ItemStack(item);
            Text count = creative ? Text.translatable("wheel.steveparty.brush.cartridge.creative")
                    : Text.translatable("wheel.steveparty.brush.cartridge.count", kind.getValue());
            Text description = Text.translatable("wheel.steveparty.brush.cartridge.description", shown.getName(), count);
            if (offHand) description = description.copy().append(" ").append(Text.translatable("wheel.steveparty.brush.cartridge.off_hand"));
            boolean available = creative || kind.getValue() > 0;
            cartridges.add(new ToolWheel.Sector(shown.getName(), description, 0xE0402020,
                    item(shown), item == picked, available,
                    () -> send(ToolWheelPayload.Action.BRUSH_CARTRIDGE, Registries.ITEM.getRawId(item))));
        }
        List<ToolWheel.Arc> arcs = List.of(
                new ToolWheel.Arc(1, -half, 360 - half, levels),
                new ToolWheel.Arc(0, -40, 40, history),
                new ToolWheel.Arc(0, 40, 320, cartridges));
        return new ToolWheel.Layout(THEME, RINGS, arcs, Text.translatable("item.steveparty.tile_linker_brush"), item(DUST));
    }

    /**
     * The kinds of Cartridges offered: the plain one first (always), then each other kind in the inventory, with how
     * many there are.
     */
    private static Map<Item, Integer> cartridgeKinds(ClientPlayerEntity player) {
        Map<Item, Integer> kinds = new LinkedHashMap<>();
        kinds.put(ModItems.BOARD_SPACE_BEHAVIOR, 0);
        if (player == null) return kinds;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!(stack.getItem() instanceof CartridgeItem)) continue;
            if (!kinds.containsKey(stack.getItem()) && kinds.size() >= MAX_CARTRIDGES) continue;
            kinds.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return kinds;
    }

    private static ToolWheel.Icon item(ItemStack stack) {
        return (context, x, y) -> context.drawItem(stack, x - 8, y - 8);
    }

    private static ToolWheel.Icon number(int power) {
        String text = Integer.toString(power);
        return (context, x, y) -> {
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.drawText(renderer, text, x - renderer.getWidth(text) / 2, y - 4, power >= 12 ? 0xFF3A0000 : 0xFFFFFFFF, power < 12);
        };
    }

    private static void send(ToolWheelPayload.Action action, int value) {
        ClientPlayNetworking.send(new ToolWheelPayload(action, value));
    }
}
