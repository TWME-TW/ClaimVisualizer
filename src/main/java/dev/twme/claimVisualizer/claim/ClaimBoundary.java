package dev.twme.claimVisualizer.claim;

import dev.twme.claimVisualizer.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

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
     * 獲取底部邊框的點
     */
    public List<Location> getBottomPoints(double spacing) {
        List<Location> points = new ArrayList<>();
        
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
        
        return points;
    }
    
    /**
     * 獲取頂部邊框的點
     */
    public List<Location> getTopPoints(double spacing) {
        List<Location> points = new ArrayList<>();
        
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
        
        return points;
    }
    
    /**
     * 獲取玩家所在高度的水平線點
     */
    public List<Location> getHorizontalPoints(double spacing, int playerY) {
        List<Location> points = new ArrayList<>();
        
        // 確保在領地邊界高度範圍內
        int displayY = Math.min(Math.max(playerY, minY), maxY);
        
        // 如果是頂部或底部，則不必重複渲染
        if (displayY == minY || displayY == maxY) {
            return points;
        }
        
        // 南北兩條線
        for (double x = minX; x <= maxX; x += spacing) {
            points.add(new Location(world, x, displayY, minZ));
            points.add(new Location(world, x, displayY, maxZ));
        }
        
        // 東西兩條線
        for (double z = minZ; z <= maxZ; z += spacing) {
            points.add(new Location(world, minX, displayY, z));
            points.add(new Location(world, maxX, displayY, z));
        }
        
        return points;
    }
    
    /**
     * 獲取垂直連接線的點
     */
    public List<Location> getVerticalPoints(double spacing) {
        List<Location> points = new ArrayList<>();
        
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
        
        // 結合所有部分的點
        points.addAll(getBottomPoints(spacing));
        points.addAll(getTopPoints(spacing));
        points.addAll(getHorizontalPoints(spacing, displayHeight));
        points.addAll(getVerticalPoints(spacing));
        
        return points;
    }
    
    /**
     * 獲取特定部分的點
     */
    public List<Location> getPointsForPart(ConfigManager.ClaimPart part, double spacing, int displayHeight) {
        return switch (part) {
            case BOTTOM -> getBottomPoints(spacing);
            case TOP -> getTopPoints(spacing);
            case HORIZONTAL -> getHorizontalPoints(spacing, displayHeight);
            case VERTICAL -> getVerticalPoints(spacing);
        };
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
    
    /**
     * 領地牆面枚舉
     */
    public enum WallFace {
        NORTH, SOUTH, EAST, WEST, TOP, BOTTOM
    }
    
    /**
     * 檢查玩家是否在領地內
     */
    public boolean isPlayerInside(Location playerLocation) {
        if (!playerLocation.getWorld().equals(world)) return false;
        
        int x = playerLocation.getBlockX();
        int y = playerLocation.getBlockY();
        int z = playerLocation.getBlockZ();
        
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    
    /**
     * 獲取玩家到領地的最近點
     */
    public Location getNearestPoint(Location playerLocation) {
        if (!playerLocation.getWorld().equals(world)) return playerLocation;
        
        double x = playerLocation.getX();
        double y = playerLocation.getY();
        double z = playerLocation.getZ();
        
        // 限制坐標在領地範圍內
        x = Math.max(minX, Math.min(maxX, x));
        y = Math.max(minY, Math.min(maxY, y));
        z = Math.max(minZ, Math.min(maxZ, z));
        
        return new Location(world, x, y, z);
    }
    
    /**
     * 獲取玩家到特定牆面的最近點
     */
    public Location getNearestPointOnFace(Location playerLocation, WallFace face) {
        if (!playerLocation.getWorld().equals(world)) return playerLocation;
        
        double x = playerLocation.getX();
        double y = playerLocation.getY();
        double z = playerLocation.getZ();
        
        // 限制坐標在平面範圍內
        x = Math.max(minX, Math.min(maxX, x));
        y = Math.max(minY, Math.min(maxY, y));
        z = Math.max(minZ, Math.min(maxZ, z));
        
        // 根據面調整坐標
        switch (face) {
            case NORTH -> z = minZ;
            case SOUTH -> z = maxZ;
            case EAST -> x = maxX;
            case WEST -> x = minX;
            case TOP -> y = maxY;
            case BOTTOM -> y = minY;
        }
        
        return new Location(world, x, y, z);
    }
    
    /**
     * 獲取玩家與領地相交的牆面列表
     */
    public List<WallFace> getIntersectingFaces(Location playerLocation, int renderDistance) {
        List<WallFace> faces = new ArrayList<>();
        
        if (!playerLocation.getWorld().equals(world)) return faces;
        
        double x = playerLocation.getX();
        double y = playerLocation.getY();
        double z = playerLocation.getZ();
        
        // 檢查每個面是否在渲染距離內
        if (Math.abs(x - minX) <= renderDistance) faces.add(WallFace.WEST);
        if (Math.abs(x - maxX) <= renderDistance) faces.add(WallFace.EAST);
        if (Math.abs(y - minY) <= renderDistance) faces.add(WallFace.BOTTOM);
        if (Math.abs(y - maxY) <= renderDistance) faces.add(WallFace.TOP);
        if (Math.abs(z - minZ) <= renderDistance) faces.add(WallFace.NORTH);
        if (Math.abs(z - maxZ) <= renderDistance) faces.add(WallFace.SOUTH);
        
        return faces;
    }
    
    /**
     * 獲取牆面上半徑內的粒子點
     * @param center 中心點
     * @param radius 半徑
     * @param face 牆面
     * @param spacing 粒子間距
     */
    public List<Location> getWallPointsInRadius(Location center, double radius, WallFace face, double spacing) {
        List<Location> points = new ArrayList<>();
        
        if (!center.getWorld().equals(world)) return points;
        
        // 決定牆面的邊界
        int faceMinX, faceMaxX, faceMinY, faceMaxY, faceMinZ, faceMaxZ;
        
        switch (face) {
            case NORTH:
                faceMinX = minX; faceMaxX = maxX;
                faceMinY = minY; faceMaxY = maxY;
                faceMinZ = faceMaxZ = minZ;
                break;
            case SOUTH:
                faceMinX = minX; faceMaxX = maxX;
                faceMinY = minY; faceMaxY = maxY;
                faceMinZ = faceMaxZ = maxZ;
                break;
            case EAST:
                faceMinX = faceMaxX = maxX;
                faceMinY = minY; faceMaxY = maxY;
                faceMinZ = minZ; faceMaxZ = maxZ;
                break;
            case WEST:
                faceMinX = faceMaxX = minX;
                faceMinY = minY; faceMaxY = maxY;
                faceMinZ = minZ; faceMaxZ = maxZ;
                break;
            case TOP:
                faceMinX = minX; faceMaxX = maxX;
                faceMinY = faceMaxY = maxY;
                faceMinZ = minZ; faceMaxZ = maxZ;
                break;
            case BOTTOM:
                faceMinX = minX; faceMaxX = maxX;
                faceMinY = faceMaxY = minY;
                faceMinZ = minZ; faceMaxZ = maxZ;
                break;
            default:
                return points;
        }
        
        // 計算中心點附近的範圍
        int startX = Math.max(faceMinX, (int) (center.getX() - radius));
        int endX = Math.min(faceMaxX, (int) (center.getX() + radius));
        int startY = Math.max(faceMinY, (int) (center.getY() - radius));
        int endY = Math.min(faceMaxY, (int) (center.getY() + radius));
        int startZ = Math.max(faceMinZ, (int) (center.getZ() - radius));
        int endZ = Math.min(faceMaxZ, (int) (center.getZ() + radius));
        
        // 根據間隔生成點
        for (double x = startX; x <= endX; x += spacing) {
            for (double y = startY; y <= endY; y += spacing) {
                for (double z = startZ; z <= endZ; z += spacing) {
                    Location loc = new Location(world, x, y, z);
                    double distance = center.distance(loc);
                    if (distance <= radius) {
                        points.add(loc);
                    }
                }
            }
        }
        
        return points;
    }
    
    /**
     * 獲取 WALL 模式下的粒子點
     */
    public List<Location> getWallModePoints(Location playerLocation, int renderDistance, double spacing) {
        List<Location> points = new ArrayList<>();
        
        // 檢查玩家是否在領地內
        boolean isInside = isPlayerInside(playerLocation);
        
        if (isInside) {
            // 玩家在領地內，顯示在渲染距離內的牆面
            List<WallFace> faces = getIntersectingFaces(playerLocation, renderDistance);
            
            for (WallFace face : faces) {
                // 計算玩家與該面最近的交點
                Location nearestPoint = getNearestPointOnFace(playerLocation, face);
                // 獲取該點半徑3格範圍內的粒子位置
                points.addAll(getWallPointsInRadius(nearestPoint, 3.0, face, spacing));
            }
        } else {
            // 玩家在領地外，只顯示最近點周圍
            Location nearestPoint = getNearestPoint(playerLocation);
            
            // 確定最近點在哪個面上
            double distToWest = Math.abs(nearestPoint.getX() - minX);
            double distToEast = Math.abs(nearestPoint.getX() - maxX);
            double distToBottom = Math.abs(nearestPoint.getY() - minY);
            double distToTop = Math.abs(nearestPoint.getY() - maxY);
            double distToNorth = Math.abs(nearestPoint.getZ() - minZ);
            double distToSouth = Math.abs(nearestPoint.getZ() - maxZ);
            
            double minDist = Math.min(Math.min(Math.min(distToWest, distToEast), Math.min(distToBottom, distToTop)), Math.min(distToNorth, distToSouth));
            
            if (minDist == distToWest) {
                points.addAll(getWallPointsInRadius(nearestPoint, 3.0, WallFace.WEST, spacing));
            } else if (minDist == distToEast) {
                points.addAll(getWallPointsInRadius(nearestPoint, 3.0, WallFace.EAST, spacing));
            } else if (minDist == distToBottom) {
                points.addAll(getWallPointsInRadius(nearestPoint, 3.0, WallFace.BOTTOM, spacing));
            } else if (minDist == distToTop) {
                points.addAll(getWallPointsInRadius(nearestPoint, 3.0, WallFace.TOP, spacing));
            } else if (minDist == distToNorth) {
                points.addAll(getWallPointsInRadius(nearestPoint, 3.0, WallFace.NORTH, spacing));
            } else if (minDist == distToSouth) {
                points.addAll(getWallPointsInRadius(nearestPoint, 3.0, WallFace.SOUTH, spacing));
            }
        }
        
        return points;
    }
}
