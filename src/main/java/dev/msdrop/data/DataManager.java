package dev.msdrop.data;

import dev.msdrop.mSDrop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class DataManager {

    private static DataManager instance;

    public static synchronized DataManager getInstance() {
        if (instance == null)
            instance = new DataManager();
        return instance;
    }

    public static synchronized void unload() {
        instance = null;
    }

    private static final String SAVES_HEAD = "saves.";

    private final CustomFile config;
    private final CustomFile saves;

    private DataManager() {
        mSDrop plugin = JavaPlugin.getPlugin(mSDrop.class);
        config = new CustomFile(plugin, "config.yml");
        saves = new CustomFile(plugin, "saves.yml");
    }

    public FileConfiguration getConfig() {
        return config.getConfig();
    }

    public FileConfiguration getSaves() {
        return saves.getConfig();
    }

    public synchronized void reload() {
        config.reloadConfig();
        saves.reloadConfig();
    }

    public synchronized Boolean getSavedStatus(UUID uuid) {
        String path = SAVES_HEAD + uuid;
        return saves.getConfig().isBoolean(path) ? saves.getConfig().getBoolean(path) : null;
    }

    public synchronized void saveStatus(UUID uuid, boolean enabled) {
        saves.getConfig().set(SAVES_HEAD + uuid, enabled);
        saves.saveConfig();
    }

    public void saveConfig() {
        config.saveConfig();
    }

    public void saveSaves() {
        saves.saveConfig();
    }

}
