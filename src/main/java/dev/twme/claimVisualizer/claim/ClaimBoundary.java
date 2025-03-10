package dev.twme.claimVisualizer.claim;

import dev.twme.claimVisualizer.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ClaimBoundary {

    private final UUID claimId;
    private final UUID ownerId;
    private final String type;
    private final World world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final WallPointGenerator wallPointGenerator;
    
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
        this.wallPointGenerator = new WallPointGenerator(this);
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
        return wallPointGenerator.getWallPointsInRadius(center, radius, face, spacing);
    }
    
    /**
     * 獲取 WALL 模式下的粒子點，新參數 wallRadius 由設定檔控制
     */
    public List<Location> getWallModePoints(Location playerLocation, int renderDistance, double spacing, double wallRadius) {
        return wallPointGenerator.getWallModePoints(playerLocation, renderDistance, spacing, wallRadius);
    }
    
    /**
     * 獲取 OUTLINE 模式下附近的邊界點，只顯示玩家附近的水平輪廓
     * @param playerLocation 玩家位置
     * @param renderDistance 渲染距離
     * @param spacing 粒子間距
     * @param radius 顯示半徑
     */
    public List<Location> getOutlineNearbyPoints(Location playerLocation, int renderDistance, double spacing, double radius) {
        List<Location> points = new ArrayList<>();
        
        // 先計算玩家與領地的最近點
        Location nearestPoint = getNearestPoint(playerLocation);
        
        // 檢查最近點是否在渲染距離內
        if (nearestPoint.distance(playerLocation) > renderDistance) {
            return points;
        }
        
        int playerY = playerLocation.getBlockY();
        
        // 只獲取玩家所在高度的水平線
        List<Location> bottomPoints = getBottomPoints(spacing);
        List<Location> topPoints = getTopPoints(spacing);
        List<Location> horizontalPoints = getHorizontalPoints(spacing, playerY);
        
        // 添加所有水平線點
        List<Location> allPoints = new ArrayList<>();
        allPoints.addAll(horizontalPoints);
        
        // 如果玩家離底部或頂部很近，也添加那些點
        if (Math.abs(playerY - minY) <= 3) {
            allPoints.addAll(bottomPoints);
        }
        
        if (Math.abs(playerY - maxY) <= 3) {
            allPoints.addAll(topPoints);
        }
        
        // 只保留距離最近點指定半徑內的點
        for (Location loc : allPoints) {
            if (loc.distance(nearestPoint) <= radius) {
                points.add(loc);
            }
        }
        
        return points;
    }
    
    /**
     * 獲取指定垂直範圍內的邊界點
     * @param part 領地部分
     * @param spacing 粒子間距
     * @param playerY 玩家Y座標
     * @param verticalRange 垂直渲染範圍
     * @return 指定範圍內的點列表
     */
    public List<Location> getPointsInVerticalRange(ConfigManager.ClaimPart part, double spacing, int playerY, int verticalRange) {
        List<Location> allPoints = getPointsForPart(part, spacing, playerY);
        List<Location> filteredPoints = new ArrayList<>();
        
        // 計算垂直範圍的上下限
        int minRenderY = playerY - verticalRange;
        int maxRenderY = playerY + verticalRange;
        
        // 過濾在垂直範圍內的點
        for (Location loc : allPoints) {
            int y = loc.getBlockY();
            if (y >= minRenderY && y <= maxRenderY) {
                filteredPoints.add(loc);
            }
        }
        
        return filteredPoints;
    }

    /**
     * 帶有角落資訊的點位置
     */
    public static class WallPoint {
        private final Location location;
        private final boolean isCorner;
        private final boolean isVertical;
        
        public WallPoint(Location location, boolean isCorner, boolean isVertical) {
            this.location = location;
            this.isCorner = isCorner;
            this.isVertical = isVertical;
        }
        
        public Location getLocation() {
            return location;
        }
        
        public boolean isCorner() {
            return isCorner;
        }
        
        public boolean isVertical() {
            return isVertical;
        }
    }
    
    /**
     * 獲取牆面上半徑內的粒子點，並區分角落點和一般點
     * @param center 中心點
     * @param radius 半徑
     * @param face 牆面
     * @param spacing 粒子間距
     * @return 帶有角落資訊的點列表
     */
    public List<WallPoint> getWallPointsInRadiusWithCorners(Location center, double radius, WallFace face, double spacing) {
        return wallPointGenerator.getWallPointsInRadiusWithCorners(center, radius, face, spacing);
    }
    
    /**
     * 獲取 WALL 模式下的粒子點，區分角落點
     */
    public List<WallPoint> getWallModePointsWithCorners(Location playerLocation, int renderDistance, double spacing, double wallRadius) {
        return wallPointGenerator.getWallModePointsWithCorners(playerLocation, renderDistance, spacing, wallRadius);
    }

    /**
     * 獲取考慮視線角度的 WALL 模式粒子點
     * @param playerLocation 玩家位置
     * @param playerDirection 玩家視線方向
     * @param renderDistance 渲染距離
     * @param spacing 粒子間距
     * @param baseRadius 基礎半徑
     * @param viewAngleEffect 視角影響係數
     * @return 帶有角落資訊的點列表
     */
    public List<WallPoint> getWallModePointsWithViewAngle(
            Location playerLocation, 
            Vector playerDirection, 
            int renderDistance, 
            double spacing, 
            double baseRadius,
            double viewAngleEffect) {
        return wallPointGenerator.getWallModePointsWithViewAngle(
            playerLocation, playerDirection, renderDistance, spacing, baseRadius, viewAngleEffect);
    }
    
    /**
     * 使用射線檢測獲取 WALL 模式下的粒子點
     * @param playerLocation 玩家位置
     * @param playerDirection 玩家視線方向
     * @param renderDistance 渲染距離
     * @param spacing 粒子間距
     * @param wallRadius 牆面渲染半徑
     * @return 帶有角落資訊的點列表
     */
    public List<WallPoint> getWallModePointsWithRaycast(
            Location playerLocation, 
            Vector playerDirection, 
            int renderDistance, 
            double spacing, 
            double wallRadius) {
        return wallPointGenerator.getWallModePointsWithRaycast(
                playerLocation, playerDirection, renderDistance, spacing, wallRadius);
    }
}
