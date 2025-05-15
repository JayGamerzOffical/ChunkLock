package org.jay.chunkLock.managers;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jay.chunkLock.ChunkLockPlugin;

import java.util.List;

public class MessageSender {


    public static void sendMessage(Player player, String message) {
        player.sendMessage(ColorManager.colorize(ChunkLockPlugin.getInstance().getPrefix()) + ColorManager.colorize(ChatColor.translateAlternateColorCodes('&', message)));
    }

    public static void sendMessage(CommandSender player, String message) {
        if (player instanceof Player) {
            player.sendMessage(ColorManager.colorize(ChunkLockPlugin.getInstance().getPrefix()) + ChatColor.translateAlternateColorCodes('&', message));
        } else {

            player.sendMessage(message);
        }
    }

    public static void sendMessage(List<Player> player, String message) {
        player.forEach(player1 -> player1.sendMessage(ChunkLockPlugin.getInstance().getPrefix() + ChatColor.translateAlternateColorCodes('&', message)));
    }
}
