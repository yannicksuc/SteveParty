package fr.lordfinn.steveparty.client.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.items.custom.ChestCartridgeItem;
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
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Chest Cartridge on the client: with it in hand, its chest is outlined in the world (gold; red when it is no
 * container any more, a double chest whole), and the tools' HUD above the hotbar says which chest and how to choose
 * one.
 */
public final class ChestCartridgeClient {
    private static final int CHEST = 0xFFC52E, GONE = 0xFF4040;

    private ChestCartridgeClient() {
    }

    public static void initialize() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ChestCartridgeClient::render);
        HudRenderCallback.EVENT.register(ChestCartridgeClient::renderHud);
    }

    private static @Nullable ItemStack held(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack main = client.player.getMainHandStack(), off = client.player.getOffHandStack();
        return main.getItem() instanceof ChestCartridgeItem ? main : off.getItem() instanceof ChestCartridgeItem ? off : null;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack cartridge = held(client);
        MatrixStack matrices = context.matrixStack();
        if (cartridge == null || client.world == null || matrices == null) return;
        GlobalPos target = ChestCartridgeItem.target(cartridge);
        if (target == null || !target.dimension().equals(client.world.getRegistryKey())) return;
        BlockPos pos = target.pos();
        Box box = new Box(pos);
        BlockState state = client.world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
            box = box.union(new Box(pos.offset(ChestBlock.getFacing(state))));
        boolean bank = PartyBank.isBank(client.world.getBlockEntity(pos));
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        PageZoneClient.drawBox(matrices, consumers, context.camera().getPos(), box.expand(0.02), bank ? CHEST : GONE, 0.12f, null);
        consumers.draw();
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || client.player == null || client.world == null
                || !(client.player.getMainHandStack().getItem() instanceof ChestCartridgeItem)) return;
        GlobalPos target = ChestCartridgeItem.target(client.player.getMainHandStack());
        Text text;
        ToolHud.Plate plate = ToolHud.Plate.TEAL;
        if (target == null) {
            text = Text.translatable("hud.steveparty.chest_cartridge.none");
        } else if (!target.dimension().equals(client.world.getRegistryKey())) {
            text = Text.translatable("hud.steveparty.chest_cartridge.away", target.dimension().getValue().toString());
        } else {
            BlockPos pos = target.pos();
            text = Text.translatable("hud.steveparty.chest_cartridge.chest", pos.getX(), pos.getY(), pos.getZ());
            plate = ToolHud.Plate.GOLD;
        }
        Text shown = text;
        ToolHud.Plate shownPlate = plate;
        int top = ToolHud.rows(context, List.of(List.of(ToolHud.element(ToolHud.textPlateWidth(shown),
                (x, y) -> ToolHud.textPlate(context, x, y, shown, shownPlate)))), 4);
        ToolHud.hint(context, Text.translatable("hud.steveparty.chest_cartridge.hint"), context.getScaledWindowWidth() / 2, top);
    }
}
