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
    
    // 全域設定 (保留向下相容)
    private int updateInterval;
    private int renderDistance;
    private double particleSpacing;
    private int particleDisplayInterval;
    private float viewAngleRange;
    
    private int maxClaims;
    private boolean asyncRendering;
    private int cacheTime;
    private DisplayMode displayMode;
    private boolean showOwnClaims;
    private boolean showOthersClaims;
    private boolean showAdminClaims;
    private boolean showTownClaims;
    private double wallRadius;
    private double outlineRadius;
    
    // 新增：每種顯示模式的設定
    private final Map<DisplayMode, ModeSettings> modeSettings = new HashMap<>();
    
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
        
        // 載入全域設定 (預設值，向下相容)
        updateInterval = config.getInt("particles.update-interval", 10);
        renderDistance = config.getInt("particles.render-distance", 20);
        particleSpacing = config.getDouble("particles.spacing", 0.5);
        particleDisplayInterval = config.getInt("particles.display-interval", 1);
        viewAngleRange = (float)config.getDouble("particles.view-angle-range", 90.0);

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
        
        // 讀取 wall-radius 和 outline-radius (向下相容)
        wallRadius = config.getDouble("display.wall-radius", 3.0);
        outlineRadius = config.getDouble("display.outline-radius", 5.0);
        
        // 新增：載入每種顯示模式的特定設定
        loadModeSettings();
        
        // 載入不同領地類型的粒子設定
        loadParticleSettings();
    }
    
    // 新增：載入每種顯示模式的特定設定
    private void loadModeSettings() {
        modeSettings.clear();
        ConfigurationSection modesSection = config.getConfigurationSection("display-modes");
        
        // 如果沒有找到模式特定設定，則使用全域設定作為預設值
        if (modesSection == null) {
            // 為每種顯示模式建立預設設定
            for (DisplayMode mode : DisplayMode.values()) {
                ModeSettings settings = new ModeSettings();
                settings.updateInterval = updateInterval;
                settings.renderDistance = renderDistance;
                settings.particleSpacing = particleSpacing;
                settings.displayInterval = particleDisplayInterval;
                
                // 針對特定模式設定特殊值
                if (mode == DisplayMode.WALL) {
                    settings.radius = wallRadius;
                } else if (mode == DisplayMode.OUTLINE) {
                    settings.radius = outlineRadius;
                }
                
                modeSettings.put(mode, settings);
            }
            return;
        }
        
        // 為每種顯示模式載入特定設定
        for (DisplayMode mode : DisplayMode.values()) {
            ConfigurationSection modeSection = modesSection.getConfigurationSection(mode.name());
            ModeSettings settings = new ModeSettings();
            
            if (modeSection == null) {
                // 如果沒有找到特定模式的設定，使用全域設定
                settings.updateInterval = updateInterval;
                settings.renderDistance = renderDistance;
                settings.particleSpacing = particleSpacing;
                settings.displayInterval = particleDisplayInterval;
            } else {
                // 載入模式特定設定
                settings.updateInterval = modeSection.getInt("update-interval", updateInterval);
                settings.renderDistance = modeSection.getInt("render-distance", renderDistance);
                settings.particleSpacing = modeSection.getDouble("spacing", particleSpacing);
                settings.displayInterval = modeSection.getInt("display-interval", particleDisplayInterval);
                
                // 載入模式特定的半徑設定
                if (mode == DisplayMode.WALL) {
                    settings.radius = modeSection.getDouble("wall-radius", wallRadius);
                } else if (mode == DisplayMode.OUTLINE) {
                    settings.radius = modeSection.getDouble("outline-radius", outlineRadius);
                } else if (mode == DisplayMode.CORNERS) {
                    settings.cornerSize = modeSection.getInt("corner-size", 5);
                }
            }
            
            modeSettings.put(mode, settings);
        }
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
    
    // 新增：獲取特定顯示模式的設定
    public ModeSettings getModeSettings(DisplayMode mode) {
        return modeSettings.getOrDefault(mode, new ModeSettings());
    }
    
    // 向下相容方法
    public int getUpdateInterval() {
        DisplayMode mode = getDisplayMode();
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).updateInterval : updateInterval;
    }
    
    // 新增：獲取特定模式的更新間隔
    public int getUpdateInterval(DisplayMode mode) {
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).updateInterval : updateInterval;
    }
    
    public int getRenderDistance() {
        DisplayMode mode = getDisplayMode();
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).renderDistance : renderDistance;
    }
    
    // 新增：獲取特定模式的渲染距離
    public int getRenderDistance(DisplayMode mode) {
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).renderDistance : renderDistance;
    }
    
    public double getParticleSpacing() {
        DisplayMode mode = getDisplayMode();
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).particleSpacing : particleSpacing;
    }
    
    // 新增：獲取特定模式的粒子間隔
    public double getParticleSpacing(DisplayMode mode) {
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).particleSpacing : particleSpacing;
    }
    
    // 新增：獲取特定模式的粒子顯示間隔
    public int getParticleDisplayInterval(DisplayMode mode) {
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).displayInterval : particleDisplayInterval;
    }
    
    // 新增：獲取角落大小
    public int getCornerSize(DisplayMode mode) {
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).cornerSize : 5;
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
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).radius : wallRadius;
    }
    
    public double getOutlineRadius() {
        DisplayMode mode = DisplayMode.OUTLINE;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).radius : outlineRadius;
    }
    
    // 新增：獲取特定模式的半徑
    public double getRadius(DisplayMode mode) {
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).radius : 
               (mode == DisplayMode.WALL ? wallRadius : 
               (mode == DisplayMode.OUTLINE ? outlineRadius : 5.0));
    }
    
    public int getParticleDisplayInterval() {
        DisplayMode mode = getDisplayMode();
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).displayInterval : particleDisplayInterval;
    }
    
    public float getViewAngleRange() {
        return viewAngleRange;
    }
    
    public ParticleSettings getParticleSettings(String claimType, ClaimPart part) {
        Map<ClaimPart, ParticleSettings> partSettings = claimTypeParticles.get(claimType);
        
        if (partSettings != null && partSettings.containsKey(part)) {
            return partSettings.get(part);
        }
        
        // 默認設定
        return new ParticleSettings(Particle.DUST, Color.WHITE);
    }
    
    public ParticleSettings getParticleSettings(String claimType) {
        return getParticleSettings(claimType, ClaimPart.BOTTOM);
    }
    
    public enum DisplayMode {
        CORNERS, OUTLINE, FULL, WALL
    }
    
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
    
    // 新增：顯示模式特定設定類別
    public static class ModeSettings {
        private int updateInterval = 10; // 預設值
        private int renderDistance = 20; // 預設值
        private double particleSpacing = 0.5; // 預設值
        private int displayInterval = 1; // 預設值
        private double radius = 5.0; // 半徑 (用於 WALL 和 OUTLINE 模式)
        private int cornerSize = 5; // 角落大小 (用於 CORNERS 模式)
        
        public int getUpdateInterval() {
            return updateInterval;
        }
        
        public int getRenderDistance() {
            return renderDistance;
        }
        
        public double getParticleSpacing() {
            return particleSpacing;
        }
        
        public int getDisplayInterval() {
            return displayInterval;
        }
        
        public double getRadius() {
            return radius;
        }
        
        public int getCornerSize() {
            return cornerSize;
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
