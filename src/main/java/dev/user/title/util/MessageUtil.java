package dev.user.title.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 消息工具类
 */
public class MessageUtil {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    /**
     * 将 & 颜色代码转换为 Component
     */
    public static Component toComponent(String message) {
        return SERIALIZER.deserialize(message);
    }

    /**
     * 发送消息给命令发送者
     */
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(toComponent(message));
    }

    /**
     * 发送消息给玩家
     */
    public static void send(Player player, String message) {
        player.sendMessage(toComponent(message));
    }

    /**
     * 发送消息给所有在线玩家
     */
    public static void broadcast(String message) {
        Bukkit.broadcast(toComponent(message));
    }

    /**
     * 将颜色代码转换为 Minecraft 颜色符号
     */
    public static String translateColors(String message) {
        return message.replace('&', '\u00A7');
    }
}
