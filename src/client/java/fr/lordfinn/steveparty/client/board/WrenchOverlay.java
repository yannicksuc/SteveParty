package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.TeleportLinks;
import fr.lordfinn.steveparty.client.renderer.GlowingCuboidRenderer;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.board.WrenchMode;
import fr.lordfinn.steveparty.board.WrenchState;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the Wrench shows its holder (and only them: everything is drawn client side): the origin framed, and the ghost
 * line from the origin to the board space aimed at, coloured by what a click would do, with a label (« Link 7 → 8 »,
 * « Close the loop »...).
 */
final class WrenchOverlay {
    static final int GREEN = 0xFF4CFF4C;
    static final int RED = 0xFFFF4040;
    static final int BLUE = 0xFF4CA6FF;
    static final int GOLD = 0xFFFFD83D;
    static final int PURPLE = 0xFFB266FF;
    private static final float LABEL_SCALE = 1f / 32f;

    private WrenchOverlay() {
    }

    static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(WrenchOverlay::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        if (player == null || world == null || matrices == null) return;
        ItemStack main = player.getMainHandStack();
        ItemStack wrench = main.getItem() instanceof WrenchItem ? main
                : player.getOffHandStack().getItem() instanceof WrenchItem ? player.getOffHandStack() : null;
        if (wrench == null) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        WrenchState state = WrenchState.of(wrench);
        BlockPos origin = WrenchActions.origin(wrench, world);
        if (origin != null && BoardLinks.container(world, origin) == null) origin = null;

        if (origin != null) frame(matrices, consumers, camera, world, origin,
                state.mode() == WrenchMode.TRACE ? GREEN : state.mode() == WrenchMode.TELEPORT ? PURPLE : BLUE);
        if (wrench == main) {
            BlockPos aimed = aimed(player, world, context.tickCounter().getTickDelta(true));
            if (aimed != null) ghost(matrices, consumers, camera, world, state, origin, aimed);
        }
        consumers.draw();
    }

    private static @Nullable BlockPos aimed(ClientPlayerEntity player, ClientWorld world, float tickDelta) {
        HitResult hit = player.raycast(WrenchActions.LONG_REACH, tickDelta, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = BoardSpaces.resolve(world, blockHit.getBlockPos());
        return BoardLinks.container(world, pos) != null ? pos : null;
    }

    /** Where links start and end on a board space: a little above the middle of its surface. */
    static Vec3d anchor(ClientWorld world, BlockPos pos) {
        return BoardSpaces.standPos(world, pos).add(0, 0.2, 0);
    }

    /** A pulsing highlight on a board space, where it is seen (lowered, sloped, all 4 blocks of a large tile). */
    static void frame(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world, BlockPos pos, int argb) {
        float pulse = 0.25f + 0.15f * (float) Math.sin(world.getTime() / 3.0);
        GlowingCuboidRenderer.drawBlockBox(matrices, consumers, pos,
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, pulse);
    }

    private static void ghost(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera, ClientWorld world,
                              WrenchState state, @Nullable BlockPos origin, BlockPos aimed) {
        CartridgeContainerBlockEntity target = BoardLinks.container(world, aimed);
        if (target == null) return;
        Vec3d labelPos = anchor(world, aimed).add(0, 1.35, 0);
        if (state.mode() == WrenchMode.TELEPORT) {
            teleportGhost(matrices, consumers, camera, world, state, origin, aimed, target, labelPos);
            return;
        }
        if (state.mode() == WrenchMode.CUT) {
            List<BlockPos> links = BoardLinks.links(target, BoardLinks.slotOf(target, WrenchState.ACTIVE_SLOT));
            for (BlockPos link : links) ghostPath(matrices, consumers, camera, world, aimed, link, RED);
            label(matrices, consumers, camera, labelPos, links.isEmpty()
                    ? Text.translatable("hud.steveparty.wrench.ghost.cut_none")
                    : Text.translatable("hud.steveparty.wrench.ghost.cut", links.size()), links.isEmpty() ? 0xFFAAAAAA : RED, LABEL_SCALE);
            return;
        }
        if (origin == null) {
            int existing = BoardLinks.links(target, BoardLinks.slotOf(target, WrenchState.ACTIVE_SLOT)).size();
            Text text = state.mode() == WrenchMode.EDIT ? Text.translatable("hud.steveparty.wrench.ghost.bind")
                    : existing > 0 ? Text.translatable("hud.steveparty.wrench.ghost.fork", existing)
                    : Text.translatable("hud.steveparty.wrench.ghost.start");
            frame(matrices, consumers, camera, world, aimed, 0xFFFFFFFF);
            label(matrices, consumers, camera, labelPos, text, 0xFFFFFFFF, LABEL_SCALE);
            return;
        }
        if (aimed.equals(origin)) {
            Text text = Text.translatable(state.mode() == WrenchMode.TRACE ? "hud.steveparty.wrench.ghost.end" : "hud.steveparty.wrench.ghost.unbind");
            label(matrices, consumers, camera, labelPos, text, 0xFFAAAAAA, LABEL_SCALE);
            return;
        }
        CartridgeContainerBlockEntity from = BoardLinks.container(world, origin);
        if (from == null) return;
        boolean linked = BoardLinks.links(from, BoardLinks.slotOf(from, state.slot())).contains(aimed);
        boolean boardSpace = target instanceof BoardSpaceBlockEntity;
        int n = Math.max(1, state.chainLength());
        int color;
        Text text;
        if (state.mode() == WrenchMode.EDIT) {
            color = linked ? RED : boardSpace ? GREEN : RED;
            text = linked ? Text.translatable("hud.steveparty.wrench.ghost.remove")
                    : boardSpace ? Text.translatable("hud.steveparty.wrench.ghost.add")
                    : Text.translatable("hud.steveparty.wrench.ghost.not_board_space");
        } else if (!boardSpace) {
            color = RED;
            text = Text.translatable("hud.steveparty.wrench.ghost.not_board_space");
        } else if (linked) {
            color = BLUE;
            text = Text.translatable("hud.steveparty.wrench.ghost.walk", n + 1);
        } else if (state.chainStart().map(aimed::equals).orElse(false)) {
            color = GOLD;
            text = Text.translatable("hud.steveparty.wrench.ghost.loop", n);
        } else if (!BoardLinks.links(target, BoardLinks.slotOf(target, WrenchState.ACTIVE_SLOT)).isEmpty()) {
            color = GOLD;
            text = Text.translatable("hud.steveparty.wrench.ghost.join", n, n + 1);
        } else {
            color = GREEN;
            text = Text.translatable("hud.steveparty.wrench.ghost.link", n, n + 1);
        }
        ghostPath(matrices, consumers, camera, world, origin, aimed, color);
        frame(matrices, consumers, camera, world, aimed, color);
        label(matrices, consumers, camera, labelPos, text, color, LABEL_SCALE);
    }

    /**
     * Teleport mode: picking a Teleport tile (purple, or red if it is none and can't be made one), then adding (purple
     * arc) or removing (red arc) an arrival; the Teleport tile itself: unbind.
     */
    private static void teleportGhost(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world,
                                      WrenchState state, @Nullable BlockPos origin, BlockPos aimed,
                                      CartridgeContainerBlockEntity target, Vec3d labelPos) {
        CartridgeContainerBlockEntity from = origin == null ? null : BoardLinks.container(world, origin);
        int slot = from == null ? 0 : BoardLinks.slotOf(from, state.slot());
        if (from == null || !TeleportLinks.isTeleportCartridge(from.getStack(slot))) {
            ItemStack current = target.getStack(BoardLinks.slotOf(target, WrenchState.ACTIVE_SLOT));
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            boolean canPick = target instanceof BoardSpaceBlockEntity && (TeleportLinks.isTeleportCartridge(current)
                    || (player != null && (TeleportLinks.isTeleportCartridge(player.getOffHandStack())
                    || (current.isEmpty() && player.getAbilities().creativeMode))));
            frame(matrices, consumers, camera, world, aimed, canPick ? PURPLE : RED);
            label(matrices, consumers, camera, labelPos, Text.translatable(canPick ? "hud.steveparty.wrench.ghost.teleport.pick"
                    : "hud.steveparty.wrench.ghost.teleport.not_teleport"), canPick ? PURPLE : RED, LABEL_SCALE);
            return;
        }
        if (aimed.equals(origin)) {
            label(matrices, consumers, camera, labelPos, Text.translatable("hud.steveparty.wrench.ghost.unbind"), 0xFFAAAAAA, LABEL_SCALE);
            return;
        }
        if (!(target instanceof BoardSpaceBlockEntity)) {
            label(matrices, consumers, camera, labelPos, Text.translatable("hud.steveparty.wrench.ghost.not_board_space"), RED, LABEL_SCALE);
            return;
        }
        boolean linked = TeleportLinks.targets(from, slot).contains(aimed);
        int color = linked ? RED : PURPLE;
        Vec3d a = anchor(world, origin).add(0, 0.05, 0), b = anchor(world, aimed).add(0, 0.05, 0);
        double phase = (world.getTime() + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true)) / 20.0 * 3.0;
        WorldDraw.arc(matrices, consumers, camera, a, b, TeleportLinks.arcHeight(a.distanceTo(b)), color, 0xFFFFFFFF, phase, 0.11, 0.3);
        frame(matrices, consumers, camera, world, aimed, color);
        label(matrices, consumers, camera, labelPos, Text.translatable(linked ? "hud.steveparty.wrench.ghost.teleport.remove"
                : "hud.steveparty.wrench.ghost.teleport.add"), color, LABEL_SCALE);
    }

    /** The path the click would make: chevrons of the board view, bigger and faster, in the colour of what it does. */
    private static void ghostPath(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world,
                                  BlockPos from, BlockPos to, int color) {
        double phase = (world.getTime() + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true)) / 20.0 * 3.0;
        WorldDraw.path(matrices, consumers, camera, anchor(world, from).add(0, 0.05, 0), anchor(world, to).add(0, 0.05, 0),
                color, 0.55, 0.5, phase, 0.4, 0);
    }

    /** A label on a plate framed in the colour of what the click would do. */
    private static void label(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d pos, Text text, int color, float scale) {
        WorldDraw.Plate plate = switch (color) {
            case GREEN -> WorldDraw.Plate.GREEN;
            case GOLD -> WorldDraw.Plate.GOLD;
            case RED -> WorldDraw.Plate.RED;
            case PURPLE -> WorldDraw.Plate.PURPLE;
            default -> WorldDraw.Plate.TEAL;
        };
        // Readable from afar, like the board view's numbers
        float grow = (float) Math.clamp(Math.sqrt(pos.squaredDistanceTo(camera.getPos())) / 7.0, 1.0, 3.0);
        WorldDraw.plateLabel(matrices, consumers, camera, pos, text, plate, WorldDraw.PLATE_TEXT, scale * grow);
    }
}
