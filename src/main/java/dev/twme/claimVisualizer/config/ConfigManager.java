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
    
    // 新增：視角效果相關設定
    private double viewAngleEffect = 0.6; // 預設視角效果強度 (0-1)

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
                } else if (mode == DisplayMode.FULL) {
                    settings.verticalRenderRange = 10; // 預設垂直範圍
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
                    // 新增 WALL 模式特有設定
                    settings.adaptiveDensity = modeSection.getBoolean("adaptive-density", true);
                    settings.focusFactor = modeSection.getDouble("focus-factor", 1.8);
                    settings.fadeDistance = modeSection.getDouble("fade-distance", 0.7);
                    settings.edgeEmphasis = modeSection.getDouble("edge-emphasis", 2.0);
                    settings.waveEffect = modeSection.getBoolean("wave-effect", true);
                    settings.waveSpeed = modeSection.getDouble("wave-speed", 1.0);
                    settings.waveIntensity = modeSection.getDouble("wave-intensity", 0.3);
                    settings.viewAngleEffect = modeSection.getDouble("view-angle-effect", 0.6);
                    // 新增兩種渲染方式的開關設定
                    settings.useRaycastMethod = modeSection.getBoolean("use-raycast-method", true);
                    settings.useViewAngleMethod = modeSection.getBoolean("use-view-angle-method", true);
                } else if (mode == DisplayMode.OUTLINE) {
                    settings.radius = modeSection.getDouble("outline-radius", outlineRadius);
                } else if (mode == DisplayMode.CORNERS) {
                    settings.cornerSize = modeSection.getInt("corner-size", 5);
                } else if (mode == DisplayMode.FULL) {
                    settings.verticalRenderRange = modeSection.getInt("vertical-render-range", 10);
                    // 新增 FULL 模式特有設定
                    settings.adaptiveDensity = modeSection.getBoolean("adaptive-density", true);
                    settings.focusFactor = modeSection.getDouble("focus-factor", 1.5);
                    settings.fadeDistance = modeSection.getDouble("fade-distance", 0.8);
                    settings.topBrightness = modeSection.getDouble("top-brightness", 1.2);
                    settings.bottomBrightness = modeSection.getDouble("bottom-brightness", 0.8);
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
    
    // 新增：獲取垂直渲染範圍
    public int getVerticalRenderRange(DisplayMode mode) {
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).verticalRenderRange : 10;
    }
    
    // 新增：獲取 FULL 模式相關設定方法
    public boolean isAdaptiveDensity() {
        DisplayMode mode = DisplayMode.FULL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).isAdaptiveDensity() : true;
    }
    
    public double getFocusFactor() {
        DisplayMode mode = DisplayMode.FULL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).getFocusFactor() : 1.5;
    }
    
    public double getFadeDistance() {
        DisplayMode mode = DisplayMode.FULL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).getFadeDistance() : 0.8;
    }
    
    public double getPartBrightness(ClaimPart part) {
        DisplayMode mode = DisplayMode.FULL;
        if (!modeSettings.containsKey(mode)) return 1.0;
        
        if (part == ClaimPart.TOP) {
            return modeSettings.get(mode).getTopBrightness();
        } else if (part == ClaimPart.BOTTOM) {
            return modeSettings.get(mode).getBottomBrightness();
        }
        return 1.0;
    }

    // 新增：獲取 WALL 模式相關設定方法
    public boolean isWallAdaptiveDensity() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).isAdaptiveDensity() : true;
    }
    
    public double getWallFocusFactor() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).getFocusFactor() : 1.8;
    }
    
    public double getWallFadeDistance() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).getFadeDistance() : 0.7;
    }
    
    public double getWallEdgeEmphasis() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).getEdgeEmphasis() : 2.0;
    }
    
    public boolean isWallWaveEffect() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).isWaveEffect() : true;
    }
    
    public double getWallWaveSpeed() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).getWaveSpeed() : 1.0;
    }
    
    public double getWallWaveIntensity() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).getWaveIntensity() : 0.3;
    }
    
    public double getWallViewAngleEffect() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).viewAngleEffect : viewAngleEffect;
    }
    
    // 新增：獲取 WALL 模式射線渲染方法開關
    public boolean isWallUseRaycastMethod() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).useRaycastMethod : true;
    }
    
    // 新增：獲取 WALL 模式視角渲染方法開關
    public boolean isWallUseViewAngleMethod() {
        DisplayMode mode = DisplayMode.WALL;
        return modeSettings.containsKey(mode) ? modeSettings.get(mode).useViewAngleMethod : true;
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
        private int verticalRenderRange = 10; // 垂直渲染範圍 (用於 FULL 模式)
        private boolean adaptiveDensity = true;  // 自適應粒子密度
        private double focusFactor = 1.5;        // 視線焦點增強因子
        private double fadeDistance = 0.8;       // 淡出距離因子
        private double topBrightness = 1.2;      // 頂部亮度增強
        private double bottomBrightness = 0.8;   // 底部亮度降低
        private double edgeEmphasis = 2.0;       // 邊緣強調程度
        private boolean waveEffect = true;       // 波浪效果
        private double waveSpeed = 1.0;          // 波浪速度
        private double waveIntensity = 0.3;      // 波浪強度
        public double viewAngleEffect = 0.6;  // 視角效果強度 (0-1)
        private boolean useRaycastMethod = true;   // 是否使用射線渲染方法
        private boolean useViewAngleMethod = true; // 是否使用視角渲染方法
        
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
        
        public int getVerticalRenderRange() {
            return verticalRenderRange;
        }
        
        public boolean isAdaptiveDensity() {
            return adaptiveDensity;
        }
        
        public double getFocusFactor() {
            return focusFactor;
        }
        
        public double getFadeDistance() {
            return fadeDistance;
        }
        
        public double getTopBrightness() {
            return topBrightness;
        }
        
        public double getBottomBrightness() {
            return bottomBrightness;
        }
        
        public double getEdgeEmphasis() {
            return edgeEmphasis;
        }
        
        public boolean isWaveEffect() {
            return waveEffect;
        }
        
        public double getWaveSpeed() {
            return waveSpeed;
        }
        
        public double getWaveIntensity() {
            return waveIntensity;
        }
        
        public boolean isUseRaycastMethod() {
            return useRaycastMethod;
        }
        
        public boolean isUseViewAngleMethod() {
            return useViewAngleMethod;
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
