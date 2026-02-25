package dev.user.title.placeholder;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.manager.DynamicTitleManager;
import dev.user.title.manager.TitleCacheManager;
import dev.user.title.manager.TitleManager;
import dev.user.title.model.TitleData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI 扩展
 * 提供 %playertitle_current% %playertitle_current_id% %playertitle_count% 占位符
 */
public class TitleExpansion extends PlaceholderExpansion {

    private final SimpleTitlePlugin plugin;

    public TitleExpansion(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playertitle";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SimpleTitle";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getRequiredPlugin() {
        return "SimpleTitle";
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        UUID playerUuid = player.getUniqueId();
        TitleManager titleManager = plugin.getTitleManager();
        TitleCacheManager cacheManager = plugin.getTitleCacheManager();
        DynamicTitleManager dynamicManager = plugin.getDynamicTitleManager();

        // 解析参数
        params = params.toLowerCase();

        switch (params) {
            case "use":
            case "current":
                // 玩家当前使用的称号（完整格式，支持动态称号）
                return getFormattedCurrentTitle(playerUuid, cacheManager, dynamicManager);

            case "raw":
                // 玩家当前称号原始文本（无边框）
                TitleData currentTitle = titleManager.getCurrentTitle(playerUuid);
                return currentTitle != null ? currentTitle.getRaw() : "";

            case "content":
                // 玩家当前称号内容（支持动态称号）
                return getCurrentContent(playerUuid, cacheManager, dynamicManager);

            case "bracket":
            case "brackets":
                // 玩家当前边框样式
                TitleData titleData = titleManager.getCurrentTitle(playerUuid);
                if (titleData != null) {
                    return titleData.getBracketLeft() + titleData.getBracketRight();
                }
                return "";

            case "bracket_left":
                // 玩家当前左边框
                TitleData dataLeft = titleManager.getCurrentTitle(playerUuid);
                return dataLeft != null ? dataLeft.getBracketLeft() : "";

            case "bracket_right":
                // 玩家当前右边框
                TitleData dataRight = titleManager.getCurrentTitle(playerUuid);
                return dataRight != null ? dataRight.getBracketRight() : "";

            case "prefix":
                // 玩家当前前缀
                TitleData dataPrefix = titleManager.getCurrentTitle(playerUuid);
                return dataPrefix != null ? dataPrefix.getPrefix() : "";

            case "suffix":
                // 玩家当前后缀
                TitleData dataSuffix = titleManager.getCurrentTitle(playerUuid);
                return dataSuffix != null ? dataSuffix.getSuffix() : "";

            case "count":
            case "amount":
                // 玩家拥有的称号数量
                return String.valueOf(titleManager.getTitleCount(playerUuid));

            case "has_title":
                // 是否拥有称号（返回 yes/no）
                return titleManager.getTitleCount(playerUuid) > 0 ? "yes" : "no";

            case "is_dynamic":
                // 当前称号是否为动态称号
                TitleData dynamicCheck = titleManager.getCurrentTitle(playerUuid);
                return (dynamicCheck != null && dynamicCheck.isDynamic()) ? "yes" : "no";

            default:
                // 检查是否有 has_<titleId> 格式
                if (params.startsWith("has_")) {
                    String titleId = params.substring(5);
                    return titleManager.hasTitle(playerUuid, titleId) ? "yes" : "no";
                }

                return null;
        }
    }

    /**
     * 获取当前称号的完整格式化字符串（支持动态称号）
     */
    private String getFormattedCurrentTitle(UUID playerUuid, TitleCacheManager cacheManager,
                                            DynamicTitleManager dynamicManager) {
        String titleId = cacheManager.getCurrentTitleId(playerUuid);
        if (titleId == null) {
            return "";
        }

        Map<String, TitleData> playerTitles = cacheManager.getPlayerTitles(playerUuid);
        if (playerTitles == null) {
            return "";
        }

        TitleData titleData = playerTitles.get(titleId);
        if (titleData == null) {
            return "";
        }

        // 获取当前显示的内容索引
        String content = dynamicManager.getCurrentContent(playerUuid, titleId, titleData);

        // 获取 padding 配置
        String paddingLeft = plugin.getConfigManager().getTitlePaddingLeft();
        String paddingRight = plugin.getConfigManager().getTitlePaddingRight();

        // 格式化
        return paddingLeft + "&r" + titleData.getBracketLeft() + "&r" + titleData.getPrefix() +
               content + titleData.getSuffix() + "&r" + titleData.getBracketRight() + "&r" + paddingRight;
    }

    /**
     * 获取当前称号的内容（支持动态称号）
     */
    private String getCurrentContent(UUID playerUuid, TitleCacheManager cacheManager,
                                     DynamicTitleManager dynamicManager) {
        String titleId = cacheManager.getCurrentTitleId(playerUuid);
        if (titleId == null) {
            return "";
        }

        Map<String, TitleData> playerTitles = cacheManager.getPlayerTitles(playerUuid);
        if (playerTitles == null) {
            return "";
        }

        TitleData titleData = playerTitles.get(titleId);
        if (titleData == null) {
            return "";
        }

        return dynamicManager.getCurrentContent(playerUuid, titleId, titleData);
    }
}
