package dev.user.title.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态称号追踪器
 * 追踪每个玩家的每个动态称号当前显示的内容索引
 */
public class DynamicTitleTracker {

    // key = "playerUuid:titleId", value = 当前索引
    private final Map<String, Integer> currentIndices = new ConcurrentHashMap<>();

    /**
     * 生成缓存键
     */
    private String getKey(UUID playerUuid, String titleId) {
        return playerUuid.toString() + ":" + titleId;
    }

    /**
     * 获取当前显示索引
     */
    public int getCurrentIndex(UUID playerUuid, String titleId) {
        String key = getKey(playerUuid, titleId);
        return currentIndices.getOrDefault(key, 0);
    }

    /**
     * 更新到下一个索引
     * @return 更新后的索引
     */
    public int nextIndex(UUID playerUuid, String titleId, int maxSize) {
        if (maxSize <= 1) return 0;

        String key = getKey(playerUuid, titleId);
        int current = currentIndices.getOrDefault(key, 0);
        int next = (current + 1) % maxSize;
        currentIndices.put(key, next);
        return next;
    }

    /**
     * 移除玩家的所有动态称号追踪数据
     */
    public void removePlayer(UUID playerUuid) {
        String prefix = playerUuid.toString() + ":";
        currentIndices.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    /**
     * 移除指定称号的追踪数据
     */
    public void removeTitle(UUID playerUuid, String titleId) {
        String key = getKey(playerUuid, titleId);
        currentIndices.remove(key);
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        currentIndices.clear();
    }
}
