package dev.twme.claimVisualizer.claim;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClaimBoundary {

    private final UUID claimId;
    private final UUID ownerId;
    private final String type;
    private final World world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    
    public ClaimBoundary(UUID claimId, UUID ownerId, String type, World world, 
                         int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.claimId = claimId;
        this.ownerId = ownerId;
        this.type = type;
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
    
    public UUID getClaimId() {
        return claimId;
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public String getType() {
        return type;
    }
    
    public World getWorld() {
        return world;
    }
    
    public int getMinX() {
        return minX;
    }
    
    public int getMinY() {
        return minY;
    }
    
    public int getMinZ() {
        return minZ;
    }
    
    public int getMaxX() {
        return maxX;
    }
    
    public int getMaxY() {
        return maxY;
    }
    
    public int getMaxZ() {
        return maxZ;
    }
    
    public boolean isNearby(Location location, int distance) {
        if (!location.getWorld().equals(world)) return false;
        
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        // 檢查位置是否在範圍內或附近
        boolean nearX = (x >= (minX - distance)) && (x <= (maxX + distance));
        boolean nearZ = (z >= (minZ - distance)) && (z <= (maxZ + distance));
        
        return nearX && nearZ;
    }
    
    /**
     * 獲取邊界線上的點，用於繪製粒子
     */
    public List<Location> getOutlinePoints(double spacing) {
        return getOutlinePoints(spacing, minY + 1);
    }

    /**
     * 獲取邊界線上的點，用於繪製粒子，根據顯示模式考慮 3D 邊界
     */
    public List<Location> getOutlinePoints(double spacing, int displayHeight) {
        List<Location> points = new ArrayList<>();
        
        // 根據玩家位置決定顯示哪個水平面
        int playerY = Math.min(Math.max(displayHeight, minY), maxY);
        
        // 底部邊框 (minY)
        // 南北兩條線
        for (double x = minX; x <= maxX; x += spacing) {
            points.add(new Location(world, x, minY, minZ));
            points.add(new Location(world, x, minY, maxZ));
        }
        
        // 東西兩條線
        for (double z = minZ; z <= maxZ; z += spacing) {
            points.add(new Location(world, minX, minY, z));
            points.add(new Location(world, maxX, minY, z));
        }
        
        // 頂部邊框 (maxY)
        // 南北兩條線
        for (double x = minX; x <= maxX; x += spacing) {
            points.add(new Location(world, x, maxY, minZ));
            points.add(new Location(world, x, maxY, maxZ));
        }
        
        // 東西兩條線
        for (double z = minZ; z <= maxZ; z += spacing) {
            points.add(new Location(world, minX, maxY, z));
            points.add(new Location(world, maxX, maxY, z));
        }
        
        // 玩家所在高度的水平輪廓線 (如果不是底部或頂部)
        if (playerY != minY && playerY != maxY) {
            // 南北兩條線
            for (double x = minX; x <= maxX; x += spacing) {
                points.add(new Location(world, x, playerY, minZ));
                points.add(new Location(world, x, playerY, maxZ));
            }
            
            // 東西兩條線
            for (double z = minZ; z <= maxZ; z += spacing) {
                points.add(new Location(world, minX, playerY, z));
                points.add(new Location(world, maxX, playerY, z));
            }
        }
        
        // 垂直連接線
        // 四個角落的垂直線
        for (double y = minY; y <= maxY; y += spacing) {
            // 西南角
            points.add(new Location(world, minX, y, minZ));
            // 東南角
            points.add(new Location(world, maxX, y, minZ));
            // 西北角
            points.add(new Location(world, minX, y, maxZ));
            // 東北角
            points.add(new Location(world, maxX, y, maxZ));
        }
        
        return points;
    }
    
    /**
     * 只獲取角落點，用於角落顯示模式
     */
    public List<Location> getCornerPoints(int cornerSize) {
        return getCornerPoints(cornerSize, minY + 1);
    }
    
    /**
     * 只獲取角落點，用於角落顯示模式，支援 3D 領地
     */
    public List<Location> getCornerPoints(int cornerSize, int displayHeight) {
        List<Location> points = new ArrayList<>();
        
        // 底部角落 (minY)
        // 西南角
        for (int x = minX; x < minX + cornerSize && x <= maxX; x++) {
            points.add(new Location(world, x, minY, minZ));
        }
        for (int z = minZ; z < minZ + cornerSize && z <= maxZ; z++) {
            points.add(new Location(world, minX, minY, z));
        }
        
        // 東南角
        for (int x = maxX; x > maxX - cornerSize && x >= minX; x--) {
            points.add(new Location(world, x, minY, minZ));
        }
        for (int z = minZ; z < minZ + cornerSize && z <= maxZ; z++) {
            points.add(new Location(world, maxX, minY, z));
        }
        
        // 西北角
        for (int x = minX; x < minX + cornerSize && x <= maxX; x++) {
            points.add(new Location(world, x, minY, maxZ));
        }
        for (int z = maxZ; z > maxZ - cornerSize && z >= minZ; z--) {
            points.add(new Location(world, minX, minY, z));
        }
        
        // 東北角
        for (int x = maxX; x > maxX - cornerSize && x >= minX; x--) {
            points.add(new Location(world, x, minY, maxZ));
        }
        for (int z = maxZ; z > maxZ - cornerSize && z >= minZ; z--) {
            points.add(new Location(world, maxX, minY, z));
        }
        
        // 頂部角落 (maxY)
        // 西南角
        for (int x = minX; x < minX + cornerSize && x <= maxX; x++) {
            points.add(new Location(world, x, maxY, minZ));
        }
        for (int z = minZ; z < minZ + cornerSize && z <= maxZ; z++) {
            points.add(new Location(world, minX, maxY, z));
        }
        
        // 東南角
        for (int x = maxX; x > maxX - cornerSize && x >= minX; x--) {
            points.add(new Location(world, x, maxY, minZ));
        }
        for (int z = minZ; z < minZ + cornerSize && z <= maxZ; z++) {
            points.add(new Location(world, maxX, maxY, z));
        }
        
        // 西北角
        for (int x = minX; x < minX + cornerSize && x <= maxX; x++) {
            points.add(new Location(world, x, maxY, maxZ));
        }
        for (int z = maxZ; z > maxZ - cornerSize && z >= minZ; z--) {
            points.add(new Location(world, minX, maxY, z));
        }
        
        // 東北角
        for (int x = maxX; x > maxX - cornerSize && x >= minX; x--) {
            points.add(new Location(world, x, maxY, maxZ));
        }
        for (int z = maxZ; z > maxZ - cornerSize && z >= minZ; z--) {
            points.add(new Location(world, maxX, maxY, z));
        }
        
        // 垂直角落線
        for (int y = minY; y < minY + cornerSize && y <= maxY; y++) {
            points.add(new Location(world, minX, y, minZ)); // 西南
            points.add(new Location(world, maxX, y, minZ)); // 東南
            points.add(new Location(world, minX, y, maxZ)); // 西北
            points.add(new Location(world, maxX, y, maxZ)); // 東北
        }
        
        for (int y = maxY; y > maxY - cornerSize && y >= minY; y--) {
            points.add(new Location(world, minX, y, minZ)); // 西南
            points.add(new Location(world, maxX, y, minZ)); // 東南
            points.add(new Location(world, minX, y, maxZ)); // 西北
            points.add(new Location(world, maxX, y, maxZ)); // 東北
        }
        
        return points;
    }
}
