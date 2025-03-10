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
                    if (mode == ConfigManager.DisplayMode.CORNERS) {
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                        int cornerSize = configManager.getCornerSize(mode);
                        
                        // 使用頂部框架的粒子設定
                        // 修正：移除多餘的 spacing 參數
                        List<Location> cornerPoints = claim.getCornerPoints(cornerSize, playerY);
                        for (Location loc : cornerPoints) {
                            if (isInPlayerViewDirection(player, loc)) {
                                allParticles.add(new ParticleData(particleSettings.getParticle(), 
                                                                loc, 
                                                                particleSettings.getColor()));
                            }
                        }
                    } else if (mode == ConfigManager.DisplayMode.OUTLINE) {
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
