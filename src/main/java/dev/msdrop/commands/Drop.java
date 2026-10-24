package dev.msdrop.commands;

import dev.msdrop.data.DataManager;
import dev.msdrop.UpdateChecker;
import dev.msdrop.managers.DropManager;
import dev.msdrop.mSDrop;
import dev.msdrop.utils.chat.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class Drop implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_USE = "sd.use";
    private static final String PERMISSION_ADMIN = "sd.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DataManager dataManager = DataManager.getInstance();

        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on", "off" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("[mSDrop] This command can only be used in-game!");
                    return true;
                }
                if (!player.hasPermission(PERMISSION_USE)) {
                    sendError(sender);
                    return true;
                }
                boolean enabled = args[0].equalsIgnoreCase("on");
                DropManager.getInstance().setStatus(player.getUniqueId(), enabled);
                send(sender, enabled ? "messages.safedrop-on" : "messages.safedrop-off");
            }
            case "reload" -> {
                if (sender.hasPermission(PERMISSION_ADMIN)) {
                    dataManager.reload();
                    send(sender, "messages.reload");
                } else
                    sendError(sender);
            }
            case "reportbug" -> {
                if (sender.hasPermission(PERMISSION_ADMIN))
                    send(sender, "messages.reportbug");
                else
                    sendError(sender);
            }
            case "update" -> {
                if (sender.hasPermission(PERMISSION_ADMIN))
                    update(sender);
                else
                    sendError(sender);
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1)
            return Collections.emptyList();

        List<String> options = new ArrayList<>();
        if (sender.hasPermission(PERMISSION_USE)) {
            options.add("on");
            options.add("off");
        }
        if (sender.hasPermission(PERMISSION_ADMIN)) {
            options.add("reload");
            options.add("update");
            options.add("reportbug");
        }

        String typed = args[0].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }

    private void update(CommandSender sender) {
        mSDrop plugin = JavaPlugin.getPlugin(mSDrop.class);
        UpdateChecker updater = plugin.getUpdateChecker();

        if (updater == null || !updater.isConfigured()) {
            send(sender, "messages.update-disabled");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                if (!updater.check()) {
                    send(sender, "messages.update-latest");
                    return;
                }
                updater.download();
                send(sender, "messages.update-downloaded", updater.getLatestVersion());
            } catch (IOException e) {
                plugin.getLogger().warning("Update failed: " + e.getMessage());
                send(sender, "messages.update-failed");
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Update failed", e);
                send(sender, "messages.update-failed");
            }
        });
    }

    /**
     * Messages are sent from the calling thread on purpose: sendMessage is thread-safe and this avoids
     * the Bukkit scheduler, which does not exist on Folia.
     */
    private void send(CommandSender sender, String key) {
        send(sender, key, "");
    }

    private void send(CommandSender sender, String key, String version) {
        String msg = DataManager.getInstance().getConfig().getString(key);
        if (msg != null && !msg.isEmpty())
            sender.sendMessage(ChatUtils.format(msg.replace("{version}", version == null ? "" : version)));
    }

    private void sendUsage(CommandSender sender) {
        DataManager dataManager = DataManager.getInstance();
        for (String msg : dataManager.getConfig().getStringList("messages.usage"))
            sender.sendMessage(ChatUtils.format(msg));

        if (sender.hasPermission(PERMISSION_ADMIN))
            for (String msg : dataManager.getConfig().getStringList("messages.usage-admin"))
                sender.sendMessage(ChatUtils.format(msg));
    }

    private void sendError(CommandSender sender) {
        send(sender, "messages.no-permission");
    }

}
