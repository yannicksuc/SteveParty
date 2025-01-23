package fr.lordfinn.steveparty.events;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.NotNull;

public interface TileUpdatedEvent {
    Event<TileUpdatedEvent> EVENT = EventFactory.createArrayBacked(TileUpdatedEvent.class, (listeners) -> (token, tileEntity) -> {
        for (TileUpdatedEvent listener : listeners) {
            ActionResult result = listener.onTileUpdated(token, tileEntity);
            if (result != ActionResult.PASS) {
                return result;
            }
        }
        return ActionResult.PASS;
    });
    ActionResult onTileUpdated(@NotNull MobEntity token, BoardSpaceBlockEntity tileEntity);
}
