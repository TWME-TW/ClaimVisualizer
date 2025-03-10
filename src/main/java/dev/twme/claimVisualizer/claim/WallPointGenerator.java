package dev.twme.claimVisualizer.claim;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 負責生成領地牆面的粒子點
 */
public class WallPointGenerator {
    private final ClaimBoundary boundary;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final World world;
    
    public WallPointGenerator(ClaimBoundary boundary) {
        this.boundary = boundary;
        this.minX = boundary.getMinX();
        this.minY = boundary.getMinY();
        this.minZ = boundary.getMinZ();
        this.maxX = boundary.getMaxX();
        this.maxY = boundary.getMaxY();
        this.maxZ = boundary.getMaxZ();
        this.world = boundary.getWorld();
    }
    
    /**
     * 獲取牆面上半徑內的粒子點
     * @param center 中心點
     * @param radius 半徑
     * @param face 牆面
     * @param spacing 粒子間距
     */
    public List<Location> getWallPointsInRadius(Location center, double radius, ClaimBoundary.WallFace face, double spacing) {
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
     * 獲取 WALL 模式下的粒子點，新參數 wallRadius 由設定檔控制
     */
    public List<Location> getWallModePoints(Location playerLocation, int renderDistance, double spacing, double wallRadius) {
        List<Location> points = new ArrayList<>();
        
        boolean isInside = boundary.isPlayerInside(playerLocation);
        
        if (isInside) {
            List<ClaimBoundary.WallFace> faces = boundary.getIntersectingFaces(playerLocation, renderDistance);
            for (ClaimBoundary.WallFace face : faces) {
                Location nearestPoint = boundary.getNearestPointOnFace(playerLocation, face);
                points.addAll(getWallPointsInRadius(nearestPoint, wallRadius, face, spacing));
            }
        } else {
            // 當玩家在領地外：根據玩家與領地最近點關係判斷要顯示的牆面個數
            Location nearestPoint = boundary.getNearestPoint(playerLocation);
            double tolerance = 1.0; // 定義允許偏差
            Set<ClaimBoundary.WallFace> candidateFaces = new HashSet<>();
            if (playerLocation.getX() < minX + tolerance) candidateFaces.add(ClaimBoundary.WallFace.WEST);
            if (playerLocation.getX() > maxX - tolerance) candidateFaces.add(ClaimBoundary.WallFace.EAST);
            if (playerLocation.getY() < minY + tolerance) candidateFaces.add(ClaimBoundary.WallFace.BOTTOM);
            if (playerLocation.getY() > maxY - tolerance) candidateFaces.add(ClaimBoundary.WallFace.TOP);
            if (playerLocation.getZ() < minZ + tolerance) candidateFaces.add(ClaimBoundary.WallFace.NORTH);
            if (playerLocation.getZ() > maxZ - tolerance) candidateFaces.add(ClaimBoundary.WallFace.SOUTH);
            
            if (candidateFaces.isEmpty()) {
                double distToWest = Math.abs(nearestPoint.getX() - minX);
                double distToEast = Math.abs(nearestPoint.getX() - maxX);
                double distToBottom = Math.abs(nearestPoint.getY() - minY);
                double distToTop = Math.abs(nearestPoint.getY() - maxY);
                double distToNorth = Math.abs(nearestPoint.getZ() - minZ);
                double distToSouth = Math.abs(nearestPoint.getZ() - maxZ);
                double minDist = Math.min(Math.min(Math.min(distToWest, distToEast), Math.min(distToBottom, distToTop)), Math.min(distToNorth, distToSouth));
                if (minDist == distToWest) candidateFaces.add(ClaimBoundary.WallFace.WEST);
                else if (minDist == distToEast) candidateFaces.add(ClaimBoundary.WallFace.EAST);
                else if (minDist == distToBottom) candidateFaces.add(ClaimBoundary.WallFace.BOTTOM);
                else if (minDist == distToTop) candidateFaces.add(ClaimBoundary.WallFace.TOP);
                else if (minDist == distToNorth) candidateFaces.add(ClaimBoundary.WallFace.NORTH);
                else if (minDist == distToSouth) candidateFaces.add(ClaimBoundary.WallFace.SOUTH);
            }
            
            for (ClaimBoundary.WallFace face : candidateFaces) {
                points.addAll(getWallPointsInRadius(nearestPoint, wallRadius, face, spacing));
            }
        }
        return points;
    }
    
    /**
     * 檢查位置是否接近邊緣，用於角落檢測
     */
    private int countNearEdges(double x, double y, double z, double edgeDistance) {
        int edgeCount = 0;
        
        // 檢查X軸的邊緣
        if (Math.abs(x - minX) < edgeDistance || Math.abs(x - maxX) < edgeDistance) {
            edgeCount++;
        }
        
        // 檢查Y軸的邊緣
        if (Math.abs(y - minY) < edgeDistance || Math.abs(y - maxY) < edgeDistance) {
            edgeCount++;
        }
        
        // 檢查Z軸的邊緣
        if (Math.abs(z - minZ) < edgeDistance || Math.abs(z - maxZ) < edgeDistance) {
            edgeCount++;
        }
        
        return edgeCount;
    }
    
    /**
     * 判斷點是否在垂直邊上
     */
    private boolean isOnVerticalEdge(double x, double y, double z, double edgeDistance) {
        boolean onXEdge = Math.abs(x - minX) < edgeDistance || Math.abs(x - maxX) < edgeDistance;
        boolean onZEdge = Math.abs(z - minZ) < edgeDistance || Math.abs(z - maxZ) < edgeDistance;
        
        // 在垂直邊上需要同時滿足：1. 在X或Z的邊緣 2. 不在Y的邊緣上
        boolean notOnYEdge = Math.abs(y - minY) >= edgeDistance && Math.abs(y - maxY) >= edgeDistance;
        
        return (onXEdge || onZEdge) && notOnYEdge;
    }
    
    /**
     * 獲取牆面上半徑內的粒子點，並區分角落點和一般點
     */
    public List<ClaimBoundary.WallPoint> getWallPointsInRadiusWithCorners(Location center, double radius, ClaimBoundary.WallFace face, double spacing) {
        List<ClaimBoundary.WallPoint> points = new ArrayList<>();
        
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
        
        // 為了確保角落被渲染，我們計算出精確的角落位置
        Set<Location> cornerLocations = new HashSet<>();
        double edgeDistance = spacing / 2.0; // 用於判斷是否靠近邊緣
        
        // 添加面的所有角落
        if (face != ClaimBoundary.WallFace.NORTH && face != ClaimBoundary.WallFace.SOUTH) {
            if (startZ <= minZ && minZ <= endZ) {
                if (startX <= minX && minX <= endX) cornerLocations.add(new Location(world, minX, startY, minZ));
                if (startX <= maxX && maxX <= endX) cornerLocations.add(new Location(world, maxX, startY, minZ));
                if (startY <= maxY && maxY <= endY) {
                    if (startX <= minX && minX <= endX) cornerLocations.add(new Location(world, minX, maxY, minZ));
                    if (startX <= maxX && maxX <= endX) cornerLocations.add(new Location(world, maxX, maxY, minZ));
                }
            }
            
            if (startZ <= maxZ && maxZ <= endZ) {
                if (startX <= minX && minX <= endX) cornerLocations.add(new Location(world, minX, startY, maxZ));
                if (startX <= maxX && maxX <= endX) cornerLocations.add(new Location(world, maxX, startY, maxZ));
                if (startY <= maxY && maxY <= endY) {
                    if (startX <= minX && minX <= endX) cornerLocations.add(new Location(world, minX, maxY, maxZ));
                    if (startX <= maxX && maxX <= endX) cornerLocations.add(new Location(world, maxX, maxY, maxZ));
                }
            }
        }
        
        if (face != ClaimBoundary.WallFace.EAST && face != ClaimBoundary.WallFace.WEST) {
            if (startX <= minX && minX <= endX) {
                if (startZ <= minZ && minZ <= endZ) cornerLocations.add(new Location(world, minX, startY, minZ));
                if (startZ <= maxZ && maxZ <= endZ) cornerLocations.add(new Location(world, minX, startY, maxZ));
                if (startY <= maxY && maxY <= endY) {
                    if (startZ <= minZ && minZ <= endZ) cornerLocations.add(new Location(world, minX, maxY, minZ));
                    if (startZ <= maxZ && maxZ <= endZ) cornerLocations.add(new Location(world, minX, maxY, maxZ));
                }
            }
            
            if (startX <= maxX && maxX <= endX) {
                if (startZ <= minZ && minZ <= endZ) cornerLocations.add(new Location(world, maxX, startY, minZ));
                if (startZ <= maxZ && maxZ <= endZ) cornerLocations.add(new Location(world, maxX, startY, maxZ));
                if (startY <= maxY && maxY <= endY) {
                    if (startZ <= minZ && minZ <= endZ) cornerLocations.add(new Location(world, maxX, maxY, minZ));
                    if (startZ <= maxZ && maxZ <= endZ) cornerLocations.add(new Location(world, maxX, maxY, maxZ));
                }
            }
        }
        
        // 首先將所有角落點加入列表
        for (Location corner : cornerLocations) {
            if (corner.distance(center) <= radius) {
                points.add(new ClaimBoundary.WallPoint(corner, true, false));
            }
        }
        
        // 根據間隔生成其他點
        for (double x = startX; x <= endX; x += spacing) {
            for (double y = startY; y <= endY; y += spacing) {
                for (double z = startZ; z <= endZ; z += spacing) {
                    Location loc = new Location(world, x, y, z);
                    
                    // 檢查點是否靠近角落
                    boolean isNearCorner = false;
                    for (Location corner : cornerLocations) {
                        if (Math.abs(corner.getX() - x) < edgeDistance &&
                            Math.abs(corner.getY() - y) < edgeDistance &&
                            Math.abs(corner.getZ() - z) < edgeDistance) {
                            isNearCorner = true;
                            break;
                        }
                    }
                    
                    // 如果點靠近角落，跳過，因為我們已經添加了精確的角落點
                    if (isNearCorner) continue;
                    
                    double distance = center.distance(loc);
                    if (distance <= radius) {
                        // 計算該點是否在邊緣，以及是否是垂直邊
                        boolean isVertical = isOnVerticalEdge(x, y, z, edgeDistance);
                        int edgeCount = countNearEdges(x, y, z, edgeDistance);
                        boolean isCorner = edgeCount >= 2;
                        
                        points.add(new ClaimBoundary.WallPoint(loc, isCorner, isVertical));
                    }
                }
            }
        }
        
        return points;
    }
    
    /**
     * 獲取 WALL 模式下的粒子點，區分角落點
     */
    public List<ClaimBoundary.WallPoint> getWallModePointsWithCorners(Location playerLocation, int renderDistance, double spacing, double wallRadius) {
        List<ClaimBoundary.WallPoint> points = new ArrayList<>();
        
        boolean isInside = boundary.isPlayerInside(playerLocation);
        
        if (isInside) {
            List<ClaimBoundary.WallFace> faces = boundary.getIntersectingFaces(playerLocation, renderDistance);
            for (ClaimBoundary.WallFace face : faces) {
                Location nearestPoint = boundary.getNearestPointOnFace(playerLocation, face);
                points.addAll(getWallPointsInRadiusWithCorners(nearestPoint, wallRadius, face, spacing));
            }
        } else {
            // 當玩家在領地外：根據玩家與領地最近點關係判斷要顯示的牆面個數
            Location nearestPoint = boundary.getNearestPoint(playerLocation);
            double tolerance = 1.0; // 定義允許偏差
            Set<ClaimBoundary.WallFace> candidateFaces = new HashSet<>();
            if (playerLocation.getX() < minX + tolerance) candidateFaces.add(ClaimBoundary.WallFace.WEST);
            if (playerLocation.getX() > maxX - tolerance) candidateFaces.add(ClaimBoundary.WallFace.EAST);
            if (playerLocation.getY() < minY + tolerance) candidateFaces.add(ClaimBoundary.WallFace.BOTTOM);
            if (playerLocation.getY() > maxY - tolerance) candidateFaces.add(ClaimBoundary.WallFace.TOP);
            if (playerLocation.getZ() < minZ + tolerance) candidateFaces.add(ClaimBoundary.WallFace.NORTH);
            if (playerLocation.getZ() > maxZ - tolerance) candidateFaces.add(ClaimBoundary.WallFace.SOUTH);
            
            if (candidateFaces.isEmpty()) {
                double distToWest = Math.abs(nearestPoint.getX() - minX);
                double distToEast = Math.abs(nearestPoint.getX() - maxX);
                double distToBottom = Math.abs(nearestPoint.getY() - minY);
                double distToTop = Math.abs(nearestPoint.getY() - maxY);
                double distToNorth = Math.abs(nearestPoint.getZ() - minZ);
                double distToSouth = Math.abs(nearestPoint.getZ() - maxZ);
                double minDist = Math.min(Math.min(Math.min(distToWest, distToEast), Math.min(distToBottom, distToTop)), Math.min(distToNorth, distToSouth));
                if (minDist == distToWest) candidateFaces.add(ClaimBoundary.WallFace.WEST);
                else if (minDist == distToEast) candidateFaces.add(ClaimBoundary.WallFace.EAST);
                else if (minDist == distToBottom) candidateFaces.add(ClaimBoundary.WallFace.BOTTOM);
                else if (minDist == distToTop) candidateFaces.add(ClaimBoundary.WallFace.TOP);
                else if (minDist == distToNorth) candidateFaces.add(ClaimBoundary.WallFace.NORTH);
                else if (minDist == distToSouth) candidateFaces.add(ClaimBoundary.WallFace.SOUTH);
            }
            
            for (ClaimBoundary.WallFace face : candidateFaces) {
                points.addAll(getWallPointsInRadiusWithCorners(nearestPoint, wallRadius, face, spacing));
            }
        }
        return points;
    }

    /**
     * 獲取 WALL 模式下的粒子點，基於視線角度調整半徑
     * @param playerLocation 玩家位置
     * @param playerDirection 玩家視線方向
     * @param renderDistance 渲染距離
     * @param spacing 粒子間距
     * @param baseRadius 基礎半徑
     * @param viewAngleEffect 視角影響係數(0-1)
     * @return 帶有角落資訊的點列表
     */
    public List<ClaimBoundary.WallPoint> getWallModePointsWithViewAngle(
            Location playerLocation, 
            Vector playerDirection, 
            int renderDistance, 
            double spacing, 
            double baseRadius,
            double viewAngleEffect) {
        
        List<ClaimBoundary.WallPoint> points = new ArrayList<>();
        boolean isInside = boundary.isPlayerInside(playerLocation);
        
        if (isInside) {
            List<ClaimBoundary.WallFace> faces = boundary.getIntersectingFaces(playerLocation, renderDistance);
            for (ClaimBoundary.WallFace face : faces) {
                Location nearestPoint = boundary.getNearestPointOnFace(playerLocation, face);
                
                // 計算視線與該點形成的角度
                double angle = getAngleBetween(playerDirection, nearestPoint.clone().subtract(playerLocation).toVector());
                
                // 基於角度調整半徑 (0度使用全半徑，90度以上使用最小半徑)
                double adjustedRadius = calculateRadiusFromAngle(baseRadius, angle, viewAngleEffect);
                
                points.addAll(getWallPointsInRadiusWithCorners(nearestPoint, adjustedRadius, face, spacing));
            }
        } else {
            // 當玩家在領地外：根據玩家與領地最近點關係判斷要顯示的牆面個數
            Location nearestPoint = boundary.getNearestPoint(playerLocation);
            double tolerance = 1.0; // 定義允許偏差
            Set<ClaimBoundary.WallFace> candidateFaces = new HashSet<>();
            
            if (playerLocation.getX() < minX + tolerance) candidateFaces.add(ClaimBoundary.WallFace.WEST);
            if (playerLocation.getX() > maxX - tolerance) candidateFaces.add(ClaimBoundary.WallFace.EAST);
            if (playerLocation.getY() < minY + tolerance) candidateFaces.add(ClaimBoundary.WallFace.BOTTOM);
            if (playerLocation.getY() > maxY - tolerance) candidateFaces.add(ClaimBoundary.WallFace.TOP);
            if (playerLocation.getZ() < minZ + tolerance) candidateFaces.add(ClaimBoundary.WallFace.NORTH);
            if (playerLocation.getZ() > maxZ - tolerance) candidateFaces.add(ClaimBoundary.WallFace.SOUTH);
            
            if (candidateFaces.isEmpty()) {
                double distToWest = Math.abs(nearestPoint.getX() - minX);
                double distToEast = Math.abs(nearestPoint.getX() - maxX);
                double distToBottom = Math.abs(nearestPoint.getY() - minY);
                double distToTop = Math.abs(nearestPoint.getY() - maxY);
                double distToNorth = Math.abs(nearestPoint.getZ() - minZ);
                double distToSouth = Math.abs(nearestPoint.getZ() - maxZ);
                double minDist = Math.min(Math.min(Math.min(distToWest, distToEast), Math.min(distToBottom, distToTop)), Math.min(distToNorth, distToSouth));
                
                if (minDist == distToWest) candidateFaces.add(ClaimBoundary.WallFace.WEST);
                else if (minDist == distToEast) candidateFaces.add(ClaimBoundary.WallFace.EAST);
                else if (minDist == distToBottom) candidateFaces.add(ClaimBoundary.WallFace.BOTTOM);
                else if (minDist == distToTop) candidateFaces.add(ClaimBoundary.WallFace.TOP);
                else if (minDist == distToNorth) candidateFaces.add(ClaimBoundary.WallFace.NORTH);
                else if (minDist == distToSouth) candidateFaces.add(ClaimBoundary.WallFace.SOUTH);
            }
            
            for (ClaimBoundary.WallFace face : candidateFaces) {
                Location faceCenter = boundary.getNearestPointOnFace(playerLocation, face);
                
                // 計算視線與該點形成的角度
                double angle = getAngleBetween(playerDirection, faceCenter.clone().subtract(playerLocation).toVector());
                
                // 基於角度調整半徑
                double adjustedRadius = calculateRadiusFromAngle(baseRadius, angle, viewAngleEffect);
                
                points.addAll(getWallPointsInRadiusWithCorners(faceCenter, adjustedRadius, face, spacing));
            }
        }
        
        return points;
    }

    /**
     * 計算兩個向量之間的角度（度數）
     */
    private double getAngleBetween(Vector v1, Vector v2) {
        // 確保向量已規一化
        Vector normalized1 = v1.clone().normalize();
        Vector normalized2 = v2.clone().normalize();
        
        // 計算角度（弧度）
        double dot = normalized1.dot(normalized2);
        // 限制在 [-1, 1] 範圍內
        dot = Math.max(-1, Math.min(1, dot));
        double angle = Math.acos(dot);
        
        // 轉換為度數
        return Math.toDegrees(angle);
    }

    /**
     * 根據視線角度計算調整後的半徑
     * @param baseRadius 基礎半徑
     * @param angle 視線與目標點的角度（度數）
     * @param effect 視角影響係數(0-1)
     * @return 調整後的半徑
     */
    private double calculateRadiusFromAngle(double baseRadius, double angle, double effect) {
        // 計算縮放係數：視線中心 100%，90度角減少到 (1-effect)%
        double scale = 1.0 - (Math.min(90.0, angle) / 90.0) * effect;
        return baseRadius * scale;
    }

    /**
     * 計算射線與領地盒體的交點
     * @param start 射線起點
     * @param direction 射線方向
     * @return 交點資訊，如果沒有交點則返回null
     */
    public RayIntersection calculateRayBoxIntersection(Location start, Vector direction) {
        // 確保方向向量已正規化
        direction = direction.clone().normalize();
        
        // 射線公式: p(t) = start + t * direction
        // 盒體範圍: (minX,minY,minZ) 到 (maxX,maxY,maxZ)
        
        // 計算射線與每個軸對齊平面的相交參數
        double tx1 = (minX - start.getX()) / direction.getX();
        double tx2 = (maxX - start.getX()) / direction.getX();
        double ty1 = (minY - start.getY()) / direction.getY();
        double ty2 = (maxY - start.getY()) / direction.getY();
        double tz1 = (minZ - start.getZ()) / direction.getZ();
        double tz2 = (maxZ - start.getZ()) / direction.getZ();
        
        // 處理方向向量元素為0的情況（平行於軸）
        if (Math.abs(direction.getX()) < 1.0E-6) {
            tx1 = Double.NEGATIVE_INFINITY;
            tx2 = Double.POSITIVE_INFINITY;
        }
        if (Math.abs(direction.getY()) < 1.0E-6) {
            ty1 = Double.NEGATIVE_INFINITY;
            ty2 = Double.POSITIVE_INFINITY;
        }
        if (Math.abs(direction.getZ()) < 1.0E-6) {
            tz1 = Double.NEGATIVE_INFINITY;
            tz2 = Double.POSITIVE_INFINITY;
        }
        
        // 確保t1 <= t2
        if (tx1 > tx2) {
            double temp = tx1;
            tx1 = tx2;
            tx2 = temp;
        }
        if (ty1 > ty2) {
            double temp = ty1;
            ty1 = ty2;
            ty2 = temp;
        }
        if (tz1 > tz2) {
            double temp = tz1;
            tz1 = tz2;
            tz2 = temp;
        }
        
        // 找到最大的tmin和最小的tmax
        double tmin = Math.max(Math.max(tx1, ty1), tz1);
        double tmax = Math.min(Math.min(tx2, ty2), tz2);
        
        // 如果tmax < tmin或tmax < 0，則沒有有效交點
        if (tmax < tmin || tmax < 0) {
            return null;
        }
        
        // 使用最近的交點（如果tmin > 0）
        double t = (tmin > 0) ? tmin : tmax;
        
        // 計算交點坐標
        Location intersection = start.clone().add(direction.clone().multiply(t));
        
        // 判斷交點在哪個面上
        ClaimBoundary.WallFace face = null;
        double epsilon = 1.0E-6;
        
        if (Math.abs(intersection.getX() - minX) < epsilon) {
            face = ClaimBoundary.WallFace.WEST;
        } else if (Math.abs(intersection.getX() - maxX) < epsilon) {
            face = ClaimBoundary.WallFace.EAST;
        } else if (Math.abs(intersection.getY() - minY) < epsilon) {
            face = ClaimBoundary.WallFace.BOTTOM;
        } else if (Math.abs(intersection.getY() - maxY) < epsilon) {
            face = ClaimBoundary.WallFace.TOP;
        } else if (Math.abs(intersection.getZ() - minZ) < epsilon) {
            face = ClaimBoundary.WallFace.NORTH;
        } else if (Math.abs(intersection.getZ() - maxZ) < epsilon) {
            face = ClaimBoundary.WallFace.SOUTH;
        }
        
        if (face == null) {
            // 如果因為浮點誤差或其他原因無法判斷交點在哪個面上，使用最接近的面
            double distToWest = Math.abs(intersection.getX() - minX);
            double distToEast = Math.abs(intersection.getX() - maxX);
            double distToBottom = Math.abs(intersection.getY() - minY);
            double distToTop = Math.abs(intersection.getY() - maxY);
            double distToNorth = Math.abs(intersection.getZ() - minZ);
            double distToSouth = Math.abs(intersection.getZ() - maxZ);
            
            double minDist = Math.min(Math.min(Math.min(distToWest, distToEast), 
                    Math.min(distToBottom, distToTop)), Math.min(distToNorth, distToSouth));
            
            if (minDist == distToWest) face = ClaimBoundary.WallFace.WEST;
            else if (minDist == distToEast) face = ClaimBoundary.WallFace.EAST;
            else if (minDist == distToBottom) face = ClaimBoundary.WallFace.BOTTOM;
            else if (minDist == distToTop) face = ClaimBoundary.WallFace.TOP;
            else if (minDist == distToNorth) face = ClaimBoundary.WallFace.NORTH;
            else face = ClaimBoundary.WallFace.SOUTH;
        }
        
        return new RayIntersection(intersection, face, t);
    }
    
    /**
     * 使用射線檢測與盒體交點的 WALL 模式渲染點生成
     * @param playerLocation 玩家位置
     * @param playerDirection 玩家視線方向
     * @param renderDistance 渲染距離
     * @param spacing 粒子間距
     * @param wallRadius 牆面渲染半徑
     * @return 牆面上的點列表（帶角落資訊）
     */
    public List<ClaimBoundary.WallPoint> getWallModePointsWithRaycast(
            Location playerLocation, 
            Vector playerDirection, 
            int renderDistance, 
            double spacing, 
            double wallRadius) {
            
        List<ClaimBoundary.WallPoint> points = new ArrayList<>();
        
        // 計算射線檢測結果
        RayIntersection intersection = calculateRayBoxIntersection(playerLocation, playerDirection);
        
        // 如果有有效的交點且在渲染距離內
        if (intersection != null && intersection.getDistance() <= renderDistance) {
            // 使用交點作為渲染中心
            points.addAll(getWallPointsInRadiusWithCorners(
                    intersection.getLocation(), 
                    wallRadius, 
                    intersection.getFace(), 
                    spacing));
        }
        
        return points;
    }
    
    /**
     * 射線與盒體交點資訊類
     */
    public static class RayIntersection {
        private final Location location; // 交點位置
        private final ClaimBoundary.WallFace face; // 交點所在面
        private final double distance; // 交點沿射線的距離參數
        
        public RayIntersection(Location location, ClaimBoundary.WallFace face, double distance) {
            this.location = location;
            this.face = face;
            this.distance = distance;
        }
        
        public Location getLocation() {
            return location;
        }
        
        public ClaimBoundary.WallFace getFace() {
            return face;
        }
        
        public double getDistance() {
            return distance;
        }
    }
}
