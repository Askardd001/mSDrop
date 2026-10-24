package dev.msdrop.listeners;

import dev.msdrop.data.DataManager;
import dev.msdrop.managers.DropManager;
import dev.msdrop.utils.chat.ChatUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.UUID;

public class PlayerDropItem implements Listener {

    /**
     * HIGHEST + ignoreCancelled: runs after protection, login and region plugins have decided,
     * and never asks for confirmation on a drop that someone else already blocked.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (player.isDead() || !player.hasPermission("sd.use"))
            return;

        if (player.getInventory().firstEmpty() == -1)
            return;

        DropManager dropManager = DropManager.getInstance();
        UUID uuid = player.getUniqueId();

        if (dropManager.hasRequested(uuid))
            return;

        if (!dropManager.getStatus(uuid))
            return;

        dropManager.addRequest(uuid);
        event.setCancelled(true);

        FileConfiguration config = DataManager.getInstance().getConfig();

        if (config.getBoolean("safe-drop.text-message.enabled"))
            player.sendMessage(ChatUtils.format(config.getString("messages.drop-text-message")));

        if (config.getBoolean("safe-drop.actionbar-message.enabled"))
            ChatUtils.sendActionBar(player, ChatUtils.format(config.getString("messages.drop-actionbar-message")));
    }

}
