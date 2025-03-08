package dev.twme.claimVisualizer.claim;

import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.ClaimTypes;
import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

public class ClaimManager {
    
    private final ClaimVisualizer plugin;
    private final ConfigManager configManager;
    
    // 快取機制
    private final Map<UUID, Map<UUID, ClaimBoundary>> claimCache = new HashMap<>();
    private final Map<UUID, Long> lastCacheUpdateTime = new HashMap<>();
    
    public ClaimManager(ClaimVisualizer plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }
    
    /**
     * 取得玩家周圍的領地
     */
    public Set<ClaimBoundary> getNearbyClaims(Player player) {
        World world = player.getWorld();
        UUID worldUUID = world.getUID();
        
        // 檢查快取是否需要更新
        long currentTime = System.currentTimeMillis();
        long cacheTimeout = configManager.getCacheTime() * 1000L;
        
        boolean needsUpdate = !lastCacheUpdateTime.containsKey(worldUUID) ||
                (currentTime - lastCacheUpdateTime.get(worldUUID)) > cacheTimeout;
                
        if (needsUpdate) {
            updateClaimCache(world);
        }
        
        Map<UUID, ClaimBoundary> worldClaims = claimCache.getOrDefault(worldUUID, new HashMap<>());
        Set<ClaimBoundary> nearbyClaims = new HashSet<>();
        
        int renderDistance = configManager.getRenderDistance();
        Location playerLoc = player.getLocation();
        
        for (ClaimBoundary boundary : worldClaims.values()) {
            if (boundary.isNearby(playerLoc, renderDistance)) {
                if (canPlayerSeeClaimType(player, boundary)) {
                    nearbyClaims.add(boundary);
                }
                
                // 限制一次顯示的領地數量
                if (nearbyClaims.size() >= configManager.getMaxClaims()) {
                    break;
                }
            }
        }
        
        return nearbyClaims;
    }
    
    private boolean canPlayerSeeClaimType(Player player, ClaimBoundary boundary) {
        UUID ownerId = boundary.getOwnerId();
        boolean isOwner = player.getUniqueId().equals(ownerId);
        String claimType = boundary.getType();
        
        if (isOwner && !configManager.showOwnClaims()) {
            return false;
        }
        
        if (!isOwner && !configManager.showOthersClaims()) {
            return false;
        }
        
        if (claimType.equals("admin") && !configManager.showAdminClaims()) {
            return false;
        }
        
        if (claimType.equals("town") && !configManager.showTownClaims()) {
            return false;
        }
        
        return true;
    }
    
    private void updateClaimCache(World world) {
        UUID worldUUID = world.getUID();
        Map<UUID, ClaimBoundary> worldClaims = new HashMap<>();
        
        // 獲取世界中的所有領地
        Collection<Claim> claims = GriefDefender.getCore().getClaimManager(world.getUID()).getWorldClaims();
        
        for (Claim claim : claims) {
            UUID claimId = claim.getUniqueId();
            UUID ownerId = claim.getOwnerUniqueId();
            
            String type = "basic";
            if (claim.getType() == ClaimTypes.ADMIN) {
                type = "admin";
            } else if (claim.getType() == ClaimTypes.TOWN) {
                type = "town";
            } else if (claim.isSubdivision()) {
                type = "subdivision";
            }
            
            // 建立邊界物件
            int minX = claim.getLesserBoundaryCorner().getX();
            int minZ = claim.getLesserBoundaryCorner().getZ();
            int maxX = claim.getGreaterBoundaryCorner().getX();
            int maxZ = claim.getGreaterBoundaryCorner().getZ();
            int y = calculateYLevel(world, minX, maxX, minZ, maxZ);
            
            ClaimBoundary boundary = new ClaimBoundary(claimId, ownerId, type, world, minX, y, minZ, maxX, y, maxZ);
            worldClaims.put(claimId, boundary);
        }
        
        claimCache.put(worldUUID, worldClaims);
        lastCacheUpdateTime.put(worldUUID, System.currentTimeMillis());
    }
    
    private int calculateYLevel(World world, int minX, int maxX, int minZ, int maxZ) {
        // 計算領地中心點位置
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        
        // 從最高層開始往下找到第一個非空氣方塊
        int y = world.getMaxHeight();
        while (y > world.getMinHeight()) {
            if (!world.getBlockAt(centerX, y, centerZ).getType().isAir()) {
                break;
            }
            y--;
        }
        
        return y + 1; // 回傳表面上方一格
    }
        
    /**
     * 清除世界的領地快取
     */
    public void clearCache(World world) {
        if (world == null) {
            claimCache.clear();
            lastCacheUpdateTime.clear();
        } else {
            claimCache.remove(world.getUID());
            lastCacheUpdateTime.remove(world.getUID());
        }
    }
    
    /**
     * 清除所有快取
     */
    public void clearAllCache() {
        claimCache.clear();
        lastCacheUpdateTime.clear();
    }
}
