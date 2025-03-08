package dev.twme.claimVisualizer.listener;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.player.PlayerSession;
import dev.twme.claimVisualizer.render.ParticleRenderer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 初始化玩家會話
        PlayerSession session = PlayerSession.getSession(event.getPlayer());
        
        // 檢查玩家是否有自動啟用視覺化的權限
        if (event.getPlayer().hasPermission("claimvisualizer.autoenable") && 
            event.getPlayer().hasPermission("claimvisualizer.use")) {
            session.setVisualizationEnabled(true);
            
            // 延遲1秒後渲染領地，確保玩家已完全載入
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getClaimManager().isWorldEnabled(event.getPlayer().getWorld())) {
                    renderer.renderClaims(event.getPlayer());
                }
            }, 20L); // 20 ticks = 1秒
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 清理玩家會話
        PlayerSession.removeSession(event.getPlayer().getUniqueId());
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
        
        // 傳送後更新領地顯示
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (session.isVisualizationEnabled()) {
                renderer.renderClaims(event.getPlayer());
            }
        }, 5L); // 等待5刻以確保玩家已完成傳送
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
