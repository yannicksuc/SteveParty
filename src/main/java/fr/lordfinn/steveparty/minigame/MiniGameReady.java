package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * « Ready »: a player of a mini-game says so (the key, Y by default; the chat's [Ready] button; « Y » typed in the
 * chat). Before a round, while its description is told, it skips the rest once everyone is ({@link MiniGameFreeze#vote});
 * during a practice round, it votes for the real round ({@link MiniGamePartyStep#toggleReady(ServerPlayerEntity)}).
 */
public final class MiniGameReady {
    /** The command of the chat's button. */
    public static final String COMMAND = "/steveparty ready";

    private MiniGameReady() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("steveparty").then(literal("ready").executes(context -> press(context.getSource().getPlayerOrThrow()) ? 1 : 0))));
        // « Y » typed in the chat while the player has a vote: it votes, nobody else reads it
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
                !(isReadyWord(message.getSignedContent()) && canVote(sender) && press(sender)));
    }

    /** @return true for « Y » or « y » (spaces around it allowed). */
    public static boolean isReadyWord(String typed) {
        return typed.strip().equalsIgnoreCase("y");
    }

    /** @return true if {@code player} has a vote now: before his round starts, or during a practice round. */
    public static boolean canVote(ServerPlayerEntity player) {
        return MiniGameFreeze.canVote(player) || MiniGamePartyStep.canVote(player);
    }

    /**
     * {@code player} says he is ready, or no longer is.
     *
     * @return false if he has no vote now
     */
    public static boolean press(ServerPlayerEntity player) {
        return MiniGameFreeze.vote(player) || MiniGamePartyStep.toggleReady(player);
    }

    /** « [Ready · Y] »: says the player is ready (as the key or « Y » typed in the chat). */
    public static MutableText button() {
        return Text.translatable("message.steveparty.minigame.ready_button", Text.keybind("key.steveparty.minigame_ready"))
                .styled(style -> style.withColor(Formatting.GREEN).withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.steveparty.minigame.ready_hover",
                                Text.keybind("key.steveparty.minigame_ready")))));
    }

    /** The line under the mini-game's name: its button (everyone ready skips the description). */
    public static Text skipLine() {
        return button();
    }
}
