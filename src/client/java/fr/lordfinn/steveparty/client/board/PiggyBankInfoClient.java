package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity;
import fr.lordfinn.steveparty.client.gui.FloatingInfo;
import fr.lordfinn.steveparty.client.gui.NotLinkedMark;
import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

/**
 * What the Piggy Bank looked at does, as a board space's panel ({@link TilePanel}): a small gold plate over it, its
 * target's icon (the dice: the player of the turn, the gold podium: the winners, the party: everyone, the compass: the
 * nearest), then each item of its cartridge with what it moves, green « +5 » given, red « -20 » taken; a « / » between
 * them when one of them only moves at a time (random or in turn). Linked to no party (it does nothing then), the
 * Tile Linker Brush crossed out in red comes first. No words. Out of sight, its card on the screen
 * ({@link FloatingInfo}).
 */
public final class PiggyBankInfoClient {
    private static final double AIM_DISTANCE = 24, PANEL_DISTANCE = 16;
    /** Text pixels: padding, an icon, icon to count, between two cells, the divider after the target. */
    private static final int PAD = 5, ICON = 9, ICON_GAP = 2, CELL_GAP = 6, DIVIDER = 1;
    private static final float SCALE = 1f / 48f, MAX_GROW = 2.5f;
    /** Above the pig (its head reaches 15/16 of the block), a little towards the camera. */
    private static final double ABOVE = 1.1, AHEAD = 0.3;
    private static final int GIVE = 0xFF1E7A1E, TAKE = 0xFFB3202A, SOFT = 0xFF8A6A1A;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    /** An item and what moves of it (text empty: the target); {@code crossed}: crossed out in red (linked to nothing). */
    private record Cell(ItemStack icon, String text, int color, boolean crossed) {
        Cell(ItemStack icon, String text, int color) {
            this(icon, text, color, false);
        }
    }

    /**
     * The cells, whether one item only moves at a time (a « / » between them), and how many cells come before the
     * items (a divider after each): the target, after the crossed brush when unlinked.
     */
    private record Shown(List<Cell> cells, boolean oneOf, int heads) {
    }


    private record CardKey(BlockPos pos) {
    }

    private PiggyBankInfoClient() {
    }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(PiggyBankInfoClient::render);
        HudRenderCallback.EVENT.register(PiggyBankInfoClient::renderHud);
    }

    /** The piggy bank under the crosshair, or null. */
    private static @Nullable BlockPos aimed(MinecraftClient client) {
        Entity viewer = client.getCameraEntity();
        if (client.world == null || viewer == null) return null;
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        if (block.getPos().squaredDistanceTo(viewer.getEyePos()) > AIM_DISTANCE * AIM_DISTANCE) return null;
        return client.world.getBlockEntity(block.getBlockPos()) instanceof PiggyBankBlockEntity ? block.getBlockPos().toImmutable() : null;
    }

    private static @Nullable Shown shown(MinecraftClient client, BlockPos pos) {
        if (client.world == null || !(client.world.getBlockEntity(pos) instanceof PiggyBankBlockEntity bank)) return null;
        List<Cell> cells = new ArrayList<>();
        if (!bank.isPartyLinked()) cells.add(new Cell(NotLinkedMark.BRUSH, "", 0, true));
        cells.add(new Cell(bank.getTarget().getIcon(), "", 0));
        int heads = cells.size();
        ItemStack cartridge = bank.getStack(0);
        InventoryComponent content = cartridge.getOrDefault(INVENTORY_COMPONENT, null);
        if (content != null) {
            for (ItemStack stack : content.getItems()) {
                if (stack.isEmpty()) continue;
                boolean taken = CartridgeTransfers.isNegative(stack);
                cells.add(new Cell(CartridgeTransfers.pattern(stack), (taken ? "-" : "+") + stack.getCount(), taken ? TAKE : GIVE));
            }
        }
        boolean oneOf = cells.size() > heads + 1 && InventoryCartridgeItem.getSelectionState(cartridge) != InventoryCartridgeItem.ALL;
        return new Shown(cells, oneOf, heads);
    }

    private static int cellWidth(TextRenderer font, Cell cell) {
        return cell.text().isEmpty() ? ICON : ICON + ICON_GAP + font.getWidth(cell.text());
    }

    /** Text pixels from a cell to the next: the divider after the target, a « / » between one-of items. */
    private static int gap(TextRenderer font, Shown shown, int index) {
        if (index < shown.heads()) return CELL_GAP + DIVIDER + CELL_GAP;
        return shown.oneOf() ? CELL_GAP / 2 + font.getWidth("/") + CELL_GAP / 2 : CELL_GAP;
    }

    private static int rowWidth(TextRenderer font, Shown shown) {
        int width = 0;
        for (int i = 0; i < shown.cells().size(); i++) {
            width += cellWidth(font, shown.cells().get(i));
            if (i + 1 < shown.cells().size()) width += gap(font, shown, i);
        }
        return width;
    }

    // ---------------------------------------------------------------- in the world

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        BlockPos pos = aimed(client);
        if (pos == null || matrices == null || client.options.hudHidden) return;
        Shown shown = shown(client, pos);
        if (shown == null) return;
        TextRenderer font = client.textRenderer;
        Camera camera = context.camera();
        Vec3d toCamera = new Vec3d(camera.getPos().x - pos.getX() - 0.5, 0, camera.getPos().z - pos.getZ() - 0.5);
        toCamera = toCamera.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : toCamera.normalize().multiply(AHEAD);
        Vec3d anchor = new Vec3d(pos.getX() + 0.5, pos.getY() + ABOVE, pos.getZ() + 0.5).add(toCamera);
        double distance = Math.sqrt(anchor.squaredDistanceTo(camera.getPos()));
        float scale = SCALE * (float) Math.clamp(distance / 7.0, 1.0, MAX_GROW);
        int width = rowWidth(font, shown) + 2 * PAD, height = PAD + 8 + PAD;
        Vec3d middle = anchor.add(0, height * scale / 2, 0);
        if (!FloatingInfo.inSight(camera, middle, PANEL_DISTANCE)) return;
        CardKey key = new CardKey(pos);
        FloatingInfo.drawnInWorld(key);
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        matrices.push();
        matrices.translate(middle.x - camera.getPos().x, middle.y - camera.getPos().y, middle.z - camera.getPos().z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float left = -width / 2f, top = -height / 2f;
        WorldDraw.plate(matrices, consumers, WorldDraw.Plate.GOLD, left, top, -left, -top);
        float x = left + PAD, y = top + PAD;
        for (int i = 0; i < shown.cells().size(); i++) {
            Cell cell = shown.cells().get(i);
            matrices.push();
            matrices.translate(x + ICON / 2f, y + 3.5f, 0.5f);
            matrices.scale(ICON, -ICON, 0.5f);
            client.getItemRenderer().renderItem(cell.icon(), ModelTransformationMode.GUI, LIGHT, OverlayTexture.DEFAULT_UV, matrices,
                    consumers, client.world, 0);
            matrices.pop();
            // On the icon's bottom-right corner (the icon spans y - 1 to y + 8)
            if (cell.crossed()) NotLinkedMark.worldCross(matrices, consumers, x + ICON, y + 8, ICON / 16f, CROSS_Z);
            if (!cell.text().isEmpty()) {
                font.draw(cell.text(), x + ICON + ICON_GAP, y, cell.color(), false, matrices.peek().getPositionMatrix(),
                        consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
            }
            x += cellWidth(font, cell);
            if (i + 1 < shown.cells().size()) {
                if (i < shown.heads()) {
                    WorldDraw.fill(matrices, consumers, x + CELL_GAP, y - 1, x + CELL_GAP + DIVIDER, y + 8, -0.25f, SOFT);
                } else if (shown.oneOf()) {
                    font.draw("/", x + CELL_GAP / 2f, y, SOFT, false, matrices.peek().getPositionMatrix(),
                            consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
                }
                x += gap(font, shown, i);
            }
        }
        consumers.draw();
        matrices.pop();
    }

    /** The cross's depth: in front of the flat icon under it (label space: +z toward the camera). */
    private static final float CROSS_Z = 1.2f;

    // ---------------------------------------------------------------- on the screen

    /** Its panel out of sight (too far...): its card on the screen, the same row. */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = aimed(client);
        if (pos == null || client.options.hudHidden || client.currentScreen != null) return;
        CardKey key = new CardKey(pos);
        if (!FloatingInfo.needsCard(key)) return;
        Shown shown = shown(client, pos);
        if (shown == null) return;
        TextRenderer font = client.textRenderer;
        int width = Math.min(rowWidth(font, shown) + 2 * FloatingInfo.PAD, FloatingInfo.MAX_WIDTH);
        int height = FloatingInfo.PAD + 8 + FloatingInfo.PAD;
        FloatingInfo.card(context, key, width, height, ToolHud.Plate.GOLD, 1f, 0, card -> {
            int x = FloatingInfo.PAD, y = FloatingInfo.PAD;
            for (int i = 0; i < shown.cells().size(); i++) {
                Cell cell = shown.cells().get(i);
                int ix = x;
                card.getMatrices().push();
                card.getMatrices().translate(ix, y - 1, 0);
                card.getMatrices().scale(ICON / 16f, ICON / 16f, 1);
                HudDepth.item(card, () -> card.drawItem(cell.icon(), 0, 0));
                if (cell.crossed()) HudDepth.onTop(card, () -> NotLinkedMark.crossOnIcon(card, 0, 0));
                card.getMatrices().pop();
                if (!cell.text().isEmpty()) {
                    HudDepth.onTop(card, () -> UiText.line(card, font, cell.text(), ix + ICON + ICON_GAP, y, font.getWidth(cell.text()),
                            cell.color(), false));
                }
                x += cellWidth(font, cell);
                if (i + 1 < shown.cells().size()) {
                    int sx = x;
                    if (i < shown.heads()) HudDepth.onTop(card, () -> card.fill(sx + CELL_GAP, y - 1, sx + CELL_GAP + DIVIDER, y + 8, SOFT));
                    else if (shown.oneOf()) HudDepth.onTop(card, () -> UiText.line(card, font, "/", sx + CELL_GAP / 2, y, font.getWidth("/"), SOFT, false));
                    x += gap(font, shown, i);
                }
            }
        });
    }
}
