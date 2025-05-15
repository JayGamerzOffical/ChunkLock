package org.jay.chunkLock.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jay.chunkLock.ChunkLockPlugin;
import org.jay.chunkLock.managers.ChunkManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChunkLookPlaceholder extends PlaceholderExpansion {

    private final ChunkLockPlugin plugin;
    private final ChunkManager chunkManager;

    public ChunkLookPlaceholder(ChunkLockPlugin plugin, ChunkManager chunkManager) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "chunklock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "JayGamerz";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        Chunk currentChunk = player.getLocation().getChunk();

        if (params.equalsIgnoreCase("totalChunkUnlocked")) {
            return String.valueOf(chunkManager.totalChunkUnlocked(player));
        }


        return null;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) return "";

        Player player = Bukkit.getPlayer(offlinePlayer.getUniqueId());
        if (player == null) return "";

        return onPlaceholderRequest(player, params); // Delegate to the main logic
    }
}
