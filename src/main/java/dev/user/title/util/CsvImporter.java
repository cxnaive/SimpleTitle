package dev.user.title.util;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;
import dev.user.title.model.TitleData;
import dev.user.title.model.TitleType;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * CSV 数据导入工具
 * 用于从其他称号插件导入数据
 */
public class CsvImporter {

    private final SimpleTitlePlugin plugin;

    public CsvImporter(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 导入结果
     */
    public static class ImportResult {
        private final int total;
        private final int success;
        private final int skipped;
        private final List<String> errors;

        public ImportResult(int total, int success, int skipped, List<String> errors) {
            this.total = total;
            this.success = success;
            this.skipped = skipped;
            this.errors = errors;
        }

        public int getTotal() { return total; }
        public int getSuccess() { return success; }
        public int getSkipped() { return skipped; }
        public List<String> getErrors() { return errors; }
    }

    /**
     * 导入 PLT 格式的 CSV 文件
     * @param fileName CSV文件名（位于插件目录下）
     * @param callback 回调函数
     */
    public void importPltCsv(String fileName, Consumer<ImportResult> callback) {
        File csvFile = new File(plugin.getDataFolder(), fileName);
        if (!csvFile.exists()) {
            callback.accept(new ImportResult(0, 0, 0, List.of("文件不存在: " + fileName)));
            return;
        }

        // 异步处理
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                ImportResult result = doImport(csvFile);
                callback.accept(result);
            } catch (Exception e) {
                plugin.getLogger().severe("CSV导入失败: " + e.getMessage());
                e.printStackTrace();
                callback.accept(new ImportResult(0, 0, 0, List.of("导入失败: " + e.getMessage())));
            }
        });
    }

    private ImportResult doImport(File csvFile) throws IOException {
        ConfigManager configManager = plugin.getConfigManager();
        String defaultLeft = configManager.getDefaultBracketLeft();
        String defaultRight = configManager.getDefaultBracketRight();

        // 按玩家分组的数据
        Map<UUID, PlayerImportData> playerDataMap = new HashMap<>();
        List<String> errors = new ArrayList<>();
        int total = 0;
        int lineNum = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                // 跳过表头
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                try {
                    // 解析 CSV 行
                    String[] fields = parseCsvLine(line);
                    if (fields.length < 7) {
                        errors.add("第 " + lineNum + " 行: 字段不足");
                        continue;
                    }

                    total++;

                    String playerName = fields[1].replace("\"", "");
                    String uuidStr = fields[2].replace("\"", "");
                    int originalTitleId = Integer.parseInt(fields[3].replace("\"", ""));
                    String titleName = fields[4];
                    int isUse = Integer.parseInt(fields[6].replace("\"", ""));

                    UUID playerUuid;
                    try {
                        playerUuid = UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException e) {
                        errors.add("第 " + lineNum + " 行: 无效的UUID " + uuidStr);
                        continue;
                    }

                    // 生成新的称号ID: 玩家名_原ID
                    String newTitleId = playerName + "_" + originalTitleId;

                    // 解析称号内容（去掉原边框）
                    String content = parseTitleContent(titleName);

                    // 创建 TitleData
                    TitleData titleData = new TitleData();
                    titleData.setContents(List.of(content));
                    titleData.setBracketLeft(defaultLeft);
                    titleData.setBracketRight(defaultRight);
                    titleData.setPrefix("");
                    titleData.setSuffix("");
                    titleData.setType(TitleType.CUSTOM);
                    titleData.setDisplayName(content.replaceAll("§[0-9a-fk-or]", "")
                            .replaceAll("&[0-9a-fk-or]", "")
                            .replaceAll("&#[0-9a-fA-F]{6}", ""));

                    // 添加到玩家数据
                    PlayerImportData playerData = playerDataMap.computeIfAbsent(playerUuid, k -> new PlayerImportData(playerName));
                    playerData.addTitle(newTitleId, titleData, isUse == 1);

                } catch (Exception e) {
                    errors.add("第 " + lineNum + " 行: " + e.getMessage());
                }
            }
        }

        // 批量写入数据库
        int success = 0;
        int skipped = 0;

        for (Map.Entry<UUID, PlayerImportData> entry : playerDataMap.entrySet()) {
            UUID playerUuid = entry.getKey();
            PlayerImportData playerData = entry.getValue();

            for (TitleImportData titleData : playerData.titles) {
                // 直接添加到数据库，UNIQUE 约束会自动处理重复
                plugin.getTitleRepository().addPlayerTitle(playerUuid, titleData.titleId, titleData.titleData, null);

                // 如果是当前使用的，设置 on_use
                if (titleData.isUse) {
                    plugin.getTitleRepository().setCurrentTitle(playerUuid, titleData.titleId, null);
                }

                success++;
            }
        }

        return new ImportResult(total, success, skipped, errors);
    }

    /**
     * 解析 CSV 行（处理引号内的逗号）
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());

        return fields.toArray(new String[0]);
    }

    /**
     * 解析称号内容，去掉原边框
     * 输入格式: §f『 内容 §f』
     */
    private String parseTitleContent(String titleName) {
        String content = titleName;

        // 去掉常见的前后缀格式
        // §f『 或 §f「 等
        if (content.startsWith("§f『 ") || content.startsWith("§f「 ")) {
            content = content.substring(4);
        } else if (content.startsWith("§f『") || content.startsWith("§f「")) {
            content = content.substring(3);
        } else if (content.startsWith("『 ") || content.startsWith("「 ")) {
            content = content.substring(2);
        } else if (content.startsWith("『") || content.startsWith("「")) {
            content = content.substring(1);
        }

        // 去掉后缀 §f』 或 §f」
        if (content.endsWith(" §f』") || content.endsWith(" §f」")) {
            content = content.substring(0, content.length() - 4);
        } else if (content.endsWith("§f』") || content.endsWith("§f」")) {
            content = content.substring(0, content.length() - 3);
        } else if (content.endsWith("』") || content.endsWith("」")) {
            content = content.substring(0, content.length() - 1);
        } else if (content.endsWith(" §f』") || content.endsWith(" §f」")) {
            content = content.substring(0, content.length() - 4);
        }

        return content.trim();
    }

    /**
     * 玩家导入数据
     */
    private static class PlayerImportData {
        final String playerName;
        final List<TitleImportData> titles = new ArrayList<>();

        PlayerImportData(String playerName) {
            this.playerName = playerName;
        }

        void addTitle(String titleId, TitleData titleData, boolean isUse) {
            titles.add(new TitleImportData(titleId, titleData, isUse));
        }
    }

    /**
     * 称号导入数据
     */
    private static class TitleImportData {
        final String titleId;
        final TitleData titleData;
        final boolean isUse;

        TitleImportData(String titleId, TitleData titleData, boolean isUse) {
            this.titleId = titleId;
            this.titleData = titleData;
            this.isUse = isUse;
        }
    }
}
