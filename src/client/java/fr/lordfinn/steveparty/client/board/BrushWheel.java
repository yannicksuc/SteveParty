package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.Steveparty;
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
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Tile Linker Brush's wheel, a few clear choices first: the normal link (the powered slot), the redstone level and
 * the kind of Cartridge (each on a page of its own, opened on demand), undo and redo (curved arrows). The level page
 * is the redstone palette (0 to 15 in the colour of the dust at that power), the cartridge page the kinds in the
 * inventory; their hub goes back. The first times, the normal link is put forward with how to paint.
 */
final class BrushWheel implements ToolWheel.Provider {
    private static final int MAIN = 0, LEVELS = 1, CARTRIDGES = 2;
    /** Different kinds of Cartridges shown at most. */
    private static final int MAX_CARTRIDGES = 10;
    /** The plates: the grey of vanilla slots, the cartridge page in the board's teal. */
    private static final int GREY = 0x8B8B8B, TEAL = 0x7FA3A9;
    private static final List<ToolWheel.Ring> MAIN_RING = List.of(new ToolWheel.Ring(26, 70));
    private static final List<ToolWheel.Ring> LEVEL_RING = List.of(new ToolWheel.Ring(26, 92));
    private static final List<ToolWheel.Ring> CARTRIDGE_RING = List.of(new ToolWheel.Ring(26, 80));
    private static final ItemStack TORCH = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack DUST = new ItemStack(Items.REDSTONE);
    private static final ItemStack KEEP = new ItemStack(fr.lordfinn.steveparty.blocks.ModBlocks.TILE);
    private static final Identifier UNDO = Steveparty.id("wheel/undo"), REDO = Steveparty.id("wheel/redo"), BACK = Steveparty.id("wheel/back");

    @Override
    public boolean handles(ItemStack stack) {
        return TileLinkerBrush.isBrush(stack);
    }

    @Override
    public ToolWheel.Layout layout(MinecraftClient client, ItemStack brush, int page) {
        return switch (page) {
            case LEVELS -> levels(brush);
            case CARTRIDGES -> cartridges(client.player, brush);
            default -> main(client.player, brush);
        };
    }

    private static ToolWheel.Layout main(ClientPlayerEntity player, ItemStack brush) {
        int level = TileLinkerBrush.level(brush);
        Item picked = TileLinkerBrush.cartridge(brush);
        ToolWheel.Sector normal = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.powered"),
                Text.translatable("wheel.steveparty.brush.powered.hint"), GREY, item(TORCH), level == TileLinkerBrush.POWERED, true,
                () -> send(ToolWheelPayload.Action.BRUSH_LEVEL, TileLinkerBrush.POWERED));
        ToolWheel.Sector levels = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.levels"),
                level == TileLinkerBrush.POWERED ? Text.translatable("wheel.steveparty.brush.levels.hint")
                        : Text.translatable("wheel.steveparty.brush.levels.current", level),
                level == TileLinkerBrush.POWERED ? GREY : RedstoneWireBlock.getWireColor(Math.max(level, 4)),
                levelIcon(level), level != TileLinkerBrush.POWERED, true, true, () -> ToolWheel.showPage(LEVELS));
        ItemStack cartridge = picked == null ? KEEP : new ItemStack(picked);
        ToolWheel.Sector cartridges = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.cartridges"),
                picked == null ? Text.translatable("wheel.steveparty.brush.keep") : cartridge.getName(),
                GREY, item(cartridge), false, true, true, () -> ToolWheel.showPage(CARTRIDGES));
        ToolWheel.Sector redo = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.redo"), null, GREY, sprite(REDO),
                false, true, () -> send(ToolWheelPayload.Action.BRUSH_REDO, 0));
        ToolWheel.Sector undo = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.undo"), null, GREY, sprite(UNDO),
                false, true, () -> send(ToolWheelPayload.Action.BRUSH_UNDO, 0));
        // Clockwise from the top: the normal link, the level, the cartridge, then redo and undo (undo on the left)
        List<ToolWheel.Arc> arcs = List.of(new ToolWheel.Arc(0, -36, 324, List.of(normal, levels, cartridges, redo, undo)));
        return new ToolWheel.Layout(MAIN_RING, arcs, item(brush.copy()), null, normal,
                Text.translatable("wheel.steveparty.brush.powered.first"));
    }

    private static ToolWheel.Layout levels(ItemStack brush) {
        int level = TileLinkerBrush.level(brush);
        List<ToolWheel.Sector> sectors = new ArrayList<>();
        for (int power = 0; power <= 15; power++) {
            int value = power;
            sectors.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.level", power),
                    Text.translatable(power == 0 ? "wheel.steveparty.brush.level.hint_zero" : "wheel.steveparty.brush.level.hint", power),
                    RedstoneWireBlock.getWireColor(power), number(power), level == power, true,
                    () -> send(ToolWheelPayload.Action.BRUSH_LEVEL, value)));
        }
        float half = 360f / 16 / 2;
        return new ToolWheel.Layout(LEVEL_RING, List.of(new ToolWheel.Arc(0, -half, 360 - half, sectors)), sprite(BACK), back(), null, null);
    }

    private static ToolWheel.Layout cartridges(ClientPlayerEntity player, ItemStack brush) {
        Item picked = TileLinkerBrush.cartridge(brush);
        List<ToolWheel.Sector> sectors = new ArrayList<>();
        // First: none picked, the painted tiles keep their cartridge
        sectors.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.keep"), Text.translatable("wheel.steveparty.brush.keep.hint"),
                TEAL, item(KEEP), picked == null, true, () -> send(ToolWheelPayload.Action.BRUSH_CARTRIDGE, ToolWheelPayload.KEEP_CARTRIDGES)));
        boolean creative = player != null && player.getAbilities().creativeMode;
        for (Map.Entry<Item, Integer> kind : cartridgeKinds(player).entrySet()) {
            Item item = kind.getKey();
            ItemStack shown = new ItemStack(item);
            boolean available = creative || kind.getValue() > 0;
            Text count = creative ? Text.translatable("wheel.steveparty.brush.cartridge.creative")
                    : Text.translatable("wheel.steveparty.brush.cartridge.count", kind.getValue());
            sectors.add(new ToolWheel.Sector(shown.getName(), count, TEAL, item(shown), item == picked, available,
                    () -> send(ToolWheelPayload.Action.BRUSH_CARTRIDGE, Registries.ITEM.getRawId(item))));
        }
        float half = 360f / sectors.size() / 2;
        return new ToolWheel.Layout(CARTRIDGE_RING, List.of(new ToolWheel.Arc(0, -half, 360 - half, sectors)), sprite(BACK), back(), null, null);
    }

    private static ToolWheel.Sector back() {
        return new ToolWheel.Sector(Text.translatable("wheel.steveparty.back"), null, GREY, sprite(BACK), false, true, true,
                () -> ToolWheel.showPage(MAIN));
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

    static ToolWheel.Icon item(ItemStack stack) {
        return (context, x, y) -> context.drawItem(stack, x - 8, y - 8);
    }

    static ToolWheel.Icon sprite(Identifier sprite) {
        return (context, x, y) -> {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            context.drawGuiTexture(sprite, x - 8, y - 8, 16, 16);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        };
    }

    /** Redstone dust, with the level set (none: the dust alone). */
    private static ToolWheel.Icon levelIcon(int level) {
        if (level == TileLinkerBrush.POWERED) return item(DUST);
        String text = Integer.toString(level);
        return (context, x, y) -> {
            context.drawItem(DUST, x - 8, y - 8);
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.drawText(renderer, text, x + 9 - renderer.getWidth(text), y + 1, 0xFFFFFFFF, true);
            context.getMatrices().pop();
        };
    }

    private static ToolWheel.Icon number(int power) {
        String text = Integer.toString(power);
        return (context, x, y) -> {
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.drawText(renderer, text, x - renderer.getWidth(text) / 2 + 1, y - 3, power >= 12 ? 0xFF3A0000 : 0xFFFFFFFF, power < 12);
        };
    }

    private static void send(ToolWheelPayload.Action action, int value) {
        ClientPlayNetworking.send(new ToolWheelPayload(action, value));
    }
}
