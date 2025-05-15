package org.jay.chunkLock.managers;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jay.chunkLock.ChunkLockPlugin;

import java.util.List;

public class PlayerManager implements Listener {

    private final ChunkLockPlugin plugin;

    // Configurable fields
    private boolean autoChunkAssignment;
    private boolean teleportOnJoin;
    private boolean firstTimeOnlyTeleport;
    private boolean fallbackLobbyEnabled;

    public String getTemplate() {
        return template;
    }

    private String template;
    private String msgTeleporting;
    private String msgMissingTemplate;
    private List<String> lobbyWorlds;

    public PlayerManager(ChunkLockPlugin plugin) {
        this.plugin = plugin;
        reloadConfigValues();
    }

    public List<String> getLobbyWorlds() {
        return lobbyWorlds;
    }

    public void reloadConfigValues() {
        FileConfiguration config = plugin.getConfig();
        this.template = config.getString("template-world-name", null);
        this.autoChunkAssignment = config.getBoolean("auto-chunk-assignment", true);
        this.teleportOnJoin = config.getBoolean("teleport-on-join", true);
        this.firstTimeOnlyTeleport = config.getBoolean("first-time-only-teleport", false);
        this.fallbackLobbyEnabled = config.getBoolean("fallback-lobby-enabled", true);
        this.msgTeleporting = config.getString("messages.teleporting", "&aTeleporting to your world...");
        this.msgMissingTemplate = config.getString("messages.missing-template", "&cNo template configured. Sending to lobby.");
        this.lobbyWorlds = config.getStringList("restricted-worlds");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!teleportOnJoin) return;

        if (template != null) {
            World world = plugin.getChunkManager().loadPlayerWorld(player);
            MessageSender.sendMessage(player, msgTeleporting);

            if (autoChunkAssignment && plugin.getChunkManager().isNewPlayer(player)) {
                plugin.getChunkManager().teleportToPlayerWorldFirstTime(player, world);
            } else {
                plugin.getChunkManager().teleportToPlayerWorld(player, world);
            }
        } else if (fallbackLobbyEnabled) {
            MessageSender.sendMessage(player, msgMissingTemplate);
            plugin.teleportToLobby(player);
        }
    }

}
