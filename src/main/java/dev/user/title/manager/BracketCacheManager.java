package dev.user.title.manager;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.database.TitleRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 边框缓存管理器
 */
public class BracketCacheManager {

    private final SimpleTitlePlugin plugin;
    private final TitleRepository repository;

    // 玩家拥有的边框缓存: playerUuid -> Set<bracketId>
    private final Map<UUID, Set<String>> playerBracketsCache = new ConcurrentHashMap<>();

    // 正在加载的玩家集合
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();

    public BracketCacheManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.repository = plugin.getTitleRepository();
    }

    /**
     * 加载玩家边框数据到缓存
     */
    public void loadPlayerBrackets(UUID playerUuid) {
        repository.getPlayerBrackets(playerUuid, bracketIds -> {
            playerBracketsCache.put(playerUuid, ConcurrentHashMap.newKeySet());
            playerBracketsCache.get(playerUuid).addAll(bracketIds);
            loadingPlayers.remove(playerUuid);
        });
    }

    /**
     * 卸载玩家缓存
     */
    public void unloadPlayer(UUID playerUuid) {
        playerBracketsCache.remove(playerUuid);
        loadingPlayers.remove(playerUuid);
    }

    /**
     * 检查玩家是否拥有边框（仅检查缓存）
     */
    public boolean hasBracket(UUID playerUuid, String bracketId) {
        Set<String> brackets = playerBracketsCache.get(playerUuid);
        return brackets != null && brackets.contains(bracketId);
    }

    /**
     * 获取玩家拥有的边框ID集合
     */
    public Set<String> getOwnedBracketIds(UUID playerUuid) {
        Set<String> cached = playerBracketsCache.get(playerUuid);
        return cached != null ? cached : Set.of();
    }

    /**
     * 添加边框到缓存
     */
    public void addBracket(UUID playerUuid, String bracketId) {
        Set<String> brackets = playerBracketsCache.computeIfAbsent(playerUuid,
                k -> ConcurrentHashMap.newKeySet());
        brackets.add(bracketId);
    }

    /**
     * 从缓存移除边框
     */
    public void removeBracket(UUID playerUuid, String bracketId) {
        Set<String> brackets = playerBracketsCache.get(playerUuid);
        if (brackets != null) {
            brackets.remove(bracketId);
        }
    }

    /**
     * 检查是否已加载
     */
    public boolean isLoaded(UUID playerUuid) {
        return playerBracketsCache.containsKey(playerUuid);
    }

    /**
     * 触发异步加载（如果尚未加载）
     */
    public void triggerAsyncLoad(UUID playerUuid) {
        if (loadingPlayers.contains(playerUuid) || playerBracketsCache.containsKey(playerUuid)) {
            return;
        }

        if (!loadingPlayers.add(playerUuid)) {
            return;
        }

        repository.getPlayerBrackets(playerUuid, bracketIds -> {
            playerBracketsCache.put(playerUuid, ConcurrentHashMap.newKeySet());
            playerBracketsCache.get(playerUuid).addAll(bracketIds);
            loadingPlayers.remove(playerUuid);
        });
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        playerBracketsCache.clear();
        loadingPlayers.clear();
    }
}
