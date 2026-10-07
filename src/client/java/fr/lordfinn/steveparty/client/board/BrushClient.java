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
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.payloads.custom.WrenchActionPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderTickCounter;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrench controls on the client: the mode key (R by default) and sneak + mouse wheel switch the mode; a small HUD above
 * the hotbar shows the mode, the chain and the board around while the Wrench is held.
 */
public final class WrenchClient {
    private static final KeyBinding MODE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(WrenchItem.MODE_KEY, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.steveparty"));
    private static final net.minecraft.util.Identifier TRACE_ICON = fr.lordfinn.steveparty.Steveparty.id("textures/particle/arrow.png");
    private static final ItemStack EDIT_ICON = new ItemStack(fr.lordfinn.steveparty.items.ModItems.WRENCH);
    private static final ItemStack CUT_ICON = new ItemStack(net.minecraft.item.Items.SHEARS);
    private static final int INSET = (ToolHud.BOX - 16) / 2;
    private static final ItemStack NO_CARTRIDGE_ICON = new ItemStack(fr.lordfinn.steveparty.items.ModItems.BOARD_SPACE_BEHAVIOR);

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
            if (clickCount <= 0 || client.currentScreen != null || !(holdsWrench(client) || holdsBrush(client))) return false;
            HitResult target = client.crosshairTarget;
            if (target != null && target.getType() != HitResult.Type.MISS) return false;
            send(player.isSneaking() ? WrenchActionPayload.Action.REDO : WrenchActionPayload.Action.UNDO, 1);
            return true;
        });
        HudRenderCallback.EVENT.register(WrenchClient::renderHud);
        HudRenderCallback.EVENT.register(WrenchClient::renderBrushHud);
        WrenchOverlay.initialize();
        BoardView.initialize();
    }

    static boolean holdsWrench(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() instanceof WrenchItem;
    }

    static boolean holdsBrush(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() instanceof TileLinkerBrushItem;
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
        if (client.currentScreen != null || client.player == null || !client.player.isSneaking()) return false;
        if (holdsBrush(client)) {
            if (vertical != 0) send(WrenchActionPayload.Action.LEVEL, vertical > 0 ? -1 : 1);
            return true;
        }
        if (!holdsWrench(client)) return false;
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

    /**
     * The Wrench HUD, in the tools' look (see {@link ToolHud}, shared with the Stencil Hammer): right above the hotbar,
     * a box with the mode's icon, a box with the cartridge the next new space will get (and how many are left), a
     * plate with the mode and what it is doing (chain, origin, edited slot), a plate summing up the board around
     * (green: fine, orange: dead ends or unreachable spaces), and the see-through hint. On two rows when too wide.
     */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || client.player == null || client.world == null || !holdsWrench(client)) return;
        ItemStack wrench = client.player.getMainHandStack();
        WrenchState state = WrenchState.of(wrench);
        BlockPos origin = WrenchActions.origin(wrench, client.world);
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
            detail = WrenchActions.slotText(originContainer, state.slot(), true);
        }
        Text mode = Text.translatable("hud.steveparty.wrench.panel", state.mode().displayName().copy().formatted(Formatting.RESET), detail);
        int[] counts = BoardView.counts();
        boolean problems = counts[1] + counts[2] > 0;
        Text board = boardSummary();

        int gap = 4;
        // The cartridge the next new space will get: none left in survival -> a red box and a hint
        ItemStack cartridge = BoardLinks.cartridgeSource(client.player);
        int left = BoardLinks.cartridgesLeft(client.player);
        ToolHud.Plate modePlate = switch (state.mode()) {
            case TRACE -> ToolHud.Plate.GREEN;
            case EDIT -> ToolHud.Plate.TEAL;
            case CUT -> ToolHud.Plate.RED;
        };
        List<ToolHud.Element> tool = new ArrayList<>();
        tool.add(ToolHud.element(ToolHud.BOX, (x, y) -> {
            ToolHud.box(context, x, y, true);
            modeIcon(context, state.mode(), x + INSET, y + INSET);
        }));
        tool.add(ToolHud.element(ToolHud.BOX, (x, y) -> cartridgeBox(context, x, y, cartridge, left)));
        if (cartridge.isEmpty()) {
            Text none = BoardText.Plate.DEAD_END.of(Text.translatable("hud.steveparty.wrench.no_cartridge"));
            tool.add(ToolHud.element(ToolHud.textPlateWidth(none), (x, y) -> ToolHud.textPlate(context, x, y, none, ToolHud.Plate.RED)));
        }
        tool.add(ToolHud.element(ToolHud.textPlateWidth(mode), (x, y) -> ToolHud.textPlate(context, x, y, mode, modePlate)));
        List<List<ToolHud.Element>> groups = new ArrayList<>(List.of(tool));
        if (board != null) {
            ToolHud.Plate boardPlate = problems ? ToolHud.Plate.ORANGE : ToolHud.Plate.GREEN;
            groups.add(List.of(ToolHud.element(ToolHud.textPlateWidth(board), (x, y) -> ToolHud.textPlate(context, x, y, board, boardPlate))));
        }
        // One row if it fits, else the board summary on a second row
        int y = ToolHud.rows(context, groups, gap);

        Text hint = state.mode() == WrenchMode.TRACE
                ? Text.translatable("hud.steveparty.wrench.hint.trace", MODE_KEY.getBoundKeyLocalizedText(),
                        Text.translatable(state.autoLink() ? "hud.steveparty.wrench.auto_link.on" : "hud.steveparty.wrench.auto_link.off"))
                : Text.translatable("hud.steveparty.wrench.hint", MODE_KEY.getBoundKeyLocalizedText());
        ToolHud.hint(context, hint, context.getScaledWindowWidth() / 2, y);
    }

    /**
     * The Tile Linker Brush HUD, in the tools' look: the brush, the cartridge a new linked tile will get (and how many
     * are left), a plate with its level, the board summary, and the controls.
     */
    private static void renderBrushHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || client.player == null || client.world == null || !holdsBrush(client)) return;
        ItemStack brush = client.player.getMainHandStack();
        Text level = Text.translatable("hud.steveparty.tile_linker_brush.panel", TileLinkerBrush.levelText(TileLinkerBrush.level(brush)));
        ItemStack cartridge = BoardLinks.cartridgeSource(client.player);
        int left = BoardLinks.cartridgesLeft(client.player);
        List<ToolHud.Element> tool = new ArrayList<>();
        tool.add(ToolHud.element(ToolHud.BOX, (x, y) -> {
            ToolHud.box(context, x, y, true);
            context.drawItem(brush, x + INSET, y + INSET);
        }));
        tool.add(ToolHud.element(ToolHud.BOX, (x, y) -> cartridgeBox(context, x, y, cartridge, left)));
        if (cartridge.isEmpty()) {
            Text none = BoardText.Plate.DEAD_END.of(Text.translatable("hud.steveparty.wrench.no_cartridge"));
            tool.add(ToolHud.element(ToolHud.textPlateWidth(none), (x, y) -> ToolHud.textPlate(context, x, y, none, ToolHud.Plate.RED)));
        }
        tool.add(ToolHud.element(ToolHud.textPlateWidth(level), (x, y) -> ToolHud.textPlate(context, x, y, level, ToolHud.Plate.GREEN)));
        List<List<ToolHud.Element>> groups = new ArrayList<>(List.of(tool));
        Text board = boardSummary();
        if (board != null) {
            ToolHud.Plate boardPlate = BoardView.counts()[1] + BoardView.counts()[2] > 0 ? ToolHud.Plate.ORANGE : ToolHud.Plate.GREEN;
            groups.add(List.of(ToolHud.element(ToolHud.textPlateWidth(board), (x, y) -> ToolHud.textPlate(context, x, y, board, boardPlate))));
        }
        int y = ToolHud.rows(context, groups, 4);
        ToolHud.hint(context, Text.translatable("hud.steveparty.tile_linker_brush.hint"), context.getScaledWindowWidth() / 2, y);
    }

    /** The board around, summed up (null: no board space around). */
    private static @org.jetbrains.annotations.Nullable Text boardSummary() {
        int[] counts = BoardView.counts();
        if (counts[0] == 0) return null;
        if (counts[1] + counts[2] == 0) {
            return BoardText.Plate.OK.of(Text.translatable("hud.steveparty.board.summary.ok", Text.translatable("hud.steveparty.board.spaces", counts[0])));
        }
        return Text.translatable("hud.steveparty.board.summary.problems",
                BoardText.Plate.NUMBER.of(Text.translatable("hud.steveparty.board.spaces", counts[0])),
                (counts[1] > 0 ? BoardText.Plate.DEAD_END : BoardText.Plate.MUTED).of(Text.translatable("hud.steveparty.board.dead_ends", counts[1])),
                (counts[2] > 0 ? BoardText.Plate.UNREACHABLE : BoardText.Plate.MUTED).of(Text.translatable("hud.steveparty.board.unreachable", counts[2])));
    }

    /**
     * The cartridge box: the icon of the cartridge the next new space will get, with how many are left (like a hotbar
     * stack; nothing in creative), or a red box with a greyed Cartridge when survival has none left (its hint is a
     * plate of its own).
     */
    private static void cartridgeBox(DrawContext context, int x, int y, ItemStack cartridge, int left) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!cartridge.isEmpty()) {
            ToolHud.box(context, x, y, false);
            context.drawItem(cartridge, x + INSET, y + INSET);
            if (left >= 0) context.drawItemInSlot(client.textRenderer, cartridge, x + INSET, y + INSET, Integer.toString(left));
            return;
        }
        ToolHud.plate(context, x, y, ToolHud.BOX, ToolHud.BOX, ToolHud.Plate.RED);
        context.drawItem(NO_CARTRIDGE_ICON, x + INSET, y + INSET);
        context.fill(x + INSET, y + INSET, x + INSET + 16, y + INSET + 16, 200, 0x80C4C4C4); // greyed
    }

    /** Trace: the board view's chevron; Edit: the Wrench; Cut: shears. */
    private static void modeIcon(DrawContext context, WrenchMode mode, int x, int y) {
        switch (mode) {
            case TRACE -> {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(0x3F / 255f, 0xB8 / 255f, 0x3F / 255f, 1f); // 0xFF3FB83F
                context.drawTexture(TRACE_ICON, x, y, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            }
            case EDIT -> context.drawItem(EDIT_ICON, x, y);
            case CUT -> context.drawItem(CUT_ICON, x, y);
        }
    }
}
