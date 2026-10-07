package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.components.ModComponents;
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
 * The Tile Linker Brush's wheel, in the mod's teal with large icons, a few clear choices first: the normal link (the powered slot), the redstone level and
 * the kind of Cartridge (each on a page of its own, opened on demand), undo and redo (curved arrows). The level page
 * is the redstone palette (0 to 15 in the colour of the dust at that power), the cartridge page the kinds in the
 * inventory; their hub goes back. The first times, the normal link is put forward with how to paint.
 */
final class BrushWheel implements ToolWheel.Provider {
    private static final int MAIN = 0, LEVELS = 1, CARTRIDGES = 2;
    /** Different kinds of Cartridges shown at most. */
    private static final int MAX_CARTRIDGES = 10;
    /** A slate frame, each sector of the main page in the colour of what it sets (the gold frame marks the setting in use). */
    private static final ToolWheel.Theme THEME = ToolWheel.SLATE;
    private static final int PLATE = THEME.body() & 0xFFFFFF;
    /** The main page, a colour per thing it sets: the normal link orange, the level red (redstone), the cartridge blue, undo and redo violet. */
    private static final int NORMAL_PLATE = 0xE48A2C, LEVEL_PLATE = 0xC8333B, CARTRIDGE_PLATE = 0x3A86CC, HISTORY_PLATE = 0x8A5CC8;
    private static final List<ToolWheel.Ring> MAIN_RING = List.of(new ToolWheel.Ring(28, 80));
    private static final List<ToolWheel.Ring> LEVEL_RING = List.of(new ToolWheel.Ring(26, 92));
    private static final List<ToolWheel.Ring> CARTRIDGE_RING = List.of(new ToolWheel.Ring(26, 80));
    /** The default link: a lasso (the lead). */
    private static final ItemStack LASSO = new ItemStack(Items.LEAD);
    private static final ItemStack WRENCH = new ItemStack(ModItems.WRENCH);
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
                Text.translatable("wheel.steveparty.brush.powered.hint"), NORMAL_PLATE, big(LASSO, 2), level == TileLinkerBrush.POWERED, true,
                () -> send(ToolWheelPayload.Action.BRUSH_LEVEL, TileLinkerBrush.POWERED));
        ToolWheel.Sector levels = new ToolWheel.Sector(level == TileLinkerBrush.POWERED ? Text.translatable("wheel.steveparty.brush.levels")
                : Text.translatable("wheel.steveparty.brush.levels.current", level), levelsHint(), LEVEL_PLATE,
                levelIcon(level), level != TileLinkerBrush.POWERED, true, true, () -> ToolWheel.showPage(LEVELS));
        ItemStack cartridge = picked == null ? KEEP : new ItemStack(picked);
        ToolWheel.Sector cartridges = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.mode"),
                modeHint(picked), CARTRIDGE_PLATE, withWrench(cartridge), false, true, true, () -> ToolWheel.showPage(CARTRIDGES));
        ToolWheel.Sector redo = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.redo"), null, HISTORY_PLATE, sprite(REDO, 2),
                false, true, () -> send(ToolWheelPayload.Action.BRUSH_REDO, 0));
        ToolWheel.Sector undo = new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.undo"), null, HISTORY_PLATE, sprite(UNDO, 2),
                false, true, () -> send(ToolWheelPayload.Action.BRUSH_UNDO, 0));
        // Fixed places, clockwise from the top: the normal link, the cartridge (top right), redo (bottom right), undo
        // (bottom left), the level (top left)
        List<ToolWheel.Arc> arcs = List.of(new ToolWheel.Arc(0, -36, 324, List.of(normal, cartridges, redo, undo, levels)));
        return new ToolWheel.Layout(MAIN_RING, arcs, big(brush.copy(), 1.5f), null, normal,
                Text.translatable("wheel.steveparty.brush.powered.first"), THEME);
    }

    private static ToolWheel.Layout levels(ItemStack brush) {
        int level = TileLinkerBrush.level(brush);
        List<ToolWheel.Sector> sectors = new ArrayList<>();
        for (int power = 0; power <= 15; power++) {
            int value = power;
            sectors.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.level", power), null,
                    RedstoneWireBlock.getWireColor(power), number(power), level == power, true,
                    () -> send(ToolWheelPayload.Action.BRUSH_LEVEL, value)));
        }
        float half = 360f / 16 / 2;
        return new ToolWheel.Layout(LEVEL_RING, List.of(new ToolWheel.Arc(0, -half, 360 - half, sectors)), sprite(BACK, 1), back(), null, null, THEME);
    }

    private static ToolWheel.Layout cartridges(ClientPlayerEntity player, ItemStack brush) {
        Item picked = TileLinkerBrush.cartridge(brush);
        List<ToolWheel.Sector> sectors = new ArrayList<>();
        // First: none picked, the painted tiles keep their cartridge
        sectors.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.brush.keep"), modeHint(null), CARTRIDGE_PLATE, big(KEEP, 1.5f), picked == null, true, () -> send(ToolWheelPayload.Action.BRUSH_CARTRIDGE, ToolWheelPayload.KEEP_CARTRIDGES)));
        boolean creative = player != null && player.getAbilities().creativeMode;
        for (Map.Entry<Item, Integer> kind : cartridgeKinds(player).entrySet()) {
            Item item = kind.getKey();
            ItemStack shown = new ItemStack(item);
            boolean available = creative || kind.getValue() > 0;
            Text name = creative ? shown.getName() : Text.translatable("wheel.steveparty.brush.cartridge.count", shown.getName(), kind.getValue());
            sectors.add(new ToolWheel.Sector(name, modeHint(item), CARTRIDGE_PLATE, big(shown, 1.5f), item == picked, available,
                    () -> send(ToolWheelPayload.Action.BRUSH_CARTRIDGE, Registries.ITEM.getRawId(item))));
        }
        float half = 360f / sectors.size() / 2;
        return new ToolWheel.Layout(CARTRIDGE_RING, List.of(new ToolWheel.Arc(0, -half, 360 - half, sectors)), sprite(BACK, 1), back(), null, null, THEME);
    }

    private static ToolWheel.Sector back() {
        return new ToolWheel.Sector(Text.translatable("wheel.steveparty.back"), null, PLATE, sprite(BACK, 1), false, true, true,
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

    /**
     * An item drawn {@code scale} times its size (2: still whole pixels), on a drop shadow one of its pixels down right
     * (its silhouette darkened, behind it), like the wheel's sprite icons.
     */
    static ToolWheel.Icon big(ItemStack stack, float scale) {
        return (context, x, y) -> {
            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.getMatrices().push();
            context.getMatrices().translate(1, 1, -50);
            // Items are drawn in batches: flushed before and after, so that the darkening applies to the shadow only
            context.draw();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.16f, 0.06f, 0.08f, 1f);
            context.drawItem(stack, -8, -8);
            context.draw();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            context.getMatrices().pop();
            context.drawItem(stack, -8, -8);
            context.getMatrices().pop();
        };
    }

    /** The gold of the Advanced Tiles. */
    private static final int ADVANCED_GOLD = 0xFFC52E;

    /** What a level links: a slot of the Advanced Tiles (their name in their gold). */
    private static Text levelsHint() {
        return Text.translatable("wheel.steveparty.brush.levels.hint",
                Text.translatable("wheel.steveparty.brush.advanced_tiles").styled(style -> style.withColor(ADVANCED_GOLD)));
    }

    /** What the link mode does: the tiles keep their cartridges, or get the one picked (its name in its colour). */
    private static Text modeHint(Item picked) {
        if (picked == null) return Text.translatable("wheel.steveparty.brush.mode.keep");
        ItemStack stack = new ItemStack(picked);
        int color = stack.getOrDefault(ModComponents.COLOR, 0xFFFFFF);
        return Text.translatable("wheel.steveparty.brush.mode.replace", stack.getName().copy().styled(style -> style.withColor(color)));
    }

    /**
     * The link mode's icon: what the painted tiles get (a tile: they keep theirs; else the cartridge picked) with the
     * Wrench over it, shifted left by half: the setting of how links are made.
     */
    private static ToolWheel.Icon withWrench(ItemStack shown) {
        ToolWheel.Icon base = big(shown, 1.75f), wrench = big(WRENCH, 1.5f);
        return (context, x, y) -> {
            base.draw(context, x + 4, y + 1);
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 60);
            wrench.draw(context, x - 5, y - 1);
            context.getMatrices().pop();
        };
    }

    static ToolWheel.Icon sprite(Identifier sprite, int scale) {
        int size = 16 * scale;
        return (context, x, y) -> {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            context.drawGuiTexture(sprite, x - size / 2, y - size / 2, size, size);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        };
    }

    /** Redstone dust, with the level set (none: the dust alone). */
    private static ToolWheel.Icon levelIcon(int level) {
        ToolWheel.Icon dust = big(DUST, 2);
        if (level == TileLinkerBrush.POWERED) return dust;
        String text = Integer.toString(level);
        return (context, x, y) -> {
            dust.draw(context, x, y);
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 400);
            context.drawText(renderer, text, x + 17 - renderer.getWidth(text), y + 9, 0xFFFFFFFF, true);
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
