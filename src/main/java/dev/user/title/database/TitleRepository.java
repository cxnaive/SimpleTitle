package dev.user.title.database;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.model.TitleData;
import dev.user.title.model.TitleType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 称号数据访问层
 * 负责玩家称号和预设称号的 CRUD 操作
 */
public class TitleRepository {

    private final SimpleTitlePlugin plugin;
    private final DatabaseQueue dbQueue;

    public TitleRepository(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.dbQueue = plugin.getDatabaseQueue();
    }

    // ==================== 玩家称号操作 ====================

    /**
     * 异步获取玩家的所有称号
     */
    public void getPlayerTitles(UUID playerUuid, Consumer<List<PlayerTitleEntry>> callback) {
        dbQueue.submit("getPlayerTitles", conn -> {
            List<PlayerTitleEntry> titles = new ArrayList<>();
            String sql = "SELECT title_id, title_data, on_use, obtained_at FROM player_titles WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String titleId = rs.getString("title_id");
                        String titleDataJson = rs.getString("title_data");
                        boolean onUse = rs.getBoolean("on_use");
                        long obtainedAt = rs.getLong("obtained_at");

                        TitleData titleData = TitleData.fromJson(titleDataJson);
                        titles.add(new PlayerTitleEntry(titleId, titleData, onUse, obtainedAt));
                    }
                }
            }
            return titles;
        }, callback, null);
    }

    /**
     * 异步获取玩家当前使用的称号
     */
    public void getCurrentTitle(UUID playerUuid, Consumer<PlayerTitleEntry> callback) {
        dbQueue.submit("getCurrentTitle", conn -> {
            String sql = "SELECT title_id, title_data, on_use, obtained_at FROM player_titles WHERE player_uuid = ? AND on_use = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String titleId = rs.getString("title_id");
                        String titleDataJson = rs.getString("title_data");
                        long obtainedAt = rs.getLong("obtained_at");
                        TitleData titleData = TitleData.fromJson(titleDataJson);
                        return new PlayerTitleEntry(titleId, titleData, true, obtainedAt);
                    }
                }
            }
            return null;
        }, callback, null);
    }

    /**
     * 异步添加玩家称号
     */
    public void addPlayerTitle(UUID playerUuid, String titleId, TitleData titleData, Consumer<Boolean> callback) {
        dbQueue.submit("addPlayerTitle", conn -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            String sql;
            if (isMySQL) {
                sql = "INSERT INTO player_titles (player_uuid, title_id, title_data, on_use, obtained_at) VALUES (?, ?, ?, FALSE, ?) " +
                      "ON DUPLICATE KEY UPDATE title_data = VALUES(title_data)";
            } else {
                sql = "MERGE INTO player_titles (player_uuid, title_id, title_data, on_use, obtained_at) KEY(player_uuid, title_id) VALUES (?, ?, ?, FALSE, ?)";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, titleId);
                ps.setString(3, titleData.toJson());
                ps.setLong(4, System.currentTimeMillis());
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    /**
     * 异步设置玩家当前使用的称号
     * 先清除该玩家所有称号的 on_use，再设置指定称号的 on_use 为 TRUE
     * 使用事务确保原子性
     */
    public void setCurrentTitle(UUID playerUuid, String titleId, Consumer<Boolean> callback) {
        dbQueue.submit("setCurrentTitle", conn -> {
            try {
                // 开启事务
                conn.setAutoCommit(false);

                // 先清除所有称号的 on_use
                String clearSql = "UPDATE player_titles SET on_use = FALSE WHERE player_uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(clearSql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.executeUpdate();
                }

                // 再设置指定称号的 on_use 为 TRUE
                String setSql = "UPDATE player_titles SET on_use = TRUE WHERE player_uuid = ? AND title_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(setSql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, titleId);
                    int rows = ps.executeUpdate();

                    if (rows > 0) {
                        // 提交事务
                        conn.commit();
                        return true;
                    } else {
                        // 称号不存在，回滚
                        conn.rollback();
                        return false;
                    }
                }
            } catch (SQLException e) {
                // 发生异常，回滚
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().warning("事务回滚失败: " + rollbackEx.getMessage());
                }
                throw e;
            } finally {
                // 恢复自动提交
                conn.setAutoCommit(true);
            }
        }, callback, null);
    }

    /**
     * 异步清除玩家当前使用的称号
     */
    public void clearCurrentTitle(UUID playerUuid, Consumer<Boolean> callback) {
        dbQueue.submit("clearCurrentTitle", conn -> {
            String sql = "UPDATE player_titles SET on_use = FALSE WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    /**
     * 异步检查玩家是否拥有指定称号
     */
    public void hasTitle(UUID playerUuid, String titleId, Consumer<Boolean> callback) {
        dbQueue.submit("hasTitle", conn -> {
            String sql = "SELECT 1 FROM player_titles WHERE player_uuid = ? AND title_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, titleId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }, callback, null);
    }

    /**
     * 异步删除玩家称号
     */
    public void removePlayerTitle(UUID playerUuid, String titleId, Consumer<Boolean> callback) {
        dbQueue.submit("removePlayerTitle", conn -> {
            String sql = "DELETE FROM player_titles WHERE player_uuid = ? AND title_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, titleId);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    /**
     * 异步获取玩家拥有的称号数量
     */
    public void getTitleCount(UUID playerUuid, Consumer<Integer> callback) {
        dbQueue.submit("getTitleCount", conn -> {
            String sql = "SELECT COUNT(*) FROM player_titles WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return 0;
        }, callback, null);
    }

    /**
     * 异步检查称号ID是否已存在（精确匹配）
     */
    public void titleIdExists(UUID playerUuid, String titleId, Consumer<Boolean> callback) {
        dbQueue.submit("titleIdExists", conn -> {
            String sql = "SELECT 1 FROM player_titles WHERE player_uuid = ? AND title_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, titleId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }, callback, null);
    }

    // ==================== 预设称号操作 ====================

    /**
     * 异步获取所有预设称号
     */
    public void getAllPresetTitles(Consumer<Map<String, TitleData>> callback) {
        dbQueue.submit("getAllPresetTitles", conn -> {
            Map<String, TitleData> titles = new HashMap<>();
            String sql = "SELECT id, title_data FROM preset_titles WHERE enabled = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String titleDataJson = rs.getString("title_data");
                    TitleData titleData = TitleData.fromJson(titleDataJson);
                    titleData.setType(TitleType.PRESET);
                    titles.put(id, titleData);
                }
            }
            return titles;
        }, callback, null);
    }

    /**
     * 异步获取单个预设称号
     */
    public void getPresetTitle(String titleId, Consumer<TitleData> callback) {
        dbQueue.submit("getPresetTitle", conn -> {
            String sql = "SELECT title_data FROM preset_titles WHERE id = ? AND enabled = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, titleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String titleDataJson = rs.getString("title_data");
                        TitleData titleData = TitleData.fromJson(titleDataJson);
                        titleData.setType(TitleType.PRESET);
                        return titleData;
                    }
                }
            }
            return null;
        }, callback, null);
    }

    /**
     * 异步保存预设称号（插入或更新）
     */
    public void savePresetTitle(String titleId, TitleData titleData, Consumer<Boolean> callback) {
        dbQueue.submit("savePresetTitle", conn -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            String sql;
            if (isMySQL) {
                sql = "INSERT INTO preset_titles (id, title_data, enabled) VALUES (?, ?, TRUE) " +
                      "ON DUPLICATE KEY UPDATE title_data = VALUES(title_data), enabled = TRUE";
            } else {
                sql = "MERGE INTO preset_titles (id, title_data, enabled) KEY(id) VALUES (?, ?, TRUE)";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, titleId);
                ps.setString(2, titleData.toJson());
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    /**
     * 异步删除预设称号
     */
    public void deletePresetTitle(String titleId, Consumer<Boolean> callback) {
        dbQueue.submit("deletePresetTitle", conn -> {
            String sql = "DELETE FROM preset_titles WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, titleId);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    /**
     * 异步禁用预设称号
     */
    public void disablePresetTitle(String titleId, Consumer<Boolean> callback) {
        dbQueue.submit("disablePresetTitle", conn -> {
            String sql = "UPDATE preset_titles SET enabled = FALSE WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, titleId);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    // ==================== 数据模型 ====================

    /**
     * 玩家称号条目
     */
    public static class PlayerTitleEntry {
        private final String titleId;
        private final TitleData titleData;
        private final boolean onUse;
        private final long obtainedAt;

        public PlayerTitleEntry(String titleId, TitleData titleData, boolean onUse, long obtainedAt) {
            this.titleId = titleId;
            this.titleData = titleData;
            this.onUse = onUse;
            this.obtainedAt = obtainedAt;
        }

        public String getTitleId() {
            return titleId;
        }

        public TitleData getTitleData() {
            return titleData;
        }

        public boolean isOnUse() {
            return onUse;
        }

        public long getObtainedAt() {
            return obtainedAt;
        }
    }

    // ==================== 玩家边框操作 ====================

    /**
     * 异步获取玩家拥有的边框ID列表
     */
    public void getPlayerBrackets(UUID playerUuid, Consumer<Set<String>> callback) {
        dbQueue.submit("getPlayerBrackets", conn -> {
            Set<String> bracketIds = new HashSet<>();
            String sql = "SELECT bracket_id FROM player_brackets WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        bracketIds.add(rs.getString("bracket_id"));
                    }
                }
            }
            return bracketIds;
        }, callback, null);
    }

    /**
     * 异步添加玩家边框
     */
    public void addPlayerBracket(UUID playerUuid, String bracketId, Consumer<Boolean> callback) {
        dbQueue.submit("addPlayerBracket", conn -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            String sql;
            if (isMySQL) {
                sql = "INSERT IGNORE INTO player_brackets (player_uuid, bracket_id, obtained_at) VALUES (?, ?, ?)";
            } else {
                sql = "MERGE INTO player_brackets (player_uuid, bracket_id, obtained_at) KEY(player_uuid, bracket_id) VALUES (?, ?, ?)";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, bracketId);
                ps.setLong(3, System.currentTimeMillis());
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    /**
     * 异步移除玩家边框
     */
    public void removePlayerBracket(UUID playerUuid, String bracketId, Consumer<Boolean> callback) {
        dbQueue.submit("removePlayerBracket", conn -> {
            String sql = "DELETE FROM player_brackets WHERE player_uuid = ? AND bracket_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, bracketId);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }, callback, null);
    }

    /**
     * 异步检查玩家是否拥有边框
     */
    public void hasBracket(UUID playerUuid, String bracketId, Consumer<Boolean> callback) {
        dbQueue.submit("hasBracket", conn -> {
            String sql = "SELECT 1 FROM player_brackets WHERE player_uuid = ? AND bracket_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, bracketId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }, callback, null);
    }
}
