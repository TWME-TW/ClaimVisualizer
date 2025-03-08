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
    
    private final Map<String, ParticleSettings> claimTypeParticles = new HashMap<>();

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
        
        // 載入不同領地類型的粒子設定
        loadParticleSettings();
    }
    
    private void loadParticleSettings() {
        claimTypeParticles.clear();
        ConfigurationSection typeSection = config.getConfigurationSection("claim-types");
        
        if (typeSection == null) return;
        
        for (String claimType : typeSection.getKeys(false)) {
            ConfigurationSection section = typeSection.getConfigurationSection(claimType);
            if (section == null) continue;
            
            String particleName = section.getString("particle", "REDSTONE");
            Particle particle = Particle.valueOf(particleName);
            
            int red = section.getInt("color.red", 255);
            int green = section.getInt("color.green", 255);
            int blue = section.getInt("color.blue", 255);
            Color color = Color.fromRGB(red, green, blue);
            
            claimTypeParticles.put(claimType, new ParticleSettings(particle, color));
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
    
    public ParticleSettings getParticleSettings(String claimType) {
        return claimTypeParticles.getOrDefault(claimType, 
                new ParticleSettings(Particle.DUST, Color.WHITE));
    }
    
    public enum DisplayMode {
        CORNERS, OUTLINE, FULL
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
