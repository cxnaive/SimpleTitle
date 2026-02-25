package dev.user.title.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库管理器
 * 支持 H2（本地）和 MySQL（跨服）数据库
 */
public class DatabaseManager {

    private final SimpleTitlePlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        // 先关闭可能存在的旧连接（处理PlugManX重载情况）
        close();

        // 保存原始 ClassLoader
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // 使用插件的 ClassLoader 作为上下文 ClassLoader
            // 这样 HikariCP 就能找到打包的 H2 驱动
            Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

            ConfigManager config = plugin.getConfigManager();
            String type = config.getDatabaseType();

            if (type.equalsIgnoreCase("mysql")) {
                initMySQL();
            } else {
                initH2();
            }

            // 创建表
            createTables();

            plugin.getLogger().info("数据库连接成功！类型: " + type);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // 恢复原始 ClassLoader
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void initMySQL() {
        HikariConfig config = new HikariConfig();
        ConfigManager cfg = plugin.getConfigManager();
        String host = cfg.getMysqlHost();
        int port = cfg.getMysqlPort();
        String database = cfg.getMysqlDatabase();
        String username = cfg.getMysqlUsername();
        String password = cfg.getMysqlPassword();
        int poolSize = cfg.getMysqlPoolSize();

        // 手动注册 MySQL 驱动（使用重定位后的类名）
        try {
            Driver mysqlDriver = (Driver) Class.forName("dev.user.title.libs.com.mysql.cj.jdbc.Driver", true, plugin.getClass().getClassLoader()).getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(mysqlDriver));
        } catch (Exception e) {
            plugin.getLogger().warning("MySQL 驱动注册失败（可能已注册）: " + e.getMessage());
        }

        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setDriverClassName("dev.user.title.libs.com.mysql.cj.jdbc.Driver");

        dataSource = new HikariDataSource(config);
    }

    private void initH2() {
        HikariConfig config = new HikariConfig();
        String filename = plugin.getConfigManager().getH2Filename();
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // 手动注册 H2 驱动（使用重定位后的类名）
        try {
            Driver h2Driver = (Driver) Class.forName("dev.user.title.libs.org.h2.Driver", true, plugin.getClass().getClassLoader()).getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(h2Driver));
        } catch (Exception e) {
            plugin.getLogger().warning("H2 驱动注册失败（可能已注册）: " + e.getMessage());
        }

        // DB_CLOSE_DELAY=0: 连接关闭时立即释放文件锁（对PlugMan重载很重要）
        // DB_CLOSE_ON_EXIT=FALSE: 防止VM关闭时自动关闭数据库
        // AUTO_RECONNECT=TRUE: 自动重连
        config.setJdbcUrl("jdbc:h2:" + new File(dataFolder, filename).getAbsolutePath() +
                ";AUTO_RECONNECT=TRUE;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setDriverClassName("dev.user.title.libs.org.h2.Driver");
        // 连接测试查询，确保连接可用
        config.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(config);
    }

    /**
     * JDBC 驱动包装类，用于在 Shadow 打包后正确注册驱动
     */
    private static class DriverShim implements Driver {
        private final Driver driver;

        DriverShim(Driver driver) {
            this.driver = driver;
        }

        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException {
            return driver.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return driver.acceptsURL(url);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
            return driver.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(DriverShim.class.getName());
        }
    }

    private void createTables() throws SQLException {
        boolean isMySQL = isMySQL();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // 玩家称号表
            String idColumn = isMySQL ? "id BIGINT AUTO_INCREMENT PRIMARY KEY" : "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
            String playerTitlesTable = "CREATE TABLE IF NOT EXISTS player_titles (" +
                    idColumn + "," +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    title_id VARCHAR(64) NOT NULL," +
                    "    title_data TEXT NOT NULL," +
                    "    on_use BOOLEAN DEFAULT FALSE," +
                    "    obtained_at BIGINT NOT NULL," +
                    "    UNIQUE(player_uuid, title_id)" +
                    ")";
            stmt.execute(playerTitlesTable);

            // 预设称号表
            String presetTitlesTable = "CREATE TABLE IF NOT EXISTS preset_titles (" +
                    "    id VARCHAR(64) PRIMARY KEY," +
                    "    title_data TEXT NOT NULL," +
                    "    enabled BOOLEAN DEFAULT TRUE" +
                    ")";
            stmt.execute(presetTitlesTable);

            // 玩家边框表
            String playerBracketsTable = "CREATE TABLE IF NOT EXISTS player_brackets (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    bracket_id VARCHAR(64) NOT NULL," +
                    "    obtained_at BIGINT NOT NULL," +
                    "    PRIMARY KEY(player_uuid, bracket_id)" +
                    ")";
            stmt.execute(playerBracketsTable);

            // 创建索引
            createIndexes(stmt, isMySQL);

            plugin.getLogger().info("数据库表创建/检查完成");
        }
    }

    /**
     * 创建数据库索引
     * MySQL 不支持 IF NOT EXISTS，需要手动检查
     */
    private void createIndexes(Statement stmt, boolean isMySQL) throws SQLException {
        // 定义要创建的索引
        String[][] indexes = {
            {"idx_player_on_use", "player_titles", "player_uuid, on_use"},
            {"idx_player_uuid", "player_titles", "player_uuid"}
        };

        for (String[] index : indexes) {
            String indexName = index[0];
            String tableName = index[1];
            String columnName = index[2];

            try {
                if (isMySQL) {
                    // MySQL: 直接执行，如果索引已存在会报错，捕获异常即可
                    stmt.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")");
                } else {
                    // H2: 支持 IF NOT EXISTS
                    stmt.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + columnName + ")");
                }
            } catch (SQLException e) {
                // 检查是否是"索引已存在"的错误
                String msg = e.getMessage().toLowerCase();
                if (msg.contains("duplicate") || msg.contains("already exists")) {
                    // 索引已存在，忽略错误
                    plugin.getLogger().fine("索引 " + indexName + " 已存在，跳过创建");
                } else {
                    // 其他错误，重新抛出
                    throw e;
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            // 关闭连接池（DB_CLOSE_DELAY=0 确保连接立即释放）
            dataSource.close();

            // H2 数据库在关闭后需要一点时间完全释放文件锁
            // PlugMan 重载时需要确保文件锁被释放
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}

            // 强制触发GC以清理未关闭的资源引用
            System.gc();

            // 再等待一段时间确保文件锁被释放
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}

            // 尝试注销驱动（解决PlugMan重载时的驱动冲突）
            try {
                // 使用插件ClassLoader查找驱动类并注销
                ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
                java.util.Enumeration<Driver> drivers = DriverManager.getDrivers();
                while (drivers.hasMoreElements()) {
                    Driver driver = drivers.nextElement();
                    // 检查驱动是否来自此插件的ClassLoader
                    if (driver.getClass().getClassLoader() == pluginClassLoader) {
                        DriverManager.deregisterDriver(driver);
                    }
                }
            } catch (Exception ignored) {
                // 驱动可能不存在或未注册，忽略错误
            }
        }
    }

    public boolean isMySQL() {
        String dbType = plugin.getConfigManager().getDatabaseType().toLowerCase();
        return dbType.equals("mysql") || dbType.equals("mariadb");
    }
}
