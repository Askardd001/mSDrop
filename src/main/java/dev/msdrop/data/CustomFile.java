package dev.msdrop.data;

import dev.msdrop.mSDrop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public class CustomFile {

    private final String name;
    private final mSDrop plugin;
    private final File file;
    private FileConfiguration config;

    public CustomFile(mSDrop plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);
        saveDefault();
    }

    public synchronized void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(file);

        try (InputStream stream = plugin.getResource(name)) {
            if (stream != null)
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (IOException ignored) {
        }
    }

    public synchronized FileConfiguration getConfig() {
        if (config == null)
            reloadConfig();
        return config;
    }

    public synchronized void saveConfig() {
        if (config == null)
            return;
        try {
            File temp = new File(file.getParentFile(), name + ".tmp");
            config.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, e);
        }
    }

    private void saveDefault() {
        if (!file.exists())
            plugin.saveResource(name, false);
    }

}
