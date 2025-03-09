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
     * 檢查世界是否啟用 GriefDefender
     */
    public boolean isWorldEnabled(World world) {
        if (world == null) return false;
        
        try {
            return GriefDefender.getCore().isEnabled(world.getUID());
        } catch (Exception e) {
            plugin.getLogger().warning("檢查世界 " + world.getName() + " 是否啟用 GriefDefender 時發生錯誤: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 取得玩家周圍的領地
     */
    public Set<ClaimBoundary> getNearbyClaims(Player player) {
        World world = player.getWorld();
        UUID worldUUID = world.getUID();
        
        // 檢查世界是否啟用 GriefDefender
        if (!isWorldEnabled(world)) {
            return new HashSet<>();
        }
        
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
        // 檢查世界是否啟用 GriefDefender
        if (!isWorldEnabled(world)) {
            return;
        }
        
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
            
            // 建立邊界物件 - 獲取完整的 3D 座標
            int minX = claim.getLesserBoundaryCorner().getX();
            int minY = claim.getLesserBoundaryCorner().getY();
            int minZ = claim.getLesserBoundaryCorner().getZ();
            int maxX = claim.getGreaterBoundaryCorner().getX();
            int maxY = claim.getGreaterBoundaryCorner().getY();
            int maxZ = claim.getGreaterBoundaryCorner().getZ();
            
            // 修正：對 maxX、maxY 和 maxZ 加 1，以包含最後一個方塊的完整體積
            ClaimBoundary boundary = new ClaimBoundary(claimId, ownerId, type, world, minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
            worldClaims.put(claimId, boundary);
        }
        
        claimCache.put(worldUUID, worldClaims);
        lastCacheUpdateTime.put(worldUUID, System.currentTimeMillis());
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
