package test.galactifun;

import java.lang.reflect.Method;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs only in disposable Paper integration testing. Never ship in the addon JAR. */
public final class NativeAlienProbe extends JavaPlugin {

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::verify, 40L);
    }

    private void verify() {
        try {
            Plugin galactifun = Bukkit.getPluginManager().getPlugin("Galactifun");
            if (galactifun == null || !galactifun.isEnabled()) {
                throw new IllegalStateException("Galactifun was not enabled");
            }
            Object manager = galactifun.getClass().getMethod("alienManager").invoke(null);
            Method find = manager.getClass().getMethod("getAlien", String.class);
            World world = Bukkit.getWorlds().get(0);

            verifyAlien(find.invoke(manager, "MARTIAN"), world, true);
            verifyAlien(find.invoke(manager, "TITAN_KING"), world, false);
            getLogger().info("NATIVE_ALIEN_POLICY_PASS");
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE, "NATIVE_ALIEN_POLICY_FAIL", failure);
        }
    }

    private static void verifyAlien(Object alien, World world, boolean expected) throws Exception {
        if (alien == null) {
            throw new IllegalStateException("Expected registered alien is missing");
        }
        Location location = world.getSpawnLocation().clone().add(1.0, 2.0, 1.0);
        Mob mob = (Mob) alien.getClass()
                .getMethod("spawn", Location.class, World.class)
                .invoke(alien, location, world);
        try {
            boolean remove = (boolean) Mob.class.getMethod("getRemoveWhenFarAway").invoke(mob);
            if (remove != expected) {
                throw new AssertionError(
                        "Wrong distance-despawn for " + alien.getClass().getSimpleName()
                                + ": expected=" + expected + " actual=" + remove
                );
            }
        } finally {
            mob.remove();
        }
    }
}
