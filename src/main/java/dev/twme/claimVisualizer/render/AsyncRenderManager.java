package dev.twme.claimVisualizer.render;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.claim.ClaimBoundary;
import dev.twme.claimVisualizer.claim.ClaimManager;
import dev.twme.claimVisualizer.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
                        // 處理其他模式（WALL, FULL 等）
                        // 為每種部分取得粒子設定
                        for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                            ConfigManager.ParticleSettings particleSettings = 
                                    configManager.getParticleSettings(claim.getType(), part);
                            
                            // 取得該部分的所有點
                            List<Location> points = claim.getPointsForPart(part, spacing, playerY);
                            
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
