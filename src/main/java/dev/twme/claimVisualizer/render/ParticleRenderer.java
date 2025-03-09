package dev.twme.claimVisualizer.render;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.claim.ClaimBoundary;
import dev.twme.claimVisualizer.claim.ClaimManager;
import dev.twme.claimVisualizer.config.ConfigManager;
import dev.twme.claimVisualizer.player.PlayerSession;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleRenderer {

    private final ClaimVisualizer plugin;
    private final ClaimManager claimManager;
    private final ConfigManager configManager;
    
    private BukkitTask renderTask;
    private BukkitTask particleDisplayTask; // 新增：用於分批顯示粒子的任務
    
    // 新增：玩家粒子佇列映射表
    private final Map<UUID, Queue<List<ParticleData>>> playerParticleQueues = new ConcurrentHashMap<>();
    // 新增：粒子分批大小
    private static final int PARTICLE_BATCH_SIZE = 20;
    // 新增：粒子顯示間隔（刻）
    private static final int PARTICLE_DISPLAY_INTERVAL = 1;
    
    public ParticleRenderer(ClaimVisualizer plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.configManager = plugin.getConfigManager();
    }
    
    /**
     * 新增：判斷位置是否在玩家面對的方向
     * @param player 玩家
     * @param location 要檢查的位置
     * @return 是否在玩家視野範圍內
     */
    private boolean isInPlayerViewDirection(Player player, Location location) {
        // 確保不是同一世界時直接返回 false
        if (!player.getWorld().equals(location.getWorld())) {
            return false;
        }
        
        Location playerLoc = player.getLocation();
        
        // 計算方向向量
        Vector playerDirection = player.getLocation().getDirection().normalize();
        Vector toLocation = location.clone().subtract(playerLoc).toVector().normalize();
        
        // 計算夾角的餘弦值
        double dotProduct = playerDirection.dot(toLocation);
        
        // 轉換為角度（弧度）
        double angleRadians = Math.acos(dotProduct);
        
        // 轉換為角度（度）
        double angleDegrees = Math.toDegrees(angleRadians);
        
        // 如果角度小於設定的視野範圍一半，則在視野內
        return angleDegrees <= configManager.getViewAngleRange() / 2;
    }
    
    /**
     * 啟動渲染排程任務
     */
    public void startRenderTask() {
        if (renderTask != null) {
            renderTask.cancel();
        }
        
        if (particleDisplayTask != null) {
            particleDisplayTask.cancel();
        }
        
        int updateInterval = configManager.getUpdateInterval();
        
        renderTask = new BukkitRunnable() {
            @Override
            public void run() {
                renderForAllPlayers();
            }
        }.runTaskTimer(plugin, updateInterval, updateInterval);
        
        // 新增：啟動粒子分批顯示任務
        particleDisplayTask = new BukkitRunnable() {
            @Override
            public void run() {
                processParticleQueues();
            }
        }.runTaskTimer(plugin, PARTICLE_DISPLAY_INTERVAL, PARTICLE_DISPLAY_INTERVAL);
    }
    
    /**
     * 停止渲染排程任務
     */
    public void stopRenderTask() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
        }
        
        if (particleDisplayTask != null) {
            particleDisplayTask.cancel();
            particleDisplayTask = null;
        }
        
        // 清空所有粒子佇列
        playerParticleQueues.clear();
    }
    
    /**
     * 為所有已啟用視覺化的玩家渲染粒子
     */
    private void renderForAllPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerSession session = PlayerSession.getSession(player);
            
            if (session.isVisualizationEnabled() && player.hasPermission("claimvisualizer.use")) {
                if (configManager.isAsyncRendering()) {
                    renderClaimsAsync(player);
                } else {
                    renderClaims(player);
                }
            }
        }
    }
    
    /**
     * 為特定玩家渲染領地粒子
     */
    public void renderClaims(Player player) {
        Set<ClaimBoundary> claims = claimManager.getNearbyClaims(player);
        double spacing = configManager.getParticleSpacing();
        // 使用玩家自訂模式，如未設定則使用預設設定
        PlayerSession session = PlayerSession.getSession(player);
        ConfigManager.DisplayMode mode = (session.getDisplayMode() != null) ? session.getDisplayMode() : configManager.getDisplayMode();
        
        int playerY = player.getLocation().getBlockY();
        int renderDistance = configManager.getRenderDistance();
        
        // 新增：收集所有粒子資料而不是直接顯示
        List<ParticleData> allParticles = new ArrayList<>();
        
        for (ClaimBoundary claim : claims) {
            if (mode == ConfigManager.DisplayMode.CORNERS) {
                ConfigManager.ParticleSettings particleSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                List<Location> points = claim.getCornerPoints(5, playerY);
                for (Location loc : points) {
                    // 修改：只收集在玩家視野內的粒子
                    if (isInPlayerViewDirection(player, loc)) {
                        allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                    }
                }
            } else if (mode == ConfigManager.DisplayMode.WALL) {
                ConfigManager.ParticleSettings particleSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                List<Location> points = claim.getWallModePoints(player.getLocation(), renderDistance, spacing, configManager.getWallRadius());
                for (Location loc : points) {
                    // 修改：只收集在玩家視野內的粒子
                    if (isInPlayerViewDirection(player, loc)) {
                        allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                    }
                }
            } else if (mode == ConfigManager.DisplayMode.OUTLINE) {
                double outlineRadius = configManager.getOutlineRadius();
                List<Location> points = claim.getOutlineNearbyPoints(player.getLocation(), renderDistance, spacing, outlineRadius);
                
                ConfigManager.ParticleSettings particleSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                                
                for (Location loc : points) {
                    // 修改：只收集在玩家視野內的粒子
                    if (isInPlayerViewDirection(player, loc)) {
                        allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                    }
                }
            } else {
                for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                    ConfigManager.ParticleSettings particleSettings = 
                            configManager.getParticleSettings(claim.getType(), part);
                    List<Location> points = claim.getPointsForPart(part, spacing, playerY);
                    for (Location loc : points) {
                        // 修改：只收集在玩家視野內的粒子
                        if (isInPlayerViewDirection(player, loc)) {
                            allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                        }
                    }
                }
            }
        }
        
        // 新增：將收集的粒子資料分批並加入佇列
        queueParticlesForPlayer(player.getUniqueId(), allParticles);
    }
    
    /**
     * 非同步渲染領地粒子
     */
    private void renderClaimsAsync(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<ClaimBoundary> claims = claimManager.getNearbyClaims(player);
                double spacing = configManager.getParticleSpacing();
                PlayerSession session = PlayerSession.getSession(player);
                ConfigManager.DisplayMode mode = (session.getDisplayMode() != null) ? session.getDisplayMode() : configManager.getDisplayMode();
                
                int playerY = player.getLocation().getBlockY();
                int renderDistance = configManager.getRenderDistance();
                
                List<ParticleData> allParticles = new ArrayList<>();
                
                for (ClaimBoundary claim : claims) {
                    if (mode == ConfigManager.DisplayMode.CORNERS) {
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                        List<Location> points = claim.getCornerPoints(5, playerY);
                        for (Location loc : points) {
                            // 修改：只收集在玩家視野內的粒子
                            if (isInPlayerViewDirection(player, loc)) {
                                allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                            }
                        }
                    } else if (mode == ConfigManager.DisplayMode.WALL) {
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                        List<Location> points = claim.getWallModePoints(player.getLocation(), renderDistance, spacing, configManager.getWallRadius());
                        for (Location loc : points) {
                            // 修改：只收集在玩家視野內的粒子
                            if (isInPlayerViewDirection(player, loc)) {
                                allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                            }
                        }
                    } else if (mode == ConfigManager.DisplayMode.OUTLINE) {
                        double outlineRadius = configManager.getOutlineRadius();
                        List<Location> points = claim.getOutlineNearbyPoints(player.getLocation(), renderDistance, spacing, outlineRadius);
                        
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                                
                        for (Location loc : points) {
                            // 修改：只收集在玩家視野內的粒子
                            if (isInPlayerViewDirection(player, loc)) {
                                allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                            }
                        }
                    } else {
                        for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                            ConfigManager.ParticleSettings particleSettings = 
                                    configManager.getParticleSettings(claim.getType(), part);
                            List<Location> points = claim.getPointsForPart(part, spacing, playerY);
                            for (Location loc : points) {
                                // 修改：只收集在玩家視野內的粒子
                                if (isInPlayerViewDirection(player, loc)) {
                                    allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                                }
                            }
                        }
                    }
                }
                
                // 修改：切換回主執行緒，但只將粒子資料加入佇列而不是立即顯示
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        queueParticlesForPlayer(player.getUniqueId(), allParticles);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * 新增：將粒子資料分批並加入玩家的粒子佇列
     */
    private void queueParticlesForPlayer(UUID playerId, List<ParticleData> particleData) {
        // 打亂粒子順序，使顯示更加自然
        Collections.shuffle(particleData);
        
        // 清除玩家現有佇列
        playerParticleQueues.remove(playerId);
        
        // 建立新佇列
        Queue<List<ParticleData>> particleQueue = new LinkedList<>();
        
        // 將粒子分批
        int totalParticles = particleData.size();
        
        // 計算每批次應包含的粒子數量
        int updateInterval = configManager.getUpdateInterval();
        int batchCount = Math.max(1, Math.min(updateInterval / PARTICLE_DISPLAY_INTERVAL, 20)); // 最多分20批
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
        playerParticleQueues.put(playerId, particleQueue);
    }
    
    /**
     * 新增：處理所有玩家的粒子佇列，每次顯示一批粒子
     */
    private void processParticleQueues() {
        // 為所有在線玩家處理粒子佇列
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Queue<List<ParticleData>> queue = playerParticleQueues.get(playerId);
            
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
     * 顯示粒子
     */
    private void spawnParticle(Player player, Particle particle, Location location, Color color) {
        if (particle == Particle.DUST) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue()), 1.0f);
            player.spawnParticle(particle, location, 1, 0, 0, 0, 0, dustOptions);
        } else {
            player.spawnParticle(particle, location, 1, 0, 0, 0, 0);
        }
    }
    
    /**
     * 粒子資料暫存類別
     */
    private static class ParticleData {
        private final Particle particle;
        private final Location location;
        private final Color color;
        
        public ParticleData(Particle particle, Location location, Color color) {
            this.particle = particle;
            this.location = location;
            this.color = color;
        }
        
        public Particle getParticle() {
            return particle;
        }
        
        public Location getLocation() {
            return location;
        }
        
        public Color getColor() {
            return color;
        }
    }
}
