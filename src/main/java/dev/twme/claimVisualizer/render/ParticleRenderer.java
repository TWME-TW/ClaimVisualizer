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
    
    // 粒子佇列管理器
    private final ParticleQueueManager queueManager;
    
    // 粒子計數器
    private final Map<UUID, AtomicInteger> playerParticleCounter = new ConcurrentHashMap<>();
    private BukkitTask counterResetTask;
    
    // 追蹤已啟用粒子計數實時顯示的管理員
    private final Set<UUID> particleCounterDisplay = new HashSet<>();

    public ParticleRenderer(ClaimVisualizer plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.configManager = plugin.getConfigManager();
        
        // 初始化粒子佇列管理器
        this.queueManager = new ParticleQueueManager(plugin, configManager, playerParticleCounter);
    }

    /**
     * 啟動渲染排程任務
     */
    public void startRenderTask() {
        // 停止所有現有任務
        stopRenderTask();
        
        // 為每種顯示模式啟動獨立的渲染任務
        for (ConfigManager.DisplayMode mode : ConfigManager.DisplayMode.values()) {
            int updateInterval = configManager.getUpdateInterval(mode);
            
            // 渲染任務 - 使用模式特定的更新間隔
            modeRenderTasks.put(mode, new BukkitRunnable() {
                @Override
                public void run() {
                    renderForAllPlayersWithMode(mode);
                }
            }.runTaskTimer(plugin, updateInterval, updateInterval));
        }
        
        // 啟動粒子佇列管理器
        queueManager.startParticleDisplayTasks();

        // 啟動粒子計數器重置任務 (每秒重置一次)
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
        
        // 停止所有模式特定的渲染任務
        for (BukkitTask task : modeRenderTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        modeRenderTasks.clear();
        
        // 停止粒子佇列管理器
        queueManager.stopParticleDisplayTasks();

        // 停止粒子計數器重置任務
        if (counterResetTask != null) {
            counterResetTask.cancel();
            counterResetTask = null;
        }
    }

    /**
     * 為所有使用特定顯示模式的玩家渲染粒子
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
        
        // 將收集的粒子資料加入佇列，使用模式特定的佇列
        queueManager.queueParticlesForPlayer(player.getUniqueId(), allParticles, mode);
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
                        queueManager.queueParticlesForPlayer(player.getUniqueId(), allParticles, mode);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 檢查位置是否在玩家視野方向內
     */
    private boolean isInPlayerViewDirection(Player player, Location location) {
        if (location.getWorld() != player.getWorld()) return false;
        
        Vector playerDirection = player.getLocation().getDirection();
        Vector playerToLocation = location.clone().subtract(player.getEyeLocation()).toVector();
        
        // 規一化向量
        playerDirection.normalize();
        playerToLocation.normalize();
        
        // 計算兩個向量之間的角度（弧度）
        double angle = Math.acos(playerDirection.dot(playerToLocation));
        
        // 轉換為度數並檢查是否在視角範圍內
        return Math.toDegrees(angle) <= configManager.getViewAngleRange() / 2;
    }

    /**
     * 獲取玩家當前的每秒粒子數
     */
    public int getPlayerParticlesPerSecond(UUID playerId) {
        return playerParticleCounter.getOrDefault(playerId, new AtomicInteger(0)).get();
    }

    /**
     * 啟用或停用粒子計數實時顯示
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
     * 查詢玩家是否已啟用粒子計數實時顯示
     */
    public boolean isParticleCounterDisplayEnabled(UUID playerId) {
        return particleCounterDisplay.contains(playerId);
    }
}
