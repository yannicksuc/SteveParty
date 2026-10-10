package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.ToolHudPanel;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
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

    /**
     * The Tile Linker Brush HUD: the cartridge a new linked tile will get (and how many are left) when one is needed, a
     * plate with its level (and a warning when no cartridge is left), and the controls.
     */
    private static void describeHud(ToolHudPanel panel, ItemStack brush, MinecraftClient client) {
        int brushLevel = TileLinkerBrush.level(brush);
        Text level = brushLevel == TileLinkerBrush.POWERED ? Text.translatable("wheel.steveparty.brush.powered")
                : Text.translatable("wheel.steveparty.brush.levels.current", brushLevel);
        // A cartridge is only needed for a kind picked on the wheel (swapped in), or for an aimed space left empty
        boolean needed = TileLinkerBrush.cartridge(brush) != null || aimsAtEmptySpace(client, brush);
        if (needed) {
            ItemStack cartridge = BoardLinks.cartridgeSource(client.player);
            int left = BoardLinks.cartridgesLeft(client.player);
            if (!cartridge.isEmpty()) {
                // How many are left like a hotbar stack (nothing in creative)
                panel.item(cartridge, left >= 0 ? Integer.toString(left) : null);
            } else {
                // None left (survival): a red box with a greyed Cartridge, and a warning plate
                ItemStack kind = new ItemStack(BoardLinks.cartridgeKind(client.player));
                panel.box(ToolHud.Plate.RED, (context, x, y) -> {
                    context.drawItem(kind, x, y);
                    context.fill(x, y, x + 16, y + 16, 200, 0x80C4C4C4); // greyed
                });
                panel.state(BoardText.Plate.DEAD_END.of(Text.translatable("hud.steveparty.wrench.no_cartridge")), ToolHud.Plate.RED);
            }
        }
        panel.state(level, ToolHud.Plate.GREEN).hint(Text.translatable("hud.steveparty.tile_linker_brush.hint"));
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
