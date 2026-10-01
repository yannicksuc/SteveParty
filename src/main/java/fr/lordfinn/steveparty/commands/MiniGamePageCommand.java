package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Moderation of the pictures of the mini-game pages, operators (level 2):
 * <ul>
 *     <li>{@code /steveparty minigamepage list}: the pages with a picture, who sent it, and their id;</li>
 *     <li>{@code /steveparty minigamepage clearimage <uuid>}: removes the picture of a page.</li>
 * </ul>
 */
public final class MiniGamePageCommand {
    private static final int OP_LEVEL = 2;

    private static final SuggestionProvider<ServerCommandSource> PAGES_WITH_IMAGE = (context, builder) -> {
        for (MiniGamePageData page : MiniGamePages.all(context.getSource().getServer())) {
            if (page.image() != null) builder.suggest(page.id().toString(), Text.literal(page.title()));
        }
        return builder.buildFuture();
    };

    private MiniGamePageCommand() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("steveparty").then(literal("minigamepage")
                .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                .then(literal("list").executes(context -> list(context.getSource())))
                .then(literal("clearimage").then(argument("page", UuidArgumentType.uuid()).suggests(PAGES_WITH_IMAGE)
                        .executes(context -> clearImage(context.getSource(), UuidArgumentType.getUuid(context, "page")))))));
    }

    private static int list(ServerCommandSource source) {
        int count = 0;
        for (MiniGamePageData page : MiniGamePages.all(source.getServer())) {
            if (page.image() == null) continue;
            count++;
            String id = page.id().toString();
            MutableText line = Text.translatable("command.steveparty.minigamepage.list.entry",
                    page.hasTitle() ? Text.literal(page.title()) : Text.translatable("item.steveparty.mini_game_page"),
                    page.image().uploader(), Text.literal(id).formatted(Formatting.GRAY));
            source.sendFeedback(() -> line.styled(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/steveparty minigamepage clearimage " + id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("command.steveparty.minigamepage.list.hover")))), false);
        }
        if (count == 0) source.sendFeedback(() -> Text.translatable("command.steveparty.minigamepage.list.none"), false);
        return count;
    }

    private static int clearImage(ServerCommandSource source, UUID page) {
        MiniGamePageData data = MiniGamePages.get(source.getServer(), page);
        if (data.image() == null) {
            source.sendError(Text.translatable("command.steveparty.minigamepage.no_image", page.toString()));
            return 0;
        }
        String uploader = data.image().uploader();
        MiniGamePages.clearImage(source.getServer(), page);
        source.sendFeedback(() -> Text.translatable("command.steveparty.minigamepage.cleared",
                data.hasTitle() ? data.title() : page.toString(), uploader), true);
        return 1;
    }
}
