package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.ToolHudPanel;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;


/**
 * The Tile Linker Brush on the client: its wheel (left click, see {@link BrushWheel}), its HUD above the hotbar, the
 * aimed tile and the stroke shown in the world (see {@link BrushOverlay}), and the board view (with the Explorer's
 * Helmet's details, see {@link HelmetView}).
 */
public final class BrushClient {
    private BrushClient() {
    }

    public static void initialize() {
        ToolWheel.register(new BrushWheel());
        ToolHudPanel.register(stack -> stack.getItem() instanceof TileLinkerBrushItem, BrushClient::describeHud);
        ClientTickEvents.END_CLIENT_TICK.register(BrushClient::strokeTrail);
        ClientTickEvents.END_CLIENT_TICK.register(BrushClient::trackHeld);
        BrushOverlay.initialize();
        BrushTrail.initialize();
        BoardView.initialize();
        HelmetView.initialize();
        SpawnMarkerView.initialize();
    }

    /** While painting: a little dust of the paint's colour where the brush is, on the tile it paints. */
    private static void strokeTrail(MinecraftClient client) {
        if (client.world == null || !BrushTrail.painting(client.player)) return;
        BlockPos aimed = BrushAim.aimed(client.player, client.world, 1f, BrushOverlay.ghosts());
        if (aimed == null) return;
        Vec3d at = BrushOverlay.anchor(client.world, aimed);
        var random = client.world.getRandom();
        int rgb = BrushTrail.color(client.player.getActiveItem());
        var color = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        for (int i = 0; i < 2; i++) {
            client.world.addParticle(new DustParticleEffect(color, 1.2f),
                    at.x + (random.nextDouble() - 0.5) * 0.6, at.y - 0.1, at.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
    }

    /** How long the controls stay shown after the brush comes in hand: a reminder, not to read at every stroke. */
    private static final int HINT_TICKS = 100;
    /** The game time the brush came in hand, -1 while it isn't. */
    private static long heldSince = -1;
    private static final Text ARROW = Text.literal("→");

    /**
     * The Tile Linker Brush HUD, only what the next stroke does: in replacement mode (a kind of Cartridge picked on the
     * wheel) an orange chip "tile -> that cartridge" and its plate, turning red over a tile whose role it would wipe;
     * otherwise the cartridge an empty aimed tile will get. The level only when one is set, the controls only for a few
     * seconds after the brush comes in hand.
     */
    private static void describeHud(ToolHudPanel panel, ItemStack brush, MinecraftClient client) {
        int brushLevel = TileLinkerBrush.level(brush);
        if (TileLinkerBrush.cartridge(brush) != null) replacement(panel, brush, client);
        else if (aimsAtEmptySpace(client, brush)) cartridgeBox(panel, client);
        if (brushLevel != TileLinkerBrush.POWERED) {
            panel.state(Text.translatable("wheel.steveparty.brush.levels.current", brushLevel), ToolHud.Plate.GREEN);
        }
        if (heldSince < 0) heldSince = client.world.getTime();
        if (client.world.getTime() - heldSince < HINT_TICKS) panel.hint(Text.translatable("hud.steveparty.tile_linker_brush.hint"));
    }

    /** Forgets when the brush came in hand once it leaves it (the controls show again next time). */
    private static void trackHeld(MinecraftClient client) {
        if (client.player == null || !(client.player.getMainHandStack().getItem() instanceof TileLinkerBrushItem)) heldSince = -1;
    }

    /** The cartridge a new linked tile will get, and how many are left; a red box and a warning plate when none is. */
    private static void cartridgeBox(ToolHudPanel panel, MinecraftClient client) {
        ItemStack cartridge = BoardLinks.cartridgeSource(client.player);
        int left = BoardLinks.cartridgesLeft(client.player);
        if (!cartridge.isEmpty()) {
            // How many are left like a hotbar stack (nothing in creative)
            panel.item(cartridge, left >= 0 ? Integer.toString(left) : null);
        } else {
            ItemStack kind = new ItemStack(BoardLinks.cartridgeKind(client.player));
            panel.box(ToolHud.Plate.RED, (context, x, y) -> greyed(context, kind, x, y));
            noneLeft(panel);
        }
    }

    /**
     * Replacement mode: "[the aimed tile's cartridge, or a Tile] -> [the new cartridge]" in an orange chip with
     * "Replaces with: X"; red, with "Will replace: Y", when the aimed tile's cartridge has a role it would lose.
     */
    private static void replacement(ToolHudPanel panel, ItemStack brush, MinecraftClient client) {
        ItemStack source = BoardLinks.cartridgeSource(client.player);
        // The kind swapped in: the off hand's cartridge first (see WrenchActions.swapCartridge)
        Item kind = client.player.getOffHandStack().getItem() instanceof CartridgeItem
                ? client.player.getOffHandStack().getItem() : BoardLinks.cartridgeKind(client.player);
        ItemStack replaced = aimedCartridge(client, brush);
        boolean losesRole = !replaced.isEmpty() && !replaced.isOf(kind) && !replaced.isOf(ModItems.BOARD_SPACE_BEHAVIOR);
        ItemStack from = replaced.isEmpty() ? new ItemStack(ModBlocks.TILE) : replaced;
        ItemStack to = source.isEmpty() ? new ItemStack(kind) : source;
        int left = BoardLinks.cartridgesLeft(client.player);
        String count = left >= 0 ? Integer.toString(left) : null;
        TextRenderer font = client.textRenderer;
        int arrow = font.getWidth(ARROW);
        int toOffset = 16 + 3 + arrow + 3;
        ToolHud.Plate frame = source.isEmpty() || losesRole ? ToolHud.Plate.RED : ToolHud.Plate.ORANGE;
        panel.box(frame, ToolHudPanel.INSET * 2 + toOffset + 16, (context, x, y) -> {
            context.drawItem(from, x, y);
            UiText.line(context, font, ARROW, x + 19, y + 4, arrow, ToolHud.TEXT, false);
            if (source.isEmpty()) {
                greyed(context, to, x + toOffset, y);
            } else {
                context.drawItem(to, x + toOffset, y);
                if (count != null) context.drawItemInSlot(font, to, x + toOffset, y, count);
            }
        });
        if (source.isEmpty()) noneLeft(panel);
        else panel.state(Text.translatable("hud.steveparty.tile_linker_brush.replace", to.getName()), ToolHud.Plate.ORANGE);
        if (losesRole) {
            panel.state(Text.translatable("hud.steveparty.tile_linker_brush.will_replace", replaced.getName()), ToolHud.Plate.RED);
        }
    }

    /** A cartridge none of which is left: greyed. */
    private static void greyed(DrawContext context, ItemStack stack, int x, int y) {
        context.drawItem(stack, x, y);
        context.fill(x, y, x + 16, y + 16, 200, 0x80C4C4C4);
    }

    private static void noneLeft(ToolHudPanel panel) {
        panel.state(BoardText.Plate.DEAD_END.of(Text.translatable("hud.steveparty.wrench.no_cartridge")), ToolHud.Plate.RED);
    }

    /** The cartridge in the brush's slot of the board space aimed at, or an empty stack. */
    private static ItemStack aimedCartridge(MinecraftClient client, ItemStack brush) {
        BlockPos aimed = BrushAim.aimed(client.player, client.world, 1f);
        if (aimed == null) return ItemStack.EMPTY;
        var container = BoardLinks.container(client.world, aimed);
        if (!(container instanceof BoardSpaceBlockEntity)) return ItemStack.EMPTY;
        return container.getStack(BoardLinks.slotOf(container, TileLinkerBrush.level(brush)));
    }

    /** Whether the brush aims at a board space whose slot (the brush's level) holds no cartridge. */
    private static boolean aimsAtEmptySpace(MinecraftClient client, ItemStack brush) {
        BlockPos aimed = BrushAim.aimed(client.player, client.world, 1f);
        if (aimed == null) return false;
        var container = BoardLinks.container(client.world, aimed);
        return container instanceof BoardSpaceBlockEntity
                && container.getStack(BoardLinks.slotOf(container, TileLinkerBrush.level(brush))).isEmpty();
    }
}
