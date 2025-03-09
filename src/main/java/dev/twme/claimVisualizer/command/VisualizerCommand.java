package dev.twme.claimVisualizer.command;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.config.ConfigManager;
import dev.twme.claimVisualizer.language.LanguageManager;
import dev.twme.claimVisualizer.player.PlayerSession;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VisualizerCommand implements CommandExecutor, TabCompleter {

    private final ClaimVisualizer plugin;
    private final LanguageManager languageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public VisualizerCommand(ClaimVisualizer plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageManager.getMessageInLanguage("command.player_only", "en"));
            return true;
        }

        if (args.length == 0) {
            toggleVisualization(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> enableVisualization(player);
            case "off" -> disableVisualization(player);
            case "reload" -> reloadPlugin(player);
            case "help" -> sendHelpMessage(player);
            case "mode" -> handleModeCommand(player, args);
            case "language", "lang" -> handleLanguageCommand(player, args);
            default -> player.sendMessage(languageManager.getMessage("command.unknown", player));
        }

        return true;
    }

    private void handleLanguageCommand(Player player, String[] args) {
        if (!player.hasPermission("claimvisualizer.language")) {
            player.sendMessage(languageManager.getMessage("command.no_permission", player));
            return;
        }

        if (args.length < 2) {
            // 顯示可用語言清單
            List<String> languages = languageManager.getAvailableLanguages();
            player.sendMessage(languageManager.getMessage("command.language.available", player));
            
            StringBuilder langList = new StringBuilder();
            for (String lang : languages) {
                langList.append(lang).append(", ");
            }
            if (langList.length() > 2) {
                langList.setLength(langList.length() - 2); // 移除最後的", "
            }
            
            // 直接使用 MiniMessage 解析字串
            player.sendMessage(miniMessage.deserialize("<yellow>" + langList));
            
            // 使用佔位符而不是 append
            String currentLang = languageManager.getPlayerLanguage(player.getUniqueId());
            player.sendMessage(languageManager.getMessage("command.language.current", player, currentLang));
            return;
        }

        String langCode = args[1].toLowerCase();
        if (!languageManager.getAvailableLanguages().contains(langCode)) {
            player.sendMessage(languageManager.getMessage("command.language.not_found", player));
            return;
        }

        languageManager.setPlayerLanguage(player.getUniqueId(), langCode);
        PlayerSession session = PlayerSession.getSession(player);
        session.setLanguage(langCode);
        
        player.sendMessage(languageManager.getMessage("command.language.changed", player));
    }

    private void handleModeCommand(Player player, String[] args) {
        if (!player.hasPermission("claimvisualizer.use")) {
            player.sendMessage(languageManager.getMessage("command.no_permission", player));
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(languageManager.getMessage("command.mode.specify", player));
            return;
        }
        
        try {
            ConfigManager.DisplayMode mode = ConfigManager.DisplayMode.valueOf(args[1].toUpperCase());
            PlayerSession session = PlayerSession.getSession(player);
            session.setDisplayMode(mode);
            
            // 使用佔位符而不是 append
            player.sendMessage(languageManager.getMessage("command.mode.set", player, mode));
        } catch (IllegalArgumentException e) {
            player.sendMessage(languageManager.getMessage("command.mode.invalid", player));
        }
    }

    private void toggleVisualization(Player player) {
        if (!player.hasPermission("claimvisualizer.use")) {
            player.sendMessage(languageManager.getMessage("command.no_permission", player));
            return;
        }

        PlayerSession session = PlayerSession.getSession(player);
        session.toggleVisualization();

        if (session.isVisualizationEnabled()) {
            player.sendMessage(languageManager.getMessage("command.on", player));
        } else {
            player.sendMessage(languageManager.getMessage("command.off", player));
        }
    }

    private void enableVisualization(Player player) {
        if (!player.hasPermission("claimvisualizer.use")) {
            player.sendMessage(languageManager.getMessage("command.no_permission", player));
            return;
        }

        PlayerSession session = PlayerSession.getSession(player);
        if (session.isVisualizationEnabled()) {
            player.sendMessage(languageManager.getMessage("command.already_on", player));
            return;
        }

        session.setVisualizationEnabled(true);
        player.sendMessage(languageManager.getMessage("command.on", player));
    }

    private void disableVisualization(Player player) {
        PlayerSession session = PlayerSession.getSession(player);
        if (!session.isVisualizationEnabled()) {
            player.sendMessage(languageManager.getMessage("command.already_off", player));
            return;
        }

        session.setVisualizationEnabled(false);
        player.sendMessage(languageManager.getMessage("command.off", player));
    }

    private void reloadPlugin(Player player) {
        if (!player.hasPermission("claimvisualizer.reload")) {
            player.sendMessage(languageManager.getMessage("command.no_permission", player));
            return;
        }

        plugin.reloadPluginConfig();
        player.sendMessage(languageManager.getMessage("command.reload", player));
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(languageManager.getMessage("command.help.header", player));
        player.sendMessage(languageManager.getMessage("command.help.toggle", player));
        player.sendMessage(languageManager.getMessage("command.help.on", player));
        player.sendMessage(languageManager.getMessage("command.help.off", player));
        player.sendMessage(languageManager.getMessage("command.help.mode", player));
        
        if (player.hasPermission("claimvisualizer.language")) {
            player.sendMessage(languageManager.getMessage("command.help.language", player));
        }
        
        if (player.hasPermission("claimvisualizer.reload")) {
            player.sendMessage(languageManager.getMessage("command.help.reload", player));
        }
        
        player.sendMessage(languageManager.getMessage("command.help.help", player));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("on", "off", "help", "mode"));
            
            if (sender.hasPermission("claimvisualizer.language")) {
                options.add("language");
                options.add("lang");
            }
            
            if (sender.hasPermission("claimvisualizer.reload")) {
                options.add("reload");
            }
            
            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("mode")) {
                List<String> modes = Arrays.stream(ConfigManager.DisplayMode.values())
                        .map(Enum::name)
                        .collect(Collectors.toList());
                return modes.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang")) {
                if (sender.hasPermission("claimvisualizer.language")) {
                    return plugin.getLanguageManager().getAvailableLanguages().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        return new ArrayList<>();
    }
}
