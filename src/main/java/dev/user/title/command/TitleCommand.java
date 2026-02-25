package dev.user.title.command;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;
import dev.user.title.gui.BracketShopGUI;
import dev.user.title.gui.TitleMainGUI;
import dev.user.title.gui.TitleShopGUI;
import dev.user.title.manager.BracketManager;
import dev.user.title.manager.CustomTitleSessionManager;
import dev.user.title.manager.TitleManager;
import dev.user.title.model.BracketData;
import dev.user.title.model.TitleData;
import dev.user.title.util.CsvImporter;
import dev.user.title.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 称号命令处理器
 */
public class TitleCommand implements CommandExecutor, TabCompleter {

    private final SimpleTitlePlugin plugin;
    private final ConfigManager configManager;
    private final TitleManager titleManager;

    public TitleCommand(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.titleManager = plugin.getTitleManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // 打开称号选择GUI（如果是玩家）
            if (sender instanceof Player) {
                openTitleGUI((Player) sender);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                return handleSet(sender, args);
            case "clear":
                return handleClear(sender);
            case "list":
                return handleList(sender);
            case "shop":
                return handleShop(sender);
            case "buy":
                return handleBuy(sender, args);
            case "custom":
                return handleCustom(sender, args);
            case "bracket":
                return handleBracket(sender, args);
            case "brackets":
                return handleBrackets(sender);
            case "import":
                return handleImport(sender, args);
            case "reload":
                return handleReload(sender);
            case "give":
                return handleGive(sender, args);
            case "help":
                sendHelp(sender);
                return true;
            default:
                MessageUtil.send(sender, configManager.getMessage("unknown-command"));
                return true;
        }
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.set")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "&c用法: /title set <称号ID>");
            return true;
        }

        String titleId = args[1];
        UUID playerUuid = player.getUniqueId();

        // 检查是否拥有该称号
        if (!titleManager.hasTitle(playerUuid, titleId)) {
            MessageUtil.send(player, configManager.getMessage("title-not-found"));
            return true;
        }

        titleManager.setCurrentTitle(playerUuid, titleId, success -> {
            if (success) {
                TitleData titleData = titleManager.getCurrentTitle(playerUuid);
                String formattedTitle = titleData != null ? titleData.getFormatted() : titleId;
                MessageUtil.send(player, configManager.getMessage("title-set", "title", formattedTitle));
            } else {
                MessageUtil.send(player, "&c设置称号失败！");
            }
        });

        return true;
    }

    private boolean handleClear(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.clear")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        titleManager.clearCurrentTitle(player.getUniqueId(), success -> {
            if (success) {
                MessageUtil.send(player, configManager.getMessage("title-cleared"));
            } else {
                MessageUtil.send(player, "&c清除称号失败！");
            }
        });

        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.list")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        Map<String, TitleData> titles = titleManager.getPlayerTitles(playerUuid);
        String currentTitleId = plugin.getTitleCacheManager().getCurrentTitleId(playerUuid);

        if (titles.isEmpty()) {
            MessageUtil.send(player, configManager.getMessage("no-titles"));
            return true;
        }

        MessageUtil.send(player, configManager.getMessage("list-header", "count", String.valueOf(titles.size())));

        for (Map.Entry<String, TitleData> entry : titles.entrySet()) {
            String titleId = entry.getKey();
            TitleData titleData = entry.getValue();
            String formatted = titleData.getFormatted();

            if (titleId.equals(currentTitleId)) {
                MessageUtil.send(player, configManager.getMessage("list-item-current",
                        "title", formatted, "id", titleId));
            } else {
                MessageUtil.send(player, configManager.getMessage("list-item",
                        "title", formatted, "id", titleId));
            }
        }

        MessageUtil.send(player, configManager.getMessage("list-footer"));

        return true;
    }

    private boolean handleShop(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.shop")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        // 显示商店信息
        openShopGUI(player);
        return true;
    }

    private boolean handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.shop")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "&c用法: /title buy <称号ID>");
            return true;
        }

        String titleId = args[1];

        titleManager.purchasePresetTitle(player, titleId, result -> {
            TitleData titleData = titleManager.getPresetTitle(titleId);
            String formattedTitle = titleData != null ? titleData.getFormatted() : titleId;

            switch (result) {
                case SUCCESS:
                    MessageUtil.send(player, configManager.getMessage("purchase-success", "title", formattedTitle));
                    break;
                case NOT_FOUND:
                    MessageUtil.send(player, configManager.getMessage("title-not-found"));
                    break;
                case ALREADY_OWNED:
                    MessageUtil.send(player, configManager.getMessage("title-already-owned"));
                    break;
                case NO_PERMISSION:
                    MessageUtil.send(player, "&c你没有权限购买这个称号！");
                    break;
                case NOT_ENOUGH_MONEY:
                    if (titleData != null) {
                        MessageUtil.send(player, configManager.getMessage("not-enough-money",
                                "price", String.format("%.2f", titleData.getPriceMoney())));
                    }
                    break;
                case NOT_ENOUGH_POINTS:
                    if (titleData != null) {
                        MessageUtil.send(player, configManager.getMessage("not-enough-points",
                                "price", String.valueOf(titleData.getPricePoints())));
                    }
                    break;
                case ECONOMY_NOT_AVAILABLE:
                    MessageUtil.send(player, configManager.getMessage("economy-not-available"));
                    break;
                case POINTS_NOT_AVAILABLE:
                    MessageUtil.send(player, configManager.getMessage("points-not-available"));
                    break;
                default:
                    MessageUtil.send(player, configManager.getMessage("purchase-failed"));
                    break;
            }
        });

        return true;
    }

    private boolean handleCustom(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.custom")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        // 检查是否已经在会话中
        CustomTitleSessionManager sessionManager = plugin.getCustomTitleSessionManager();
        if (sessionManager.hasSession(player.getUniqueId())) {
            MessageUtil.send(player, "&c你正在创建自定义称号，请先完成或取消当前操作");
            MessageUtil.send(player, "&7输入 \"取消\" 可取消操作");
            return true;
        }

        // 检查自定义称号功能是否启用
        if (!configManager.isCustomTitleEnabled()) {
            MessageUtil.send(player, configManager.getMessage("custom-disabled"));
            return true;
        }

        // 启动会话，显示选择类型提示
        sessionManager.startSession(player);

        MessageUtil.send(player, "&e========== 创建自定义称号 ==========");
        MessageUtil.send(player, "&7请选择称号类型：");
        MessageUtil.send(player, "&e1. 静态称号 &7- 固定内容");
        MessageUtil.send(player, "   &7价格: &e" + formatPrice(
                configManager.getCustomTitlePriceMoney(),
                configManager.getCustomTitlePricePoints()));
        MessageUtil.send(player, "&e2. 动态称号 &7- 内容循环切换");
        MessageUtil.send(player, "   &7价格: &e" + formatPrice(
                configManager.getCustomTitleDynamicPriceMoney(),
                configManager.getCustomTitleDynamicPricePoints()));
        MessageUtil.send(player, "&e请输入 1 或 2 选择类型");
        MessageUtil.send(player, configManager.getMessage("custom-input-cancel"));
        MessageUtil.send(player, "&e====================================");

        return true;
    }

    private boolean handleBracket(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.bracket")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(player, "&c用法: /title bracket <称号ID> <边框ID>");
            return true;
        }

        String titleId = args[1];
        String bracketId = args[2];
        UUID playerUuid = player.getUniqueId();

        // 检查是否拥有该称号
        if (!titleManager.hasTitle(playerUuid, titleId)) {
            MessageUtil.send(player, configManager.getMessage("title-not-found"));
            return true;
        }

        // 检查边框是否存在
        BracketManager bracketManager = plugin.getBracketManager();
        BracketData bracket = bracketManager.getPresetBracket(bracketId);
        if (bracket == null) {
            MessageUtil.send(player, "&c边框不存在！");
            return true;
        }

        // 检查是否拥有该边框
        if (!bracketManager.hasBracket(playerUuid, bracketId)) {
            MessageUtil.send(player, "&c你没有这个边框！");
            return true;
        }

        // 获取称号数据并更新边框
        TitleData titleData = titleManager.getPlayerTitles(playerUuid).get(titleId);
        titleData.setBracketLeft(bracket.getBracketLeft());
        titleData.setBracketRight(bracket.getBracketRight());

        titleManager.updatePlayerTitleData(playerUuid, titleId, titleData, success -> {
            if (success) {
                MessageUtil.send(player, "&a边框已更新为: " + bracket.getDisplayName());
                MessageUtil.send(player, "&7预览: " + bracket.getPreview());
            } else {
                MessageUtil.send(player, "&c边框更新失败！");
            }
        });

        return true;
    }

    private boolean handleBrackets(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("simpletitle.bracket")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return true;
        }

        // 打开边框商城
        BracketShopGUI.open(plugin, player, 0);
        return true;
    }

    private boolean handleImport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simpletitle.import")) {
            MessageUtil.send(sender, configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(sender, "&c用法: /title import plt <csv文件名>");
            return true;
        }

        String format = args[1].toLowerCase();
        if (!format.equals("plt")) {
            MessageUtil.send(sender, "&c不支持的格式: " + format);
            MessageUtil.send(sender, "&7支持的格式: plt");
            return true;
        }

        String fileName = args[2];
        MessageUtil.send(sender, "&e开始导入 " + fileName + " ...");

        plugin.getCsvImporter().importPltCsv(fileName, result -> {
            MessageUtil.send(sender, "&a导入完成！");
            MessageUtil.send(sender, "&7总计: " + result.getTotal() + " 条记录");
            MessageUtil.send(sender, "&7成功: &a" + result.getSuccess() + " 条");
            if (result.getSkipped() > 0) {
                MessageUtil.send(sender, "&7跳过: &e" + result.getSkipped() + " 条（已存在）");
            }
            if (!result.getErrors().isEmpty()) {
                MessageUtil.send(sender, "&c错误: " + result.getErrors().size() + " 条");
                for (String error : result.getErrors().subList(0, Math.min(5, result.getErrors().size()))) {
                    MessageUtil.send(sender, "&c  - " + error);
                }
                if (result.getErrors().size() > 5) {
                    MessageUtil.send(sender, "&c  ... 还有 " + (result.getErrors().size() - 5) + " 条错误");
                }
            }
        });

        return true;
    }

    private String formatPrice(double money, int points) {
        StringBuilder sb = new StringBuilder();
        if (money > 0) {
            sb.append(String.format("%.0f金币", money));
        }
        if (points > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(points).append("点券");
        }
        if (sb.length() == 0) {
            sb.append("免费");
        }
        return sb.toString();
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("simpletitle.reload")) {
            MessageUtil.send(sender, configManager.getMessage("no-permission"));
            return true;
        }

        plugin.reload();
        MessageUtil.send(sender, configManager.getMessage("reload-success"));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simpletitle.give")) {
            MessageUtil.send(sender, configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(sender, "&c用法: /title give <玩家> <称号ID>");
            return true;
        }

        String playerName = args[1];
        String titleId = args[2];

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            MessageUtil.send(sender, configManager.getMessage("player-not-found"));
            return true;
        }

        // 获取预设称号
        TitleData titleData = titleManager.getPresetTitle(titleId);
        if (titleData == null) {
            MessageUtil.send(sender, configManager.getMessage("title-not-found"));
            return true;
        }

        titleManager.giveTitle(targetPlayer.getUniqueId(), titleId, titleData, success -> {
            if (success) {
                MessageUtil.send(sender, configManager.getMessage("give-success",
                        "player", targetPlayer.getName(), "title", titleData.getFormatted()));
            } else {
                MessageUtil.send(sender, configManager.getMessage("give-failed"));
            }
        });

        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, configManager.getMessage("help-header"));
        MessageUtil.send(sender, configManager.getMessage("help-title"));
        MessageUtil.send(sender, configManager.getMessage("help-set"));
        MessageUtil.send(sender, configManager.getMessage("help-clear"));
        MessageUtil.send(sender, configManager.getMessage("help-list"));
        MessageUtil.send(sender, configManager.getMessage("help-shop"));
        MessageUtil.send(sender, configManager.getMessage("help-buy"));
        MessageUtil.send(sender, configManager.getMessage("help-custom"));
        MessageUtil.send(sender, configManager.getMessage("help-brackets"));
        MessageUtil.send(sender, configManager.getMessage("help-bracket"));
        if (sender.hasPermission("simpletitle.reload")) {
            MessageUtil.send(sender, configManager.getMessage("help-reload"));
        }
        if (sender.hasPermission("simpletitle.give")) {
            MessageUtil.send(sender, configManager.getMessage("help-give"));
        }
        if (sender.hasPermission("simpletitle.import")) {
            MessageUtil.send(sender, configManager.getMessage("help-import"));
        }
        MessageUtil.send(sender, configManager.getMessage("help-footer"));
    }

    private void openTitleGUI(Player player) {
        if (!player.hasPermission("simpletitle.gui")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return;
        }
        TitleMainGUI.open(plugin, player);
    }

    private void openShopGUI(Player player) {
        if (!player.hasPermission("simpletitle.shop")) {
            MessageUtil.send(player, configManager.getMessage("no-permission"));
            return;
        }
        TitleShopGUI.open(plugin, player, 0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 子命令补全
            List<String> subCommands = new ArrayList<>(Arrays.asList("set", "clear", "list", "shop", "buy", "custom", "bracket", "brackets", "help"));
            if (sender.hasPermission("simpletitle.reload")) {
                subCommands.add("reload");
            }
            if (sender.hasPermission("simpletitle.give")) {
                subCommands.add("give");
            }
            if (sender.hasPermission("simpletitle.import")) {
                subCommands.add("import");
            }

            String prefix = args[0].toLowerCase();
            completions.addAll(subCommands.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList()));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("import")) {
                // 补全导入格式
                String prefix = args[1].toLowerCase();
                List<String> formats = Arrays.asList("plt");
                completions.addAll(formats.stream()
                        .filter(s -> s.startsWith(prefix))
                        .collect(Collectors.toList()));
            } else if (subCommand.equals("set") || subCommand.equals("bracket")) {
                // 补全玩家拥有的称号ID
                if (sender instanceof Player) {
                    String prefix = args[1].toLowerCase();
                    completions.addAll(titleManager.getPlayerTitles(((Player) sender).getUniqueId())
                            .keySet().stream()
                            .filter(s -> s.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList()));
                }
            } else if (subCommand.equals("buy")) {
                // 补全预设称号ID
                String prefix = args[1].toLowerCase();
                completions.addAll(titleManager.getPresetTitles().keySet().stream()
                        .filter(s -> s.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList()));
            } else if (subCommand.equals("give")) {
                // 补全在线玩家名
                String prefix = args[1].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList()));
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("give")) {
                // 补全预设称号ID
                String prefix = args[2].toLowerCase();
                completions.addAll(titleManager.getPresetTitles().keySet().stream()
                        .filter(s -> s.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList()));
            } else if (subCommand.equals("bracket")) {
                // 补全边框ID
                String prefix = args[2].toLowerCase();
                completions.addAll(plugin.getBracketManager().getPresetBracketIds().stream()
                        .filter(s -> s.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList()));
            }
        }

        return completions;
    }
}
