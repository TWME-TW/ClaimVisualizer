package dev.twme.claimVisualizer.listener;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.player.PlayerSession;
import dev.twme.claimVisualizer.render.ParticleRenderer;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class EventListener implements Listener {

    private final ClaimVisualizer plugin;
    private final ParticleRenderer renderer;
    
    // 移動事件優化：每個玩家最小更新間隔 (毫秒)
    private static final long MOVE_UPDATE_THRESHOLD = 500; 

    public EventListener(ClaimVisualizer plugin, ParticleRenderer renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
    }

    /**
     * 處理玩家登入事件
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 初始化玩家會話
        PlayerSession session = PlayerSession.getSession(event.getPlayer());
        // 檢查目標世界是否支援 GriefDefender
        if (!plugin.getClaimManager().isWorldEnabled(event.getPlayer().getWorld())) {
            return;
        }
        
        // 設定玩家語言
        String locale = event.getPlayer().getLocale();
        plugin.getLanguageManager().setPlayerLanguage(event.getPlayer().getUniqueId(), locale);
        session.setLanguage(plugin.getLanguageManager().getPlayerLanguage(event.getPlayer().getUniqueId()));
        
        // 確認玩家已啟用視覺化，並有權限
        if (!session.isVisualizationEnabled() || 
            !event.getPlayer().hasPermission("claimvisualizer.use")) {
            return;
        }
        
        // 使用延遲時間顯示領地
        int delay = plugin.getConfigManager().getLoginDelay();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && session.isVisualizationEnabled()) {
                renderer.renderClaims(event.getPlayer());

                // 發送提示訊息，使用多語言系統
                event.getPlayer().sendMessage(plugin.getLanguageManager().getMessage("command.auto_enable", event.getPlayer()));
                event.getPlayer().sendMessage(plugin.getLanguageManager().getMessage("command.help.toggle_hint", event.getPlayer()));
            }
        }, delay);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 清理玩家會話
        PlayerSession.removeSession(event.getPlayer().getUniqueId());
    }
    
    @EventHandler
    public void onPlayerLocaleChange(PlayerLocaleChangeEvent event) {
        // 當玩家更改語言設定時，更新語言偏好
        String locale = event.getLocale();
        plugin.getLanguageManager().setPlayerLanguage(event.getPlayer().getUniqueId(), locale);
        
        PlayerSession session = PlayerSession.getSession(event.getPlayer());
        session.setLanguage(plugin.getLanguageManager().getPlayerLanguage(event.getPlayer().getUniqueId()));
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 檢查是否有實際的移動（不僅僅是旋轉視角）
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // 檢查該世界是否支援 GriefDefender
        if (!plugin.getClaimManager().isWorldEnabled(event.getPlayer().getWorld())) {
            return;
        }
        
        PlayerSession session = PlayerSession.getSession(event.getPlayer());
        
        // 確認玩家已啟用視覺化，並有權限
        if (!session.isVisualizationEnabled() || 
            !event.getPlayer().hasPermission("claimvisualizer.use")) {
            return;
        }
        
        // 限制更新頻率，避免過於頻繁的更新
        long currentTime = System.currentTimeMillis();
        if (currentTime - session.getLastUpdateTime() < MOVE_UPDATE_THRESHOLD) {
            return;
        }
        
        session.updateLastUpdateTime();
        
        // 在下一個遊戲刻更新領地顯示
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (session.isVisualizationEnabled()) {
                renderer.renderClaims(event.getPlayer());
            }
        });
    }

    /**
     * 處理玩家切換世界事件
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // 檢查目標世界是否支援 GriefDefender
        if (!plugin.getClaimManager().isWorldEnabled(event.getPlayer().getWorld())) {
            return;
        }
        
        PlayerSession session = PlayerSession.getSession(event.getPlayer());
        
        // 確認玩家已啟用視覺化，並有權限
        if (!session.isVisualizationEnabled() || 
            !event.getPlayer().hasPermission("claimvisualizer.use")) {
            return;
        }
        
        // 使用延遲時間顯示領地
        int delay = plugin.getConfigManager().getWorldChangeDelay();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && session.isVisualizationEnabled()) {
                renderer.renderClaims(event.getPlayer());
            }
        }, delay);
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // 檢查目標世界是否支援 GriefDefender
        if (!plugin.getClaimManager().isWorldEnabled(event.getTo().getWorld())) {
            return;
        }
        
        PlayerSession session = PlayerSession.getSession(event.getPlayer());
        
        // 確認玩家已啟用視覺化，並有權限
        if (!session.isVisualizationEnabled() || 
            !event.getPlayer().hasPermission("claimvisualizer.use")) {
            return;
        }
        
        // 修改：使用設定檔中的世界切換延遲時間
        int delay = plugin.getConfigManager().getWorldChangeDelay();
        
        // 傳送後更新領地顯示
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (session.isVisualizationEnabled()) {
                renderer.renderClaims(event.getPlayer());
            }
        }, delay); // 使用設定的延遲時間
    }
    
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // 當世界載入時，重置該世界的領地快取
        plugin.getClaimManager().clearCache(event.getWorld());
    }
    
    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        // 當世界卸載時，清除該世界的領地快取
        plugin.getClaimManager().clearCache(event.getWorld());
    }
}
