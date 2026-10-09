package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.client.board.WorldDraw;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.minigame.PageZoneClient;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Inventory Cartridge on the client, in either hand: each of its containers is outlined in the world with its
 * number in the list (gold; red when it is no container any more; a double chest whole), and the tools' HUD above the
 * hotbar says how many it has and how a click is read. Its destinations are shown as every cartridge's
 * (DestinationsRenderer).
 */
public final class InventoryCartridgeClient {
    private static final int CONTAINER = 0xFFC52E, GONE = 0xFF4040;
    private static final float LABEL_SCALE = 1f / 40f;

    private InventoryCartridgeClient() {
    }

    public static void initialize() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(InventoryCartridgeClient::render);
        HudRenderCallback.EVENT.register(InventoryCartridgeClient::renderHud);
    }

    private static @Nullable ItemStack held(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack main = client.player.getMainHandStack(), off = client.player.getOffHandStack();
        return CartridgeContainers.linksContainers(main) ? main : CartridgeContainers.linksContainers(off) ? off : null;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack cartridge = held(client);
        MatrixStack matrices = context.matrixStack();
        if (cartridge == null || client.world == null || matrices == null) return;
        List<GlobalPos> containers = CartridgeContainers.of(cartridge, client.world.getRegistryKey());
        if (containers.isEmpty()) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        for (int i = 0; i < containers.size(); i++) {
            GlobalPos target = containers.get(i);
            if (!target.dimension().equals(client.world.getRegistryKey())) continue;
            BlockPos pos = target.pos();
            Box box = new Box(pos);
            BlockState state = client.world.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
                box = box.union(new Box(pos.offset(ChestBlock.getFacing(state))));
            boolean container = client.world.getBlockEntity(pos) instanceof Inventory;
            PageZoneClient.drawBox(matrices, consumers, context.camera().getPos(), box.expand(0.02), container ? CONTAINER : GONE, 0.12f, null);
            consumers.draw();
            // Its number in the list, above it
            WorldDraw.plateLabel(matrices, consumers, context.camera(), new Vec3d(pos.getX() + 0.5, box.maxY + 0.45, pos.getZ() + 0.5),
                    Text.literal(Integer.toString(i + 1)), container ? WorldDraw.Plate.GOLD : WorldDraw.Plate.RED, WorldDraw.PLATE_TEXT, LABEL_SCALE);
        }
        consumers.draw();
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack cartridge = held(client);
        if (client.options.hudHidden || client.currentScreen != null || cartridge == null || client.world == null) return;
        int count = CartridgeContainers.of(cartridge, client.world.getRegistryKey()).size();
        Text text = count == 0 ? Text.translatable("hud.steveparty.inventory_cartridge.none")
                : Text.translatable("hud.steveparty.inventory_cartridge.count", count, CartridgeContainers.MAX);
        ToolHud.Plate plate = count == 0 ? ToolHud.Plate.TEAL : ToolHud.Plate.GOLD;
        int top = ToolHud.rows(context, List.of(List.of(ToolHud.element(ToolHud.textPlateWidth(text),
                (x, y) -> ToolHud.textPlate(context, x, y, text, plate)))), 4);
        ToolHud.hint(context, Text.translatable("hud.steveparty.inventory_cartridge.hint"), context.getScaledWindowWidth() / 2, top);
    }
}
