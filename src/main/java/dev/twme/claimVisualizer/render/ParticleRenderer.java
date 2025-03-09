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

import java.util.*;

public class ParticleRenderer {

    private final ClaimVisualizer plugin;
    private final ClaimManager claimManager;
    private final ConfigManager configManager;
    
    private BukkitTask renderTask;
    
    public ParticleRenderer(ClaimVisualizer plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.configManager = plugin.getConfigManager();
    }
    
    /**
     * 啟動渲染排程任務
     */
    public void startRenderTask() {
        if (renderTask != null) {
            renderTask.cancel();
        }
        
        int updateInterval = configManager.getUpdateInterval();
        
        renderTask = new BukkitRunnable() {
            @Override
            public void run() {
                renderForAllPlayers();
            }
        }.runTaskTimer(plugin, updateInterval, updateInterval);
    }
    
    /**
     * 停止渲染排程任務
     */
    public void stopRenderTask() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
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
     * 為特定玩家渲染領地粒子
     */
    public void renderClaims(Player player) {
        Set<ClaimBoundary> claims = claimManager.getNearbyClaims(player);
        double spacing = configManager.getParticleSpacing();
        ConfigManager.DisplayMode mode = configManager.getDisplayMode();
        
        // 獲取玩家當前高度，用於確定顯示哪些粒子
        int playerY = player.getLocation().getBlockY();
        int renderDistance = configManager.getRenderDistance();
        
        for (ClaimBoundary claim : claims) {
            if (mode == ConfigManager.DisplayMode.CORNERS) {
                // 角落模式使用舊的邏輯，但使用頂部的粒子設定
                ConfigManager.ParticleSettings particleSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                
                List<Location> points = claim.getCornerPoints(5, playerY);
                for (Location loc : points) {
                    spawnParticle(player, particleSettings.getParticle(), 
                            loc, particleSettings.getColor());
                }
            } else if (mode == ConfigManager.DisplayMode.WALL) {
                // WALL 模式 - 使用新的牆面顯示邏輯
                ConfigManager.ParticleSettings particleSettings = 
                        configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                
                List<Location> points = claim.getWallModePoints(player.getLocation(), renderDistance, spacing);
                for (Location loc : points) {
                    spawnParticle(player, particleSettings.getParticle(), 
                            loc, particleSettings.getColor());
                }
            } else {
                // OUTLINE 和 FULL 模式使用新的邏輯，為每個部分使用不同粒子設定
                for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                    ConfigManager.ParticleSettings particleSettings = 
                            configManager.getParticleSettings(claim.getType(), part);
                    
                    List<Location> points = claim.getPointsForPart(part, spacing, playerY);
                    for (Location loc : points) {
                        spawnParticle(player, particleSettings.getParticle(), 
                                loc, particleSettings.getColor());
                    }
                }
            }
        }
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
                ConfigManager.DisplayMode mode = configManager.getDisplayMode();
                
                // 獲取玩家當前高度
                int playerY = player.getLocation().getBlockY();
                int renderDistance = configManager.getRenderDistance();
                
                List<ParticleData> particleData = new ArrayList<>();
                
                for (ClaimBoundary claim : claims) {
                    if (mode == ConfigManager.DisplayMode.CORNERS) {
                        // 角落模式使用舊的邏輯，但使用頂部的粒子設定
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.TOP);
                        
                        List<Location> points = claim.getCornerPoints(5, playerY);
                        for (Location loc : points) {
                            particleData.add(new ParticleData(particleSettings.getParticle(), 
                                    loc, particleSettings.getColor()));
                        }
                    } else if (mode == ConfigManager.DisplayMode.WALL) {
                        // WALL 模式 - 使用新的牆面顯示邏輯
                        ConfigManager.ParticleSettings particleSettings = 
                                configManager.getParticleSettings(claim.getType(), ConfigManager.ClaimPart.VERTICAL);
                        
                        List<Location> points = claim.getWallModePoints(player.getLocation(), renderDistance, spacing);
                        for (Location loc : points) {
                            particleData.add(new ParticleData(particleSettings.getParticle(), 
                                    loc, particleSettings.getColor()));
                        }
                    } else {
                        // OUTLINE 和 FULL 模式使用新的邏輯，為每個部分使用不同粒子設定
                        for (ConfigManager.ClaimPart part : ConfigManager.ClaimPart.values()) {
                            ConfigManager.ParticleSettings particleSettings = 
                                    configManager.getParticleSettings(claim.getType(), part);
                            
                            List<Location> points = claim.getPointsForPart(part, spacing, playerY);
                            for (Location loc : points) {
                                particleData.add(new ParticleData(particleSettings.getParticle(), 
                                        loc, particleSettings.getColor()));
                            }
                        }
                    }
                }
                
                // 切換回主執行緒顯示粒子
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (ParticleData data : particleData) {
                            spawnParticle(player, data.getParticle(), data.getLocation(), data.getColor());
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
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
