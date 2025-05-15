package org.jay.chunkLock.listeners;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jay.chunkLock.ChunkLockPlugin;
import org.jay.chunkLock.managers.ChunkManager;
import org.jay.chunkLock.managers.MessageSender;

public class ChunkListener implements Listener {
    private final ChunkLockPlugin plugin;

    private boolean preventBlockBreak;
    private boolean preventBlockPlace;
    private boolean preventInteraction;
    private boolean preventItemDrop;


    private String templateWorld;

    public Location getTemplateSpawn() {
        return templateSpawn;
    }

    private Location templateSpawn;
    private int maxDistanceFromSpawn;
    private String msgChunkLocked;
    private String msgChunkUnlocked;
    private String msgTooFar;
    private String msgError;

    private Sound unlockSound;
    private float unlockVolume;
    private float unlockPitch;

    public ChunkListener(ChunkLockPlugin plugin) {
        this.plugin = plugin;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        FileConfiguration config = plugin.getConfig();

        this.preventBlockBreak = config.getBoolean("protection.prevent-block-break", true);
        this.preventBlockPlace = config.getBoolean("protection.prevent-block-place", true);
        this.preventInteraction = config.getBoolean("protection.prevent-interaction", true);
        this.preventItemDrop = config.getBoolean("protection.prevent-item-drop", true);
        this.maxDistanceFromSpawn = config.getInt("max-distance-from-spawn", 10);

        this.templateWorld = config.getString("template.world", "template_world");
        this.templateSpawn = config.getLocation("template.spawn");
        this.maxDistanceFromSpawn = config.getInt("max-distance-from-spawn", 100);
        this.msgChunkLocked = config.getString("messages.chunk-locked", "&cThis chunk is locked! Required items: {items}");
        this.msgChunkUnlocked = config.getString("messages.chunk-unlocked", "&aYou unlocked the chunk!");
        this.msgTooFar = config.getString("messages.too-far", "&cYou can't unlock chunks more than {max} chunks from spawn!");
        this.msgError = config.getString("messages.error", "&cSomething went wrong!");
        // Load sound
        try {
            this.unlockSound = Sound.valueOf(config.getString("sound.unlock.name", "ENTITY_PLAYER_LEVELUP").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[ChunkLock] Invalid sound name in config! Defaulting to ENTITY_PLAYER_LEVELUP");
            this.unlockSound = Sound.ENTITY_PLAYER_LEVELUP;
        }
        this.unlockVolume = (float) config.getDouble("sound.unlock.volume", 1.0);
        this.unlockPitch = (float) config.getDouble("sound.unlock.pitch", 1.0);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerManager().getLobbyWorlds().contains(player.getWorld().getName())) return;

        World world = player.getWorld();

        if (world.getName().equalsIgnoreCase("world") ||
                world.getName().equalsIgnoreCase(templateWorld) ||
                !world.getName().equalsIgnoreCase("chunk_world_" + player.getUniqueId())) {
            return;
        }

        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        Chunk toChunk = event.getTo().getChunk();
        if (!plugin.getChunkManager().isChunkUnlocked(player, toChunk)) {
            event.setCancelled(true);
            String itemInfo = plugin.getChunkManager().formatedItemAndCount(player, toChunk);
            MessageSender.sendMessage(player, msgChunkLocked.replace("{items}", itemInfo));
        }
    }

    private boolean canAccessChunk(Player player, Chunk chunk) {
        ChunkManager manager = plugin.getChunkManager();
        return manager.getUnlockedChunks().get(player.getUniqueId()).contains(manager.serializeChunk(chunk));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getPlayerManager().getLobbyWorlds().contains(event.getPlayer().getWorld().getName())) return;

        if (!preventBlockBreak) return;

        Player player = event.getPlayer();
        if (!canAccessChunk(player, event.getBlock().getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getPlayerManager().getLobbyWorlds().contains(event.getPlayer().getWorld().getName())) return;

        if (!preventBlockPlace) return;

        Player player = event.getPlayer();
        if (!canAccessChunk(player, event.getBlock().getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Optional future logic
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getChunkManager().getUnlockedChunks().containsKey(player.getUniqueId())) {
            plugin.getChunkManager().removeAllChunkDisplayEntities(player);
            plugin.getChunkManager().saveAndUnloadPlayerWorld(player);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getPlayerManager().getLobbyWorlds().contains(event.getPlayer().getWorld().getName())) return;

        if (!preventInteraction) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        Chunk chunk = block.getChunk();
        ChunkManager manager = plugin.getChunkManager();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!manager.isItemRequiredForChunk(player, chunk, item)) return;

        if (!manager.getUnlockedChunks().get(player.getUniqueId()).contains(manager.serializeChunk(chunk))) {
            if (isTooFar(player, chunk)) {
                MessageSender.sendMessage(player, msgTooFar.replace("{max}", String.valueOf(maxDistanceFromSpawn)));
                event.setCancelled(true);
                return;
            }

            if (manager.hasEnoughRequiredItems(player, chunk)) {
                boolean success = manager.takeRequiredItemsSafely(player, chunk);
                if (success) {
                    manager.unlockChunk(player, chunk);
                    try {
                        player.playSound(player.getLocation(), unlockSound, unlockVolume, unlockPitch);
                    } catch (Throwable e) {
                    }
                    MessageSender.sendMessage(player, msgChunkUnlocked);
                } else if (manager.debug) {
                    MessageSender.sendMessage(player, msgError);
                }
            } else {
                String itemInfo = manager.formatedItemAndCount(player, chunk);
                MessageSender.sendMessage(player, msgChunkLocked.replace("{items}", itemInfo));
            }
            event.setCancelled(true);
        }
    }

    private boolean isTooFar(Player player, Chunk targetChunk) {
        Location spawn = plugin.getListener().getTemplateSpawn().clone();
        spawn.setWorld(player.getWorld());

        return plugin.getChunkManager().getChunkDistance(targetChunk, spawn.getChunk()) >= maxDistanceFromSpawn;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {

        if (plugin.getPlayerManager().getLobbyWorlds().contains(event.getPlayer().getWorld().getName())) return;

        Player player = event.getPlayer();
        Location forwardLocation = event.getItemDrop().getLocation().clone().add(player.getLocation().getDirection().normalize().multiply(2));
        Chunk dropChunk = forwardLocation.getChunk();
        ChunkManager chunkManager = plugin.getChunkManager();

        if (!chunkManager.getUnlockedChunks().get(player.getUniqueId()).contains(chunkManager.serializeChunk(dropChunk))) {
            if (isTooFar(player, dropChunk)) {
                MessageSender.sendMessage(player, msgTooFar.replace("{max}", String.valueOf(maxDistanceFromSpawn)));
                event.setCancelled(true);
                return;
            }

            ItemStack droppedItem = event.getItemDrop().getItemStack();
            if (!chunkManager.isItemRequiredForChunk(player, dropChunk, droppedItem)) {
                if (!preventItemDrop) return;
                event.setCancelled(true);
                return;
            }

            boolean success = chunkManager.takeRequiredItemsIncludingDrop(player, dropChunk, droppedItem);
            if (success) {
                chunkManager.unlockChunk(player, dropChunk);
                try {
                    player.playSound(player.getLocation(), unlockSound, unlockVolume, unlockPitch);
                } catch (Throwable e) {
                }
                MessageSender.sendMessage(player, msgChunkUnlocked);
            } else if (chunkManager.debug) {
                MessageSender.sendMessage(player, msgError);
            }

            event.setCancelled(true);
        } else {
            event.setCancelled(true);
            String itemInfo = chunkManager.formatedItemAndCount(player, dropChunk);
            MessageSender.sendMessage(player, msgChunkLocked.replace("{items}", itemInfo));
        }
    }

}