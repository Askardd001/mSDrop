package dev.msdrop.managers;

import dev.msdrop.data.DataManager;
import dev.msdrop.database.manager.DatabaseManager;
import dev.msdrop.mSDrop;
import dev.msdrop.model.User;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DropManager {

    private static DropManager instance;

    private final Map<UUID, Boolean> dropMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requests = new ConcurrentHashMap<>();

    public static synchronized DropManager getInstance() {
        if (instance == null)
            instance = new DropManager();
        return instance;
    }

    public static synchronized void unload() {
        instance = null;
    }

    private DropManager() {
    }

    /**
     * Opens the confirmation window. It ends by itself, so no scheduler is needed (works on Spigot, Paper and Folia).
     */
    public void addRequest(UUID uuid) {
        long seconds = Math.max(1L, DataManager.getInstance().getConfig().getLong("safe-drop.seconds"));
        requests.put(uuid, System.nanoTime() + seconds * 1_000_000_000L);
    }

    public void removeRequest(UUID uuid) {
        requests.remove(uuid);
    }

    public boolean hasRequested(UUID uuid) {
        Long expires = requests.get(uuid);
        if (expires == null)
            return false;

        if (System.nanoTime() - expires >= 0) {
            requests.remove(uuid, expires);
            return false;
        }
        return true;
    }

    public void addDropStatus(UUID uuid, boolean drop) {
        dropMap.put(uuid, drop);
    }

    public void removeDropStatus(UUID uuid) {
        dropMap.remove(uuid);
    }

    public boolean hasStatus(UUID uuid) {
        return dropMap.containsKey(uuid);
    }

    public boolean getStatus(UUID uuid) {
        Boolean status = dropMap.get(uuid);
        if (status == null)
            return DataManager.getInstance().getConfig().getBoolean("safe-drop.enabled");
        return status;
    }

    /**
     * Changes the status and saves it right away, so a restart or reload never loses a toggle.
     */
    public void setStatus(UUID uuid, boolean enabled) {
        dropMap.put(uuid, enabled);
        requests.remove(uuid);

        DatabaseManager database = JavaPlugin.getPlugin(mSDrop.class).getDatabaseManager();
        if (database != null)
            database.getUsersTable().save(new User(uuid, enabled));
        else
            DataManager.getInstance().saveStatus(uuid, enabled);
    }

}
