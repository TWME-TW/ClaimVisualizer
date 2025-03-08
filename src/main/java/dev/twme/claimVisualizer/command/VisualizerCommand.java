package dev.twme.claimVisualizer.command;

import dev.twme.claimVisualizer.ClaimVisualizer;
import dev.twme.claimVisualizer.player.PlayerSession;
import org.bukkit.ChatColor;
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

    public VisualizerCommand(ClaimVisualizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家執行！");
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
            default -> sendUnknownCommandMessage(player);
        }

        return true;
    }

    private void toggleVisualization(Player player) {
        if (!player.hasPermission("claimvisualizer.use")) {
            player.sendMessage(ChatColor.RED + "您沒有權限使用此功能！");
            return;
        }

        PlayerSession session = PlayerSession.getSession(player);
        session.toggleVisualization();

        if (session.isVisualizationEnabled()) {
            player.sendMessage(ChatColor.GREEN + "已啟用領地視覺化效果！");
        } else {
            player.sendMessage(ChatColor.YELLOW + "已停用領地視覺化效果！");
        }
    }

    private void enableVisualization(Player player) {
        if (!player.hasPermission("claimvisualizer.use")) {
            player.sendMessage(ChatColor.RED + "您沒有權限使用此功能！");
            return;
        }

        PlayerSession session = PlayerSession.getSession(player);
        if (session.isVisualizationEnabled()) {
            player.sendMessage(ChatColor.YELLOW + "領地視覺化效果已經是啟用狀態！");
            return;
        }

        session.setVisualizationEnabled(true);
        player.sendMessage(ChatColor.GREEN + "已啟用領地視覺化效果！");
    }

    private void disableVisualization(Player player) {
        PlayerSession session = PlayerSession.getSession(player);
        if (!session.isVisualizationEnabled()) {
            player.sendMessage(ChatColor.YELLOW + "領地視覺化效果已經是停用狀態！");
            return;
        }

        session.setVisualizationEnabled(false);
        player.sendMessage(ChatColor.YELLOW + "已停用領地視覺化效果！");
    }

    private void reloadPlugin(Player player) {
        if (!player.hasPermission("claimvisualizer.reload")) {
            player.sendMessage(ChatColor.RED + "您沒有權限重新載入插件！");
            return;
        }

        plugin.reloadPluginConfig();
        player.sendMessage(ChatColor.GREEN + "插件設定已重新載入！");
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GREEN + "===== 領地視覺化插件指令 =====");
        player.sendMessage(ChatColor.YELLOW + "/claimvisual " + ChatColor.WHITE + "- 切換領地視覺化效果");
        player.sendMessage(ChatColor.YELLOW + "/claimvisual on " + ChatColor.WHITE + "- 啟用領地視覺化效果");
        player.sendMessage(ChatColor.YELLOW + "/claimvisual off " + ChatColor.WHITE + "- 停用領地視覺化效果");
        
        if (player.hasPermission("claimvisualizer.reload")) {
            player.sendMessage(ChatColor.YELLOW + "/claimvisual reload " + ChatColor.WHITE + "- 重新載入插件設定");
        }
        
        player.sendMessage(ChatColor.YELLOW + "/claimvisual help " + ChatColor.WHITE + "- 顯示此幫助訊息");
    }

    private void sendUnknownCommandMessage(Player player) {
        player.sendMessage(ChatColor.RED + "未知的指令！請使用 /claimvisual help 查看可用指令。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("on", "off", "help"));
            
            if (sender.hasPermission("claimvisualizer.reload")) {
                options.add("reload");
            }
            
            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
