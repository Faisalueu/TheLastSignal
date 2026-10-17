package com.lastsignal.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Minimal tick scheduler for multi-step events. Server thread only. Bounded: each task has a hard
 * max lifetime, and everything is dropped on server stop (so nothing leaks across worlds).
 */
public final class Tasks {
    private static final class Task {
        int delay, period, remaining, run;
        final IntPredicate body;   // return false to cancel
        Task(int delay, int period, int times, IntPredicate body) {
            this.delay = delay; this.period = Math.max(1, period); this.remaining = times; this.body = body;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final int MAX_TASKS = 512;

    public static void later(int delayTicks, Runnable r) {
        every(delayTicks, 1, 1, i -> { r.run(); return false; });
    }

    /** Run body(i) after delay, then every period ticks, up to times; cancel by returning false. */
    public static void every(int delayTicks, int period, int times, IntPredicate body) {
        if (TASKS.size() >= MAX_TASKS) return;
        TASKS.add(new Task(delayTicks, period, times, body));
    }

    public static void tick() {
        if (TASKS.isEmpty()) return;
        for (Iterator<Task> it = new ArrayList<>(TASKS).iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (--t.delay > 0) continue;
            t.delay = t.period;
            boolean keep;
            try {
                keep = t.body.test(t.run++);
            } catch (RuntimeException ex) {
                com.lastsignal.LastSignalMod.LOGGER.error("Scheduled task failed", ex);
                keep = false;
            }
            if (!keep || --t.remaining <= 0) TASKS.remove(t);
        }
    }

    public static void clear() { TASKS.clear(); }

    private Tasks() {}
}
