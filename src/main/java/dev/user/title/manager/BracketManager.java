package dev.user.title.manager;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;
import dev.user.title.database.DatabaseQueue;
import dev.user.title.database.TitleRepository;
import dev.user.title.economy.EconomyManager;
import dev.user.title.economy.PlayerPointsManager;
import dev.user.title.model.BracketData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 边框管理器
 */
public class BracketManager {

    private final SimpleTitlePlugin plugin;
    private final ConfigManager configManager;
    private final TitleRepository repository;
    private final DatabaseQueue databaseQueue;
    private final BracketCacheManager cacheManager;
    private final EconomyManager economyManager;
    private final PlayerPointsManager playerPointsManager;

    // 预设边框缓存
    private Map<String, BracketData> presetBrackets = new HashMap<>();

    public BracketManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.repository = plugin.getTitleRepository();
        this.databaseQueue = plugin.getDatabaseQueue();
        this.cacheManager = plugin.getBracketCacheManager();
        this.economyManager = plugin.getEconomyManager();
        this.playerPointsManager = plugin.getPlayerPointsManager();
    }

    /**
     * 加载边框配置
     */
    public void load() {
        File bracketsFile = new File(plugin.getDataFolder(), "brackets.yml");
        if (!bracketsFile.exists()) {
            plugin.saveResource("brackets.yml", false);
        }
        FileConfiguration bracketsConfig = YamlConfiguration.loadConfiguration(bracketsFile);

        // 合并默认值
        InputStreamReader defaultReader = new InputStreamReader(
                plugin.getResource("brackets.yml"), StandardCharsets.UTF_8);
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultReader);
        bracketsConfig.setDefaults(defaultConfig);

        // 解析边框
        this.presetBrackets.clear();
        ConfigurationSection bracketsSection = bracketsConfig.getConfigurationSection("brackets");
        if (bracketsSection != null) {
            for (String bracketId : bracketsSection.getKeys(false)) {
                ConfigurationSection bracketSection = bracketsSection.getConfigurationSection(bracketId);
                if (bracketSection != null) {
                    BracketData data = parseBracketData(bracketId, bracketSection);
                    presetBrackets.put(bracketId, data);
                }
            }
        }

        plugin.getLogger().info("已加载 " + presetBrackets.size() + " 个预设边框");
    }

    private BracketData parseBracketData(String bracketId, ConfigurationSection section) {
        BracketData data = new BracketData(bracketId);
        data.setBracketLeft(section.getString("bracket-left", "["));
        data.setBracketRight(section.getString("bracket-right", "]"));
        data.setDisplayName(section.getString("display-name", bracketId));
        data.setPriceMoney(section.getDouble("price-money", 0));
        data.setPricePoints(section.getInt("price-points", 0));
        data.setPermission(section.getString("permission", null));
        data.setCategory(section.getString("category", "default"));
        data.setDefault(section.getBoolean("is-default", false));
        return data;
    }

    /**
     * 获取预设边框
     */
    public BracketData getPresetBracket(String bracketId) {
        return presetBrackets.get(bracketId);
    }

    /**
     * 获取所有预设边框
     */
    public Map<String, BracketData> getPresetBrackets() {
        return new HashMap<>(presetBrackets);
    }

    /**
     * 获取预设边框ID集合
     */
    public Set<String> getPresetBracketIds() {
        return presetBrackets.keySet();
    }

    /**
     * 获取所有默认边框
     */
    public List<BracketData> getDefaultBrackets() {
        List<BracketData> defaults = new ArrayList<>();
        for (BracketData bracket : presetBrackets.values()) {
            if (bracket.isDefault()) {
                defaults.add(bracket);
            }
        }
        return defaults;
    }

    /**
     * 检查玩家是否拥有边框（考虑默认边框）
     */
    public boolean hasBracket(UUID playerUuid, String bracketId) {
        BracketData bracket = presetBrackets.get(bracketId);
        if (bracket == null) return false;

        // 默认边框所有玩家都有
        if (bracket.isDefault()) return true;

        // 检查缓存
        return cacheManager.hasBracket(playerUuid, bracketId);
    }

    /**
     * 获取玩家拥有的所有边框（包括默认边框）
     */
    public List<BracketData> getPlayerBrackets(UUID playerUuid) {
        List<BracketData> result = new ArrayList<>();

        // 添加默认边框
        for (BracketData bracket : presetBrackets.values()) {
            if (bracket.isDefault()) {
                result.add(bracket);
            }
        }

        // 添加购买的边框
        Set<String> ownedIds = cacheManager.getOwnedBracketIds(playerUuid);
        for (String bracketId : ownedIds) {
            BracketData bracket = presetBrackets.get(bracketId);
            if (bracket != null && !bracket.isDefault()) {
                result.add(bracket);
            }
        }

        return result;
    }

    /**
     * 购买边框
     * @return 购买结果
     */
    public PurchaseResult purchaseBracket(Player player, String bracketId) {
        UUID playerUuid = player.getUniqueId();
        BracketData bracket = presetBrackets.get(bracketId);

        if (bracket == null) {
            return PurchaseResult.NOT_FOUND;
        }

        // 默认边框无需购买
        if (bracket.isDefault()) {
            return PurchaseResult.ALREADY_OWNED;
        }

        // 检查是否已拥有
        if (hasBracket(playerUuid, bracketId)) {
            return PurchaseResult.ALREADY_OWNED;
        }

        // 检查权限
        if (bracket.requiresPermission() && !player.hasPermission(bracket.getPermission())) {
            return PurchaseResult.NO_PERMISSION;
        }

        // 检查金币
        if (bracket.requiresMoney()) {
            if (!economyManager.isEnabled()) {
                return PurchaseResult.ECONOMY_NOT_AVAILABLE;
            }
            if (economyManager.getBalance(player) < bracket.getPriceMoney()) {
                return PurchaseResult.NOT_ENOUGH_MONEY;
            }
        }

        // 检查点券
        if (bracket.requiresPoints()) {
            if (!playerPointsManager.isEnabled()) {
                return PurchaseResult.POINTS_NOT_AVAILABLE;
            }
            if (playerPointsManager.getPoints(player) < bracket.getPricePoints()) {
                return PurchaseResult.NOT_ENOUGH_POINTS;
            }
        }

        // 扣除金币
        if (bracket.requiresMoney()) {
            economyManager.withdraw(player, bracket.getPriceMoney());
        }

        // 扣除点券
        if (bracket.requiresPoints()) {
            playerPointsManager.takePoints(player, bracket.getPricePoints());
        }

        // 保存到数据库
        repository.addPlayerBracket(playerUuid, bracketId, null);
        cacheManager.addBracket(playerUuid, bracketId);

        return PurchaseResult.SUCCESS;
    }

    /**
     * 给玩家添加边框（管理员操作，不扣费）
     */
    public void giveBracket(UUID playerUuid, String bracketId) {
        if (!hasBracket(playerUuid, bracketId)) {
            repository.addPlayerBracket(playerUuid, bracketId, null);
            cacheManager.addBracket(playerUuid, bracketId);
        }
    }

    /**
     * 从玩家移除边框
     */
    public void removeBracket(UUID playerUuid, String bracketId) {
        repository.removePlayerBracket(playerUuid, bracketId, null);
        cacheManager.removeBracket(playerUuid, bracketId);
    }

    /**
     * 加载玩家的边框数据
     */
    public void loadPlayerBrackets(UUID playerUuid) {
        cacheManager.loadPlayerBrackets(playerUuid);
    }

    /**
     * 卸载玩家的边框数据
     */
    public void unloadPlayerBrackets(UUID playerUuid) {
        cacheManager.unloadPlayer(playerUuid);
    }

    /**
     * 购买结果枚举
     */
    public enum PurchaseResult {
        SUCCESS("购买成功！"),
        NOT_FOUND("边框不存在！"),
        ALREADY_OWNED("你已经拥有这个边框了！"),
        NO_PERMISSION("你没有权限购买这个边框！"),
        NOT_ENOUGH_MONEY("金币不足！"),
        NOT_ENOUGH_POINTS("点券不足！"),
        ECONOMY_NOT_AVAILABLE("经济系统不可用！"),
        POINTS_NOT_AVAILABLE("点券系统不可用！");

        private final String message;

        PurchaseResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
