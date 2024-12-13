package fr.lordfinn.steveparty.utils;

import java.util.function.Consumer;

public class Task<T> {
    private int ticksRemaining;
    private final Runnable callback;

    public Task(int ticksRemaining, Runnable callback) {
        this.ticksRemaining = ticksRemaining;
        this.callback = callback;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }

    public void execute() {
        callback.run();
    }
}
