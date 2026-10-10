package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.mixin.MobEntityGoalsAccessor;
import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.mob.MobEntity;

/**
 * A mob of ours a board space may summon for its show (see {@link BoardActors}): once {@link #makeBoardActor made one},
 * it has no will of its own (no AI), is invulnerable and is never kept. Implemented by a {@link MobEntity}, which
 * keeps the flag ({@link #isBoardActor}) and also leaves it out of saves and loot ({@code shouldSave},
 * {@code shouldDropLoot}).
 */
public interface BoardActor {
    /** Whether it is a board space's actor (server side; never set on the client). */
    boolean isBoardActor();

    /** Records that it is a board actor ({@link #isBoardActor} true from now on); called by {@link #makeBoardActor}. */
    void setBoardActor();

    /** What else becoming a board actor changes for this mob (no gravity, no light...), after its AI is turned off. */
    default void onBoardActor() {
    }

    /** Makes it a board actor, before it is spawned: no AI, then {@link #onBoardActor}, then marked (server side). */
    default void makeBoardActor() {
        MobEntity self = (MobEntity) this;
        setBoardActor();
        self.setAiDisabled(true);
        // No goal of its own, ever (a hologram: it only plays what its space scripts)
        ((MobEntityGoalsAccessor) self).steveparty$goals().clear(goal -> true);
        ((MobEntityGoalsAccessor) self).steveparty$targets().clear(goal -> true);
        onBoardActor();
        if (!self.getWorld().isClient) BoardActors.mark(self); // invulnerable, never kept
    }
}
