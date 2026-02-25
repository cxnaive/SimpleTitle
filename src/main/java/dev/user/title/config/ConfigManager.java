package dev.user.title.config;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.model.TitleData;
import dev.user.title.model.TitleType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置管理器
 */
public class ConfigManager {

    private final SimpleTitlePlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messagesConfig;
    private FileConfiguration titlesConfig;

    // 数据库配置
    private String databaseType;
    private String h2Filename;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int mysqlPoolSize;

    // 默认边框
    private String defaultBracketLeft;
    private String defaultBracketRight;

    // 称号空格
    private String titlePaddingLeft;
    private String titlePaddingRight;

    // 自定义称号配置
    private boolean customTitleEnabled;
    private int customTitleMaxLength;
    private int customTitleMaxNameLength;
    private double customTitlePriceMoney;
    private int customTitlePricePoints;
    private double customTitleDynamicPriceMoney;
    private int customTitleDynamicPricePoints;
    private List<String> customTitleForbiddenWords;
    private int customTitleSessionTimeout; // 会话超时时间（秒）

    // 动态称号配置
    private int dynamicTitleSwitchInterval;
    private int dynamicTitleMaxContents;

    // 预设称号缓存
    private Map<String, TitleData> presetTitles = new HashMap<>();

    public ConfigManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        // 加载主配置
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // 读取数据库配置
        this.databaseType = config.getString("database.type", "h2");
        this.h2Filename = config.getString("database.h2.filename", "titles");
        this.mysqlHost = config.getString("database.mysql.host", "localhost");
        this.mysqlPort = config.getInt("database.mysql.port", 3306);
        this.mysqlDatabase = config.getString("database.mysql.database", "simpletitle");
        this.mysqlUsername = config.getString("database.mysql.username", "root");
        this.mysqlPassword = config.getString("database.mysql.password", "password");
        this.mysqlPoolSize = config.getInt("database.mysql.pool-size", 5);

        // 读取默认边框
        this.defaultBracketLeft = config.getString("default-bracket.left", "[");
        this.defaultBracketRight = config.getString("default-bracket.right", "]");

        // 读取称号空格
        this.titlePaddingLeft = config.getString("title-padding.left", "");
        this.titlePaddingRight = config.getString("title-padding.right", " ");

        // 读取自定义称号配置
        this.customTitleEnabled = config.getBoolean("custom-title.enabled", true);
        this.customTitleMaxLength = config.getInt("custom-title.max-length", 128);
        this.customTitleMaxNameLength = config.getInt("custom-title.max-name-length", 32);
        this.customTitlePriceMoney = config.getDouble("custom-title.price-money", 1000);
        this.customTitlePricePoints = config.getInt("custom-title.price-points", 10);
        this.customTitleDynamicPriceMoney = config.getDouble("custom-title.dynamic-price-money", 5000);
        this.customTitleDynamicPricePoints = config.getInt("custom-title.dynamic-price-points", 50);
        this.customTitleForbiddenWords = config.getStringList("custom-title.forbidden-words");
        this.customTitleSessionTimeout = config.getInt("custom-title.session-timeout", 30);

        // 读取动态称号配置
        this.dynamicTitleSwitchInterval = config.getInt("dynamic-title.switch-interval", 4);
        this.dynamicTitleMaxContents = config.getInt("dynamic-title.max-contents", 10);

        // 加载消息配置
        loadMessagesConfig();

        // 加载预设称号配置
        loadTitlesConfig();
    }

    private void loadMessagesConfig() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // 合并默认值
        InputStreamReader defaultReader = new InputStreamReader(
                plugin.getResource("messages.yml"), StandardCharsets.UTF_8);
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultReader);
        this.messagesConfig.setDefaults(defaultConfig);
    }

    private void loadTitlesConfig() {
        File titlesFile = new File(plugin.getDataFolder(), "titles.yml");
        if (!titlesFile.exists()) {
            plugin.saveResource("titles.yml", false);
        }
        this.titlesConfig = YamlConfiguration.loadConfiguration(titlesFile);

        // 解析预设称号
        this.presetTitles.clear();
        ConfigurationSection titlesSection = titlesConfig.getConfigurationSection("titles");
        if (titlesSection != null) {
            for (String titleId : titlesSection.getKeys(false)) {
                ConfigurationSection titleSection = titlesSection.getConfigurationSection(titleId);
                if (titleSection != null) {
                    TitleData titleData = parseTitleData(titleSection);
                    titleData.setType(TitleType.PRESET);
                    presetTitles.put(titleId, titleData);
                }
            }
        }

        plugin.getLogger().info("已加载 " + presetTitles.size() + " 个预设称号");
    }

    private TitleData parseTitleData(ConfigurationSection section) {
        TitleData data = new TitleData();

        // 解析 contents 列表
        List<String> contents = section.getStringList("contents");
        if (contents.isEmpty()) {
            // 兼容旧的 content 字段
            String singleContent = section.getString("content", "");
            contents = java.util.Collections.singletonList(singleContent);
        }
        data.setContents(contents);

        data.setBracketLeft(section.getString("bracket-left", defaultBracketLeft));
        data.setBracketRight(section.getString("bracket-right", defaultBracketRight));
        data.setPrefix(section.getString("prefix", ""));
        data.setSuffix(section.getString("suffix", ""));
        data.setDisplayName(section.getString("display-name", ""));
        data.setPriceMoney(section.getDouble("price-money", 0));
        data.setPricePoints(section.getInt("price-points", 0));
        data.setPermission(section.getString("permission", null));
        data.setSlot(section.getInt("slot", 0));
        data.setCategory(section.getString("category", "default"));
        data.setType(TitleType.PRESET);
        return data;
    }

    /**
     * 获取消息
     */
    public String getMessage(String key, String... replacements) {
        String message = messagesConfig.getString(key, "");
        if (message.isEmpty()) {
            return "&c消息未找到: " + key;
        }
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }

    /**
     * 获取预设称号
     */
    public TitleData getPresetTitle(String titleId) {
        return presetTitles.get(titleId);
    }

    /**
     * 获取所有预设称号
     */
    public Map<String, TitleData> getPresetTitles() {
        return new HashMap<>(presetTitles);
    }

    /**
     * 获取预设称号ID集合
     */
    public Set<String> getPresetTitleIds() {
        return presetTitles.keySet();
    }

    // ==================== Getters ====================

    public String getDatabaseType() {
        return databaseType;
    }

    public String getH2Filename() {
        return h2Filename;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public int getMysqlPoolSize() {
        return mysqlPoolSize;
    }

    public String getDefaultBracketLeft() {
        return defaultBracketLeft;
    }

    public String getDefaultBracketRight() {
        return defaultBracketRight;
    }

    public String getTitlePaddingLeft() {
        return titlePaddingLeft;
    }

    public String getTitlePaddingRight() {
        return titlePaddingRight;
    }

    public boolean isCustomTitleEnabled() {
        return customTitleEnabled;
    }

    public int getCustomTitleMaxLength() {
        return customTitleMaxLength;
    }

    public int getCustomTitleMaxNameLength() {
        return customTitleMaxNameLength;
    }

    public double getCustomTitlePriceMoney() {
        return customTitlePriceMoney;
    }

    public int getCustomTitlePricePoints() {
        return customTitlePricePoints;
    }

    public double getCustomTitleDynamicPriceMoney() {
        return customTitleDynamicPriceMoney;
    }

    public int getCustomTitleDynamicPricePoints() {
        return customTitleDynamicPricePoints;
    }

    public List<String> getCustomTitleForbiddenWords() {
        return customTitleForbiddenWords;
    }

    public int getCustomTitleSessionTimeout() {
        return customTitleSessionTimeout;
    }

    public int getDynamicTitleSwitchInterval() {
        return dynamicTitleSwitchInterval;
    }

    public int getDynamicTitleMaxContents() {
        return dynamicTitleMaxContents;
    }

    /**
     * 检查内容是否包含敏感词
     */
    public boolean containsForbiddenWord(String content) {
        String lowerContent = content.toLowerCase();
        for (String word : customTitleForbiddenWords) {
            if (lowerContent.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
