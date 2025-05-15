package org.jay.chunkLock.managers;


import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jay.chunkLock.ChunkLockPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class ChunkManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Set<String>> unlockedChunks = new HashMap<>();
    private final Map<UUID, Map<String, String>> requiredItemsMap = new HashMap<>();
    private final File dataFile;
    private final Map<UUID, Map<String, List<Entity>>> chunkDisplayEntities = new HashMap<>();
    public boolean debug = false;
    private FileConfiguration dataConfig;
    private int teleportDelaySeconds;
    private Sound teleportSound;
    private float teleportVolume;
    private float teleportPitch;
    private String msgTeleportStart;
    private String msgTeleportCountdown;
    private String msgTeleportDone;
    private String msgNoLocation;
    private Sound tickSound;
    private float tickVolume;
    private float tickPitch;
    private boolean unlockAnimationEnabled;
    private int minimumRequiredAmount;
    private int maximumRequiredAmount;

    public int getBorderHeight() {
        return borderHeight;
    }

    public void setBorderHeight(int borderHeight) {
        this.borderHeight = borderHeight;
        for (Map.Entry<UUID, Map<String, List<Entity>>> entry : chunkDisplayEntities.entrySet()) {
            for (List<Entity> entities : entry.getValue().values()) {
                for (Entity entity : entities) {
                    if (entity instanceof TextDisplay) {
                        ((TextDisplay) entity).setText("\n".repeat(borderHeight) + "                                                                                                                                                                " + "\n".repeat(borderHeight));

                    }
                }
            }
        }
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(int borderWidth) {
        this.borderWidth = borderWidth;
        for (Map.Entry<UUID, Map<String, List<Entity>>> entry : chunkDisplayEntities.entrySet()) {
            for (List<Entity> entities : entry.getValue().values()) {
                for (Entity entity : entities) {
                    if (entity instanceof TextDisplay) {

                        ((TextDisplay) entity).setDisplayWidth(borderLineWidth);
                    }
                }
            }
        }
    }

    public int getBorderLineWidth() {
        return borderLineWidth;
    }

    public void setBorderLineWidth(int borderLineWidth) {

        this.borderLineWidth = borderLineWidth;
        for (Map.Entry<UUID, Map<String, List<Entity>>> entry : chunkDisplayEntities.entrySet()) {
            for (List<Entity> entities : entry.getValue().values()) {
                for (Entity entity : entities) {
                    if (entity instanceof TextDisplay) {
                        ((TextDisplay) entity).setLineWidth(borderLineWidth);
                    }
                }
            }
        }
    }

    private int borderHeight;
    private int borderWidth;
    private int borderLineWidth;

    public ChunkManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "chunk_data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
        reloadConfigValues();
    }

    public boolean isChunkUnlocked(Player player, Chunk chunk) {
        return unlockedChunks.getOrDefault(player.getUniqueId(), Collections.emptySet()).contains(serializeChunk(chunk));
    }

    public void reloadConfigValues() {
        FileConfiguration config = plugin.getConfig();

        this.borderHeight = config.getInt("border.height", 50);
        this.borderWidth = config.getInt("border.width", 50);
        this.borderLineWidth = config.getInt("border.lineWidth", 50);
        this.teleportDelaySeconds = config.getInt("delay-seconds", 5);
        this.minimumRequiredAmount = config.getInt("minimumRequiredAmounts", 1);
        this.maximumRequiredAmount = config.getInt("maximumRequiredAmount", 32);

        try {
            this.teleportSound = Sound.valueOf(config.getString("sound.teleport.name", "ENTITY_ENDERMAN_TELEPORT").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid teleport sound, defaulting to ENTITY_ENDERMAN_TELEPORT.");
            this.teleportSound = Sound.ENTITY_ENDERMAN_TELEPORT;
        }

        this.unlockAnimationEnabled = config.getBoolean("unlockAnimationEnabled", false);
        this.teleportVolume = (float) config.getDouble("sound.teleport.volume", 1.0);
        this.teleportPitch = (float) config.getDouble("sound.teleport.pitch", 1.0);

        this.msgTeleportStart = config.getString("messages.starting", "&eTeleporting to the world in {seconds} seconds...");
        this.msgTeleportCountdown = config.getString("messages.countdown", "&bTeleporting in {seconds} second(s)...");
        this.msgTeleportDone = config.getString("messages.done", "&aTeleported!");
        this.msgNoLocation = config.getString("messages.no-location", "&cPlease first set a chunk!");
        try {
            this.tickSound = Sound.valueOf(config.getString("unlocking-tick.name", "BLOCK_NOTE_BLOCK_PLING").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid tick sound, defaulting to BLOCK_NOTE_BLOCK_PLING.");
            this.tickSound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        this.tickVolume = (float) config.getDouble("unlocking-tick.volume", 1.0);
        this.tickPitch = (float) config.getDouble("unlocking-tick.pitch", 1.0);

    }

    public String formatedItemAndCount(Player player, Chunk chunk) {
        String requiredItems = getRequiredItemsForChunk(player, chunk);

        if (requiredItems.isEmpty()) {
            return ChatColor.GRAY + "nothing";
        }

        String[] parts = requiredItems.split(":");
        if (parts.length != 2) {
            return ChatColor.RED + "Invalid item format";
        }

        String materialName = parts[0].toUpperCase().replace("_", " ");
        int count;

        try {
            count = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return ChatColor.RED + "Invalid item count";
        }

        return ChatColor.GOLD + String.valueOf(count) + "x " + materialName;
    }

    public Set<Chunk> getUnassignedChunks(Player player) {
        Set<Chunk> unassignedChunks = new HashSet<>();
        UUID uuid = player.getUniqueId();

        Set<String> unlocked = unlockedChunks.getOrDefault(uuid, new HashSet<>());
        Map<String, String> required = requiredItemsMap.getOrDefault(uuid, new HashMap<>());

        World world = player.getWorld();

        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (String chunkKey : unlocked) {
            Chunk base = deserializeChunk(chunkKey);
            if (base == null) continue;

            for (int[] dir : directions) {
                int x = base.getX() + dir[0];
                int z = base.getZ() + dir[1];

                Chunk nearby = world.getChunkAt(x, z);
                String serialized = serializeChunk(nearby);

                // Check not unlocked and not assigned any required items
                if (!unlocked.contains(serialized) && !required.containsKey(serialized)) {
                    unassignedChunks.add(nearby);
                }
            }
        }

        return unassignedChunks;
    }

    public int getChunkDistance(Chunk from, Chunk to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        return dx + dz; // Manhattan distance in chunks
    }

    public void unlockChunk(Player player, Chunk chunk) {
        unlockedChunks.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(serializeChunk(chunk));
        requiredItemsMap.getOrDefault(player.getUniqueId(), new HashMap<>()).remove(serializeChunk(chunk));
        removeChunkDisplayEntities(player, chunk);
        if (unlockAnimationEnabled) {
            buildBarrierWalls(chunk.getWorld(), chunk);
        }
        World world = player.getWorld();
        Map<Material, Integer> blockCount = getBlockCount(player, world, chunk);
        if (blockCount == null) return;

        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] dir : directions) {
            Chunk nearby = world.getChunkAt(chunk.getX() + dir[0], chunk.getZ() + dir[1]);
            if (!isChunkUnlocked(player, nearby) && requiredItemsMap.get(player.getUniqueId()).getOrDefault(serializeChunk(nearby), null) == null) {
                if (debug) {
                    plugin.getLogger().info("Assigning required items to chunk (" + nearby.getX() + "," + nearby.getZ() + ") for player " + player.getName());
                }
                if (isAlreadySet(nearby)) {
                    ItemStack itemStack = getDefaultItem(nearby);
                    if (itemStack == null) {
                        if (debug)
                            plugin.getLogger().info("No default item found for chunk (" + nearby.getX() + "," + nearby.getZ() + ")");
                        continue;
                    }
                    setRequiredItemsForChunk(player, nearby, itemStack.getType().toString() + ":" + itemStack.getAmount());
                    continue;
                }
                setRequiredItemsForChunk(player, nearby, getStrings(blockCount));

            } else {
                if (debug) {
                    plugin.getLogger().info("Chunk (" + nearby.getX() + "," + nearby.getZ() + ") already unlocked for player " + player.getName());
                }
            }
        }
        saveData();

    }


    public String getChunkKey(Location location, UUID uuid) {
        Chunk chunk = location.getChunk();
        String world = location.getWorld().getName();
        return "chunk_" + world + "_" + uuid + "," + chunk.getX() + "," + chunk.getZ();
    }

    public void spawnAllRequirementDisplaysForPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if player has any locked chunks with requirements
        if (!requiredItemsMap.containsKey(uuid)) return;

        Map<String, String> chunkItemMap = requiredItemsMap.get(uuid);
        Set<String> unlocked = unlockedChunks.getOrDefault(uuid, Collections.emptySet());

        for (Map.Entry<String, String> entry : chunkItemMap.entrySet()) {
            String chunkKey = entry.getKey();
            Chunk chunk = deserializeChunk(chunkKey);

            if (chunk == null) continue;

            // Skip if chunk is already unlocked
            if (unlocked.contains(serializeChunk(chunk))) continue;

            String requiredItems = entry.getValue();

            setRequiredItemsForChunk(player, chunk, requiredItems);

        }
    }


    public void lockAllChunks(Player player) {
        unlockedChunks.put(player.getUniqueId(), new HashSet<>());
        requiredItemsMap.put(player.getUniqueId(), new HashMap<>());
        saveData();
    }

    public void setRequiredItemsForChunk(Player player, Chunk chunk, String items) {
        requiredItemsMap.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(serializeChunk(chunk), items);
        spawnChunkRequirementDisplays(player, chunk, items);
        saveData();
    }

    public String getRequiredItemsForChunk(Player player, Chunk chunk) {
        return requiredItemsMap.getOrDefault(player.getUniqueId(), Collections.emptyMap()).getOrDefault(serializeChunk(chunk), "");
    }

    public int totalChunkUnlocked(Player player) {
        Set<String> chunks = unlockedChunks.get(player.getUniqueId());
        return chunks != null ? chunks.size() : 0;
    }

    public int getRequiredItemsForChunkCount(Player player, Chunk chunk) {
        return Integer.parseInt(requiredItemsMap.getOrDefault(player.getUniqueId(), Collections.emptyMap()).getOrDefault(serializeChunk(chunk), "").split(":")[1]);
    }

    private Material convertOreToItem(Material mat) {
        switch (mat) {
            case COAL_ORE:
                return Material.COAL;
            case DEEPSLATE_COAL_ORE:
                return Material.COAL;

            case IRON_ORE:
                return Material.IRON_INGOT;
            case DEEPSLATE_IRON_ORE:
                return Material.IRON_INGOT;

            case GOLD_ORE:
                return Material.GOLD_INGOT;
            case DEEPSLATE_GOLD_ORE:
                return Material.GOLD_INGOT;

            case COPPER_ORE:
                return Material.COPPER_INGOT;
            case DEEPSLATE_COPPER_ORE:
                return Material.COPPER_INGOT;

            case DIAMOND_ORE:
                return Material.DIAMOND;
            case DEEPSLATE_DIAMOND_ORE:
                return Material.DIAMOND;

            case EMERALD_ORE:
                return Material.EMERALD;
            case DEEPSLATE_EMERALD_ORE:
                return Material.EMERALD;

            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                return Material.REDSTONE;

            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
                return Material.LAPIS_LAZULI;

            case NETHER_QUARTZ_ORE:
                return Material.QUARTZ;
            case NETHER_GOLD_ORE:
                return Material.GOLD_NUGGET;

            default:
                return mat; // Non-ore blocks return themselves
        }
    }

    public void autoAssignItemsForNearbyChunks(Player player) {
        Chunk center = player.getLocation().getChunk();
        World world = center.getWorld();

        if (!isChunkUnlocked(player, center)) {
            if (debug) {
                plugin.getLogger().info("Chunk (" + center.getX() + "," + center.getZ() + ") is locked for player " + player.getName());
            }
            return;
        }

        Map<Material, Integer> blockCount = getBlockCount(player, world, center);
        if (blockCount == null) return;

        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] dir : directions) {
            Chunk nearby = world.getChunkAt(center.getX() + dir[0], center.getZ() + dir[1]);
            if (!isChunkUnlocked(player, nearby)) {
                if (debug) {
                    plugin.getLogger().info("Assigning required items to chunk (" + nearby.getX() + "," + nearby.getZ() + ") for player " + player.getName());
                }
                if (isAlreadySet(nearby)) {
                    ItemStack itemStack = getDefaultItem(nearby);
                    if (itemStack == null) {
                        if (debug)
                            plugin.getLogger().info("No default item found for chunk (" + nearby.getX() + "," + nearby.getZ() + ")");
                        continue;
                    }
                    setRequiredItemsForChunk(player, nearby, itemStack.getType().toString() + ":" + itemStack.getAmount());
                    continue;
                }
                setRequiredItemsForChunk(player, nearby, getStrings(blockCount));
            } else {
                if (debug) {
                    plugin.getLogger().info("Chunk (" + nearby.getX() + "," + nearby.getZ() + ") already unlocked for player " + player.getName());
                }
            }
        }
    }

    private boolean isAlreadySet(Chunk nearby) {
        for (String chunk : ChunkLockPlugin.getInstance().getCustomChunkItems().keySet()) {
            Chunk chunk1 = deserializeChunk(chunk);
            if (chunk1 == null) continue;
            if (chunk1.getX() == nearby.getX() && chunk1.getZ() == nearby.getZ())
                return true;
        }
        return false;
    }

    private ItemStack getDefaultItem(Chunk nearby) {
        for (String chunk : ChunkLockPlugin.getInstance().getCustomChunkItems().keySet()) {
            Chunk chunk1 = deserializeChunk(chunk);
            if (chunk1 == null) continue;
            if (chunk1.getX() == nearby.getX() && chunk1.getZ() == nearby.getZ())
                return ChunkLockPlugin.getInstance().getCustomChunkItems().get(chunk);
        }
        return null;
    }

    private Map<Material, Integer> getBlockCount(Player player, World world, Chunk center) {
        Map<Material, Integer> blockCount = new HashMap<>();
        for (int x = 0; x < 16; x++) {
            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                for (int z = 0; z < 16; z++) {
                    Material baseMat = center.getBlock(x, y, z).getType();

                    // Skip air, lava, and water
                    if (baseMat.isAir() || baseMat == Material.LAVA || baseMat == Material.WATER ||
                            baseMat == Material.LAVA_CAULDRON || baseMat == Material.WATER_CAULDRON || baseMat == Material.BEDROCK || baseMat == Material.BUBBLE_COLUMN) {
                        continue;
                    }

                    // Convert ores to their item drops
                    Material converted = convertOreToItem(baseMat);
                    blockCount.put(converted, blockCount.getOrDefault(converted, 0) + 1);
                }
            }
        }

        if (blockCount.isEmpty()) {
            if (debug) {
                plugin.getLogger().info("No valid blocks found in chunk (" + center.getX() + "," + center.getZ() + ") for player " + player.getName());
            }
            return null;
        }

        // Log block counts for debug purposes
        if (debug) {
            for (Map.Entry<Material, Integer> entry : blockCount.entrySet()) {
                plugin.getLogger().info("Block/Item " + entry.getKey() + ": " + entry.getValue());
            }
        }
        return blockCount;
    }


    private @NotNull String getStrings(Map<Material, Integer> blockCount) {
        List<Map.Entry<Material, Integer>> entries = new ArrayList<>(blockCount.entrySet());
        Map.Entry<Material, Integer> randomEntry = entries.get(new Random().nextInt(entries.size()));
        Material mostCommonMaterial = randomEntry.getKey();
        int totalCount = randomEntry.getValue();


        // Calculate dynamic requiredAmount based on availability (e.g. 20% of total)
        int requiredAmount = Math.max(minimumRequiredAmount, Math.min(maximumRequiredAmount, totalCount / 5));
        if (debug) {
            plugin.getLogger().info("Most common block: " + mostCommonMaterial + ", count: " + totalCount + ", requiredAmount: " + requiredAmount);
        }

        return (mostCommonMaterial.toString() + ":" + requiredAmount);
    }

    public Location getNearestLocation(Location target, Location[] locations) {
        Location nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Location loc : locations) {
            double distanceSquared = loc.distanceSquared(target); // more efficient than distance()
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = loc;
            }
        }

        return nearest;
    }

    public void spawnChunkRequirementDisplays(Player player, Chunk chunk, String requiredItems) {
        UUID uuid = player.getUniqueId();
        World world = player.getWorld();

        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int centerX = minX + 8;
        int centerZ = minZ + 8;

        if (debug) {
            plugin.getLogger().info("Spawning requirement displays for chunk (" + chunk.getX() + "," + chunk.getZ() + ") for player " + player.getName());
            plugin.getLogger().info("Chunk boundaries: min(" + minX + "," + minZ + "), max(" + maxX + "," + maxZ + "), center(" + centerX + "," + centerZ + ")");
        }

        if (isChunkUnlocked(player, chunk)) return;

        // Prevent duplicates
        Map<String, List<Entity>> chunkMap = chunkDisplayEntities.computeIfAbsent(uuid, k -> new HashMap<>());
        if (chunkMap.containsKey(serializeChunk(chunk))) {
            if (debug) plugin.getLogger().info("Display entities already exist for this chunk and player.");
            return;
        }

        // Parse item
        String[] parts = requiredItems.split(":");
        Material material = Material.matchMaterial(parts[0].toUpperCase());
        int amount = Integer.parseInt(parts[1]);

        if (material == null) {
            if (debug) plugin.getLogger().warning("Material " + parts[0] + " not recognized for required item!");
            return;
        }

        // Calculate wall positions with offset
        TextDisplay wallNorth = (TextDisplay) showChunkTextWall(player, new Location(world, centerX, world.getHighestBlockYAt(centerX, minZ) + 1, minZ), "NORTH");
        TextDisplay wallSouth = (TextDisplay) showChunkTextWall(player, new Location(world, centerX, world.getHighestBlockYAt(centerX, maxZ) + 1, maxZ), "SOUTH");
        TextDisplay wallWest = (TextDisplay) showChunkTextWall(player, new Location(world, minX, world.getHighestBlockYAt(minX, centerZ) + 1, centerZ), "WEST");
        TextDisplay wallEast = (TextDisplay) showChunkTextWall(player, new Location(world, maxX, world.getHighestBlockYAt(maxX, centerZ) + 1, centerZ), "EAST");


        // ItemDisplay location (centered)
        Location[] positions = new Location[]{
                new Location(world, centerX, world.getHighestBlockYAt(centerX, minZ) + 1, minZ),
                new Location(world, centerX, world.getHighestBlockYAt(centerX, maxZ) + 1, maxZ),
                new Location(world, minX, world.getHighestBlockYAt(minX, centerZ) + 1, centerZ),
                new Location(world, maxX, world.getHighestBlockYAt(maxX, centerZ) + 1, centerZ)
        };

        Location itemLoc = getNearestLocation(player.getLocation(), positions);
        Location loc = itemLoc.clone();
        loc.setY(player.getY());

        ItemDisplay itemDisplay = world.spawn(loc, ItemDisplay.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        itemDisplay.setItemStack(new ItemStack(material));
        itemDisplay.setCustomNameVisible(false);

        // Floating item follows player's Y
        if (ChunkLockPlugin.getInstance().getConfig().getBoolean("floating-item-movement", false)) {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline()) return;
                Location clone = itemDisplay.getLocation().clone();
                clone.setY(player.getLocation().getY());
                itemDisplay.teleport(clone);
            }, 0L, 20L);
        }

        Entity nameDisplay = spawnItemNameDisplay(player, loc, material, amount, debug);

        // Store all entities
        List<Entity> entityList = new ArrayList<>();
        entityList.add(nameDisplay);
        entityList.add(itemDisplay);
        entityList.add(wallNorth);
        entityList.add(wallSouth);
        entityList.add(wallWest);
        entityList.add(wallEast);

        chunkMap.put(serializeChunk(chunk), entityList);
    }


    public Entity spawnItemNameDisplay(Player player, Location location, Material material, int amount, boolean debug) {
        Location baseLoc = location.add(0, 3, 0); // 2 blocks above player
        World world = player.getWorld();

        ArmorStand armorStand = world.spawn(baseLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setCustomName(ChatColor.GOLD + String.valueOf(amount) + "x " + material.toString().replace("_", " "));
            stand.setCustomNameVisible(true);
            stand.setMarker(true); // Prevents collision and hitbox
            stand.setGravity(false);
            stand.setSmall(true); // Optional: Makes it cleaner
        });

        // Use a repeating task to keep the ArmorStand 2 blocks above the player
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || armorStand.isDead()) {
                    return;
                }
                Location clone = armorStand.getLocation().clone();
                clone.setY(player.getLocation().getY() + 2);
                armorStand.teleport(clone);
            }
        }, 0L, 20L); // Repeat every tick (20 times per second)

        if (debug) {
            plugin.getLogger().info("Spawned armor stand display: " + armorStand.getCustomName() + " at " + baseLoc);
        }
        return armorStand;
    }

    public int getRequiredAmount(Player player, Chunk chunk) {
        String requiredItems = getRequiredItemsForChunk(player, chunk);
        if (requiredItems.isEmpty()) return 0;

        try {
            return Integer.parseInt(requiredItems.split(":")[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean takeRequiredItemsIncludingDrop(Player player, Chunk chunk, ItemStack droppedItem) {
        Material requiredMaterial = getRequiredMaterial(player, chunk);
        int requiredAmount = getRequiredAmount(player, chunk);

        if (requiredMaterial == null || requiredAmount <= 0) return false;

        // Check if dropped item is the required material
        if (droppedItem.getType() != requiredMaterial) return false;

        int droppedAmount = droppedItem.getAmount();
        int remaining = requiredAmount - droppedAmount;

        if (remaining < 0) return false; // Player dropped more than required

        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == requiredMaterial) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    remaining -= itemAmount;
                    contents[i] = null;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }

                if (remaining <= 0) break;
            }
        }

        if (remaining <= 0) {
            player.getInventory().setContents(contents);
            return true;
        }

        return false;
    }


    public boolean takeRequiredItemsSafely(Player player, Chunk chunk) {
        Material requiredMaterial = getRequiredMaterial(player, chunk);
        int requiredAmount = getRequiredAmount(player, chunk);

        if (requiredMaterial == null || requiredAmount <= 0) return false;

        int totalFound = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == requiredMaterial) {
                totalFound += item.getAmount();
            }
        }

        if (totalFound < requiredAmount) return false;

        // Now remove items safely since we know the player has enough
        int remaining = requiredAmount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == requiredMaterial) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    remaining -= itemAmount;
                    contents[i] = null;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }

                if (remaining <= 0) break;
            }
        }

        player.getInventory().setContents(contents);
        return true;
    }


    public boolean hasEnoughRequiredItems(Player player, Chunk chunk) {
        Material requiredMaterial = getRequiredMaterial(player, chunk);
        int requiredAmount = getRequiredAmount(player, chunk);

        if (requiredMaterial == null || requiredAmount == 0) return false;

        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == requiredMaterial) {
                total += item.getAmount();
            }
        }

        return total >= requiredAmount;
    }


    public boolean isItemRequiredForChunk(Player player, Chunk chunk, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material requiredMaterial = getRequiredMaterial(player, chunk);
        return requiredMaterial != null && item.getType() == requiredMaterial;
    }

    public Entity showChunkTextWall(Player player, Location baseLocation, String direction) {
        World world = player.getWorld();

        double y = baseLocation.getY();

        Location textLocation = baseLocation.clone();
        textLocation.setY(y);

        float yaw = switch (direction.toUpperCase()) {
            case "NORTH" -> 180f;
            case "SOUTH" -> 0f;
            case "WEST" -> 90f;
            case "EAST" -> -90f;
            default -> 0f;
        };

        TextDisplay entity = world.spawn(textLocation, TextDisplay.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        entity.setRotation(yaw, 0f);
        entity.setText("\n".repeat(borderHeight) + "                                                                                                                                                                " + "\n".repeat(borderHeight));
        entity.setLineWidth(borderLineWidth);
        entity.setDisplayWidth(borderWidth);
        entity.setDisplayHeight(borderHeight);
        entity.setBillboard(Display.Billboard.FIXED);
        entity.setSeeThrough(false);
        entity.setVisibleByDefault(true);
        entity.setPersistent(true);
        entity.setBackgroundColor(Color.fromARGB(75, 211, 211, 211));
        entity.setGlowColorOverride(Color.AQUA);
        entity.setGlowing(true);
        return entity;
    }


    public Material getRequiredMaterial(Player player, Chunk chunk) {
        String requiredItems = getRequiredItemsForChunk(player, chunk);
        if (requiredItems.isEmpty()) return null;

        String[] parts = requiredItems.split(":");
        if (parts.length != 2) return null;

        return Material.matchMaterial(parts[0].toUpperCase());
    }

    public ItemStack getRequiredItemStack(Player player, Chunk chunk) {
        String requiredItems = getRequiredItemsForChunk(player, chunk);
        if (requiredItems.isEmpty()) return null;

        String[] parts = requiredItems.split(":");
        if (parts.length != 2) return null;

        try {
            Material material = Material.matchMaterial(parts[0].toUpperCase());
            int amount = Integer.parseInt(parts[1]);
            if (material == null) return null;

            return new ItemStack(material, amount);
        } catch (Exception e) {
            return null;
        }
    }

    public World loadPlayerWorld(Player player) {
        String worldName = "chunk_world_" + player.getUniqueId();
        Path pluginWorldPath = plugin.getDataFolder().toPath().resolve("player_worlds").resolve(worldName);
        Path serverWorldPath = Bukkit.getWorldContainer().toPath().resolve(worldName);

        try {
            if (!Files.exists(serverWorldPath)) {
                Files.createDirectories(serverWorldPath);
                if (Files.exists(pluginWorldPath)) {
                    copyDirectory(pluginWorldPath, serverWorldPath);
                } else {
                    // Create from template if no saved world
                    Path templatePath = plugin.getDataFolder().toPath().resolve(ChunkLockPlugin.getInstance().getPlayerManager().getTemplate());
                    copyDirectory(templatePath, serverWorldPath);
                }
            }

            return Bukkit.createWorld(new WorldCreator(worldName));
        } catch (IOException e) {
            e.printStackTrace();
            MessageSender.sendMessage(player, "§cFailed to load your world.");
            return null;
        }
    }

    public void saveAndUnloadPlayerWorld(Player player) {
        String worldName = "chunk_world_" + player.getUniqueId();
        World world = Bukkit.getWorld(worldName);

        if (world == null) return;

        Path serverWorldPath = Bukkit.getWorldContainer().toPath().resolve(worldName);
        Path pluginWorldPath = plugin.getDataFolder().toPath().resolve("player_worlds").resolve(worldName);

        // Teleport player to lobby before unload
        for (Player p : world.getPlayers()) {
            ChunkLockPlugin.getInstance().teleportToLobby(p);
        }

        Bukkit.unloadWorld(world, true);

        try {
            if (!Files.exists(pluginWorldPath)) {
                Files.createDirectories(pluginWorldPath);
            }
            moveDirectory(serverWorldPath, pluginWorldPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("Source directory does not exist: " + source.toAbsolutePath());
        }

        // Move files and directories
        Files.walk(source)
                .sorted(Comparator.reverseOrder()) // move files before directories
                .forEach(path -> {
                    try {
                        Path targetPath = target.resolve(source.relativize(path));
                        if (Files.isDirectory(path)) {
                            if (!Files.exists(targetPath)) {
                                Files.createDirectories(targetPath);
                            }
                        } else {
                            Files.createDirectories(targetPath.getParent()); // make sure parent folder exists
                            Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to move file or directory: " + path, e);
                    }
                });
        if (!source.endsWith("template_world")) {
            // Delete source directory after moving everything
            Files.walk(source)
                    .sorted(Comparator.reverseOrder()) // delete files before folders
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("Source directory does not exist: " + source.toAbsolutePath());
        }

        // Copy files and directories
        Files.walk(source)
                .sorted(Comparator.reverseOrder()) // copy files before directories
                .forEach(path -> {
                    try {
                        Path targetPath = target.resolve(source.relativize(path));
                        if (Files.isDirectory(path)) {
                            if (!Files.exists(targetPath)) {
                                Files.createDirectories(targetPath);
                            }
                        } else {
                            Files.createDirectories(targetPath.getParent()); // make sure parent folder exists
                            Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to copy file or directory: " + path, e);
                    }
                });
    }


    public void resetPlayerWorld(Player player) {
        String worldName = "chunk_world_" + player.getUniqueId();
        World world = Bukkit.getWorld(worldName);

        // Unload world
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        Path pluginWorldPath = plugin.getDataFolder().toPath().resolve("player_worlds").resolve(worldName);
        // Delete folder
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            try {
                deleteDirectory(worldFolder);
                deleteDirectory(pluginWorldPath.toFile());
                plugin.getLogger().info("Deleted world folder: " + worldFolder.getName());
            } catch (IOException e) {
                e.printStackTrace();
                MessageSender.sendMessage(player, "§cFailed to delete your world. Contact an admin.");
                return;
            }
        }

        // Clear data (optional: only if you want a true reset)
        unlockedChunks.remove(player.getUniqueId());
        requiredItemsMap.remove(player.getUniqueId());
        chunkDisplayEntities.remove(player.getUniqueId());
        saveData();

        MessageSender.sendMessage(player, "§aYour chunk world has been successfully deleted.");
    }

    private void deleteDirectory(File dir) throws IOException {
        if (dir.isDirectory()) {
            File[] entries = dir.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    deleteDirectory(entry);
                }
            }
        }
        if (!dir.delete()) {
            throw new IOException("Failed to delete " + dir);
        }
    }

    public World createPlayerWorld(Player player) {
        String templateWorldName = plugin.getConfig().getString("template.world");
        if (templateWorldName == null) {
            plugin.getLogger().warning("No template world set!");
            return null;
        }

        File worldFolder = new File(plugin.getDataFolder(), "player_worlds/" + player.getUniqueId());
        File templateFolder = new File(Bukkit.getWorldContainer(), templateWorldName);

        try {
            copyWorldFolder(templateFolder, worldFolder);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        WorldCreator creator = new WorldCreator(worldFolder.getName());
        return Bukkit.createWorld(creator);
    }

    private void copyWorldFolder(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) dest.mkdirs();
            String[] files = src.list();
            for (String file : files) {
                copyWorldFolder(new File(src, file), new File(dest, file));
            }
        } else {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void teleportToPlayerWorld(Player player, World world) {
        Location loc = plugin.getConfig().getLocation("template.spawn");
        if (loc == null) {
            MessageSender.sendMessage(player, msgNoLocation);
            return;
        }
        loc.setWorld(world);

        // Send initial message
        MessageSender.sendMessage(player, msgTeleportStart.replace("{seconds}", String.valueOf(teleportDelaySeconds)));

        new BukkitRunnable() {
            int countdown = teleportDelaySeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                player.sendActionBar(ChatColor.translateAlternateColorCodes('&',
                        msgTeleportCountdown.replace("{seconds}", String.valueOf(countdown))));
                player.playSound(player.getLocation(), tickSound, tickVolume, tickPitch);

                if (countdown <= 0) {
                    player.teleport(loc);
                    if (!chunkDisplayEntities.containsKey(player.getUniqueId())) {
                        spawnAllRequirementDisplaysForPlayer(player);
                    }
                    player.playSound(player.getLocation(), teleportSound, teleportVolume, teleportPitch);
                    MessageSender.sendMessage(player, msgTeleportDone);
                    cancel();
                    return;
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20 ticks = 1 second
    }


    public void removeChunkDisplayEntities(Player player, Chunk chunk) {
        UUID uuid = player.getUniqueId();
        Map<String, List<Entity>> map = chunkDisplayEntities.get(uuid);
        if (map == null) return;
        List<Entity> entities = map.remove(serializeChunk(chunk));
        if (entities != null) {
            for (Entity e : entities) {
                e.remove();
            }
        }
    }

    public void removeAllChunkDisplayEntities(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, List<Entity>> map = chunkDisplayEntities.remove(uuid); // Remove entire map to clean up
        if (map == null) return;

        for (List<Entity> entities : map.values()) {
            for (Entity entity : entities) {
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
        }
    }

    public void buildBarrierWalls(World world, Chunk chunk) {
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        int maxY = world.getMaxHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (x == 0 || x == 15 || z == 0 || z == 15) {
                    for (int y = world.getMinHeight(); y < maxY; y++) {
                        Location loc = new Location(world, chunkX + x, y, chunkZ + z);
                        world.spawnParticle(Particle.BLOCK_MARKER, loc, 1, Material.GREEN_STAINED_GLASS_PANE.createBlockData());
                    }
                }
            }
        }
    }

    public void buildBarrierWall(Player player, Chunk targetChunk) {
        World world = targetChunk.getWorld();
        Chunk playerChunk = player.getLocation().getChunk();

        int chunkX = targetChunk.getX() << 4;
        int chunkZ = targetChunk.getZ() << 4;
        int maxY = world.getMaxHeight();
        int minY = world.getMinHeight();

        // Determine which side of the targetChunk is adjacent to the playerChunk
        int dx = targetChunk.getX() - playerChunk.getX();
        int dz = targetChunk.getZ() - playerChunk.getZ();

        // Only spawn wall on the player's side
        if (dx == 1) {
            // Wall on west side (x == 0)
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, chunkX, y, chunkZ + z);
                    world.spawnParticle(Particle.BLOCK_MARKER, loc, 1, Material.RED_STAINED_GLASS_PANE.createBlockData());
                }
            }
        } else if (dx == -1) {
            // Wall on east side (x == 15)
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, chunkX + 15, y, chunkZ + z);
                    world.spawnParticle(Particle.BLOCK_MARKER, loc, 1, Material.RED_STAINED_GLASS_PANE.createBlockData());
                }
            }
        } else if (dz == 1) {
            // Wall on north side (z == 0)
            for (int x = 0; x < 16; x++) {
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, chunkX + x, y, chunkZ);
                    world.spawnParticle(Particle.BLOCK_MARKER, loc, 1, Material.RED_STAINED_GLASS_PANE.createBlockData());
                }
            }
        } else if (dz == -1) {
            // Wall on south side (z == 15)
            for (int x = 0; x < 16; x++) {
                for (int y = minY; y < maxY; y++) {
                    Location loc = new Location(world, chunkX + x, y, chunkZ + 15);
                    world.spawnParticle(Particle.BLOCK_MARKER, loc, 1, Material.RED_STAINED_GLASS_PANE.createBlockData());
                }
            }
        }
    }

    public void teleportPlayerToNearestUnlockedChunk(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> unlocked = unlockedChunks.getOrDefault(uuid, Collections.emptySet());

        if (unlocked.isEmpty()) {
            MessageSender.sendMessage(player, ChatColor.RED + "No unlocked chunks found! Contact staff.");
            return;
        }

        Location nearest = null;
        double closestDist = Double.MAX_VALUE;

        for (String chunka : unlocked) {

            Chunk chunk = deserializeChunk(chunka);
            if (chunk == null) continue;
            Location loc = chunk.getBlock(8, chunk.getWorld().getHighestBlockYAt(chunk.getBlock(8, 0, 8).getLocation()) + 1, 8).getLocation();
            double dist = loc.distanceSquared(player.getLocation());
            if (dist < closestDist) {
                closestDist = dist;
                nearest = loc;
            }
        }

        if (nearest != null) {
            player.teleport(nearest);
            MessageSender.sendMessage(player, ChatColor.YELLOW + "You were teleported to your nearest unlocked chunk.");
        }
    }


    public void saveData() {
        for (UUID uuid : unlockedChunks.keySet()) {
            String key = uuid.toString();
            Set<String> chunks = unlockedChunks.get(uuid);
            dataConfig.set("players." + key + ".unlocked", new ArrayList<>(chunks));


            Map<String, String> chunkItems = requiredItemsMap.getOrDefault(uuid, new HashMap<>());
            for (Map.Entry<String, String> entry : chunkItems.entrySet()) {
                String chunkKey = entry.getKey();
                dataConfig.set("players." + key + ".required." + chunkKey, entry.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;

        ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Set<String> chunkKeys = new HashSet<>(dataConfig.getStringList("players." + uuidStr + ".unlocked"));
            unlockedChunks.put(uuid, chunkKeys); // Store as Chunk set

            if (dataConfig.isConfigurationSection("players." + uuidStr + ".required")) {
                Map<String, String> required = new HashMap<>();
                ConfigurationSection requiredSection = dataConfig.getConfigurationSection("players." + uuidStr + ".required");

                for (String chunkKey : requiredSection.getKeys(false)) {
                    String items = requiredSection.getString(chunkKey);
                    required.put(chunkKey, items);
                }

                requiredItemsMap.put(uuid, required); // You will need a Map<UUID, Map<String, List<String>>>
            }
        }
    }

    private Set<Chunk> convertToChunks(Set<String> keys) {
        Set<Chunk> chunks = new HashSet<>();
        for (String key : keys) {
            Chunk chunk = deserializeChunk(key);
            if (chunk != null) chunks.add(chunk);
        }
        return chunks;
    }


    public String serializeChunk(Chunk chunk) {
        return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
    }

    private Chunk deserializeChunk(String str) {
        String[] parts = str.split(",");
        if (parts.length != 3) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return world.getChunkAt(x, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    public void clearChunkDisplays(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, List<Entity>> entityMap = chunkDisplayEntities.getOrDefault(uuid, new HashMap<>());
        for (List<Entity> entities : entityMap.values()) {
            for (Entity e : entities) {
                e.remove();
            }
        }
        chunkDisplayEntities.remove(uuid);
    }

    public void clearPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        unlockedChunks.remove(uuid);
        requiredItemsMap.remove(uuid);
        clearChunkDisplays(player);
        saveData();
    }

    public Map<UUID, Set<String>> getUnlockedChunks() {
        return unlockedChunks;
    }

    public boolean isNewPlayer(Player player) {
        return !unlockedChunks.containsKey(player.getUniqueId());
    }

    public void teleportToPlayerWorldFirstTime(Player player, World world) {
        Location loc = plugin.getConfig().getLocation("template.spawn");
        if (loc == null) {
            MessageSender.sendMessage(player, msgNoLocation);
            return;
        }
        loc.setWorld(world);

        // Send initial message
        MessageSender.sendMessage(player, msgTeleportStart.replace("{seconds}", String.valueOf(teleportDelaySeconds)));

        new BukkitRunnable() {
            int countdown = teleportDelaySeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                player.sendActionBar(ChatColor.translateAlternateColorCodes('&',
                        msgTeleportCountdown.replace("{seconds}", String.valueOf(countdown))));
                player.playSound(player.getLocation(), tickSound, tickVolume, tickPitch);

                if (countdown <= 0) {
                    player.teleport(loc);
                    MessageSender.sendMessage(player, msgTeleportDone);
                    player.playSound(player.getLocation(), teleportSound, teleportVolume, teleportPitch);
                    lockAllChunks(player);
                    autoAssignItemsForNearbyChunks(player);
                    unlockChunk(player, player.getChunk());

                    cancel();
                    return;
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20 ticks = 1 second
    }

}