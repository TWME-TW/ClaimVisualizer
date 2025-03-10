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
    
    // 粒子統計管理器
    private final ParticleStatisticsManager statisticsManager;
    
    // 非同步渲染管理器
    private final AsyncRenderManager asyncRenderManager;

    public ParticleRenderer(ClaimVisualizer plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.configManager = plugin.getConfigManager();
        
        // 初始化統計管理器
        this.statisticsManager = new ParticleStatisticsManager(plugin);
        
        // 初始化粒子佇列管理器 (使用統計管理器的參考)
        this.queueManager = new ParticleQueueManager(plugin, configManager, statisticsManager);
        
        // 初始化非同步渲染管理器
        this.asyncRenderManager = new AsyncRenderManager(plugin, claimManager, queueManager, statisticsManager);
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
        
        // 啟動統計管理器
        statisticsManager.startStatisticsTask();
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

        // 停止統計管理器
        statisticsManager.stopStatisticsTask();
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
                
                // 獲取 WALL 模式的增強設定
                boolean adaptiveDensity = configManager.isWallAdaptiveDensity();
                double focusFactor = configManager.getWallFocusFactor();
                double fadeDistance = configManager.getWallFadeDistance();
                double edgeEmphasis = configManager.getWallEdgeEmphasis();
                boolean waveEffect = configManager.isWallWaveEffect();
                double waveSpeed = configManager.getWallWaveSpeed();
                double waveIntensity = configManager.getWallWaveIntensity();
                
                // 玩家視線方向向量
                Vector playerDirection = player.getLocation().getDirection();
                
                // 取得當前時間戳用於波浪效果
                long currentTimeMillis = System.currentTimeMillis();
                
                // 獲取水平和垂直線的粒子設定
                ConfigManager.ParticleSettings horizontalSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                ConfigManager.ParticleSettings verticalSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                ConfigManager.ParticleSettings cornerSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                
                // 使用新的帶角落資訊的牆面點
                List<ClaimBoundary.WallPoint> points = claim.getWallModePointsWithCorners(player.getLocation(), renderDistance, spacing, wallRadius);
                
                for (ClaimBoundary.WallPoint point : points) {
                    Location loc = point.getLocation();
                    if (isInPlayerViewDirection(player, loc)) {
                        // 1. 自適應密度：根據距離決定是否渲染
                        if (adaptiveDensity) {
                            double distance = player.getLocation().distance(loc);
                            double maxDistance = configManager.getRenderDistance(mode);
                            double relativeDistance = distance / maxDistance;
                            
                            // 根據距離計算渲染機率
                            double chance = calculateRenderChance(relativeDistance, fadeDistance);
                            
                            // 2. 提高視線焦點區域的渲染機率
                            if (isInFocusArea(player, loc, playerDirection)) {
                                chance *= focusFactor;
                            }
                            
                            // 3. 強化邊緣
                            if (point.isCorner() || point.isVertical()) {
                                chance *= edgeEmphasis;
                            }
                            
                            // 根據機率決定是否渲染
                            if (Math.random() > chance) {
                                continue;
                            }
                        }
                        
                        // 4. 波浪效果：根據時間和位置調整顏色亮度
                        double brightnessFactor = 1.0;
                        if (waveEffect) {
                            // 計算基於時間和位置的波浪效果
                            double waveOffset = (currentTimeMillis / 1000.0) * waveSpeed;
                            double locationFactor = (loc.getBlockX() + loc.getBlockY() + loc.getBlockZ()) * 0.1;
                            double waveFactor = Math.sin(waveOffset + locationFactor) * waveIntensity + 1.0;
                            brightnessFactor *= waveFactor;
                        }
                        
                        // 根據點的屬性選擇適當的顏色和亮度
                        if (point.isCorner()) {
                            // 角落點使用頂部框架的顏色，並增強亮度
                            Color adjustedColor = adjustColorBrightness(cornerSettings.getColor(), brightnessFactor * 1.5);
                            allParticles.add(new ParticleData(cornerSettings.getParticle(), loc, adjustedColor));
                        } else if (point.isVertical()) {
                            // 垂直點使用垂直線的顏色，並適當增強
                            Color adjustedColor = adjustColorBrightness(verticalSettings.getColor(), brightnessFactor * 1.2);
                            allParticles.add(new ParticleData(verticalSettings.getParticle(), loc, adjustedColor));
                        } else {
                            // 其他點使用水平線的顏色，正常亮度
                            Color adjustedColor = adjustColorBrightness(horizontalSettings.getColor(), brightnessFactor);
                            allParticles.add(new ParticleData(horizontalSettings.getParticle(), loc, adjustedColor));
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
                // FULL 模式 - 應用垂直渲染範圍限制和增強效果
                int verticalRange = configManager.getVerticalRenderRange(mode);
                boolean adaptiveDensity = configManager.isAdaptiveDensity();
                double focusFactor = configManager.getFocusFactor();
                double fadeDistance = configManager.getFadeDistance();
                
                // 玩家視線方向向量
                Vector playerDirection = player.getLocation().getDirection();
                
                for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                    ConfigManager.ParticleSettings particleSettings = 
                            configManager.getParticleSettings(claim.getType(), part);
                    
                    // 根據部位調整粒子顏色亮度
                    Color adjustedColor = adjustColorBrightness(
                            particleSettings.getColor(), 
                            configManager.getPartBrightness(part));
                    
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
                            // 自適應密度：根據距離決定是否渲染
                            if (adaptiveDensity) {
                                double distance = player.getLocation().distance(loc);
                                double maxDistance = configManager.getRenderDistance(mode);
                                double relativeDistance = distance / maxDistance;
                                
                                // 根據距離計算渲染機率
                                double chance = calculateRenderChance(relativeDistance, fadeDistance);
                                
                                // 提高視線焦點區域的渲染機率
                                if (isInFocusArea(player, loc, playerDirection)) {
                                    chance *= focusFactor;
                                }
                                
                                // 根據機率決定是否渲染
                                if (Math.random() > chance) {
                                    continue;
                                }
                            }
                            
                            allParticles.add(new ParticleData(
                                    particleSettings.getParticle(), 
                                    loc, 
                                    adjustedColor));
                        }
                    }
                }
            }
        }
        
        // 將收集的粒子資料加入佇列，使用模式特定的佇列
        queueManager.queueParticlesForPlayer(player.getUniqueId(), allParticles, mode);
    }
    
    /**
     * 根據相對距離計算渲染機率
     * @param relativeDistance 相對距離(0-1)
     * @param fadeFactor 淡出因子
     * @return 渲染機率(0-1)
     */
    private double calculateRenderChance(double relativeDistance, double fadeFactor) {
        // 越近的點渲染機率越高
        return Math.max(0, 1 - Math.pow(relativeDistance / fadeFactor, 2));
    }
    
    /**
     * 檢查位置是否在玩家視線焦點區域
     */
    private boolean isInFocusArea(Player player, Location location, Vector playerDirection) {
        if (location.getWorld() != player.getWorld()) return false;
        
        Vector playerToLocation = location.clone().subtract(player.getEyeLocation()).toVector();
        
        // 規一化向量
        playerDirection.normalize();
        playerToLocation.normalize();
        
        // 計算兩個向量之間的角度（弧度）
        double angle = Math.acos(playerDirection.dot(playerToLocation));
        
        // 轉換為度數並檢查是否在焦點範圍內(15度)
        return Math.toDegrees(angle) <= 15;
    }
    
    /**
     * 調整顏色亮度
     * @param original 原始顏色
     * @param factor 亮度因子(>1增亮, <1減暗)
     * @return 調整後的顏色
     */
    private Color adjustColorBrightness(Color original, double factor) {
        int r = Math.min(255, Math.max(0, (int)(original.getRed() * factor)));
        int g = Math.min(255, Math.max(0, (int)(original.getGreen() * factor)));
        int b = Math.min(255, Math.max(0, (int)(original.getBlue() * factor)));
        return Color.fromRGB(r, g, b);
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
        // 委託給非同步渲染管理器
        asyncRenderManager.renderClaimsAsync(player, mode);
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
     * 取得玩家每秒粒子數量
     */
    public int getPlayerParticlesPerSecond(UUID playerId) {
        return statisticsManager.getPlayerParticlesPerSecond(playerId);
    }
    
    /**
     * 切換玩家粒子計數顯示
     */
    public boolean toggleParticleCounterDisplay(UUID playerId) {
        return statisticsManager.toggleParticleCounterDisplay(playerId);
    }
    
    /**
     * 檢查玩家是否已啟用粒子計數顯示
     */
    public boolean isParticleCounterDisplayEnabled(UUID playerId) {
        return statisticsManager.isParticleCounterDisplayEnabled(playerId);
    }
}
