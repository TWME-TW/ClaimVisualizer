package dev.twme.claimVisualizer.render;

import dev.twme.claimVisualizer.ClaimVisualizer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 粒子統計管理器 - 負責追蹤和顯示粒子計數統計資訊
 */
public class ParticleStatisticsManager {
    private final ClaimVisualizer plugin;
    
    // 粒子計數器
    private final Map<UUID, AtomicInteger> playerParticleCounter = new ConcurrentHashMap<>();
    
    // 追蹤已啟用粒子計數實時顯示的管理員
    private final Set<UUID> particleCounterDisplay = new HashSet<>();
    
    // 粒子計數器重置任務
    private BukkitTask counterResetTask;
    
    public ParticleStatisticsManager(ClaimVisualizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 啟動粒子統計任務
     */
    public void startStatisticsTask() {
        stopStatisticsTask();
        
        // 每秒重置所有玩家的粒子計數
        counterResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 為啟用了實時粒子計數顯示的玩家顯示統計資訊
                for (UUID playerId : particleCounterDisplay) {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        int count = playerParticleCounter.getOrDefault(playerId, new AtomicInteger(0)).get();
                        player.sendActionBar(plugin.getLanguageManager().getMessage("command.debug.particles_live", player, count));
                    }
                }
                
                // 重置所有玩家的粒子計數
                playerParticleCounter.clear();
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒執行一次
    }
    
    /**
     * 停止粒子統計任務
     */
    public void stopStatisticsTask() {
        if (counterResetTask != null) {
            counterResetTask.cancel();
            counterResetTask = null;
        }
    }
    
    /**
     * 增加玩家粒子計數
     */
    public void incrementPlayerParticleCount(UUID playerId) {
        playerParticleCounter.computeIfAbsent(playerId, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * 取得玩家每秒粒子數量
     */
    public int getPlayerParticlesPerSecond(UUID playerId) {
        return playerParticleCounter.getOrDefault(playerId, new AtomicInteger(0)).get();
    }
    
    /**
     * 切換粒子計數器實時顯示
     */
    public boolean toggleParticleCounterDisplay(UUID playerId) {
        if (particleCounterDisplay.contains(playerId)) {
            particleCounterDisplay.remove(playerId);
            return false;
        } else {
            particleCounterDisplay.add(playerId);
            return true;
        }
    }
    
    /**
     * 檢查玩家是否已啟用粒子計數顯示
     */
    public boolean isParticleCounterDisplayEnabled(UUID playerId) {
        return particleCounterDisplay.contains(playerId);
    }
}
