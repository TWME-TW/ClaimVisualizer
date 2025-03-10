package dev.twme.claimVisualizer.render;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.claim.ClaimBoundary;
import dev.twme.claimVisualizer.claim.ClaimManager;
import dev.twme.claimVisualizer.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * 非同步渲染管理器 - 負責處理非同步渲染領地粒子
 */
public class AsyncRenderManager {
    private final ClaimVisualizer plugin;
    private final ClaimManager claimManager;
    private final ConfigManager configManager;
    private final ParticleQueueManager queueManager;
    private final ParticleStatisticsManager statisticsManager;
    
    public AsyncRenderManager(ClaimVisualizer plugin, ClaimManager claimManager, 
                              ParticleQueueManager queueManager, ParticleStatisticsManager statisticsManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.configManager = plugin.getConfigManager();
        this.queueManager = queueManager;
        this.statisticsManager = statisticsManager;
    }
    
    /**
     * 非同步渲染領地粒子，使用指定顯示模式
     */
    public void renderClaimsAsync(Player player, ConfigManager.DisplayMode mode) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<ClaimBoundary> claims = claimManager.getNearbyClaims(player);
                double spacing = configManager.getParticleSpacing(mode);
                int renderDistance = configManager.getRenderDistance(mode);
                int playerY = player.getLocation().getBlockY();
                
                List<ParticleData> allParticles = new ArrayList<>();
                
                for (ClaimBoundary claim : claims) {
                    if (mode == ConfigManager.DisplayMode.OUTLINE) {
                        double outlineRadius = configManager.getRadius(mode);
                        
                        // 使用輪廓框架的粒子設定
                        for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                            ConfigManager.ParticleSettings particleSettings = 
                                    configManager.getParticleSettings(claim.getType(), part);
                            
                            List<Location> points;
                            if (part == ConfigManager.ClaimPart.VERTICAL) {
                                // 修正：使用 getOutlineNearbyPoints 代替不存在的 getVerticalLines 方法
                                points = claim.getOutlineNearbyPoints(player.getLocation(), renderDistance, spacing, outlineRadius);
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
                        double viewAngleEffect = configManager.getWallViewAngleEffect();
                        boolean useRaycastMethod = configManager.isWallUseRaycastMethod();
                        boolean useViewAngleMethod = configManager.isWallUseViewAngleMethod();
                        
                        // 玩家視線方向向量
                        Vector playerDirection = player.getLocation().getDirection();
                        
                        // 取得當前時間戳用於波浪效果
                        long currentTimeMillis = System.currentTimeMillis();
                        
                        // 獲取粒子設定
                        ConfigManager.ParticleSettings horizontalSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.HORIZONTAL);
                        ConfigManager.ParticleSettings verticalSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                        ConfigManager.ParticleSettings cornerSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                        
                        // 收集兩種方法產生的所有點
                        List<ClaimBoundary.WallPoint> raycastPoints = new ArrayList<>();
                        List<ClaimBoundary.WallPoint> viewAnglePoints = new ArrayList<>();
                        
                        // 使用視線射線檢測的方法
                        if (useRaycastMethod) {
                            raycastPoints.addAll(claim.getWallModePointsWithRaycast(
                                    player.getLocation(), playerDirection, renderDistance, spacing, wallRadius));
                        }
                                
                        // 使用基於最近點和視角的方法
                        if (useViewAngleMethod) {
                            viewAnglePoints.addAll(claim.getWallModePointsWithViewAngle(
                                    player.getLocation(), playerDirection, renderDistance, spacing, wallRadius, viewAngleEffect));
                        }
                        
                        // 合併並去除重複點
                        List<ClaimBoundary.WallPoint> points = removeDuplicateWallPoints(raycastPoints, viewAnglePoints);
                        
                        for (ClaimBoundary.WallPoint point : points) {
                            Location loc = point.getLocation();
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
                                    
                                    // 強化邊緣
                                    if (point.isCorner() || point.isVertical()) {
                                        chance *= edgeEmphasis;
                                    }
                                    
                                    // 根據機率決定是否渲染
                                    if (Math.random() > chance) {
                                        continue;
                                    }
                                }
                                
                                // 波浪效果：根據時間和位置調整顏色亮度
                                double brightnessFactor = 1.0;
                                if (waveEffect) {
                                    // 計算基於時間和位置的波浪效果
                                    double waveOffset = (currentTimeMillis / 1000.0) * waveSpeed;
                                    double locationFactor = (loc.getBlockX() + loc.getBlockY() + loc.getBlockZ()) * 0.1;
                                    double waveFactor = Math.sin(waveOffset + locationFactor) * waveIntensity + 1.0;
                                    brightnessFactor *= waveFactor;
                                }
                                
                                // 根據點的屬性選擇適當的顏色
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
                    } else {
                        // 處理 FULL 模式
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
     * 合併並移除重複的牆面點
     * @param list1 第一個點列表
     * @param list2 第二個點列表
     * @return 合併後不含重複的點列表
     */
    private List<ClaimBoundary.WallPoint> removeDuplicateWallPoints(List<ClaimBoundary.WallPoint> list1, List<ClaimBoundary.WallPoint> list2) {
        // 使用空間網格法來快速判斷鄰近點
        // 網格大小設為粒子間距的一半，確保可以捕捉到重複點
        double gridSize = 0.25;
        Map<GridKey, ClaimBoundary.WallPoint> pointGrid = new HashMap<>();
        List<ClaimBoundary.WallPoint> result = new ArrayList<>();
        
        // 處理第一個列表的點
        for (ClaimBoundary.WallPoint point : list1) {
            Location loc = point.getLocation();
            GridKey key = new GridKey(
                    Math.floor(loc.getX() / gridSize),
                    Math.floor(loc.getY() / gridSize),
                    Math.floor(loc.getZ() / gridSize)
            );
            
            // 如果該網格尚未有點，則添加
            if (!pointGrid.containsKey(key)) {
                pointGrid.put(key, point);
                result.add(point);
            }
        }
        
        // 處理第二個列表的點
        for (ClaimBoundary.WallPoint point : list2) {
            Location loc = point.getLocation();
            GridKey key = new GridKey(
                    Math.floor(loc.getX() / gridSize),
                    Math.floor(loc.getY() / gridSize),
                    Math.floor(loc.getZ() / gridSize)
            );
            
            // 如果該網格尚未有點，則添加
            if (!pointGrid.containsKey(key)) {
                pointGrid.put(key, point);
                result.add(point);
            }
        }
        
        return result;
    }
    
    /**
     * 空間網格索引鍵，用於快速判斷點是否在同一網格
     */
    private static class GridKey {
        private final double x, y, z;
        
        public GridKey(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GridKey gridKey = (GridKey) o;
            return Double.compare(gridKey.x, x) == 0 &&
                    Double.compare(gridKey.y, y) == 0 &&
                    Double.compare(gridKey.z, z) == 0;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
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
     * 檢查位置是否在玩家視野範圍內
     */
    private boolean isInPlayerViewDirection(Player player, Location location) {
        // 從玩家到目標位置的向量
        Location playerLoc = player.getEyeLocation();
        
        if (location.getWorld() != playerLoc.getWorld()) return false;
        
        double distance = playerLoc.distance(location);
        if (distance > configManager.getRenderDistance()) return false;
        
        // 只檢查前方視野範圍內的粒子
        if (configManager.getViewAngleRange() < 360) {
            // 計算玩家視線方向與目標位置的夾角
            double angle = Math.abs(playerLoc.getDirection().angle(
                location.toVector().subtract(playerLoc.toVector())));
            
            // 轉換為度數
            double degrees = Math.toDegrees(angle);
            
            // 檢查是否在視野範圍內
            return degrees <= configManager.getViewAngleRange() / 2;
        }
        
        return true;
    }
}
