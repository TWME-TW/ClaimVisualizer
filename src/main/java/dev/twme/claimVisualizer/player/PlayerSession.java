package dev.twme.claimVisualizer.player;

import dev.twme.claimVisualizer.config.ConfigManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSession {

    private static final Map<UUID, PlayerSession> sessions = new HashMap<>();
    
    private final UUID playerId;
    private boolean visualizationEnabled;
    private long lastUpdateTime;
    // 新增：玩家自訂的粒子顯示模式 (若為 null 表示使用預設設定)
    private ConfigManager.DisplayMode displayMode;
    
    private PlayerSession(UUID playerId) {
        this.playerId = playerId;
        this.visualizationEnabled = false;
        this.lastUpdateTime = System.currentTimeMillis();
        this.displayMode = null; // 使用預設模式
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
    
    public ConfigManager.DisplayMode getDisplayMode() {
        return displayMode;
    }
    
    public void setDisplayMode(ConfigManager.DisplayMode mode) {
        this.displayMode = mode;
        this.lastUpdateTime = System.currentTimeMillis();
    }
}
