package dev.twme.claimVisualizer.language;

import dev.twme.claimVisualizer.ClaimVisualizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LanguageManager {
    private final ClaimVisualizer plugin;
    private final Map<String, YamlConfiguration> languageFiles = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final String defaultLanguage = "en";
    private final Map<UUID, String> playerLanguages = new HashMap<>();

    public LanguageManager(ClaimVisualizer plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    /**
     * 載入所有語言檔案
     */
    public void loadLanguages() {
        languageFiles.clear();
        
        // 確保語言資料夾存在
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        
        // 儲存預設語言檔案
        saveDefaultLanguageFile("en.yml");
        saveDefaultLanguageFile("zh-tw.yml");
        
        // 載入所有語言檔案
        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String langCode = file.getName().replaceAll("\\.yml$", "");
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                
                // 嘗試讀取內建資源檔案作為預設值
                InputStream defaultLangStream = plugin.getResource("lang/" + file.getName());
                if (defaultLangStream != null) {
                    YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(defaultLangStream, StandardCharsets.UTF_8));
                    config.setDefaults(defaultConfig);
                }
                
                languageFiles.put(langCode, config);
                plugin.getLogger().info("Language loaded: " + langCode);
            }
        }
        
        // 確保預設語言存在
        if (!languageFiles.containsKey(defaultLanguage)) {
            plugin.getLogger().warning("Cannot find default language file: " + defaultLanguage + ".yml");
        }
    }
    
    /**
     * 儲存預設語言檔案
     */
    private void saveDefaultLanguageFile(String fileName) {
        File file = new File(plugin.getDataFolder() + "/lang", fileName);
        if (!file.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
    }
    
    /**
     * 設定玩家語言
     */
    public void setPlayerLanguage(UUID playerUuid, String langCode) {
        // 如果語言不存在，則使用預設語言
        if (!languageFiles.containsKey(langCode)) {
            if (langCode.contains("_")) {
                // 嘗試匹配主要語言代碼 (例如 zh_TW -> zh-tw)
                String mainCode = langCode.split("_")[0].toLowerCase();
                for (String code : languageFiles.keySet()) {
                    if (code.startsWith(mainCode)) {
                        playerLanguages.put(playerUuid, code);
                        return;
                    }
                }
            }
            playerLanguages.put(playerUuid, defaultLanguage);
        } else {
            playerLanguages.put(playerUuid, langCode);
        }
    }
    
    /**
     * 獲取玩家語言
     */
    public String getPlayerLanguage(UUID playerUuid) {
        return playerLanguages.getOrDefault(playerUuid, defaultLanguage);
    }
    
    /**
     * 獲取訊息並轉換為 MiniMessage 格式的 Component
     */
    public Component getMessage(String path, Player player) {
        String langCode = getPlayerLanguage(player.getUniqueId());
        return getMessageInLanguage(path, langCode);
    }
    
    /**
     * 獲取訊息並轉換為 MiniMessage 格式的 Component，支援佔位符替換
     * 例如：getMessage("command.hello", player, "世界")
     * 對應語言檔案中：command.hello: "<green>你好，{0}！"
     */
    public Component getMessage(String path, Player player, Object... args) {
        String langCode = getPlayerLanguage(player.getUniqueId());
        return getMessageInLanguage(path, langCode, args);
    }
    
    /**
     * 根據語言代碼獲取訊息
     */
    public Component getMessageInLanguage(String path, String langCode) {
        String message = getMessageString(path, langCode);
        return miniMessage.deserialize(message);
    }
    
    /**
     * 根據語言代碼獲取訊息，支援佔位符替換
     */
    public Component getMessageInLanguage(String path, String langCode, Object... args) {
        String message = getMessageString(path, langCode);
        
        // 替換佔位符 {0}, {1}, {2}, ... 等
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        
        return miniMessage.deserialize(message);
    }
    
    /**
     * 獲取原始訊息字串
     */
    public String getMessageString(String path, String langCode) {
        // 嘗試從指定語言獲取訊息
        YamlConfiguration config = languageFiles.get(langCode);
        if (config != null && config.contains(path)) {
            return config.getString(path, "");
        }
        
        // 如果指定語言找不到，嘗試從預設語言獲取
        config = languageFiles.get(defaultLanguage);
        if (config != null && config.contains(path)) {
            return config.getString(path, "");
        }
        
        // 如果還是找不到，返回錯誤訊息
        return "<red>Missing message: " + path;
    }
    
    /**
     * 獲取所有可用語言
     */
    public List<String> getAvailableLanguages() {
        return new ArrayList<>(languageFiles.keySet());
    }
}
