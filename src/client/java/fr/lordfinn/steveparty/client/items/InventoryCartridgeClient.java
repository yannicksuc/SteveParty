package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.minigame.ZoneCartridgeClient;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Inventory Cartridge on the client, in either hand: the container it remembers is outlined in the world (gold;
 * red when it is no container any more; a double chest whole), and the tools' HUD above the hotbar says which one
 * and how to choose one. The same for a board space's cartridge and for a Party Controller's bank.
 */
public final class InventoryCartridgeClient {
    private static final int CONTAINER = 0xFFC52E, GONE = 0xFF4040;

    private InventoryCartridgeClient() {
    }

    public static void initialize() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(InventoryCartridgeClient::render);
        HudRenderCallback.EVENT.register(InventoryCartridgeClient::renderHud);
    }

    private static @Nullable ItemStack held(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack main = client.player.getMainHandStack(), off = client.player.getOffHandStack();
        return main.getItem() instanceof InventoryCartridgeItem ? main : off.getItem() instanceof InventoryCartridgeItem ? off : null;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack cartridge = held(client);
        MatrixStack matrices = context.matrixStack();
        if (cartridge == null || client.world == null || matrices == null) return;
        GlobalPos target = InventoryCartridgeItem.getSavedContainer(cartridge, client.world.getRegistryKey());
        if (target == null || !target.dimension().equals(client.world.getRegistryKey())) return;
        BlockPos pos = target.pos();
        Box box = new Box(pos);
        BlockState state = client.world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
            box = box.union(new Box(pos.offset(ChestBlock.getFacing(state))));
        boolean container = client.world.getBlockEntity(pos) instanceof Inventory;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        ZoneCartridgeClient.drawBox(matrices, consumers, context.camera().getPos(), box.expand(0.02), container ? CONTAINER : GONE, 0.12f, null);
        consumers.draw();
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack cartridge = held(client);
        if (client.options.hudHidden || client.currentScreen != null || cartridge == null || client.world == null) return;
        GlobalPos target = InventoryCartridgeItem.getSavedContainer(cartridge, client.world.getRegistryKey());
        Text text;
        ToolHud.Plate plate = ToolHud.Plate.TEAL;
        if (target == null) {
            text = Text.translatable("hud.steveparty.inventory_cartridge.none");
        } else if (!target.dimension().equals(client.world.getRegistryKey())) {
            text = Text.translatable("hud.steveparty.inventory_cartridge.away", target.dimension().getValue().toString());
        } else {
            BlockPos pos = target.pos();
            text = Text.translatable("hud.steveparty.inventory_cartridge.chest", pos.getX(), pos.getY(), pos.getZ());
            plate = ToolHud.Plate.GOLD;
        }
        Text shown = text;
        ToolHud.Plate shownPlate = plate;
        int top = ToolHud.rows(context, List.of(List.of(ToolHud.element(ToolHud.textPlateWidth(shown),
                (x, y) -> ToolHud.textPlate(context, x, y, shown, shownPlate)))), 4);
        ToolHud.hint(context, Text.translatable("hud.steveparty.inventory_cartridge.hint"), context.getScaledWindowWidth() / 2, top);
    }
}
