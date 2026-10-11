package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /steveparty links}, for everyone: how things are linked, once, in the chat, so that the screens and tooltips
 * only point here instead of explaining it each time. Two parts: linking with the Tile Linker Brush (see
 * {@code BrushLinks}, {@code CartridgeLinks}), and linking by clicking with a Cartridge or a Mini-game Page in hand
 * (see {@code CartridgeItem#useOnBlock}, {@code InventoryCartridgeItem}, {@code MiniGamePipes#click},
 * {@code Podiums#clickLink}).
 */
public final class LinksCommand {
    /** The command, without its slash (what a screen sends). */
    public static final String COMMAND = "steveparty links";
    private static final String KEY = "command.steveparty.links.";
    /** The lines of each part ({@code command.steveparty.links.<part>.<n>}). */
    public static final int BRUSH_LINES = 7;
    public static final int CLICK_LINES = 5;

    private LinksCommand() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("steveparty").then(literal("links").executes(context -> {
            ServerCommandSource source = context.getSource();
            for (Text line : lines()) source.sendFeedback(() -> line, false);
            return 1;
        })));
    }

    /** What the command says, in order. */
    public static List<Text> lines() {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(KEY + "title").formatted(Formatting.GOLD, Formatting.BOLD));
        part(lines, "brush", BRUSH_LINES);
        part(lines, "click", CLICK_LINES);
        return lines;
    }

    private static void part(List<Text> lines, String part, int count) {
        lines.add(Text.translatable(KEY + part + ".title").formatted(Formatting.YELLOW));
        for (int n = 1; n <= count; n++) {
            MutableText line = Text.literal(" • ").formatted(Formatting.DARK_GRAY)
                    .append(Text.translatable(KEY + part + "." + n).formatted(Formatting.GRAY));
            lines.add(line);
        }
    }

    /** Every key the command uses (tests). */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY + "title");
        for (String part : new String[]{"brush", "click"}) {
            keys.add(KEY + part + ".title");
            int count = part.equals("brush") ? BRUSH_LINES : CLICK_LINES;
            for (int n = 1; n <= count; n++) keys.add(KEY + part + "." + n);
        }
        return keys;
    }
}
