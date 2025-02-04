package fr.lordfinn.steveparty.utils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.*;
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
        // We will use a list to collect the entries to remove, so no modification happens directly during iteration.
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, Task<?>> entry : tasks.entrySet()) {
            if (entry == null) continue;
            Task<?> task = entry.getValue();
            if (task == null) continue;
            int ticksRemaining = task.getTicksRemaining() - 1;
            Boolean condition = task.condition();

            if (Boolean.FALSE.equals(condition)) {
                task.executeLastCallback();
                toRemove.add(entry.getKey());  // Collect the UUID for removal after iteration
                continue;
            }

            if (ticksRemaining <= 0) {
                task.execute();
                if (Boolean.TRUE.equals(condition)) {
                    task.setTicksRemaining(task.getInitialTicks());
                } else {
                    task.executeLastCallback();
                    toRemove.add(entry.getKey());  // Collect the UUID for removal after iteration
                }
            } else {
                task.setTicksRemaining(ticksRemaining);
            }
        }

        // After the iteration, remove the collected entries safely
        toRemove.forEach(tasks::remove);
    }
}
