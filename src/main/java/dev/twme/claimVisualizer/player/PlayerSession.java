package dev.twme.claimVisualizer.player;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSession {

    private static final Map<UUID, PlayerSession> sessions = new HashMap<>();
    
    private final UUID playerId;
    private boolean visualizationEnabled;
    private long lastUpdateTime;
    
    private PlayerSession(UUID playerId) {
        this.playerId = playerId;
        this.visualizationEnabled = false;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public static PlayerSession getSession(Player player) {
        UUID playerId = player.getUniqueId();
        if (!sessions.containsKey(playerId)) {
            sessions.put(playerId, new PlayerSession(playerId));
        }
        return sessions.get(playerId);
    }
    
    public static void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }
    
    public static void cleanupSessions() {
        sessions.clear();
    }
    
    public boolean isVisualizationEnabled() {
        return visualizationEnabled;
    }
    
    public void setVisualizationEnabled(boolean enabled) {
        this.visualizationEnabled = enabled;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public void toggleVisualization() {
        setVisualizationEnabled(!visualizationEnabled);
    }
    
    public void updateLastUpdateTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
}
