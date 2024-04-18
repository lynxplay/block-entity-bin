package dev.lynxplay.paper.beb;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixers;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class BlockEntityBinPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void on(final WorldInitEvent event) {
        final ServerLevel level = levelFromWorld(event.getWorld());
        final BlockEntityBinWorldUpgrader upgrader = new BlockEntityBinWorldUpgrader(
            level, level.convertable.dimensionType, level.convertable.getDimensionPath(level.dimension()),
            DataFixers.getDataFixer(), Runtime.getRuntime().availableProcessors() * 3 / 8
        );

        upgrader.run();
    }

    @NotNull
    private ServerLevel levelFromWorld(final @NotNull World world) {
        try {
            return (ServerLevel) world.getClass().getMethod("getHandle").invoke(world);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access ServerLevel from api world", e);
        }
    }

}
