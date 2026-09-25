package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.ServerScoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the goal pole bases about score changes on their objectives, so that they never have to poll the scoreboard
 * (see {@link GoalPoleNetwork}). One map lookup per score change.
 */
@Mixin(ServerScoreboard.class)
public class ServerScoreboardMixin {
    @Inject(method = "updateScore", at = @At("TAIL"))
    private void steveparty$scoreUpdated(ScoreHolder holder, ScoreboardObjective objective, ScoreboardScore score, CallbackInfo ci) {
        GoalPoleNetwork.onScoreUpdated(objective, holder.getNameForScoreboard(), score.getScore());
    }

    @Inject(method = "onScoreRemoved", at = @At("TAIL"))
    private void steveparty$scoreRemoved(ScoreHolder holder, ScoreboardObjective objective, CallbackInfo ci) {
        GoalPoleNetwork.onScoreUpdated(objective, holder.getNameForScoreboard(), 0);
    }

    @Inject(method = "onScoreHolderRemoved", at = @At("TAIL"))
    private void steveparty$holderRemoved(ScoreHolder holder, CallbackInfo ci) {
        GoalPoleNetwork.onHolderRemoved(holder.getNameForScoreboard());
    }

    @Inject(method = "updateRemovedObjective", at = @At("TAIL"))
    private void steveparty$objectiveRemoved(ScoreboardObjective objective, CallbackInfo ci) {
        GoalPoleNetwork.onObjectiveRemoved(objective);
    }
}
