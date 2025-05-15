package org.jay.chunkLock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jay.chunkLock.commands.ChunkLockCommand;
import org.jay.chunkLock.hooks.ChunkLookPlaceholder;
import org.jay.chunkLock.listeners.ChunkListener;
import org.jay.chunkLock.managers.ChunkManager;
import org.jay.chunkLock.managers.MessageSender;
import org.jay.chunkLock.managers.PlayerManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChunkLockPlugin extends JavaPlugin {

    private static ChunkLockPlugin instance;
    public String prefixOP;
    private ChunkManager chunkManager;
    private PlayerManager playerManager;
    private ChunkListener listener;
    private File defaultchunkitemsFile;

    public HashMap<String, ItemStack> getCustomChunkItems() {
        return customChunkItems;
    }

    private final HashMap<String, ItemStack> customChunkItems = new HashMap<>();

    public FileConfiguration getDefaultchunkitemsConfig() {
        return defaultchunkitemsConfig;
    }

    private FileConfiguration defaultchunkitemsConfig;

    public static ChunkLockPlugin getInstance() {
        return instance;
    }

    public ChunkListener getListener() {
        return listener;
    }

    @Override
    public void onEnable() {
        // Startup banner
        getLogger().info("========= ChunkLock Plugin =========");
        getLogger().info("Author   : Jay");
        getLogger().info("Version  : " + getDescription().getVersion());
        getLogger().info("Status   : Initializing...");
        instance = this;
        saveDefaultConfig();
        defaultchunkitemsFile = new File(getDataFolder(), "defaultchunkitems.yml");
        if (!defaultchunkitemsFile.exists()) {
            try {
                defaultchunkitemsFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        defaultchunkitemsConfig = YamlConfiguration.loadConfiguration(defaultchunkitemsFile);
        prefixOP = getConfig().getString("prefix", "");
        playerManager = new PlayerManager(this);
        File templateFolder = new File(getDataFolder(), playerManager.getTemplate());
        if (!templateFolder.exists()) {
            boolean created = templateFolder.mkdirs();
            getLogger().info("Template folder: " + templateFolder.getName() + (created ? " (created)" : " (already exists)"));
        } else {
            getLogger().info("Template folder: " + templateFolder.getName() + " (already exists)");
        }
        chunkManager = new ChunkManager(this);
        loadCustomItems();
        // PlaceholderAPI hook
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ChunkLookPlaceholder(this, chunkManager).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholders will not be available.");
        }

        listener = new ChunkListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(playerManager, this);

        ChunkLockCommand chunkLockCommand = new ChunkLockCommand(this);
        getCommand("chunklock").setExecutor(chunkLockCommand);
        getCommand("chunklock").setTabCompleter(chunkLockCommand);
        getLogger().info("ChunkLock has been enabled.");
        getLogger().info("====================================");

    }

    @Override
    public void onDisable() {
        saveCustomItems();
        for (Player player : Bukkit.getOnlinePlayers()) {
            chunkManager.removeAllChunkDisplayEntities(player);
            chunkManager.saveAndUnloadPlayerWorld(player);
        }
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }


    public void teleportToLobby(Player player) {
        Location lobby = getConfig().getLocation("lobby");
        if (lobby != null) {
            player.teleport(lobby);
            MessageSender.sendMessage(player, "Teleported to Lobby!");
        }
    }

    public void saveCustomItem(String chunk, ItemStack item) {
        defaultchunkitemsConfig.set("defaultchunkitems." + chunk, item); // Save to config
    }

    public ItemStack getCustomItem(String chunk) {
        return (ItemStack) defaultchunkitemsConfig.get("defaultchunkitems." + chunk); // Save to config
    }

    public List<String> getCustomItemsList(String chunk) {
        return defaultchunkitemsConfig.getStringList("defaultchunkitems"); // Save to config
    }

    void loadCustomItems() {
        customChunkItems.clear();
        ConfigurationSection section = defaultchunkitemsConfig.getConfigurationSection("defaultchunkitems");
        if (section != null) {
            for (String chunk : section.getKeys(false)) {
                ItemStack item = section.getItemStack(chunk);
                if (item != null) {
                    customChunkItems.put(chunk, item);
                    if (chunkManager.debug) getLogger().info("Loaded item for chunk: " + chunk);
                } else {
                    if (chunkManager.debug) getLogger().warning("ItemStack for chunk " + chunk + " is null!");
                }
            }
        } else {
            if (chunkManager.debug) getLogger().warning("No 'defaultchunkitems' section found in config!");
        }
    }


    public void saveCustomItems() {
        if (defaultchunkitemsConfig == null) return;

        // Clear the current section to avoid leftover/old data
        defaultchunkitemsConfig.set("defaultchunkitems", null);

        // Save all custom items for each chunk
        for (Map.Entry<String, ItemStack> entry : customChunkItems.entrySet()) {
            defaultchunkitemsConfig.set("defaultchunkitems." + entry.getKey(), entry.getValue());
        }

        // Save the file
        try {
            defaultchunkitemsConfig.save(defaultchunkitemsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public ItemStack getRequiredItemStack(String chunk) {
        return customChunkItems.get(chunk);
    }

    public ItemStack getRequiredItemStack(Chunk chunk) {
        return customChunkItems.get(getChunkManager().serializeChunk(chunk));
    }

    public void addRequiredItemStack(String chunk, ItemStack item) {
        customChunkItems.put(chunk, item);
    }

    public void addRequiredItemStack(Chunk chunk, ItemStack item) {
        customChunkItems.put(getChunkManager().serializeChunk(chunk), item);
    }

    public void removeRequiredItemStack(Chunk chunk) {
        customChunkItems.remove(getChunkManager().serializeChunk(chunk));
        saveCustomItems();
    }

    public String getPrefix() {
        return prefixOP;
    }


}