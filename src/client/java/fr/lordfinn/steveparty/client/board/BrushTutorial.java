package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBoard;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.board.BrushTutorialSteps.Step;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The Tile Linker Brush's tutorial: while the brush is in hand and it is not done, a vanilla-like tutorial toast (top
 * right, see {@link BrushTutorialToast}) shows the next step, until the player does it (the server tells each link
 * painted or erased: {@link fr.lordfinn.steveparty.payloads.custom.BrushTutorialPayload}). Recommended order: link the
 * Party Controller to a start tile, the start tiles to the board, the tiles together ({@link #TILE_LINKS} links, a
 * progress bar), then remove a link; a step whose blocks are not around (no Party Controller, no start tile within
 * {@link #NEAR} blocks) waits, the next one shows, and it comes back once the others are done. A step done in any order
 * counts.
 * <p>
 * Remembered per player on this computer (the config folder); {@code /brushtutorial reset} starts it again.
 */
public final class BrushTutorial {
    /** Tiles to link for the « link the tiles » step. */
    static final int TILE_LINKS = 4;
    /** The blocks a step needs are looked for this far around the player. */
    private static final int NEAR = 32;
    /** How often what is around is looked at again (ticks). */
    private static final int SCAN_TICKS = 20;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("steveparty-tutorials.properties");
    private static final String KEY = "toast.steveparty.brush_tutorial.";

    private static @Nullable Properties properties;
    private static @Nullable BrushTutorialToast toast;
    private static boolean controllerNear, startNear;
    private static int scanAge = SCAN_TICKS;
    /** No toast during the first ticks in a world (what is around is not received yet). */
    private static final int SETTLE_TICKS = 40;
    private static @Nullable ClientWorld world;
    private static int worldTicks;

    private BrushTutorial() {
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(BrushTutorial::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("brushtutorial").then(ClientCommandManager.literal("reset").executes(context -> {
                    reset();
                    context.getSource().sendFeedback(Text.translatable(KEY + "reset"));
                    return 1;
                }))));
    }

    // ---------------------------------------------------------------- the steps

    /** The server told a step done by the player. */
    public static void onStep(int ordinal) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || ordinal < 0 || ordinal >= Step.values().length || finished()) return;
        Step step = Step.values()[ordinal];
        if (done(step)) return;
        if (step == Step.TILES) set(step, Integer.toString(Math.min(TILE_LINKS, tileLinks() + 1)));
        else set(step, "done");
        if (toast != null && toast.step == step) {
            toast.progress(progress(step));
            if (done(step)) toast.done();
        }
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            toast = null;
            world = null;
            return;
        }
        // A world just joined: its chunks and block entities come first, the first toast fits what is around
        if (client.world != world) {
            world = client.world;
            worldTicks = 0;
        }
        if (worldTicks < SETTLE_TICKS) {
            worldTicks++;
            return;
        }
        boolean holding = TileLinkerBrush.isBrush(client.player.getMainHandStack());
        if (!holding || finished()) {
            if (toast != null) toast.hide();
            toast = null;
            scanAge = SCAN_TICKS;
            return;
        }
        if (++scanAge >= SCAN_TICKS) {
            scanAge = 0;
            scan(client.world, client.player.getPos());
        }
        if (toast != null && !toast.hidden()) {
            // A step done is shown full, then leaves (the toast hides itself); one no longer next gives way
            if (done(toast.step) || toast.step == next()) return;
            toast.hide();
        }
        Step next = next();
        if (next == null) return;
        toast = new BrushTutorialToast(next, new ItemStack(ModItems.TILE_LINKER_BRUSH),
                Text.translatable(KEY + key(next)), Text.translatable(KEY + key(next) + ".line"), next == Step.TILES, progress(next));
        client.getToastManager().add(toast);
    }

    /**
     * The step to show: the first not done whose blocks are around (linking the controller needs a controller and a
     * start tile, linking the starts a start tile); none of them: the first not done.
     */
    private static @Nullable Step next() {
        Step first = null;
        for (Step step : Step.values()) {
            if (done(step)) continue;
            if (first == null) first = step;
            boolean around = switch (step) {
                case CONTROLLER -> controllerNear && startNear;
                case STARTS -> startNear;
                case TILES, ERASE -> true;
            };
            if (around) return step;
        }
        return first;
    }

    /** Whether a Party Controller and a start tile are within {@link #NEAR} blocks of {@code center}. */
    private static void scan(ClientWorld world, Vec3d center) {
        controllerNear = startNear = false;
        double radius2 = (double) NEAR * NEAR;
        for (int chunkX = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.x - NEAR)); chunkX <= ChunkSectionPos.getSectionCoord(MathHelper.floor(center.x + NEAR)); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.z - NEAR)); chunkZ <= ChunkSectionPos.getSectionCoord(MathHelper.floor(center.z + NEAR)); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (Vec3d.ofCenter(blockEntity.getPos()).squaredDistanceTo(center) > radius2) continue;
                    if (blockEntity instanceof PartyControllerEntity) controllerNear = true;
                    else if (blockEntity instanceof BoardSpaceBlockEntity && PartyBoard.isStart(world, blockEntity.getPos())) startNear = true;
                    if (controllerNear && startNear) return;
                }
            }
        }
    }

    private static String key(Step step) {
        return step.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static float progress(Step step) {
        if (step == Step.TILES) return tileLinks() / (float) TILE_LINKS;
        return done(step) ? 1 : 0;
    }

    private static int tileLinks() {
        try {
            return Integer.parseInt(get(Step.TILES));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean done(Step step) {
        return step == Step.TILES ? tileLinks() >= TILE_LINKS : "done".equals(get(step));
    }

    /** Every step done: the tutorial is over for this player. */
    static boolean finished() {
        for (Step step : Step.values()) if (!done(step)) return false;
        return true;
    }

    /** Starts the tutorial again for this player. */
    static void reset() {
        String prefix = prefix();
        if (prefix == null) return;
        properties().keySet().removeIf(key -> key.toString().startsWith(prefix));
        save();
        if (toast != null) toast.hide();
        toast = null;
    }

    // ---------------------------------------------------------------- memory (per player, this computer)

    private static @Nullable String prefix() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? null : client.player.getUuidAsString() + ".brush.";
    }

    private static String get(Step step) {
        String prefix = prefix();
        return prefix == null ? "" : properties().getProperty(prefix + key(step), "");
    }

    private static void set(Step step, String value) {
        String prefix = prefix();
        if (prefix == null) return;
        properties().setProperty(prefix + key(step), value);
        save();
    }

    private static Properties properties() {
        if (properties == null) {
            properties = new Properties();
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    properties.load(reader);
                } catch (IOException e) {
                    Steveparty.LOGGER.warn("Could not read {}: {}", FILE, e.getMessage());
                }
            }
        }
        return properties;
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            properties().store(writer, "Steve Party: the tutorials each player has done");
        } catch (IOException e) {
            Steveparty.LOGGER.warn("Could not write {}: {}", FILE, e.getMessage());
        }
    }
}
