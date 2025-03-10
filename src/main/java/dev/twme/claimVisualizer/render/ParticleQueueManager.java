package dev.twme.claimVisualizer.render;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.config.ConfigManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 粒子佇列管理器 - 負責管理粒子的佇列和定時顯示
 */
public class ParticleQueueManager {
    private final ClaimVisualizer plugin;
    private final ConfigManager configManager;
    
    // 按顯示模式分類的玩家粒子佇列映射表
    private final Map<ConfigManager.DisplayMode, Map<UUID, Queue<List<ParticleData>>>> modePlayerParticleQueues = new HashMap<>();
    
    // 模式特定的粒子顯示任務
    private final Map<ConfigManager.DisplayMode, BukkitTask> modeParticleDisplayTasks = new HashMap<>();

    // 粒子分批大小
    private static final int PARTICLE_BATCH_SIZE = 20;
    
    // 粒子計數器 (供統計使用)
    private final Map<UUID, AtomicInteger> playerParticleCounter;
    
    /**
     * 建立粒子佇列管理器
     * @param plugin 插件主類別
     * @param configManager 設定管理器
     * @param playerParticleCounter 玩家粒子計數器參考
     */
    public ParticleQueueManager(ClaimVisualizer plugin, ConfigManager configManager, Map<UUID, AtomicInteger> playerParticleCounter) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerParticleCounter = playerParticleCounter;
        
        // 初始化每種模式的佇列
        for (ConfigManager.DisplayMode mode : ConfigManager.DisplayMode.values()) {
            modePlayerParticleQueues.put(mode, new ConcurrentHashMap<>());
        }
    }
    
    /**
     * 將粒子資料分批並加入玩家的粒子佇列，依顯示模式分開處理
     */
    public void queueParticlesForPlayer(UUID playerId, List<ParticleData> particleData, ConfigManager.DisplayMode mode) {
        // 打亂粒子順序，使顯示更加自然
        Collections.shuffle(particleData);
        
        // 取得該模式的佇列映射表
        Map<UUID, Queue<List<ParticleData>>> modeQueues = modePlayerParticleQueues.get(mode);
        if (modeQueues == null) {
            modeQueues = new ConcurrentHashMap<>();
            modePlayerParticleQueues.put(mode, modeQueues);
        }
        
        // 清除玩家在該模式下的現有佇列
        modeQueues.remove(playerId);
        
        // 建立新佇列
        Queue<List<ParticleData>> particleQueue = new LinkedList<>();
        
        // 將粒子分批
        int totalParticles = particleData.size();
        
        // 計算每批次應包含的粒子數量，使用模式特定的更新間隔
        int updateInterval = configManager.getUpdateInterval(mode);
        int displayInterval = configManager.getParticleDisplayInterval(mode);
        int batchCount = Math.max(1, Math.min(updateInterval / displayInterval, 20)); // 最多分20批
        int particlesPerBatch = Math.max(1, totalParticles / batchCount);
        
        // 分批將粒子加入佇列
        List<ParticleData> batch = new ArrayList<>(particlesPerBatch);
        for (ParticleData data : particleData) {
            batch.add(data);
            
            if (batch.size() >= particlesPerBatch) {
                particleQueue.add(new ArrayList<>(batch));
                batch.clear();
            }
        }
        
        // 處理剩餘粒子
        if (!batch.isEmpty()) {
            particleQueue.add(batch);
        }
        
        // 將佇列加入映射表
        modeQueues.put(playerId, particleQueue);
    }
    
    /**
     * 處理特定模式的所有玩家粒子佇列，每次顯示一批粒子
     */
    public void processParticleQueues(ConfigManager.DisplayMode mode) {
        // 取得該模式的佇列映射表
        Map<UUID, Queue<List<ParticleData>>> modeQueues = modePlayerParticleQueues.get(mode);
        if (modeQueues == null) return;
        
        // 為所有在線玩家處理粒子佇列
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Queue<List<ParticleData>> queue = modeQueues.get(playerId);
            
            if (queue != null && !queue.isEmpty()) {
                // 從佇列取出一批粒子資料並顯示
                List<ParticleData> batch = queue.poll();
                
                if (batch != null) {
                    for (ParticleData data : batch) {
                        spawnParticle(player, data.getParticle(), data.getLocation(), data.getColor());
                    }
                }
            }
        }
    }
    
    /**
     * 顯示粒子並更新計數
     */
    private void spawnParticle(Player player, Particle particle, Location location, Color color) {
        if (particle == Particle.DUST) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue()), 1.0f);
            player.spawnParticle(particle, location, 1, 0, 0, 0, 0, dustOptions);
        } else {
            player.spawnParticle(particle, location, 1, 0, 0, 0, 0);
        }

        // 增加粒子計數
        playerParticleCounter.computeIfAbsent(player.getUniqueId(), k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * 啟動粒子顯示任務
     */
    public void startParticleDisplayTasks() {
        stopParticleDisplayTasks();
        
        for (ConfigManager.DisplayMode mode : ConfigManager.DisplayMode.values()) {
            int displayInterval = configManager.getParticleDisplayInterval(mode);
            
            // 粒子顯示任務 - 使用模式特定的顯示間隔
            modeParticleDisplayTasks.put(mode, new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    processParticleQueues(mode);
                }
            }.runTaskTimer(plugin, displayInterval, displayInterval));
        }
    }
    
    /**
     * 停止粒子顯示任務
     */
    public void stopParticleDisplayTasks() {
        for (BukkitTask task : modeParticleDisplayTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        modeParticleDisplayTasks.clear();
        
        // 清空所有粒子佇列
        for (Map<UUID, Queue<List<ParticleData>>> queues : modePlayerParticleQueues.values()) {
            queues.clear();
        }
    }
    
    /**
     * 清除特定玩家的所有粒子佇列
     */
    public void clearPlayerQueues(UUID playerId) {
        for (Map<UUID, Queue<List<ParticleData>>> modeQueues : modePlayerParticleQueues.values()) {
            modeQueues.remove(playerId);
        }
    }
}
