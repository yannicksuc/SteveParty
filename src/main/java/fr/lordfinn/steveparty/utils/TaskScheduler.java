package fr.lordfinn.steveparty.utils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public class TaskScheduler {
    private final Map<UUID, Task<?>> tasks = new HashMap<>();

    public TaskScheduler() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tick());
    }

    public <T> void schedule(UUID taskId, int delayInTicks, Runnable callback) {
        tasks.put(taskId, new Task<>(delayInTicks, callback));
    }

    public <T> void repeat(UUID taskId, int delayInTicks, Runnable callback, Callable<Boolean> condition, Runnable lastCallback) {
        tasks.put(taskId, new Task<>(delayInTicks, callback, condition, lastCallback));
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Task<?>>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Task<?>> entry = iterator.next();
            Task<?> task = entry.getValue();
            int ticksRemaining = task.getTicksRemaining() - 1;
            Boolean condition = task.condition();
            if (Boolean.FALSE.equals(condition)) {
                task.executeLastCallback();
                iterator.remove();
                continue;
            }
            if (ticksRemaining <= 0) {
                task.execute();
                if (Boolean.TRUE.equals(condition)) {
                    task.setTicksRemaining(task.getInitialTicks());
                } else {
                    task.executeLastCallback();
                    iterator.remove();
                }
            } else {
                task.setTicksRemaining(ticksRemaining);
            }
        }
    }
}
