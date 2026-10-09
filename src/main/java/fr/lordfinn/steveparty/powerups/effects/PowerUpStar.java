package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The Star of the board, as the power-ups aiming at it see it (the Golden Pipe reads it, the Star Whistle moves it):
 * one {@link StarRelocator} for the whole mod, asked at each use.
 * <p>
 * By default the party's star of the Star Cartridge ({@link PartyStarRelocator}). A board with no star (no active star
 * space) answers "no Star": the Golden Pipe and the Star Whistle are then refused (not used up) with a message.
 */
public final class PowerUpStar {
    private static StarRelocator relocator = PartyStarRelocator.INSTANCE;

    private PowerUpStar() {
    }

    /** The Star's reader and mover used by the power-ups now. */
    public static StarRelocator relocator() {
        return relocator;
    }

    /**
     * Plugs in another Star reader and mover (a test one in the gametests).
     *
     * @return the one it replaces (to put it back)
     */
    public static StarRelocator install(StarRelocator starRelocator) {
        StarRelocator previous = relocator;
        relocator = Objects.requireNonNull(starRelocator);
        return previous;
    }

    /** Why a Star power-up is refused, or what it found: to its user, or to the whole party when no player used it. */
    public static void tell(PartyControllerEntity party, @Nullable ServerPlayerEntity user, Text message) {
        if (user != null) MessageUtils.sendToPlayer(user, message, MessageUtils.MessageType.CHAT);
        else MessageUtils.sendToPlayers(party.getPartyAudience(), message, MessageUtils.MessageType.CHAT);
    }
}
