package dev.user.title.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
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
        close();

        try {
            ConfigManager config = plugin.getConfigManager();
            String type = config.getDatabaseType();

            if (type.equalsIgnoreCase("mysql")) {
                initMySQL();
            } else {
                initH2();
            }

            createTables();

            plugin.getLogger().info("数据库连接成功！类型: " + type);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
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

        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        dataSource = new HikariDataSource(config);
    }

    private void initH2() {
        HikariConfig config = new HikariConfig();
        String filename = plugin.getConfigManager().getH2Filename();
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        config.setJdbcUrl("jdbc:h2:" + new File(dataFolder, filename).getAbsolutePath() +
                ";AUTO_RECONNECT=TRUE;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setDriverClassName("org.h2.Driver");

        dataSource = new HikariDataSource(config);
    }

    private void createTables() throws SQLException {
        boolean isMySQL = isMySQL();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

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

            String presetTitlesTable = "CREATE TABLE IF NOT EXISTS preset_titles (" +
                    "    id VARCHAR(64) PRIMARY KEY," +
                    "    title_data TEXT NOT NULL," +
                    "    enabled BOOLEAN DEFAULT TRUE" +
                    ")";
            stmt.execute(presetTitlesTable);

            String playerBracketsTable = "CREATE TABLE IF NOT EXISTS player_brackets (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    bracket_id VARCHAR(64) NOT NULL," +
                    "    obtained_at BIGINT NOT NULL," +
                    "    PRIMARY KEY(player_uuid, bracket_id)" +
                    ")";
            stmt.execute(playerBracketsTable);

            createIndexes(stmt, isMySQL);

            plugin.getLogger().info("数据库表创建/检查完成");
        }
    }

    private void createIndexes(Statement stmt, boolean isMySQL) throws SQLException {
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
                    stmt.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")");
                } else {
                    stmt.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + columnName + ")");
                }
            } catch (SQLException e) {
                String msg = e.getMessage().toLowerCase();
                if (msg.contains("duplicate") || msg.contains("already exists")) {
                    plugin.getLogger().fine("索引 " + indexName + " 已存在，跳过创建");
                } else {
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
            dataSource.close();
        }
    }

    public boolean isMySQL() {
        String dbType = plugin.getConfigManager().getDatabaseType().toLowerCase();
        return dbType.equals("mysql") || dbType.equals("mariadb");
    }
}
