package dev.msdrop.commands;

import dev.msdrop.data.DataManager;
import dev.msdrop.managers.DropManager;
import dev.msdrop.utils.chat.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DropCommand implements CommandExecutor {

    private static final String PERMISSION_USE = "sd.use";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DataManager dataManager = DataManager.getInstance();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[mSDrop] This command can only be used in-game!");
            return true;
        }

        if (!player.hasPermission(PERMISSION_USE)) {
            player.sendMessage(ChatUtils.format(dataManager.getConfig().getString("messages.no-permission")));
            return true;
        }

        DropManager dropManager = DropManager.getInstance();
        boolean enabled = !dropManager.getStatus(player.getUniqueId());
        dropManager.setStatus(player.getUniqueId(), enabled);

        String key = enabled ? "messages.safedrop-on" : "messages.safedrop-off";
        player.sendMessage(ChatUtils.format(dataManager.getConfig().getString(key)));

        return true;
    }

}
