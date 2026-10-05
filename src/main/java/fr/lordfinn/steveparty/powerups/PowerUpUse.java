package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A power-up being used: who uses it, in which party and turn, and on what.
 *
 * @param world        the world of the party controller
 * @param player       the player whose turn it is
 * @param controller   the party
 * @param turn         the current turn, the player's
 * @param powerUp      the power-up used
 * @param targetPlayer the player aimed at ({@link PowerUp.Target#PLAYER}), null otherwise
 * @param targetTile   the board space aimed at ({@link PowerUp.Target#TILE}), null otherwise
 */
public record PowerUpUse(ServerWorld world, ServerPlayerEntity player, PartyControllerEntity controller,
                         TokenTurnPartyStep turn, PowerUp powerUp, @Nullable UUID targetPlayer,
                         @Nullable BlockPos targetTile) {

    /** The power-up state of the turn: what {@link PowerUp#apply} sets up for the rest of the turn goes in its data. */
    public PowerUpTurn state() {
        return turn.getPowerUps();
    }

    /** The token of the turn. */
    public UUID token() {
        return turn.getTokenUUID();
    }

    /** The player aimed at, if online. */
    public @Nullable ServerPlayerEntity targetPlayerEntity() {
        return targetPlayer == null ? null : world.getServer().getPlayerManager().getPlayer(targetPlayer);
    }
}
