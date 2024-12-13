package fr.lordfinn.steveparty.utils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TaskScheduler {
    private final Map<UUID, Task<?>> tasks = new HashMap<>();

    public TaskScheduler() {
        // Hook dans le cycle des ticks du serveur
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    public <T> void schedule(UUID taskId, int delayInTicks, Runnable callback) {
        tasks.put(taskId, new Task<>(delayInTicks, callback));
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Task<?>>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Task<?>> entry = iterator.next();
            Task<?> task = entry.getValue();
            int ticksRemaining = task.getTicksRemaining() - 1;
            if (ticksRemaining <= 0) {
                task.execute(); // Exécute le callback avec le contexte
                iterator.remove();
            } else {
                task.setTicksRemaining(ticksRemaining);
            }
        }
    }
}
