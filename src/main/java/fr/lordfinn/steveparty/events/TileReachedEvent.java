package fr.lordfinn.steveparty.events;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.NotNull;

public interface TileReachedEvent {
    Event<TileReachedEvent> EVENT = EventFactory.createArrayBacked(TileReachedEvent.class, (listeners) -> (token, tileEntity) -> {
        for (TileReachedEvent listener : listeners) {
            ActionResult result = listener.onTileReached(token, tileEntity);
            if (result != ActionResult.PASS) {
                return result;
            }
        }
        return ActionResult.PASS;
    });
    ActionResult onTileReached(@NotNull MobEntity token, BoardSpaceBlockEntity tileEntity);
}
