package dev.msdrop;

import dev.msdrop.commands.Drop;
import dev.msdrop.commands.DropCommand;
import dev.msdrop.data.DataManager;
import dev.msdrop.database.manager.DatabaseManager;
import dev.msdrop.listeners.PlayerDropItem;
import dev.msdrop.listeners.PlayerJoinQuit;
import dev.msdrop.managers.DropManager;
import dev.msdrop.utils.async.Async;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class mSDrop extends JavaPlugin {

    private DatabaseManager databaseManager;
    private UpdateChecker updateChecker;
    private ScheduledExecutorService updateTimer;

    @Override
    public void onEnable() {
        DataManager.getInstance();

        loadDatabase();
        registerCommands();
        registerListeners();
        startUpdateTask();

        getLogger().info("mSDrop " + getDescription().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (updateTimer != null)
            updateTimer.shutdownNow();
        Async.shutdown();
        if (databaseManager != null)
            databaseManager.close();
        DropManager.unload();
        DataManager.unload();
        getLogger().info("mSDrop disabled");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public File getJarFile() {
        return getFile();
    }

    public String getJarName() {
        return getFile().getName();
    }

    private void loadDatabase() {
        if (!DataManager.getInstance().getConfig().getBoolean("MySQL"))
            return;

        try {
            DatabaseManager manager = new DatabaseManager();
            if (manager.load())
                databaseManager = manager;
            else {
                manager.close();
                getLogger().severe("Could not connect to MySQL, using saves.yml instead");
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Invalid MySQL settings, using saves.yml instead", e);
        }
    }

    private void registerCommands() {
        Drop drop = new Drop();
        Objects.requireNonNull(getCommand("msdrop")).setExecutor(drop);
        Objects.requireNonNull(getCommand("msdrop")).setTabCompleter(drop);
        Objects.requireNonNull(getCommand("droptoggle")).setExecutor(new DropCommand());
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerDropItem(), this);
        pm.registerEvents(new PlayerJoinQuit(this), this);
    }

    private void startUpdateTask() {
        updateChecker = new UpdateChecker(this);

        if (!DataManager.getInstance().getConfig().getBoolean("update.check"))
            return;

        if (!updateChecker.isConfigured()) {
            getLogger().info("Set update.repository in config.yml to enable update checks");
            return;
        }

        long hours = Math.max(1, DataManager.getInstance().getConfig().getLong("update.interval-hours"));
        updateTimer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mSDrop-updater");
            thread.setDaemon(true);
            return thread;
        });
        updateTimer.scheduleWithFixedDelay(this::runUpdateCheck, 10, hours * 3600, TimeUnit.SECONDS);
    }

    private void runUpdateCheck() {
        try {
            if (!updateChecker.check() || updateChecker.isDownloaded())
                return;

            String version = updateChecker.getLatestVersion();
            if (DataManager.getInstance().getConfig().getBoolean("update.auto-download")) {
                updateChecker.download();
                getLogger().info("Version " + version + " downloaded, restart the server to apply it");
            } else
                getLogger().info("Version " + version + " is available, use /sd update to download it");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not check for updates: " + e.getMessage());
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Could not check for updates", e);
        }
    }

}
