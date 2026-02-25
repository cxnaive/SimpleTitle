package dev.user.title.manager;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;
import dev.user.title.util.MessageUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义称号会话管理器
 * 管理玩家创建自定义称号时的多步输入流程
 */
public class CustomTitleSessionManager {

    private final SimpleTitlePlugin plugin;

    // 玩家会话缓存: playerUuid -> Session
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    // 超时提醒任务
    private ScheduledTask timeoutTask;

    public CustomTitleSessionManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        startTimeoutChecker();
    }

    /**
     * 启动超时检查定时任务
     */
    private void startTimeoutChecker() {
        // 每1秒检查一次
        timeoutTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            checkAndNotifyTimeouts();
        }, 20L, 20L); // 20 ticks = 1秒
    }

    /**
     * 检查并通知超时的会话
     */
    private void checkAndNotifyTimeouts() {
        if (sessions.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int timeoutSeconds = plugin.getConfigManager().getCustomTitleSessionTimeout();
        long timeoutMillis = timeoutSeconds * 1000L;

        sessions.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            if (now - session.getCreateTime() > timeoutMillis) {
                // 通知玩家超时
                Player player = Bukkit.getPlayer(session.getPlayerUuid());
                if (player != null && player.isOnline()) {
                    plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                        MessageUtil.send(player, plugin.getConfigManager().getMessage("custom-timeout"));
                    });
                }
                return true;
            }
            return false;
        });
    }

    /**
     * 停止定时任务
     */
    public void shutdown() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        sessions.clear();
    }

    /**
     * 获取会话超时时间（毫秒）
     */
    public long getTimeoutMillis() {
        return plugin.getConfigManager().getCustomTitleSessionTimeout() * 1000L;
    }

    /**
     * 开始新的自定义称号会话（从GUI调用）
     * 这会提示玩家输入称号内容
     */
    public void startSession(Player player) {
        // 创建一个空会话，等待玩家输入内容
        // 实际内容会在 PlayerListener 中设置
        sessions.put(player.getUniqueId(), new Session(
                player.getUniqueId(),
                player.getName(),
                "", // 内容将在聊天输入时设置
                System.currentTimeMillis()
        ));
    }

    /**
     * 创建新会话（第一步：输入称号内容后）
     */
    public void createSession(Player player, String content) {
        Session session = new Session(
                player.getUniqueId(),
                player.getName(),
                content,
                System.currentTimeMillis()
        );
        sessions.put(player.getUniqueId(), session);
    }

    /**
     * 获取玩家当前会话
     */
    public Session getSession(UUID playerUuid) {
        Session session = sessions.get(playerUuid);
        if (session == null) {
            return null;
        }

        // 检查是否超时
        if (System.currentTimeMillis() - session.getCreateTime() > getTimeoutMillis()) {
            sessions.remove(playerUuid);
            return null;
        }

        return session;
    }

    /**
     * 检查玩家是否在会话中
     */
    public boolean hasSession(UUID playerUuid) {
        return getSession(playerUuid) != null;
    }

    /**
     * 移除会话
     */
    public void removeSession(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    /**
     * 更新会话的名称（第二步：输入名称后）
     */
    public void setSessionName(UUID playerUuid, String name) {
        Session session = sessions.get(playerUuid);
        if (session != null) {
            session.setName(name);
            session.refresh();
        }
    }

    /**
     * 更新会话的内容（从GUI启动时，第一步输入内容后）
     */
    public void updateSessionContent(UUID playerUuid, String content) {
        Session session = sessions.get(playerUuid);
        if (session != null) {
            session.addContent(content);
            session.refresh();
        }
    }

    /**
     * 更新会话状态为等待确认
     */
    public void setWaitingConfirm(UUID playerUuid, boolean waiting) {
        Session session = sessions.get(playerUuid);
        if (session != null) {
            session.setWaitingConfirm(waiting);
            session.refresh();
        }
    }

    /**
     * 检查会话是否在等待确认
     */
    public boolean isWaitingConfirm(UUID playerUuid) {
        Session session = sessions.get(playerUuid);
        return session != null && session.isWaitingConfirm();
    }

    /**
     * 获取会话剩余时间（秒）
     */
    public int getRemainingSeconds(UUID playerUuid) {
        Session session = sessions.get(playerUuid);
        if (session == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - session.getCreateTime();
        int timeout = plugin.getConfigManager().getCustomTitleSessionTimeout();
        int remaining = timeout - (int) (elapsed / 1000);
        return Math.max(0, remaining);
    }

    /**
     * 会话数据类
     */
    public static class Session {
        private final UUID playerUuid;
        private final String playerName;
        private java.util.List<String> contents;  // 内容列表（静态1个，动态多个）
        private long createTime;
        private String name;
        private boolean waitingConfirm;
        private boolean isDynamic;            // 是否为动态称号
        private SessionStage stage;           // 当前阶段

        public Session(UUID playerUuid, String playerName, String content, long createTime) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.contents = new java.util.ArrayList<>();
            this.createTime = createTime;
            this.name = null;
            this.waitingConfirm = false;
            this.isDynamic = false;
            this.stage = SessionStage.SELECT_TYPE;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public java.util.List<String> getContents() {
            return contents;
        }

        public void addContent(String content) {
            this.contents.add(content);
        }

        public String getFirstContent() {
            return contents.isEmpty() ? "" : contents.get(0);
        }

        public long getCreateTime() {
            return createTime;
        }

        public void refresh() {
            this.createTime = System.currentTimeMillis();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isWaitingConfirm() {
            return waitingConfirm;
        }

        public void setWaitingConfirm(boolean waitingConfirm) {
            this.waitingConfirm = waitingConfirm;
        }

        public boolean isDynamic() {
            return isDynamic;
        }

        public void setDynamic(boolean dynamic) {
            isDynamic = dynamic;
        }

        public SessionStage getStage() {
            return stage;
        }

        public void setStage(SessionStage stage) {
            this.stage = stage;
        }

        public String generateTitleId() {
            return playerName + "_" + name;
        }

        public String getContentSummary() {
            return String.join(" | ", contents);
        }
    }

    /**
     * 会话阶段枚举
     */
    public enum SessionStage {
        SELECT_TYPE,      // 选择称号类型（静态/动态）
        INPUT_CONTENT,    // 输入内容
        INPUT_NAME,       // 输入名称
        WAITING_CONFIRM   // 等待确认
    }
}
