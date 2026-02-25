package dev.user.title;

import dev.user.title.command.TitleCommand;
import dev.user.title.config.ConfigManager;
import dev.user.title.database.DatabaseManager;
import dev.user.title.database.DatabaseQueue;
import dev.user.title.database.TitleRepository;
import dev.user.title.economy.EconomyManager;
import dev.user.title.economy.PlayerPointsManager;
import dev.user.title.listener.GUIListener;
import dev.user.title.listener.PlayerListener;
import dev.user.title.manager.TitleCacheManager;
import dev.user.title.manager.TitleManager;
import dev.user.title.manager.CustomTitleSessionManager;
import dev.user.title.manager.DynamicTitleManager;
import dev.user.title.manager.BracketManager;
import dev.user.title.manager.BracketCacheManager;
import dev.user.title.util.CsvImporter;
import dev.user.title.placeholder.TitleExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SimpleTitle 插件主类
 * 提供玩家称号系统，支持 PlaceholderAPI
 */
public class SimpleTitlePlugin extends JavaPlugin {

    private static SimpleTitlePlugin instance;

    // 配置管理
    private ConfigManager configManager;

    // 数据库
    private DatabaseManager databaseManager;
    private DatabaseQueue databaseQueue;
    private TitleRepository titleRepository;

    // 经济系统
    private EconomyManager economyManager;
    private PlayerPointsManager playerPointsManager;

    // 业务逻辑
    private TitleManager titleManager;
    private TitleCacheManager titleCacheManager;
    private CustomTitleSessionManager customTitleSessionManager;
    private DynamicTitleManager dynamicTitleManager;
    private BracketCacheManager bracketCacheManager;
    private BracketManager bracketManager;
    private CsvImporter csvImporter;

    // PAPI 扩展
    private TitleExpansion titleExpansion;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        // 初始化配置管理器
        this.configManager = new ConfigManager(this);
        configManager.load();

        // 初始化数据库
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getLogger().severe("数据库初始化失败，插件将禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 检查跨服配置
        if (configManager.getDatabaseType().equalsIgnoreCase("h2")) {
            getLogger().warning("============================================");
            getLogger().warning("当前使用 H2 数据库（本地文件模式）");
            getLogger().warning("H2 不支持多服务器同时访问！");
            getLogger().warning("如需跨服部署，请改用 MySQL 数据库");
            getLogger().warning("============================================");
        } else {
            getLogger().info("使用 MySQL 数据库，支持跨服部署");
        }

        // 初始化数据库队列
        this.databaseQueue = new DatabaseQueue(this);

        // 初始化数据访问层
        this.titleRepository = new TitleRepository(this);

        // 初始化经济系统（软依赖）
        this.economyManager = new EconomyManager(this);
        economyManager.init();

        // 初始化点券系统（软依赖）
        this.playerPointsManager = new PlayerPointsManager(this);
        playerPointsManager.init();

        // 初始化缓存管理器
        this.titleCacheManager = new TitleCacheManager(this);

        // 初始化边框缓存管理器
        this.bracketCacheManager = new BracketCacheManager(this);

        // 初始化边框管理器
        this.bracketManager = new BracketManager(this);
        bracketManager.load();

        // 初始化自定义称号会话管理器
        this.customTitleSessionManager = new CustomTitleSessionManager(this);

        // 初始化业务逻辑管理器
        this.titleManager = new TitleManager(this);

        // 初始化动态称号管理器
        this.dynamicTitleManager = new DynamicTitleManager(this);
        dynamicTitleManager.start();

        // 初始化 CSV 导入工具
        this.csvImporter = new CsvImporter(this);

        // 注册 PlaceholderAPI 扩展（硬依赖）
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.titleExpansion = new TitleExpansion(this);
            if (titleExpansion.register()) {
                getLogger().info("PlaceholderAPI 扩展已注册！");
            } else {
                getLogger().severe("PlaceholderAPI 扩展注册失败！");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            getLogger().severe("未找到 PlaceholderAPI，插件无法运行！");
            getLogger().severe("PlaceholderAPI 是本插件的硬依赖，请先安装！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 注册监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);

        // 注册命令
        registerCommands();

        // 加载在线玩家的称号数据
        for (Player player : getServer().getOnlinePlayers()) {
            titleManager.onPlayerJoin(player.getUniqueId());
        }

        getLogger().info("SimpleTitle 插件已启用！");
    }

    @Override
    public void onDisable() {
        // 注销 PAPI 扩展
        if (titleExpansion != null) {
            titleExpansion.unregister();
        }

        // 关闭动态称号管理器
        if (dynamicTitleManager != null) {
            dynamicTitleManager.shutdown();
        }

        // 关闭自定义称号会话管理器
        if (customTitleSessionManager != null) {
            customTitleSessionManager.shutdown();
        }

        // 关闭经济队列
        if (economyManager != null) {
            economyManager.shutdown();
        }

        // 关闭数据库队列
        if (databaseQueue != null) {
            databaseQueue.shutdown();
        }

        // 关闭数据库连接池
        if (databaseManager != null) {
            databaseManager.close();
        }

        // 清空缓存
        if (titleCacheManager != null) {
            titleCacheManager.clearAll();
        }

        getLogger().info("SimpleTitle 插件已禁用！");
    }

    private void registerCommands() {
        TitleCommand titleCommand = new TitleCommand(this);
        getCommand("title").setExecutor(titleCommand);
        getCommand("title").setTabCompleter(titleCommand);
    }

    /**
     * 重载配置
     */
    public void reload() {
        configManager.load();
        // 重启 DynamicTitleManager 以应用新的 switch-interval
        if (dynamicTitleManager != null) {
            dynamicTitleManager.shutdown();
            dynamicTitleManager.start();
        }
        getLogger().info("配置已重载！");
    }

    // ==================== Getters ====================

    public static SimpleTitlePlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DatabaseQueue getDatabaseQueue() {
        return databaseQueue;
    }

    public TitleRepository getTitleRepository() {
        return titleRepository;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PlayerPointsManager getPlayerPointsManager() {
        return playerPointsManager;
    }

    public TitleManager getTitleManager() {
        return titleManager;
    }

    public TitleCacheManager getTitleCacheManager() {
        return titleCacheManager;
    }

    public CustomTitleSessionManager getCustomTitleSessionManager() {
        return customTitleSessionManager;
    }

    public DynamicTitleManager getDynamicTitleManager() {
        return dynamicTitleManager;
    }

    public BracketCacheManager getBracketCacheManager() {
        return bracketCacheManager;
    }

    public BracketManager getBracketManager() {
        return bracketManager;
    }

    public CsvImporter getCsvImporter() {
        return csvImporter;
    }
}
