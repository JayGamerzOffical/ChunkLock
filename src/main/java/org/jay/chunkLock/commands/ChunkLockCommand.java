package org.jay.chunkLock.commands;


import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jay.chunkLock.ChunkLockPlugin;
import org.jay.chunkLock.managers.MessageSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChunkLockCommand implements CommandExecutor, TabExecutor {

    private final ChunkLockPlugin plugin;

    public ChunkLockCommand(ChunkLockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        if (args.length == 0 && sender.hasPermission("chunklock.admin")) {
            MessageSender.sendMessage(player, "§cUsage: /chunklock <join|quit|resetworld> or /chunklock <admin> <setchunk|setlobby|reload|additem|border>");
            return true;
        } else if (args.length == 0) {
            MessageSender.sendMessage(player, "§cUsage: /chunklock <join|quit|resetworld> ");
            return true;
        }

        String subCMD = args[0].toLowerCase();

        // PLAYER COMMANDS
        switch (subCMD) {
            case "join":
            case "j":
                if (plugin.getChunkManager().isNewPlayer(player) && plugin.getConfig().getString("template") != null) {
                    World world = plugin.getChunkManager().loadPlayerWorld(player);
                    plugin.getChunkManager().teleportToPlayerWorldFirstTime(player, world);
                } else if (plugin.getConfig().getString("template") != null) {
                    World world = plugin.getChunkManager().loadPlayerWorld(player);
                    plugin.getChunkManager().teleportToPlayerWorld(player, world);
                }
                return true;


            case "quit":
                plugin.teleportToLobby(player);
                MessageSender.sendMessage(player, "§aTeleported to the lobby.");
                return true;

            case "resetworld":
                plugin.getChunkManager().resetPlayerWorld(player);
                MessageSender.sendMessage(player, "§cYour chunk world has been reset. Use §e/chunklock join §cto generate it again.");
                return true;

            case "admin":
                if (!player.hasPermission("chunklock.admin")) {
                    MessageSender.sendMessage(player, "§cYou don't have permission to use admin commands.");
                    return true;
                }

                if (args.length < 2) {
                    MessageSender.sendMessage(player, "§cUsage: /chunklock admin <setchunk|setlobby>");
                    return true;
                }

                String adminSub = args[1].toLowerCase();

                switch (adminSub) {
                    case "setchunk":
                        Chunk chunk = player.getLocation().getChunk();
                        plugin.getConfig().set("template.world", chunk.getWorld().getName());
                        plugin.getConfig().set("template.spawn", player.getLocation());
                        plugin.getConfig().set("template.x", chunk.getX());
                        plugin.getConfig().set("template.z", chunk.getZ());
                        plugin.saveConfig();
                        MessageSender.sendMessage(player, "§aSet current chunk and world as template.");
                        break;

                    case "setlobby":
                        plugin.getConfig().set("lobby", player.getLocation());
                        plugin.saveConfig();
                        MessageSender.sendMessage(player, "§aLobby set!");
                        break;
                    case "reload":
                        plugin.reloadConfig();
                        plugin.getPlayerManager().reloadConfigValues();
                        plugin.getChunkManager().reloadConfigValues();
                        plugin.getListener().reloadConfigValues();
                        plugin.prefixOP = plugin.getConfig().getString("prefix", "");
                        MessageSender.sendMessage(player, "§aConfig reloaded.");
                        break;
                    case "additem":
                        plugin.addRequiredItemStack(player.getChunk(), player.getInventory().getItemInMainHand());
                        plugin.saveCustomItems();
                        MessageSender.sendMessage(player, "§aHand item Added to your current chunk.");
                        return true;
                    case "removeitem":
                        plugin.removeRequiredItemStack(player.getChunk());
                        plugin.saveCustomItems();
                        MessageSender.sendMessage(player, "§bYour current chunk's default item has been clear.");
                        return true;
                    case "border":
                        if (args.length <= 3) {
                            if (args[2].equalsIgnoreCase("get")) {
                                int height = plugin.getConfig().getInt("border.height", 50);
                                int width = plugin.getConfig().getInt("border.width", 50);
                                int lineWidth = plugin.getConfig().getInt("border.lineWidth", 50);
                                MessageSender.sendMessage(player, "§eCurrent Border Values:");
                                MessageSender.sendMessage(player, "§7Height: §b" + height);
                                MessageSender.sendMessage(player, "§7Width: §b" + width);
                                MessageSender.sendMessage(player, "§7Line Width: §b" + lineWidth);
                            } else {
                                MessageSender.sendMessage(player, "§cUsage: /chunklock admin border <set> <height|width|linewidth> <value>");
                                MessageSender.sendMessage(player, "§cUsage: /chunklock admin border get");
                            }
                            return true;
                        }

                        String borderAction = args[2].toLowerCase();

                        if (borderAction.equals("get")) {
                            int height = plugin.getConfig().getInt("border.height", 50);
                            int width = plugin.getConfig().getInt("border.width", 50);
                            int lineWidth = plugin.getConfig().getInt("border.lineWidth", 50);
                            MessageSender.sendMessage(player, "§eCurrent Border Values:");
                            MessageSender.sendMessage(player, "§7Height: §b" + height);
                            MessageSender.sendMessage(player, "§7Width: §b" + width);
                            MessageSender.sendMessage(player, "§7Line Width: §b" + lineWidth);
                            return true;
                        }

                        if (!borderAction.equals("set") || args.length < 5) {
                            MessageSender.sendMessage(player, "§cUsage: /chunklock admin border set <height|width|linewidth> <value>");
                            return true;
                        }

                        String type = args[3].toLowerCase();
                        String valueStr = args[4];
                        int value;

                        try {
                            value = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            MessageSender.sendMessage(player, "§cInvalid number: " + valueStr);
                            return true;
                        }

                        if (value <= 0) {
                            MessageSender.sendMessage(player, "§cValue must be greater than zero.");
                            return true;
                        }

                        switch (type) {
                            case "height":
                                plugin.getConfig().set("border.height", value);
                                plugin.getChunkManager().setBorderHeight(value);
                                plugin.saveConfig();
                                MessageSender.sendMessage(player, "§aBorder height set to §b" + value);
                                break;
                            case "width":
                                plugin.getConfig().set("border.width", value);
                                plugin.getChunkManager().setBorderWidth(value);
                                plugin.saveConfig();
                                MessageSender.sendMessage(player, "§aBorder width set to §b" + value);
                                break;
                            case "linewidth":
                                plugin.getConfig().set("border.lineWidth", value);
                                plugin.getChunkManager().setBorderLineWidth(value);
                                plugin.saveConfig();
                                MessageSender.sendMessage(player, "§aBorder lineWidth set to §b" + value);
                                break;
                            default:
                                MessageSender.sendMessage(player, "§cInvalid type. Use 'height' or 'width'.");
                                break;
                        }
                        return true;
                    default:
                        MessageSender.sendMessage(player, "§cUnknown admin command: " + adminSub);
                        break;
                }
                return true;

            default:
                MessageSender.sendMessage(player, "§cUnknown subcommand. Use: /chunklock <join|quit|resetworld> or /chunklock admin <setchunk|setlobby>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("join", "quit", "resetworld"));
            if (player.hasPermission("chunklock.admin")) base.add("admin");

            return base.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && player.hasPermission("chunklock.admin")) {
            List<String> adminSub = Arrays.asList("setchunk", "setlobby", "reload", "additem", "removeitem", "border");
            return adminSub.stream()
                    .filter(sub -> sub.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        // /chunklock admin border <get|set>
        if (args.length == 3 &&
                args[0].equalsIgnoreCase("admin") &&
                args[1].equalsIgnoreCase("border") &&
                player.hasPermission("chunklock.admin")) {

            List<String> borderActions = Arrays.asList("get", "set");
            return borderActions.stream()
                    .filter(sub -> sub.startsWith(args[2].toLowerCase()))
                    .toList();
        }

        // /chunklock admin border set <height|width|linewidth>
        if (args.length == 4 &&
                args[0].equalsIgnoreCase("admin") &&
                args[1].equalsIgnoreCase("border") &&
                args[2].equalsIgnoreCase("set") &&
                player.hasPermission("chunklock.admin")) {

            List<String> dimensions = Arrays.asList("height", "width", "linewidth");
            return dimensions.stream()
                    .filter(sub -> sub.startsWith(args[3].toLowerCase()))
                    .toList();
        }


        return Collections.emptyList();
    }

}
