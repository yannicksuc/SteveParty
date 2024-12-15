package fr.lordfinn.steveparty.utils;

import java.util.concurrent.Callable;

public class Task<T> {
    private int ticksRemaining;
    private final int initialTicks;
    private final Runnable callback;
    private final Runnable lastCallback;
    private final Callable<Boolean> condition;

    public Task(int ticksRemaining, Runnable callback, Callable<Boolean> condition, Runnable lastCallback) {
        this.ticksRemaining = ticksRemaining;
        this.initialTicks = ticksRemaining;
        this.callback = callback;
        this.condition = condition;
        this.lastCallback = lastCallback;
    }

    public Task(int ticksRemaining, Runnable callback) {
        this(ticksRemaining, callback, null, null);
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public int getInitialTicks() {
        return initialTicks;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }

    public void execute() {
        callback.run();
    }

    public void executeLastCallback() {
        if (lastCallback == null) return;
        lastCallback.run();
    }

    public Boolean condition() {
        if (condition == null) return null;
        try {
            return condition.call();
        } catch (Exception e) {
            return null;
        }
    }
}
