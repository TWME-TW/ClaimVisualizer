package dev.twme.claimVisualizer.render;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

/**
 * 粒子資料 - 儲存單個粒子的資訊
 */
public class ParticleData {
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
