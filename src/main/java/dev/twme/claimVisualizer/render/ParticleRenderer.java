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
import java.util.concurrent.atomic.AtomicInteger;

public class ParticleRenderer {

    private final ClaimVisualizer plugin;
    private final ClaimManager claimManager;
    private final ConfigManager configManager;
    
    private BukkitTask renderTask;
    private final Map<ConfigManager.DisplayMode, BukkitTask> modeRenderTasks = new HashMap<>();
    private final Map<ConfigManager.DisplayMode, BukkitTask> modeParticleDisplayTasks = new HashMap<>();
    
    // 新增：按顯示模式分類的玩家粒子佇列映射表
    private final Map<ConfigManager.DisplayMode, Map<UUID, Queue<List<ParticleData>>>> modePlayerParticleQueues = new HashMap<>();
    
    // 新增：粒子分批大小
    private static final int PARTICLE_BATCH_SIZE = 20;

    private final Map<UUID, AtomicInteger> playerParticleCounter = new ConcurrentHashMap<>();
    private BukkitTask counterResetTask;
    
    // 新增: 追蹤已啟用粒子計數實時顯示的管理員
    private final Set<UUID> particleCounterEnabledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    public ParticleRenderer(ClaimVisualizer plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.configManager = plugin.getConfigManager();
        
        // 初始化每種模式的佇列
        for (ConfigManager.DisplayMode mode : ConfigManager.DisplayMode.values()) {
            modePlayerParticleQueues.put(mode, new ConcurrentHashMap<>());
        }
    }
    
    /**
     * 判斷位置是否在玩家面對的方向
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
        // 停止所有現有任務
        stopRenderTask();
        
        // 為每種顯示模式啟動獨立的渲染和顯示任務
        for (ConfigManager.DisplayMode mode : ConfigManager.DisplayMode.values()) {
            int updateInterval = configManager.getUpdateInterval(mode);
            int displayInterval = configManager.getParticleDisplayInterval(mode);
            
            // 渲染任務 - 使用模式特定的更新間隔
            modeRenderTasks.put(mode, new BukkitRunnable() {
                @Override
                public void run() {
                    renderForAllPlayersWithMode(mode);
                }
            }.runTaskTimer(plugin, updateInterval, updateInterval));
            
            // 粒子顯示任務 - 使用模式特定的顯示間隔
            modeParticleDisplayTasks.put(mode, new BukkitRunnable() {
                @Override
                public void run() {
                    processParticleQueues(mode);
                }
            }.runTaskTimer(plugin, displayInterval, displayInterval));
        }

        // 啟動粒子計數器重置任務 (每秒重置一次)
        counterResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 向啟用了計數顯示的管理員發送粒子數量訊息
                for (UUID playerId : particleCounterEnabledPlayers) {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        AtomicInteger counter = playerParticleCounter.getOrDefault(playerId, new AtomicInteger(0));
                        int count = counter.get();
                        player.sendMessage(plugin.getLanguageManager().getMessage("command.debug.particles_live", player, count));
                        counter.set(0);
                    }
                }
                
                // 重置粒子計數器
                for (AtomicInteger counter : playerParticleCounter.values()) {
                    counter.set(0);
                }
            }
        }.runTaskTimer(plugin, 20, 20); // 每 20 ticks (1秒) 執行一次
    }
    
    /**
     * 停止渲染排程任務
     */
    public void stopRenderTask() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
        }
        
        // 停止所有模式特定的任務
        for (BukkitTask task : modeRenderTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        modeRenderTasks.clear();
        
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

        // 停止粒子計數器重置任務
        if (counterResetTask != null) {
            counterResetTask.cancel();
            counterResetTask = null;
        }
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
     * 新增：為所有使用特定顯示模式的玩家渲染粒子
     */
    private void renderForAllPlayersWithMode(ConfigManager.DisplayMode targetMode) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerSession session = PlayerSession.getSession(player);
            
            if (session.isVisualizationEnabled() && player.hasPermission("claimvisualizer.use")) {
                // 取得玩家實際使用的顯示模式
                ConfigManager.DisplayMode playerMode = session.getDisplayMode() != null ? 
                                                      session.getDisplayMode() : 
                                                      configManager.getDisplayMode();
                
                // 只處理與目標模式相同的玩家
                if (playerMode == targetMode) {
                    if (configManager.isAsyncRendering()) {
                        renderClaimsAsync(player, playerMode);
                    } else {
                        renderClaims(player, playerMode);
                    }
                }
            }
        }
    }
    
    /**
     * 為特定玩家渲染領地粒子
     */
    public void renderClaims(Player player) {
        // 使用玩家自訂模式，如未設定則使用預設設定
        PlayerSession session = PlayerSession.getSession(player);
        ConfigManager.DisplayMode mode = (session.getDisplayMode() != null) ? session.getDisplayMode() : configManager.getDisplayMode();
        
        renderClaims(player, mode);
    }
    
    /**
     * 為特定玩家渲染領地粒子，使用指定顯示模式
     */
    public void renderClaims(Player player, ConfigManager.DisplayMode mode) {
        Set<ClaimBoundary> claims = claimManager.getNearbyClaims(player);
        double spacing = configManager.getParticleSpacing(mode);
        int renderDistance = configManager.getRenderDistance(mode);
        int playerY = player.getLocation().getBlockY();
        
        // 收集所有粒子資料
        List<ParticleData> allParticles = new ArrayList<>();
        
        for (ClaimBoundary claim : claims) {
            if (mode == ConfigManager.DisplayMode.CORNERS) {
                ConfigManager.ParticleSettings particleSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                // 使用模式特定的角落大小
                int cornerSize = configManager.getCornerSize(mode);
                List<Location> points = claim.getCornerPoints(cornerSize, playerY);
                for (Location loc : points) {
                    // 只收集在玩家視野內的粒子
                    if (isInPlayerViewDirection(player, loc)) {
                        allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                    }
                }
            } else if (mode == ConfigManager.DisplayMode.WALL) {
                // 使用模式特定的牆面半徑
                double wallRadius = configManager.getRadius(mode);
                
                // 獲取水平和垂直線的粒子設定
                ConfigManager.ParticleSettings horizontalSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                ConfigManager.ParticleSettings verticalSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                
                // 使用新的帶角落資訊的牆面點
                List<ClaimBoundary.WallPoint> points = claim.getWallModePointsWithCorners(player.getLocation(), renderDistance, spacing, wallRadius);
                
                for (ClaimBoundary.WallPoint point : points) {
                    if (isInPlayerViewDirection(player, point.getLocation())) {
                        // 根據點的屬性選擇適當的顏色
                        if (point.isCorner()) {
                            // 角落點使用頂部框架的顏色
                            ConfigManager.ParticleSettings cornerSettings = 
                                    configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                            allParticles.add(new ParticleData(cornerSettings.getParticle(), 
                                                            point.getLocation(), 
                                                            cornerSettings.getColor()));
                        } else if (point.isVertical()) {
                            // 垂直點使用垂直線的顏色
                            allParticles.add(new ParticleData(verticalSettings.getParticle(), 
                                                            point.getLocation(), 
                                                            verticalSettings.getColor()));
                        } else {
                            // 其他點使用水平線的顏色
                            allParticles.add(new ParticleData(horizontalSettings.getParticle(), 
                                                            point.getLocation(), 
                                                            horizontalSettings.getColor()));
                        }
                    }
                }
            } else if (mode == ConfigManager.DisplayMode.OUTLINE) {
                // 使用模式特定的輪廓半徑
                double outlineRadius = configManager.getRadius(mode);
                List<Location> points = claim.getOutlineNearbyPoints(player.getLocation(), renderDistance, spacing, outlineRadius);
                
                ConfigManager.ParticleSettings particleSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                                
                for (Location loc : points) {
                    if (isInPlayerViewDirection(player, loc)) {
                        allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                    }
                }
            } else {
                // FULL 模式 - 應用垂直渲染範圍限制
                int verticalRange = configManager.getVerticalRenderRange(mode);
                
                for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                    ConfigManager.ParticleSettings particleSettings = 
                            configManager.getParticleSettings(claim.getType(), part);
                    
                    // 使用新方法獲取垂直範圍內的點
                    List<Location> points;
                    if (part == ConfigManager.ClaimPart.TOP || part == ConfigManager.ClaimPart.VERTICAL) {
                        points = claim.getPointsInVerticalRange(part, spacing, playerY, verticalRange);
                    } else {
                        // 對於水平線和底部，使用原始方法
                        points = claim.getPointsForPart(part, spacing, playerY);
                    }
                    
                    for (Location loc : points) {
                        if (isInPlayerViewDirection(player, loc)) {
                            allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                        }
                    }
                }
            }
        }
        
        // 將收集的粒子資料分批並加入佇列，使用模式特定的佇列
        queueParticlesForPlayer(player.getUniqueId(), allParticles, mode);
    }
    
    /**
     * 非同步渲染領地粒子
     */
    private void renderClaimsAsync(Player player) {
        PlayerSession session = PlayerSession.getSession(player);
        ConfigManager.DisplayMode mode = (session.getDisplayMode() != null) ? session.getDisplayMode() : configManager.getDisplayMode();
        renderClaimsAsync(player, mode);
    }
    
    /**
     * 非同步渲染領地粒子，使用指定顯示模式
     */
    private void renderClaimsAsync(Player player, ConfigManager.DisplayMode mode) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<ClaimBoundary> claims = claimManager.getNearbyClaims(player);
                double spacing = configManager.getParticleSpacing(mode);
                int renderDistance = configManager.getRenderDistance(mode);
                int playerY = player.getLocation().getBlockY();
                
                List<ParticleData> allParticles = new ArrayList<>();
                
                for (ClaimBoundary claim : claims) {
                    if (mode == ConfigManager.DisplayMode.CORNERS) {
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                        int cornerSize = configManager.getCornerSize(mode);
                        List<Location> points = claim.getCornerPoints(cornerSize, playerY);
                        for (Location loc : points) {
                            if (isInPlayerViewDirection(player, loc)) {
                                allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                            }
                        }
                    } else if (mode == ConfigManager.DisplayMode.WALL) {
                        double wallRadius = configManager.getRadius(mode);
                        
                        // 獲取水平和垂直線的粒子設定
                        ConfigManager.ParticleSettings horizontalSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                        ConfigManager.ParticleSettings verticalSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                        
                        // 使用新的帶角落資訊的牆面點
                        List<ClaimBoundary.WallPoint> points = claim.getWallModePointsWithCorners(player.getLocation(), renderDistance, spacing, wallRadius);
                        
                        for (ClaimBoundary.WallPoint point : points) {
                            if (isInPlayerViewDirection(player, point.getLocation())) {
                                // 根據點的屬性選擇適當的顏色
                                if (point.isCorner()) {
                                    // 角落點使用頂部框架的顏色
                                    ConfigManager.ParticleSettings cornerSettings = 
                                            configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                                    allParticles.add(new ParticleData(cornerSettings.getParticle(), 
                                                                    point.getLocation(), 
                                                                    cornerSettings.getColor()));
                                } else if (point.isVertical()) {
                                    // 垂直點使用垂直線的顏色
                                    allParticles.add(new ParticleData(verticalSettings.getParticle(), 
                                                                    point.getLocation(), 
                                                                    verticalSettings.getColor()));
                                } else {
                                    // 其他點使用水平線的顏色
                                    allParticles.add(new ParticleData(horizontalSettings.getParticle(), 
                                                                    point.getLocation(), 
                                                                    horizontalSettings.getColor()));
                                }
                            }
                        }
                    } else if (mode == ConfigManager.DisplayMode.OUTLINE) {
                        double outlineRadius = configManager.getRadius(mode);
                        List<Location> points = claim.getOutlineNearbyPoints(player.getLocation(), renderDistance, spacing, outlineRadius);
                        
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                                
                        for (Location loc : points) {
                            if (isInPlayerViewDirection(player, loc)) {
                                allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                            }
                        }
                    } else {
                        // FULL 模式 - 應用垂直渲染範圍限制
                        int verticalRange = configManager.getVerticalRenderRange(mode);
                        
                        for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                            ConfigManager.ParticleSettings particleSettings = 
                                    configManager.getParticleSettings(claim.getType(), part);
                            
                            // 使用新方法獲取垂直範圍內的點
                            List<Location> points;
                            if (part == ConfigManager.ClaimPart.TOP || part == ConfigManager.ClaimPart.VERTICAL) {
                                points = claim.getPointsInVerticalRange(part, spacing, playerY, verticalRange);
                            } else {
                                // 對於水平線和底部，使用原始方法
                                points = claim.getPointsForPart(part, spacing, playerY);
                            }
                            
                            for (Location loc : points) {
                                if (isInPlayerViewDirection(player, loc)) {
                                    allParticles.add(new ParticleData(particleSettings.getParticle(), loc, particleSettings.getColor()));
                                }
                            }
                        }
                    }
                }
                
                // 切換回主執行緒，將粒子資料加入佇列
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        queueParticlesForPlayer(player.getUniqueId(), allParticles, mode);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * 將粒子資料分批並加入玩家的粒子佇列，依顯示模式分開處理
     */
    private void queueParticlesForPlayer(UUID playerId, List<ParticleData> particleData, ConfigManager.DisplayMode mode) {
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
    private void processParticleQueues(ConfigManager.DisplayMode mode) {
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

        // 增加粒子計數
        playerParticleCounter.computeIfAbsent(player.getUniqueId(), k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * 獲取玩家當前的每秒粒子數
     */
    public int getPlayerParticlesPerSecond(UUID playerId) {
        AtomicInteger counter = playerParticleCounter.get(playerId);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 啟用或停用粒子計數實時顯示
     */
    public boolean toggleParticleCounterDisplay(UUID playerId) {
        boolean isEnabled = particleCounterEnabledPlayers.contains(playerId);
        if (isEnabled) {
            particleCounterEnabledPlayers.remove(playerId);
            return false;
        } else {
            particleCounterEnabledPlayers.add(playerId);
            return true;
        }
    }
    
    /**
     * 查詢玩家是否已啟用粒子計數實時顯示
     */
    public boolean isParticleCounterDisplayEnabled(UUID playerId) {
        return particleCounterEnabledPlayers.contains(playerId);
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
