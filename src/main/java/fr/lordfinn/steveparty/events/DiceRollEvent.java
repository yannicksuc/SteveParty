package fr.lordfinn.steveparty.events;

import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface DiceRollEvent {
    Event<DiceRollEvent> EVENT = EventFactory.createArrayBacked(DiceRollEvent.class, (listeners) -> (entity, owner, rollValue) -> {
        for (DiceRollEvent listener : listeners) {
            ActionResult result = listener.onRoll(entity, owner, rollValue);
            if (result != ActionResult.PASS) {
                return result;
            }
        }
        return ActionResult.PASS;
    });

    ActionResult onRoll(@NotNull DiceEntity diceEntity, @NotNull UUID owner, int rollValue);
}
