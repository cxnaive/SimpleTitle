package dev.user.title.listener;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;
import dev.user.title.manager.CustomTitleSessionManager;
import dev.user.title.manager.CustomTitleSessionManager.SessionStage;
import dev.user.title.manager.TitleManager;
import dev.user.title.model.TitleData;
import dev.user.title.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;

/**
 * 玩家事件监听器
 */
public class PlayerListener implements Listener {

    private final SimpleTitlePlugin plugin;

    public PlayerListener(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 加载玩家称号数据到缓存
        plugin.getTitleManager().onPlayerJoin(event.getPlayer().getUniqueId());
        // 加载玩家边框数据到缓存
        plugin.getBracketManager().loadPlayerBrackets(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 清理玩家缓存
        plugin.getTitleManager().onPlayerQuit(player.getUniqueId());
        // 清理边框缓存
        plugin.getBracketManager().unloadPlayerBrackets(player.getUniqueId());
        // 清理会话
        plugin.getCustomTitleSessionManager().removeSession(player.getUniqueId());
        // 清理动态称号追踪
        plugin.getDynamicTitleManager().onPlayerQuit(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        CustomTitleSessionManager sessionManager = plugin.getCustomTitleSessionManager();

        CustomTitleSessionManager.Session session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        event.setCancelled(true);

        ConfigManager configManager = plugin.getConfigManager();
        TitleManager titleManager = plugin.getTitleManager();

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        plugin.getLogger().fine("[CustomTitle] 玩家 " + player.getName() + " 输入: " + message);

        // 处理取消命令
        if (message.equalsIgnoreCase("取消") || message.equalsIgnoreCase("cancel")) {
            sessionManager.removeSession(player.getUniqueId());
            MessageUtil.send(player, configManager.getMessage("custom-cancelled"));
            return;
        }

        // 根据会话阶段处理
        SessionStage stage = session.getStage();
        switch (stage) {
            case SELECT_TYPE:
                handleSelectType(player, message, session, sessionManager, configManager);
                break;
            case INPUT_CONTENT:
                handleContentInput(player, message, session, sessionManager, configManager);
                break;
            case INPUT_NAME:
                handleNameInput(player, message, session, sessionManager, configManager, titleManager);
                break;
            case WAITING_CONFIRM:
                handleConfirm(player, message, session, sessionManager, configManager, titleManager);
                break;
        }
    }

    /**
     * 处理选择称号类型
     */
    private void handleSelectType(Player player, String message, CustomTitleSessionManager.Session session,
                                   CustomTitleSessionManager sessionManager, ConfigManager configManager) {
        if (message.equals("1") || message.equalsIgnoreCase("静态")) {
            session.setDynamic(false);
            session.setStage(SessionStage.INPUT_CONTENT);
            session.refresh();

            MessageUtil.send(player, "&e========== 创建静态称号 ==========");
            MessageUtil.send(player, "&7价格: &e" + formatPrice(
                    configManager.getCustomTitlePriceMoney(),
                    configManager.getCustomTitlePricePoints()));
            MessageUtil.send(player, configManager.getMessage("custom-input-content",
                    "timeout", String.valueOf(configManager.getCustomTitleSessionTimeout())));
            MessageUtil.send(player, configManager.getMessage("custom-input-cancel"));
            MessageUtil.send(player, "&e==================================");

        } else if (message.equals("2") || message.equalsIgnoreCase("动态")) {
            session.setDynamic(true);
            session.setStage(SessionStage.INPUT_CONTENT);
            session.refresh();

            int maxContents = configManager.getDynamicTitleMaxContents();
            MessageUtil.send(player, "&e========== 创建动态称号 ==========");
            MessageUtil.send(player, "&7价格: &e" + formatPrice(
                    configManager.getCustomTitleDynamicPriceMoney(),
                    configManager.getCustomTitleDynamicPricePoints()));
            MessageUtil.send(player, "&7最多 " + maxContents + " 个内容，每个内容颜色可以不同");
            MessageUtil.send(player, "&e请输入第 1 个内容：");
            MessageUtil.send(player, configManager.getMessage("custom-input-cancel"));
            MessageUtil.send(player, "&7输入 \"完成\" 结束输入");
            MessageUtil.send(player, "&e==================================");

        } else {
            MessageUtil.send(player, "&c请输入 1 或 2 来选择称号类型");
        }
    }

    /**
     * 处理内容输入
     */
    private void handleContentInput(Player player, String content, CustomTitleSessionManager.Session session,
                                     CustomTitleSessionManager sessionManager, ConfigManager configManager) {
        int maxLength = configManager.getCustomTitleMaxLength();

        // 动态称号：检查是否输入"完成"
        if (session.isDynamic()) {
            if (content.equalsIgnoreCase("完成") || content.equalsIgnoreCase("done")) {
                // 检查是否至少有2个内容
                if (session.getContents().size() < 2) {
                    MessageUtil.send(player, "&c动态称号至少需要 2 个内容！");
                    MessageUtil.send(player, "&7当前已有 " + session.getContents().size() + " 个内容，请继续输入");
                    return;
                }
                // 进入名称输入阶段
                session.setStage(SessionStage.INPUT_NAME);
                session.refresh();
                MessageUtil.send(player, "&a已收集 " + session.getContents().size() + " 个内容");
                MessageUtil.send(player, configManager.getMessage("custom-input-name"));
                MessageUtil.send(player, configManager.getMessage("custom-input-cancel"));
                return;
            }

            // 检查是否达到最大数量
            int maxContents = configManager.getDynamicTitleMaxContents();
            if (session.getContents().size() >= maxContents) {
                MessageUtil.send(player, "&c已达到最大内容数量(" + maxContents + ")！请输入 \"完成\" 结束");
                return;
            }

            // 验证内容
            if (content.length() > maxLength) {
                MessageUtil.send(player, configManager.getMessage("custom-too-long",
                        "max", String.valueOf(maxLength)));
                return;
            }
            if (configManager.containsForbiddenWord(content)) {
                MessageUtil.send(player, configManager.getMessage("custom-forbidden"));
                return;
            }

            // 添加内容
            session.addContent(content);
            session.refresh();

            int current = session.getContents().size();
            if (current < maxContents) {
                MessageUtil.send(player, "&a已添加第 " + current + " 个内容: &f" + content);
                MessageUtil.send(player, "&e请输入第 " + (current + 1) + " 个内容，或输入 \"完成\" 结束");
            } else {
                MessageUtil.send(player, "&a已添加第 " + current + " 个内容: &f" + content);
                MessageUtil.send(player, "&e已达到最大数量，请输入 \"完成\" 结束");
            }
            return;
        }

        // 静态称号：验证并保存内容
        if (content.length() > maxLength) {
            MessageUtil.send(player, configManager.getMessage("custom-too-long",
                    "max", String.valueOf(maxLength)));
            return;
        }
        if (configManager.containsForbiddenWord(content)) {
            MessageUtil.send(player, configManager.getMessage("custom-forbidden"));
            return;
        }

        session.addContent(content);
        session.setStage(SessionStage.INPUT_NAME);
        session.refresh();

        MessageUtil.send(player, configManager.getMessage("custom-input-name"));
        MessageUtil.send(player, configManager.getMessage("custom-input-cancel"));
    }

    /**
     * 处理名称输入
     */
    private void handleNameInput(Player player, String name, CustomTitleSessionManager.Session session,
                                  CustomTitleSessionManager sessionManager, ConfigManager configManager,
                                  TitleManager titleManager) {
        int maxNameLength = configManager.getCustomTitleMaxNameLength();
        if (name.length() > maxNameLength) {
            MessageUtil.send(player, configManager.getMessage("custom-name-too-long",
                    "max", String.valueOf(maxNameLength)));
            return;
        }

        if (configManager.containsForbiddenWord(name)) {
            MessageUtil.send(player, configManager.getMessage("custom-forbidden"));
            return;
        }

        String titleId = player.getName() + "_" + name;

        titleManager.checkTitleIdExists(player.getUniqueId(), titleId, exists -> {
            if (exists) {
                MessageUtil.send(player, configManager.getMessage("custom-name-duplicate", "name", name));
            } else {
                session.setName(name);
                session.setStage(SessionStage.WAITING_CONFIRM);
                session.refresh();

                // 显示确认信息
                double priceMoney = session.isDynamic() ?
                        configManager.getCustomTitleDynamicPriceMoney() : configManager.getCustomTitlePriceMoney();
                int pricePoints = session.isDynamic() ?
                        configManager.getCustomTitleDynamicPricePoints() : configManager.getCustomTitlePricePoints();

                MessageUtil.send(player, "&e========== 确认创建称号 ==========");
                MessageUtil.send(player, "&7类型: &f" + (session.isDynamic() ? "动态称号" : "静态称号"));
                MessageUtil.send(player, "&7内容: &f" + session.getContentSummary());
                MessageUtil.send(player, "&7名称: &f" + name);
                MessageUtil.send(player, "&7称号ID: &8" + titleId);
                MessageUtil.send(player, "&7价格: &e" + formatPrice(priceMoney, pricePoints));
                MessageUtil.send(player, "&e输入 \"确认\" 或 \"y\" 确认购买");
                MessageUtil.send(player, "&7输入 \"取消\" 取消操作");
                MessageUtil.send(player, "&e====================================");
            }
        });
    }

    /**
     * 处理确认
     */
    private void handleConfirm(Player player, String message, CustomTitleSessionManager.Session session,
                                CustomTitleSessionManager sessionManager, ConfigManager configManager,
                                TitleManager titleManager) {
        if (!message.equalsIgnoreCase("确认") && !message.equalsIgnoreCase("y") && !message.equalsIgnoreCase("yes")) {
            MessageUtil.send(player, "&c请输入 \"确认\" 或 \"取消\"");
            return;
        }

        CustomTitleSessionManager.Session latestSession = sessionManager.getSession(player.getUniqueId());
        if (latestSession == null) {
            MessageUtil.send(player, "&c会话已过期，请重新开始");
            return;
        }

        String name = latestSession.getName();
        if (name == null) {
            sessionManager.removeSession(player.getUniqueId());
            MessageUtil.send(player, "&c会话异常，请重新开始");
            return;
        }

        sessionManager.removeSession(player.getUniqueId());

        // 创建称号
        if (latestSession.isDynamic()) {
            // 创建动态称号
            titleManager.createDynamicCustomTitle(player, latestSession.getContents(), name, result -> {
                handleCreateResult(player, result, latestSession.getFirstContent(), name, configManager, true);
            });
        } else {
            // 创建静态称号
            titleManager.createCustomTitleWithName(player, latestSession.getFirstContent(), name, result -> {
                handleCreateResult(player, result, latestSession.getFirstContent(), name, configManager, false);
            });
        }
    }

    /**
     * 处理创建结果
     */
    private void handleCreateResult(Player player, TitleManager.PurchaseResult result,
                                     String firstContent, String name, ConfigManager configManager,
                                     boolean isDynamic) {
        switch (result) {
            case SUCCESS:
                String bracketLeft = configManager.getDefaultBracketLeft();
                String bracketRight = configManager.getDefaultBracketRight();
                String formattedTitle = "&r" + bracketLeft + "&r" + firstContent + "&r" + bracketRight + "&r";
                MessageUtil.send(player, configManager.getMessage("custom-success", "title", formattedTitle));
                MessageUtil.send(player, "&7称号ID: &8" + player.getName() + "_" + name);
                if (isDynamic) {
                    MessageUtil.send(player, "&7类型: &a动态称号");
                }
                break;
            case CUSTOM_DISABLED:
                MessageUtil.send(player, configManager.getMessage("custom-disabled"));
                break;
            case TOO_LONG:
                MessageUtil.send(player, configManager.getMessage("custom-too-long",
                        "max", String.valueOf(configManager.getCustomTitleMaxLength())));
                break;
            case NAME_TOO_LONG:
                MessageUtil.send(player, configManager.getMessage("custom-name-too-long",
                        "max", String.valueOf(configManager.getCustomTitleMaxNameLength())));
                break;
            case NAME_DUPLICATE:
                MessageUtil.send(player, configManager.getMessage("custom-name-duplicate", "name", name));
                break;
            case FORBIDDEN_WORD:
                MessageUtil.send(player, configManager.getMessage("custom-forbidden"));
                break;
            case NOT_ENOUGH_MONEY:
                double priceMoney = isDynamic ?
                        configManager.getCustomTitleDynamicPriceMoney() : configManager.getCustomTitlePriceMoney();
                MessageUtil.send(player, configManager.getMessage("not-enough-money",
                        "price", String.format("%.2f", priceMoney)));
                break;
            case NOT_ENOUGH_POINTS:
                int pricePoints = isDynamic ?
                        configManager.getCustomTitleDynamicPricePoints() : configManager.getCustomTitlePricePoints();
                MessageUtil.send(player, configManager.getMessage("not-enough-points",
                        "price", String.valueOf(pricePoints)));
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
    }

    /**
     * 格式化价格显示
     */
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
}
