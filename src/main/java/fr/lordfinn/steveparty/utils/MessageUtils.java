package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.List;

public class MessageUtils {

    /**
     * Sends a message to all players on the server.
     *
     * @param server The Minecraft server instance.
     * @param message The message to send. Can be a String or a Text.
     * @param messageType The type of message (CHAT, ACTION_BAR, or TITLE).
     */
    public static void sendToAll(MinecraftServer server, Object message, MessageType messageType) {
        if (server == null) return;
        Text text = convertToText(message);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendMessage(player, text, messageType);
        }
    }

    /**
     * Sends a message to a specific player.
     *
     * @param player The player to send the message to.
     * @param message The message to send. Can be a String or a Text.
     * @param messageType The type of message (CHAT, ACTION_BAR, or TITLE).
     */
    public static void sendToPlayer(ServerPlayerEntity player, Object message, MessageType messageType) {
        Text text = convertToText(message);
        sendMessage(player, text, messageType);
    }

    /**
     * Sends a message to players near a specific position, only in the given world (dimension).
     *
     * @param world The world the position belongs to.
     * @param position The position to use as a reference.
     * @param radius The radius around the position.
     * @param message The message to send. Can be a String or a Text.
     * @param messageType The type of message (CHAT, ACTION_BAR, or TITLE).
     */
    public static void sendToNearby(ServerWorld world, Vec3d position, double radius, Object message, MessageType messageType) {
        if (world == null) return;
        Text text = convertToText(message);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getPos().isInRange(position, radius)) {
                sendMessage(player, text, messageType);
            }
        }
    }

    /**
     * Sends a message to a list of players.
     *
     * @param players The list of players to send the message to.
     * @param message The message to send. Can be a String or a Text.
     * @param messageType The type of message (CHAT, ACTION_BAR, or TITLE).
     */
    public static void sendToPlayers(Iterable<ServerPlayerEntity> players, Object message, MessageType messageType) {
        Text text = convertToText(message);
        for (ServerPlayerEntity player : players) {
            sendMessage(player, text, messageType);
        }
    }

    /**
     * Converts an Object (String or Text) to a Text object.
     *
     * @param message The message to convert.
     * @return A Text object.
     */
    private static Text convertToText(Object message) {
        if (message instanceof String) {
            return Text.literal((String) message);
        } else if (message instanceof Text) {
            return (Text) message;
        } else {
            throw new IllegalArgumentException("Message must be a String or Text.");
        }
    }

    /**
     * Sends a message to a player with the specified message type.
     *
     * @param player The player to send the message to.
     * @param message The message to send.
     * @param messageType The type of message (CHAT, ACTION_BAR, or TITLE).
     */
    private static void sendMessage(ServerPlayerEntity player, Text message, MessageType messageType) {
        switch (messageType) {
            case CHAT -> player.sendMessage(message, false);
            case ACTION_BAR -> player.sendMessage(message, true);
            case TITLE -> {
                player.networkHandler.sendPacket(new TitleS2CPacket(message));
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 50, 20));
            }
        }
    }


    public static int getColorFromText(Text text) {
        Integer color = findColor(text);
        return color != null ? color : Color.WHITE.getRGB();
    }

    /**
     * First colour found in the text: its siblings, then its own style, then the texts passed as arguments of a
     * translatable text (e.g. "Token of %s" with the coloured owner name). Null if none.
     */
    private static Integer findColor(Text text) {
        if (text == null) return null;
        for (Text component : text.getSiblings()) {
            Style style = component.getStyle();
            if (style.getColor() != null && style.getColor().getRgb() != -1) {
                return style.getColor().getRgb();
            }
        }
        if (text.getStyle().getColor() != null && text.getStyle().getColor().getRgb() != -1) {
            return text.getStyle().getColor().getRgb();
        }
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Text argText) {
                    Integer color = findColor(argText);
                    if (color != null) return color;
                }
            }
        }
        return null;
    }

    public static void sendToPlayers(List<PlayerEntity> owners, MutableText translatable, MessageType messageType) {
        try {
            sendToPlayers(owners.stream().map(e -> (ServerPlayerEntity) e).toList(), translatable, messageType);
        } catch (Exception e) {
            Steveparty.LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Enum for different types of messages.
     */
    public enum MessageType {
        CHAT,
        ACTION_BAR,
        TITLE
    }
}
