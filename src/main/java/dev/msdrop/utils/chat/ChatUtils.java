package dev.msdrop.utils.chat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private static boolean bungeeActionBar = true;

    private ChatUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String format(String msg) {
        if (msg == null)
            return "";

        Matcher matcher = HEX_PATTERN.matcher(msg);
        StringBuilder builder = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(builder, Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        matcher.appendTail(builder);

        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }

    public static void sendActionBar(Player player, String msg) {
        if (bungeeActionBar) {
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                return;
            } catch (LinkageError e) {
                bungeeActionBar = false;
            }
        }
        sendAdventureActionBar(player, msg);
    }

    private static void sendAdventureActionBar(Player player, String msg) {
        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");

            Object serializer = serializerClass.getMethod("legacySection").invoke(null);
            Object component = null;
            for (Method method : serializerClass.getMethods()) {
                if (method.getName().equals("deserialize") && method.getParameterCount() == 1) {
                    component = method.invoke(serializer, msg);
                    break;
                }
            }

            if (component != null)
                Player.class.getMethod("sendActionBar", componentClass).invoke(player, component);
        } catch (ReflectiveOperationException ignored) {
        }
    }

}
