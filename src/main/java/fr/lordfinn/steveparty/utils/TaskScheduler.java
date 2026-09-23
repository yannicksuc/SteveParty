package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Server-side delayed / repeating task runner, ticked at the start of every server tick.
 * <p>
 * Tasks may safely schedule or cancel other tasks from inside their callbacks: additions made
 * during a tick are queued and merged once the iteration is over. A task that throws is logged
 * and dropped instead of crashing the server. Everything is cleared when the server stops, so
 * nothing leaks from one singleplayer world to the next.
 */
public class TaskScheduler {
    private final Map<UUID, Task<?>> tasks = new LinkedHashMap<>();
    private final Map<UUID, Task<?>> pending = new LinkedHashMap<>();
    private final Set<UUID> cancelled = new HashSet<>();
    private boolean ticking = false;

    public TaskScheduler() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    /** Runs {@code callback} once after {@code delayInTicks}. Ignored if a task with this id already exists. */
    public void schedule(UUID taskId, int delayInTicks, Runnable callback) {
        if (isScheduled(taskId)) return;
        add(taskId, new Task<>(delayInTicks, callback));
    }

    /**
     * Runs {@code callback} every {@code delayInTicks} while {@code condition} is true, then {@code lastCallback}.
     * Replaces any existing task with the same id.
     */
    public void repeat(UUID taskId, int delayInTicks, Runnable callback, Callable<Boolean> condition, Runnable lastCallback) {
        add(taskId, new Task<>(delayInTicks, callback, condition, lastCallback));
    }

    public boolean isScheduled(UUID taskId) {
        return (tasks.containsKey(taskId) && !cancelled.contains(taskId)) || pending.containsKey(taskId);
    }

    public void cancel(UUID taskId) {
        pending.remove(taskId);
        if (ticking) cancelled.add(taskId);
        else tasks.remove(taskId);
    }

    public void clear() {
        tasks.clear();
        pending.clear();
        cancelled.clear();
    }

    private void add(UUID taskId, Task<?> task) {
        if (ticking) {
            cancelled.remove(taskId);
            pending.put(taskId, task);
        } else {
            tasks.put(taskId, task);
        }
    }

    private void tick() {
        if (tasks.isEmpty() && pending.isEmpty()) return;
        ticking = true;
        try {
            Iterator<Map.Entry<UUID, Task<?>>> it = tasks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Task<?>> entry = it.next();
                if (cancelled.contains(entry.getKey())) {
                    it.remove();
                    continue;
                }
                try {
                    if (runTask(entry.getValue())) it.remove();
                } catch (Exception e) {
                    Steveparty.LOGGER.error("Scheduled task {} failed and was dropped", entry.getKey(), e);
                    it.remove();
                }
            }
        } finally {
            ticking = false;
            cancelled.forEach(tasks::remove);
            cancelled.clear();
            tasks.putAll(pending);
            pending.clear();
        }
    }

    /** @return true when the task is finished and must be removed */
    private boolean runTask(Task<?> task) {
        Boolean condition = task.condition();
        if (Boolean.FALSE.equals(condition)) {
            task.executeLastCallback();
            return true;
        }
        int ticksRemaining = task.getTicksRemaining() - 1;
        if (ticksRemaining > 0) {
            task.setTicksRemaining(ticksRemaining);
            return false;
        }
        task.execute();
        if (Boolean.TRUE.equals(condition)) {
            task.setTicksRemaining(task.getInitialTicks());
            return false;
        }
        task.executeLastCallback();
        return true;
    }
}
