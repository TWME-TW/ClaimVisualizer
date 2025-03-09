package dev.twme.claimVisualizer;

import dev.twme.claimVisualizer.claim.ClaimManager;
import dev.twme.claimVisualizer.command.VisualizerCommand;
import dev.twme.claimVisualizer.config.ConfigManager;
import dev.twme.claimVisualizer.language.LanguageManager;
import dev.twme.claimVisualizer.listener.EventListener;
import dev.twme.claimVisualizer.player.PlayerSession;
import dev.twme.claimVisualizer.render.ParticleRenderer;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimVisualizer extends JavaPlugin {

    private ConfigManager configManager;
    private ClaimManager claimManager;
    private ParticleRenderer particleRenderer;
    private LanguageManager languageManager;
    
    @Override
    public void onEnable() {
        // 初始化配置管理器
        configManager = new ConfigManager(this);
        
        // 初始化語言管理器
        languageManager = new LanguageManager(this);
        
        // 初始化領地管理器
        claimManager = new ClaimManager(this);
        
        // 初始化粒子渲染器
        particleRenderer = new ParticleRenderer(this, claimManager);
        
        // 註冊命令
        VisualizerCommand command = new VisualizerCommand(this);
        getCommand("claimvisual").setExecutor(command);
        getCommand("claimvisual").setTabCompleter(command);
        
        // 註冊事件監聽器
        getServer().getPluginManager().registerEvents(
                new EventListener(this, particleRenderer), this);
        
        // 啟動渲染排程任務
        particleRenderer.startRenderTask();
        
        getLogger().info("ClaimVisualizer 插件已啟用！");
    }

    @Override
    public void onDisable() {
        // 停止渲染任務
        if (particleRenderer != null) {
            particleRenderer.stopRenderTask();
        }
        
        // 清理玩家會話
        PlayerSession.cleanupSessions();
        
        // 清除快取資料
        if (claimManager != null) {
            claimManager.clearAllCache();
        }
        
        getLogger().info("ClaimVisualizer 插件已停用！");
    }
    
    /**
     * 重新載入插件配置
     */
    public void reloadPluginConfig() {
        // 重新載入配置
        configManager.loadConfig();
        
        // 重新載入語言檔案
        languageManager.loadLanguages();
        
        // 清除領地快取
        claimManager.clearAllCache();
        
        // 重新啟動渲染任務
        particleRenderer.stopRenderTask();
        particleRenderer.startRenderTask();
        
        getLogger().info("ClaimVisualizer 設定已重新載入！");
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public ClaimManager getClaimManager() {
        return claimManager;
    }
    
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}
