package dev.user.title.manager;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.database.TitleRepository;
import dev.user.title.model.TitleData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 称号缓存管理器
 * 缓存玩家的称号数据，减少数据库查询
 */
public class TitleCacheManager {

    private final SimpleTitlePlugin plugin;
    private final TitleRepository repository;

    // 玩家称号缓存: playerUuid -> (titleId -> TitleData)
    private final Map<UUID, Map<String, TitleData>> playerTitlesCache = new ConcurrentHashMap<>();

    // 玩家当前使用的称号缓存: playerUuid -> TitleData
    private final Map<UUID, TitleData> currentTitleCache = new ConcurrentHashMap<>();

    // 玩家当前使用的称号ID缓存: playerUuid -> titleId
    private final Map<UUID, String> currentTitleIdCache = new ConcurrentHashMap<>();

    // 玩家称号数量缓存
    private final Map<UUID, Integer> titleCountCache = new ConcurrentHashMap<>();

    // 正在加载的玩家集合（防止重复加载）
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();

    public TitleCacheManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.repository = plugin.getTitleRepository();
    }

    /**
     * 加载玩家称号数据到缓存
     */
    public void loadPlayerTitles(UUID playerUuid) {
        triggerAsyncLoad(playerUuid);
    }

    /**
     * 卸载玩家缓存（玩家退出时调用）
     */
    public void unloadPlayer(UUID playerUuid) {
        playerTitlesCache.remove(playerUuid);
        currentTitleCache.remove(playerUuid);
        currentTitleIdCache.remove(playerUuid);
        titleCountCache.remove(playerUuid);
        loadingPlayers.remove(playerUuid);
    }

    /**
     * 获取玩家当前使用的称号（从缓存）
     * 如果缓存中没有数据，触发异步加载并返回 null
     */
    public TitleData getCurrentTitle(UUID playerUuid) {
        if (!isLoaded(playerUuid)) {
            triggerAsyncLoad(playerUuid);
        }
        return currentTitleCache.get(playerUuid);
    }

    /**
     * 获取玩家当前使用的称号ID（从缓存）
     * 如果缓存中没有数据，触发异步加载并返回 null
     */
    public String getCurrentTitleId(UUID playerUuid) {
        if (!isLoaded(playerUuid)) {
            triggerAsyncLoad(playerUuid);
        }
        return currentTitleIdCache.get(playerUuid);
    }

    /**
     * 设置玩家当前使用的称号（更新缓存）
     */
    public void setCurrentTitle(UUID playerUuid, String titleId, TitleData titleData) {
        currentTitleCache.put(playerUuid, titleData);
        currentTitleIdCache.put(playerUuid, titleId);
    }

    /**
     * 清除玩家当前使用的称号（更新缓存）
     */
    public void clearCurrentTitle(UUID playerUuid) {
        currentTitleCache.remove(playerUuid);
        currentTitleIdCache.remove(playerUuid);
    }

    /**
     * 添加玩家称号到缓存
     */
    public void addPlayerTitle(UUID playerUuid, String titleId, TitleData titleData) {
        Map<String, TitleData> titleMap = playerTitlesCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
        titleMap.put(titleId, titleData);
        titleCountCache.put(playerUuid, titleMap.size());
    }

    /**
     * 从缓存移除玩家称号
     */
    public void removePlayerTitle(UUID playerUuid, String titleId) {
        Map<String, TitleData> titleMap = playerTitlesCache.get(playerUuid);
        if (titleMap != null) {
            titleMap.remove(titleId);
            titleCountCache.put(playerUuid, titleMap.size());
        }
    }

    /**
     * 获取玩家所有称号（从缓存）
     * 如果缓存中没有数据，返回空 Map 并触发异步加载
     */
    public Map<String, TitleData> getPlayerTitles(UUID playerUuid) {
        Map<String, TitleData> cached = playerTitlesCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        // 缓存中没有数据，触发异步加载
        triggerAsyncLoad(playerUuid);

        return new ConcurrentHashMap<>();
    }

    /**
     * 触发异步加载玩家数据（如果尚未加载）
     */
    private void triggerAsyncLoad(UUID playerUuid) {
        // 检查是否正在加载或已有缓存
        if (loadingPlayers.contains(playerUuid) || playerTitlesCache.containsKey(playerUuid)) {
            return;
        }

        // 标记为正在加载
        if (!loadingPlayers.add(playerUuid)) {
            return; // 并发情况下其他线程已经添加了
        }

        // 异步加载
        repository.getPlayerTitles(playerUuid, titles -> {
            Map<String, TitleData> titleMap = new ConcurrentHashMap<>();
            for (TitleRepository.PlayerTitleEntry entry : titles) {
                titleMap.put(entry.getTitleId(), entry.getTitleData());
                if (entry.isOnUse()) {
                    currentTitleCache.put(playerUuid, entry.getTitleData());
                    currentTitleIdCache.put(playerUuid, entry.getTitleId());
                }
            }
            playerTitlesCache.put(playerUuid, titleMap);
            titleCountCache.put(playerUuid, titles.size());
            loadingPlayers.remove(playerUuid);
        });
    }

    /**
     * 检查玩家数据是否已加载到缓存
     */
    public boolean isLoaded(UUID playerUuid) {
        return playerTitlesCache.containsKey(playerUuid);
    }

    /**
     * 检查玩家是否拥有指定称号（从缓存）
     * 如果缓存中没有数据，触发异步加载并返回 false
     */
    public boolean hasTitle(UUID playerUuid, String titleId) {
        if (!isLoaded(playerUuid)) {
            triggerAsyncLoad(playerUuid);
            return false;
        }
        Map<String, TitleData> titleMap = playerTitlesCache.get(playerUuid);
        return titleMap != null && titleMap.containsKey(titleId);
    }

    /**
     * 获取玩家拥有的称号数量（从缓存）
     * 如果缓存中没有数据，触发异步加载并返回 0
     */
    public int getTitleCount(UUID playerUuid) {
        if (!isLoaded(playerUuid)) {
            triggerAsyncLoad(playerUuid);
            return 0;
        }
        return titleCountCache.getOrDefault(playerUuid, 0);
    }

    /**
     * 刷新玩家缓存（重新从数据库加载）
     */
    public void refresh(UUID playerUuid) {
        unloadPlayer(playerUuid);
        loadPlayerTitles(playerUuid);
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        playerTitlesCache.clear();
        currentTitleCache.clear();
        currentTitleIdCache.clear();
        titleCountCache.clear();
        loadingPlayers.clear();
    }
}
