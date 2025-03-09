package dev.twme.claimVisualizer.config;

import dev.twme.claimVisualizer.ClaimVisualizer;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final ClaimVisualizer plugin;
    private FileConfiguration config;
    
    private int updateInterval;
    private int renderDistance;
    private double particleSpacing;
    private double particleHeight;
    private int maxClaims;
    private boolean asyncRendering;
    private int cacheTime;
    private DisplayMode displayMode;
    private boolean showOwnClaims;
    private boolean showOthersClaims;
    private boolean showAdminClaims;
    private boolean showTownClaims;
    private double wallRadius;   // 牆面顯示半徑
    private double outlineRadius; // 新增：OUTLINE模式顯示半徑
    
    // 更新數據結構 - 為每種領地類型儲存不同部分的粒子設定
    private final Map<String, Map<ClaimPart, ParticleSettings>> claimTypeParticles = new HashMap<>();

    public ConfigManager(ClaimVisualizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // 載入基本設定
        updateInterval = config.getInt("particles.update-interval", 10);
        renderDistance = config.getInt("particles.render-distance", 30);
        particleSpacing = config.getDouble("particles.spacing", 0.5);
        particleHeight = config.getDouble("particles.height", 1.0);
        
        // 載入性能設定
        maxClaims = config.getInt("performance.max-claims", 20);
        asyncRendering = config.getBoolean("performance.async-rendering", true);
        cacheTime = config.getInt("performance.cache-time", 30);
        
        // 載入顯示設定
        String mode = config.getString("display.mode", "OUTLINE");
        displayMode = DisplayMode.valueOf(mode.toUpperCase());
        showOwnClaims = config.getBoolean("display.show-own-claims", true);
        showOthersClaims = config.getBoolean("display.show-others-claims", true);
        showAdminClaims = config.getBoolean("display.show-admin-claims", true);
        showTownClaims = config.getBoolean("display.show-town-claims", true);
        
        // 讀取 wall-radius 和 outline-radius
        wallRadius = config.getDouble("display.wall-radius", 3.0);
        outlineRadius = config.getDouble("display.outline-radius", 5.0); // 新增：OUTLINE模式顯示半徑
        
        // 載入不同領地類型的粒子設定
        loadParticleSettings();
    }
    
    private void loadParticleSettings() {
        claimTypeParticles.clear();
        ConfigurationSection typeSection = config.getConfigurationSection("claim-types");
        
        if (typeSection == null) return;
        
        for (String claimType : typeSection.getKeys(false)) {
            ConfigurationSection typeConfigSection = typeSection.getConfigurationSection(claimType);
            if (typeConfigSection == null) continue;
            
            Map<ClaimPart, ParticleSettings> partSettings = new HashMap<>();
            
            // 載入每個部分的粒子設定
            for (ClaimPart part : ClaimPart.values()) {
                ConfigurationSection partSection = typeConfigSection.getConfigurationSection(part.getConfigKey());
                
                // 如果找不到特定部分的設定，則使用舊格式或預設值
                if (partSection == null) {
                    // 嘗試使用舊格式的設定
                    String particleName = typeConfigSection.getString("particle", "REDSTONE");
                    Particle particle = Particle.valueOf(particleName);
                    
                    int red = typeConfigSection.getInt("color.red", 255);
                    int green = typeConfigSection.getInt("color.green", 255);
                    int blue = typeConfigSection.getInt("color.blue", 255);
                    Color color = Color.fromRGB(red, green, blue);
                    
                    partSettings.put(part, new ParticleSettings(particle, color));
                } else {
                    // 使用新格式的設定
                    String particleName = partSection.getString("particle", "REDSTONE");
                    Particle particle = Particle.valueOf(particleName);
                    
                    int red = partSection.getInt("color.red", 255);
                    int green = partSection.getInt("color.green", 255);
                    int blue = partSection.getInt("color.blue", 255);
                    Color color = Color.fromRGB(red, green, blue);
                    
                    partSettings.put(part, new ParticleSettings(particle, color));
                }
            }
            
            claimTypeParticles.put(claimType, partSettings);
        }
    }
    
    public int getUpdateInterval() {
        return updateInterval;
    }
    
    public int getRenderDistance() {
        return renderDistance;
    }
    
    public double getParticleSpacing() {
        return particleSpacing;
    }
    
    public double getParticleHeight() {
        return particleHeight;
    }
    
    public int getMaxClaims() {
        return maxClaims;
    }
    
    public boolean isAsyncRendering() {
        return asyncRendering;
    }
    
    public int getCacheTime() {
        return cacheTime;
    }
    
    public DisplayMode getDisplayMode() {
        return displayMode;
    }
    
    public boolean showOwnClaims() {
        return showOwnClaims;
    }
    
    public boolean showOthersClaims() {
        return showOthersClaims;
    }
    
    public boolean showAdminClaims() {
        return showAdminClaims;
    }
    
    public boolean showTownClaims() {
        return showTownClaims;
    }
    
    public double getWallRadius() {
        return wallRadius;
    }
    
    public double getOutlineRadius() {
        return outlineRadius;
    }
    
    /**
     * 獲取特定領地類型和部分的粒子設定
     */
    public ParticleSettings getParticleSettings(String claimType, ClaimPart part) {
        Map<ClaimPart, ParticleSettings> partSettings = claimTypeParticles.get(claimType);
        
        if (partSettings != null && partSettings.containsKey(part)) {
            return partSettings.get(part);
        }
        
        // 默認設定
        return new ParticleSettings(Particle.DUST, Color.WHITE);
    }
    
    /**
     * 向下相容的方法
     */
    public ParticleSettings getParticleSettings(String claimType) {
        return getParticleSettings(claimType, ClaimPart.BOTTOM);
    }
    
    public enum DisplayMode {
        CORNERS, OUTLINE, FULL, WALL
    }
    
    /**
     * 領地的不同部分
     */
    public enum ClaimPart {
        BOTTOM("bottom"),   // 底部邊框
        TOP("top"),         // 頂部邊框
        HORIZONTAL("horizontal"), // 玩家所在高度的水平線
        VERTICAL("vertical");     // 垂直連接線
        
        private final String configKey;
        
        ClaimPart(String configKey) {
            this.configKey = configKey;
        }
        
        public String getConfigKey() {
            return configKey;
        }
    }
    
    public static class ParticleSettings {
        private final Particle particle;
        private final Color color;
        
        public ParticleSettings(Particle particle, Color color) {
            this.particle = particle;
            this.color = color;
        }
        
        public Particle getParticle() {
            return particle;
        }
        
        public Color getColor() {
            return color;
        }
    }
}
