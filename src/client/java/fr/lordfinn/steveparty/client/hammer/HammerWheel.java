package fr.lordfinn.steveparty.client.hammer;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlock;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.ToolWheelPayload;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The Stencil Hammer's wheel: its slots themselves, as cases to pick. On the right its 9 stencil slots, on the left
 * Engrave (bottom) and its 9 dye slots going up; an empty slot shows the silhouette of what it takes, and a click on
 * it opens the hammer's slots beside the inventory to fill it, as the chest on top does. Engrave is a netherite
 * pickaxe, or an axe when the player looks at a cut-out panel (the hammer cuts it). A pick applies at once.
 */
public final class HammerWheel implements ToolWheel.Provider {
    private static final List<ToolWheel.Ring> RINGS = List.of(new ToolWheel.Ring(34, 116));
    private static final float TOP = 12;
    private static final int PAPER = 0xC9B48C, GREY = 0x8B8B8B, WOOD = 0x9C7A4C;
    public static final Identifier EMPTY_STENCIL = Steveparty.id("item/empty_slot_stencil");
    public static final Identifier EMPTY_DYE = Steveparty.id("item/empty_slot_dye");

    @Override
    public boolean handles(ItemStack stack) {
        return stack.getItem() instanceof StencilGunItem;
    }

    @Override
    public ToolWheel.Layout layout(MinecraftClient client, ItemStack hammer, int page) {
        List<ItemStack> contents = StencilGunItem.contents(hammer);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(hammer));
        StencilGunItem.Load load = StencilGunItem.selectedLoad(hammer);
        int paint = load.color() != null ? 0xFF000000 | load.color().getEntityColor() : 0xFF6A6A6A;

        // Top: its slots beside the inventory
        ToolWheel.Sector inventory = new ToolWheel.Sector(Text.translatable("wheel.steveparty.hammer.inventory"),
                Text.translatable("wheel.steveparty.hammer.inventory.hint"), WOOD,
                item(new ItemStack(Items.CHEST)), false, true, () -> send(ToolWheelPayload.Action.HAMMER_OPEN, 0));

        // Right: the stencil slots
        List<ToolWheel.Sector> stencils = new ArrayList<>();
        boolean anyStencil = false;
        for (int slot = 0; slot < StencilGunItem.STENCIL_SLOTS; slot++) {
            ItemStack stencil = contents.get(slot);
            if (stencil.isEmpty()) {
                stencils.add(empty("wheel.steveparty.hammer.empty_stencil", EMPTY_STENCIL));
                continue;
            }
            anyStencil = true;
            byte[] shape = stencil.getItem() instanceof StencilItem ? StencilItem.getShape(stencil) : null;
            StencilPatterns.Pattern pattern = shape == null ? null : StencilPatterns.byShape(shape);
            Text name = pattern != null ? pattern.name() : Text.translatable("tooltip.steveparty.stencil.custom");
            int value = slot;
            stencils.add(new ToolWheel.Sector(name, null, PAPER, shape(shape, paint), selection.stencil() == slot,
                    !StencilShape.isBlank(shape), () -> send(ToolWheelPayload.Action.HAMMER_STENCIL, value)));
        }

        // Left: Engrave (bottom), then the dye slots going up
        List<ToolWheel.Sector> colors = new ArrayList<>();
        boolean cuts = looksAtCutOutPanel(client);
        colors.add(new ToolWheel.Sector(Text.translatable(cuts ? "wheel.steveparty.hammer.cut" : "wheel.steveparty.hammer.engrave"),
                Text.translatable(cuts ? "wheel.steveparty.hammer.cut.hint" : "wheel.steveparty.hammer.engrave.hint"), GREY,
                item(engraveIcon(client)), selection.dye() == StencilGunSelection.ENGRAVE, true,
                () -> send(ToolWheelPayload.Action.HAMMER_DYE, StencilGunSelection.ENGRAVE)));
        for (int slot = 0; slot < StencilGunItem.DYE_SLOTS; slot++) {
            ItemStack dye = contents.get(StencilGunItem.STENCIL_SLOTS + slot);
            if (!(dye.getItem() instanceof DyeItem dyeItem)) {
                colors.add(empty("wheel.steveparty.hammer.empty_dye", EMPTY_DYE));
                continue;
            }
            DyeColor color = dyeItem.getColor();
            int value = slot;
            colors.add(new ToolWheel.Sector(Text.translatable("color.minecraft." + color.getName()),
                    Text.translatable("wheel.steveparty.hammer.color.hint", dye.getCount()), color.getEntityColor(), stack(dye),
                    selection.dye() == slot, true, () -> send(ToolWheelPayload.Action.HAMMER_DYE, value)));
        }

        List<ToolWheel.Arc> arcs = List.of(
                new ToolWheel.Arc(0, -TOP, TOP, List.of(inventory)),
                new ToolWheel.Arc(0, TOP, 180, stencils),
                new ToolWheel.Arc(0, 180, 360 - TOP, colors));
        return new ToolWheel.Layout(RINGS, arcs, item(hammer.copy()), null, anyStencil ? null : inventory,
                Text.translatable("wheel.steveparty.hammer.inventory.first"));
    }

    /** An empty slot: the silhouette of what it takes; a click opens the slots to fill it. */
    private static ToolWheel.Sector empty(String key, Identifier silhouette) {
        return new ToolWheel.Sector(Text.translatable(key), Text.translatable("wheel.steveparty.hammer.empty.hint"), GREY,
                atlas(silhouette), false, true, () -> send(ToolWheelPayload.Action.HAMMER_OPEN, 0));
    }

    /** Whether the player looks at a cut-out panel: the hammer cuts it rather than engraves. */
    public static boolean looksAtCutOutPanel(MinecraftClient client) {
        return client.world != null && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && client.world.getBlockState(hit.getBlockPos()).getBlock() instanceof StencilCanvasBlock canvas && canvas.usesSilhouette();
    }

    /** The no-paint mode's icon: a netherite pickaxe (engrave), or an axe on a cut-out panel (cut). */
    public static ItemStack engraveIcon(MinecraftClient client) {
        return new ItemStack(looksAtCutOutPanel(client) ? Items.NETHERITE_AXE : Items.NETHERITE_PICKAXE);
    }

    private static ToolWheel.Icon item(ItemStack stack) {
        return (context, x, y) -> context.drawItem(stack, x - 8, y - 8);
    }

    /** A sprite of the block atlas (the slot silhouettes). */
    public static ToolWheel.Icon atlas(Identifier sprite) {
        return (context, x, y) -> drawAtlas(context, sprite, x - 8, y - 8);
    }

    public static void drawAtlas(DrawContext context, Identifier sprite, int x, int y) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        context.drawSprite(x, y, 0, 16, 16, MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(sprite));
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** A dye stack, with its count. */
    private static ToolWheel.Icon stack(ItemStack stack) {
        return (context, x, y) -> {
            context.drawItem(stack, x - 8, y - 8);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x - 8, y - 8);
        };
    }

    /** The stencil's pattern, 16 x 16 pixels, in the current paint. */
    private static ToolWheel.Icon shape(byte[] shape, int paint) {
        return (context, x, y) -> {
            context.fill(x - 9, y - 9, x + 9, y + 9, 0xFFEDE3CC);
            if (shape == null) return;
            for (int px = 0; px < StencilShape.SIDE; px++) {
                for (int py = 0; py < StencilShape.SIDE; py++) {
                    if (StencilShape.get(shape, px, py)) context.fill(x - 8 + px, y - 8 + py, x - 7 + px, y - 7 + py, paint);
                }
            }
        };
    }

    private static void send(ToolWheelPayload.Action action, int value) {
        ClientPlayNetworking.send(new ToolWheelPayload(action, value));
    }
}
