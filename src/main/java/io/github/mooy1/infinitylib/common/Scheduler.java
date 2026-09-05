package io.github.mooy1.infinitylib.common;

import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import io.github.mooy1.infinitylib.core.AbstractAddon;

@ParametersAreNonnullByDefault
public final class Scheduler {

    private Scheduler() {}

    public static BukkitTask run(Runnable runnable) {
        return Bukkit.getScheduler().runTask(AbstractAddon.addonInstance(), runnable);
    }

    public static BukkitTask runAsync(Runnable runnable) {
        return Bukkit.getScheduler().runTaskAsynchronously(AbstractAddon.addonInstance(), runnable);
    }

    public static BukkitTask run(int delayTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskLater(AbstractAddon.addonInstance(), runnable, delayTicks);
    }

    public static BukkitTask runAsync(int delayTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskLaterAsynchronously(AbstractAddon.addonInstance(), runnable, delayTicks);
    }

    public static BukkitTask repeat(int intervalTicks, Runnable runnable) {
        return repeat(intervalTicks, 1, runnable);
    }

    public static BukkitTask repeatAsync(int intervalTicks, Runnable runnable) {
        return repeatAsync(intervalTicks, 1, runnable);
    }

    public static BukkitTask repeat(int intervalTicks, int delayTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskTimer(AbstractAddon.addonInstance(), runnable, delayTicks, Math.max(1, intervalTicks));
    }

    public static BukkitTask repeatAsync(int intervalTicks, int delayTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(AbstractAddon.addonInstance(), runnable, delayTicks, Math.max(1, intervalTicks));
    }
}
