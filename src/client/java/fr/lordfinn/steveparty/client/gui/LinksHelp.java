package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.commands.LinksCommand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;

/**
 * The short line a screen shows instead of explaining how to link (« Linking: /steveparty links »): clicked, it closes
 * the screen and runs the command, whose explanation is then read in the chat.
 */
public final class LinksHelp {
    /** The line itself. */
    public static final String HINT_KEY = "gui.steveparty.links.hint";
    /** Its colour under the mouse (it is clickable). */
    public static final int HOVER_COLOR = 0xFFFFFF55;

    private LinksHelp() {
    }

    public static Text hint() {
        return Text.translatable(HINT_KEY);
    }

    /** Closes the screen and asks the server for the explanation, in the chat. */
    public static void show() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (client.currentScreen instanceof HandledScreen<?>) client.player.closeHandledScreen();
        else client.setScreen(null);
        client.player.networkHandler.sendChatCommand(LinksCommand.COMMAND);
    }
}
