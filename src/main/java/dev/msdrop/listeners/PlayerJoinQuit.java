package dev.msdrop.listeners;

import dev.msdrop.data.DataManager;
import dev.msdrop.database.manager.DatabaseManager;
import dev.msdrop.database.table.UsersTable;
import dev.msdrop.UpdateChecker;
import dev.msdrop.mSDrop;
import dev.msdrop.managers.DropManager;
import dev.msdrop.model.User;
import dev.msdrop.utils.chat.ChatUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerJoinQuit implements Listener {

    private final mSDrop plugin;

    public PlayerJoinQuit(mSDrop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        DataManager dataManager = DataManager.getInstance();
        DropManager dropManager = DropManager.getInstance();

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean def = dataManager.getConfig().getBoolean("safe-drop.enabled");

        notifyUpdate(player);

        DatabaseManager database = plugin.getDatabaseManager();
        if (database != null) {
            UsersTable usersTable = database.getUsersTable();
            usersTable.getByUuid(uuid).whenComplete((user, error) -> {
                boolean enabled = def;
                if (error != null)
                    plugin.getLogger().warning("Could not load the status of " + player.getName() + ", using the default");
                else if (user == null)
                    usersTable.save(new User(uuid, def));
                else
                    enabled = user.isEnabled();

                if (player.isOnline() && !dropManager.hasStatus(uuid))
                    dropManager.addDropStatus(uuid, enabled);
            });
            return;
        }

        Boolean saved = dataManager.getSavedStatus(uuid);
        dropManager.addDropStatus(uuid, saved != null ? saved : def);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DropManager dropManager = DropManager.getInstance();
        UUID uuid = event.getPlayer().getUniqueId();

        dropManager.removeDropStatus(uuid);
        dropManager.removeRequest(uuid);
    }

    private void notifyUpdate(Player player) {
        FileConfiguration config = DataManager.getInstance().getConfig();
        UpdateChecker updater = plugin.getUpdateChecker();

        if (!config.getBoolean("update.notify-admins") || !player.hasPermission("sd.admin"))
            return;
        if (updater == null || !updater.isUpdateAvailable())
            return;

        String key = updater.isDownloaded() ? "messages.update-downloaded" : "messages.update-available";
        player.sendMessage(ChatUtils.format(config.getString(key).replace("{version}", updater.getLatestVersion())));
    }

}
