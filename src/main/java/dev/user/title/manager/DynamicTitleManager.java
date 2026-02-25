package dev.user.title.manager;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;
import dev.user.title.model.TitleData;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * 动态称号管理器
 * 管理动态称号的内容切换
 */
public class DynamicTitleManager {

    private final SimpleTitlePlugin plugin;
    private final DynamicTitleTracker tracker;
    private ScheduledTask switchTask;

    public DynamicTitleManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.tracker = new DynamicTitleTracker();
    }

    /**
     * 启动定时切换任务
     */
    public void start() {
        ConfigManager config = plugin.getConfigManager();
        int interval = config.getDynamicTitleSwitchInterval();

        switchTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            updateDynamicTitles();
        }, interval, interval);
    }

    /**
     * 停止定时任务
     */
    public void shutdown() {
        if (switchTask != null) {
            switchTask.cancel();
            switchTask = null;
        }
        tracker.clear();
    }

    /**
     * 更新所有使用动态称号的玩家
     */
    private void updateDynamicTitles() {
        TitleCacheManager cacheManager = plugin.getTitleCacheManager();

        // 遍历在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUuid = player.getUniqueId();

            // 获取玩家当前使用的称号
            String titleId = cacheManager.getCurrentTitleId(playerUuid);
            if (titleId == null) continue;

            // 获取称号数据
            Map<String, TitleData> playerTitles = cacheManager.getPlayerTitles(playerUuid);
            if (playerTitles == null) continue;

            TitleData titleData = playerTitles.get(titleId);
            if (titleData == null) continue;

            // 只处理动态称号
            if (!titleData.isDynamic()) continue;

            // 更新索引
            tracker.nextIndex(playerUuid, titleId, titleData.getContentCount());
        }
    }

    /**
     * 获取动态称号当前显示的内容
     */
    public String getCurrentContent(UUID playerUuid, String titleId, TitleData titleData) {
        if (!titleData.isDynamic()) {
            return titleData.getContent(0);
        }

        int index = tracker.getCurrentIndex(playerUuid, titleId);
        return titleData.getContent(index);
    }

    /**
     * 玩家退出时清理
     */
    public void onPlayerQuit(UUID playerUuid) {
        tracker.removePlayer(playerUuid);
    }

    /**
     * 获取追踪器
     */
    public DynamicTitleTracker getTracker() {
        return tracker;
    }
}
