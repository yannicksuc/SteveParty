package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardText;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.util.hit.HitResult;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.board.WrenchMode;
import fr.lordfinn.steveparty.board.WrenchState;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.payloads.custom.WrenchActionPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Wrench controls on the client: the mode key (R by default) and sneak + mouse wheel switch the mode; a small HUD next
 * to the hotbar shows the mode and the chain while the Wrench is held.
 */
public final class WrenchClient {
    private static final KeyBinding MODE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(WrenchItem.MODE_KEY, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.steveparty"));
    private static final int HINT_COLOR = 0xAADDDDDD;

    private WrenchClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (MODE_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null && holdsWrench(client)) {
                    send(client.player.isSneaking() ? WrenchActionPayload.Action.AUTO_LINK : WrenchActionPayload.Action.MODE, 1);
                }
            }
        });
        // Left click in the air: undo (sneaking: redo). Left click on a block keeps breaking it.
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount <= 0 || client.currentScreen != null || !holdsWrench(client)) return false;
            HitResult target = client.crosshairTarget;
            if (target != null && target.getType() != HitResult.Type.MISS) return false;
            send(player.isSneaking() ? WrenchActionPayload.Action.REDO : WrenchActionPayload.Action.UNDO, 1);
            return true;
        });
        HudRenderCallback.EVENT.register(WrenchClient::renderHud);
        WrenchOverlay.initialize();
        BoardView.initialize();
    }

    static boolean holdsWrench(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() instanceof WrenchItem;
    }

    private static void send(WrenchActionPayload.Action action, int direction) {
        ClientPlayNetworking.send(new WrenchActionPayload(action, direction));
    }

    /**
     * Mouse wheel hook: sneaking with the Wrench in the main hand, the wheel switches the mode instead of the hotbar slot.
     *
     * @return true if the scroll was used
     */
    public static boolean onScroll(double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || !client.player.isSneaking() || !holdsWrench(client)) return false;
        if (vertical == 0) return true;
        send(editsSlots(client) ? WrenchActionPayload.Action.SLOT : WrenchActionPayload.Action.MODE, vertical > 0 ? -1 : 1);
        return true;
    }

    /** The origin holds several cartridges (Advanced Tile, check point): the wheel picks the edited one. */
    private static boolean editsSlots(MinecraftClient client) {
        ItemStack wrench = client.player.getMainHandStack();
        if (WrenchState.of(wrench).mode() == WrenchMode.CUT || client.world == null) return false;
        BlockPos origin = WrenchActions.origin(wrench, client.world);
        CartridgeContainerBlockEntity container = origin == null ? null : BoardLinks.container(client.world, origin);
        return container != null && container.size() > 1;
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || client.player == null || client.world == null || !holdsWrench(client)) return;
        ItemStack wrench = client.player.getMainHandStack();
        WrenchState state = WrenchState.of(wrench);
        BlockPos origin = WrenchActions.origin(wrench, client.world);
        // Top left corner, out of the way of the hotbar and the action bar messages
        int x = 4;
        int y = 24;
        Text mode = Text.translatable("hud.steveparty.wrench.mode", state.mode().displayName());
        Text detail;
        if (origin == null) {
            detail = Text.translatable("hud.steveparty.wrench.no_origin." + state.mode().asString());
        } else if (state.mode() == WrenchMode.TRACE && state.chainLength() > 0) {
            detail = Text.translatable("hud.steveparty.wrench.chain", state.chainLength());
        } else {
            detail = Text.translatable("hud.steveparty.wrench.origin", BoardText.pos(origin));
        }
        CartridgeContainerBlockEntity originContainer = origin == null ? null : BoardLinks.container(client.world, origin);
        if (originContainer != null && originContainer.size() > 1 && state.mode() != WrenchMode.CUT) {
            detail = WrenchActions.slotText(originContainer, state.slot());
        }
        context.drawTextWithShadow(client.textRenderer, mode, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, detail.copy().formatted(Formatting.GRAY), x, y + 10, 0xFFFFFFFF);
        Text hint = state.mode() == WrenchMode.TRACE
                ? Text.translatable("hud.steveparty.wrench.hint.trace", MODE_KEY.getBoundKeyLocalizedText(),
                        Text.translatable(state.autoLink() ? "hud.steveparty.wrench.auto_link.on" : "hud.steveparty.wrench.auto_link.off"))
                : Text.translatable("hud.steveparty.wrench.hint", MODE_KEY.getBoundKeyLocalizedText());
        context.drawTextWithShadow(client.textRenderer, hint, x, y - 10, HINT_COLOR);
        // The board view around: problems at a glance
        int[] counts = BoardView.counts();
        if (counts[0] > 0) {
            Text board = Text.translatable("hud.steveparty.board.counts", counts[0], counts[1], counts[2]);
            context.drawTextWithShadow(client.textRenderer, board, x, y - 20, counts[1] + counts[2] > 0 ? 0xFFFFB050 : 0xFF90FF90);
        }
    }
}
