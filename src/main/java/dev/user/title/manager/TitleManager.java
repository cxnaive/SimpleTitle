package dev.user.title.manager;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.config.ConfigManager;
import dev.user.title.database.TitleRepository;
import dev.user.title.economy.EconomyManager;
import dev.user.title.economy.PlayerPointsManager;
import dev.user.title.model.TitleData;
import dev.user.title.model.TitleType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 称号业务逻辑管理器
 */
public class TitleManager {

    private final SimpleTitlePlugin plugin;
    private final ConfigManager configManager;
    private final TitleRepository repository;
    private final TitleCacheManager cacheManager;
    private final EconomyManager economyManager;
    private final PlayerPointsManager playerPointsManager;

    public TitleManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.repository = plugin.getTitleRepository();
        this.cacheManager = plugin.getTitleCacheManager();
        this.economyManager = plugin.getEconomyManager();
        this.playerPointsManager = plugin.getPlayerPointsManager();
    }

    /**
     * 玩家登录时加载称号数据
     */
    public void onPlayerJoin(UUID playerUuid) {
        cacheManager.loadPlayerTitles(playerUuid);
    }

    /**
     * 玩家退出时清理缓存
     */
    public void onPlayerQuit(UUID playerUuid) {
        cacheManager.unloadPlayer(playerUuid);
    }

    /**
     * 获取玩家当前使用的称号
     */
    public TitleData getCurrentTitle(UUID playerUuid) {
        return cacheManager.getCurrentTitle(playerUuid);
    }

    /**
     * 获取玩家所有称号
     */
    public Map<String, TitleData> getPlayerTitles(UUID playerUuid) {
        return cacheManager.getPlayerTitles(playerUuid);
    }

    /**
     * 检查玩家是否拥有指定称号
     */
    public boolean hasTitle(UUID playerUuid, String titleId) {
        return cacheManager.hasTitle(playerUuid, titleId);
    }

    /**
     * 获取玩家拥有的称号数量
     */
    public int getTitleCount(UUID playerUuid) {
        return cacheManager.getTitleCount(playerUuid);
    }

    /**
     * 购买预设称号
     * @return 购买结果
     */
    public void purchasePresetTitle(Player player, String titleId, Consumer<PurchaseResult> callback) {
        UUID playerUuid = player.getUniqueId();

        // 检查称号是否存在
        TitleData titleData = configManager.getPresetTitle(titleId);
        if (titleData == null) {
            callback.accept(PurchaseResult.NOT_FOUND);
            return;
        }

        // 检查是否已拥有
        if (hasTitle(playerUuid, titleId)) {
            callback.accept(PurchaseResult.ALREADY_OWNED);
            return;
        }

        // 检查权限
        if (titleData.requiresPermission() && !player.hasPermission(titleData.getPermission())) {
            callback.accept(PurchaseResult.NO_PERMISSION);
            return;
        }

        // 检查价格类型并处理支付
        boolean needMoney = titleData.getPriceMoney() > 0;
        boolean needPoints = titleData.getPricePoints() > 0;

        if (needMoney && !economyManager.isEnabled()) {
            callback.accept(PurchaseResult.ECONOMY_NOT_AVAILABLE);
            return;
        }

        if (needPoints && !playerPointsManager.isEnabled()) {
            callback.accept(PurchaseResult.POINTS_NOT_AVAILABLE);
            return;
        }

        // 先检查余额
        if (needMoney) {
            if (!economyManager.hasEnough(player, titleData.getPriceMoney())) {
                callback.accept(PurchaseResult.NOT_ENOUGH_MONEY);
                return;
            }
        }

        if (needPoints) {
            if (!playerPointsManager.hasEnoughPoints(player, titleData.getPricePoints())) {
                callback.accept(PurchaseResult.NOT_ENOUGH_POINTS);
                return;
            }
        }

        // 执行支付
        if (needMoney) {
            economyManager.withdrawAsync(player, titleData.getPriceMoney(), success -> {
                if (!success) {
                    callback.accept(PurchaseResult.PAYMENT_FAILED);
                    return;
                }

                // 扣除点券（如果需要）
                if (needPoints) {
                    playerPointsManager.takePointsAsync(player, titleData.getPricePoints(), pointsSuccess -> {
                        if (!pointsSuccess) {
                            // 退还金币
                            economyManager.deposit(player, titleData.getPriceMoney());
                            callback.accept(PurchaseResult.PAYMENT_FAILED);
                            return;
                        }
                        // 支付成功，给予称号
                        giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                    });
                } else {
                    giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                }
            });
        } else if (needPoints) {
            playerPointsManager.takePointsAsync(player, titleData.getPricePoints(), success -> {
                if (!success) {
                    callback.accept(PurchaseResult.PAYMENT_FAILED);
                    return;
                }
                giveTitleToPlayer(playerUuid, titleId, titleData, callback);
            });
        } else {
            // 免费称号
            giveTitleToPlayer(playerUuid, titleId, titleData, callback);
        }
    }

    private void giveTitleToPlayer(UUID playerUuid, String titleId, TitleData titleData, Consumer<PurchaseResult> callback) {
        repository.addPlayerTitle(playerUuid, titleId, titleData, success -> {
            if (success) {
                cacheManager.addPlayerTitle(playerUuid, titleId, titleData);
                callback.accept(PurchaseResult.SUCCESS);
            } else {
                callback.accept(PurchaseResult.DATABASE_ERROR);
            }
        });
    }

    /**
     * 购买自定义称号
     */
    public void purchaseCustomTitle(Player player, String content, Consumer<PurchaseResult> callback) {
        UUID playerUuid = player.getUniqueId();

        // 检查自定义称号功能是否启用
        if (!configManager.isCustomTitleEnabled()) {
            callback.accept(PurchaseResult.CUSTOM_DISABLED);
            return;
        }

        // 检查长度
        if (content.length() > configManager.getCustomTitleMaxLength()) {
            callback.accept(PurchaseResult.TOO_LONG);
            return;
        }

        // 检查敏感词
        if (configManager.containsForbiddenWord(content)) {
            callback.accept(PurchaseResult.FORBIDDEN_WORD);
            return;
        }

        // 生成自定义称号ID
        String customTitleId = "custom_" + playerUuid.toString().substring(0, 8) + "_" + System.currentTimeMillis();

        // 创建称号数据
        TitleData titleData = TitleData.createCustom(
                content,
                configManager.getDefaultBracketLeft(),
                configManager.getDefaultBracketRight(),
                "",
                ""
        );

        double priceMoney = configManager.getCustomTitlePriceMoney();
        int pricePoints = configManager.getCustomTitlePricePoints();

        // 处理支付
        boolean needMoney = priceMoney > 0;
        boolean needPoints = pricePoints > 0;

        if (needMoney && !economyManager.isEnabled()) {
            callback.accept(PurchaseResult.ECONOMY_NOT_AVAILABLE);
            return;
        }

        if (needPoints && !playerPointsManager.isEnabled()) {
            callback.accept(PurchaseResult.POINTS_NOT_AVAILABLE);
            return;
        }

        // 检查余额
        if (needMoney && !economyManager.hasEnough(player, priceMoney)) {
            callback.accept(PurchaseResult.NOT_ENOUGH_MONEY);
            return;
        }

        if (needPoints && !playerPointsManager.hasEnoughPoints(player, pricePoints)) {
            callback.accept(PurchaseResult.NOT_ENOUGH_POINTS);
            return;
        }

        // 执行支付
        if (needMoney) {
            economyManager.withdrawAsync(player, priceMoney, success -> {
                if (!success) {
                    callback.accept(PurchaseResult.PAYMENT_FAILED);
                    return;
                }
                if (needPoints) {
                    playerPointsManager.takePointsAsync(player, pricePoints, pointsSuccess -> {
                        if (!pointsSuccess) {
                            economyManager.deposit(player, priceMoney);
                            callback.accept(PurchaseResult.PAYMENT_FAILED);
                            return;
                        }
                        giveTitleToPlayer(playerUuid, customTitleId, titleData, callback);
                    });
                } else {
                    giveTitleToPlayer(playerUuid, customTitleId, titleData, callback);
                }
            });
        } else if (needPoints) {
            playerPointsManager.takePointsAsync(player, pricePoints, success -> {
                if (!success) {
                    callback.accept(PurchaseResult.PAYMENT_FAILED);
                    return;
                }
                giveTitleToPlayer(playerUuid, customTitleId, titleData, callback);
            });
        } else {
            giveTitleToPlayer(playerUuid, customTitleId, titleData, callback);
        }
    }

    /**
     * 创建自定义称号（带名称）
     * @param player 玩家
     * @param content 称号内容
     * @param name 称号名称（会组合成 玩家名_名称 作为ID）
     * @param callback 回调
     */
    public void createCustomTitleWithName(Player player, String content, String name, Consumer<PurchaseResult> callback) {
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        // 检查自定义称号功能是否启用
        if (!configManager.isCustomTitleEnabled()) {
            callback.accept(PurchaseResult.CUSTOM_DISABLED);
            return;
        }

        // 检查内容长度
        if (content.length() > configManager.getCustomTitleMaxLength()) {
            callback.accept(PurchaseResult.TOO_LONG);
            return;
        }

        // 检查名称长度
        if (name.length() > configManager.getCustomTitleMaxNameLength()) {
            callback.accept(PurchaseResult.NAME_TOO_LONG);
            return;
        }

        // 检查内容敏感词
        if (configManager.containsForbiddenWord(content)) {
            callback.accept(PurchaseResult.FORBIDDEN_WORD);
            return;
        }

        // 检查名称敏感词
        if (configManager.containsForbiddenWord(name)) {
            callback.accept(PurchaseResult.FORBIDDEN_WORD);
            return;
        }

        // 生成称号ID
        String titleId = playerName + "_" + name;

        // 检查是否已有同名称号（先检查缓存，再查数据库）
        if (cacheManager.hasTitle(playerUuid, titleId)) {
            callback.accept(PurchaseResult.NAME_DUPLICATE);
            return;
        }

        // 异步检查数据库
        repository.titleIdExists(playerUuid, titleId, exists -> {
            if (exists) {
                callback.accept(PurchaseResult.NAME_DUPLICATE);
                return;
            }

            // 创建称号数据
            TitleData titleData = TitleData.createCustom(
                    content,
                    configManager.getDefaultBracketLeft(),
                    configManager.getDefaultBracketRight(),
                    "",
                    ""
            );

            double priceMoney = configManager.getCustomTitlePriceMoney();
            int pricePoints = configManager.getCustomTitlePricePoints();

            boolean needMoney = priceMoney > 0;
            boolean needPoints = pricePoints > 0;

            if (needMoney && !economyManager.isEnabled()) {
                callback.accept(PurchaseResult.ECONOMY_NOT_AVAILABLE);
                return;
            }

            if (needPoints && !playerPointsManager.isEnabled()) {
                callback.accept(PurchaseResult.POINTS_NOT_AVAILABLE);
                return;
            }

            // 检查余额
            if (needMoney && !economyManager.hasEnough(player, priceMoney)) {
                callback.accept(PurchaseResult.NOT_ENOUGH_MONEY);
                return;
            }

            if (needPoints && !playerPointsManager.hasEnoughPoints(player, pricePoints)) {
                callback.accept(PurchaseResult.NOT_ENOUGH_POINTS);
                return;
            }

            // 执行支付
            if (needMoney) {
                economyManager.withdrawAsync(player, priceMoney, success -> {
                    if (!success) {
                        callback.accept(PurchaseResult.PAYMENT_FAILED);
                        return;
                    }
                    if (needPoints) {
                        playerPointsManager.takePointsAsync(player, pricePoints, pointsSuccess -> {
                            if (!pointsSuccess) {
                                economyManager.deposit(player, priceMoney);
                                callback.accept(PurchaseResult.PAYMENT_FAILED);
                                return;
                            }
                            giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                        });
                    } else {
                        giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                    }
                });
            } else if (needPoints) {
                playerPointsManager.takePointsAsync(player, pricePoints, success -> {
                    if (!success) {
                        callback.accept(PurchaseResult.PAYMENT_FAILED);
                        return;
                    }
                    giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                });
            } else {
                giveTitleToPlayer(playerUuid, titleId, titleData, callback);
            }
        });
    }

    /**
     * 创建动态自定义称号
     * @param player 玩家
     * @param contents 内容列表
     * @param name 称号名称
     * @param callback 回调
     */
    public void createDynamicCustomTitle(Player player, java.util.List<String> contents, String name, Consumer<PurchaseResult> callback) {
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        // 检查自定义称号功能是否启用
        if (!configManager.isCustomTitleEnabled()) {
            callback.accept(PurchaseResult.CUSTOM_DISABLED);
            return;
        }

        // 检查内容数量
        if (contents == null || contents.isEmpty()) {
            callback.accept(PurchaseResult.TOO_LONG);
            return;
        }

        int maxContents = configManager.getDynamicTitleMaxContents();
        if (contents.size() > maxContents) {
            callback.accept(PurchaseResult.TOO_LONG);
            return;
        }

        // 检查每个内容
        int maxLength = configManager.getCustomTitleMaxLength();
        for (String content : contents) {
            if (content.length() > maxLength) {
                callback.accept(PurchaseResult.TOO_LONG);
                return;
            }
            if (configManager.containsForbiddenWord(content)) {
                callback.accept(PurchaseResult.FORBIDDEN_WORD);
                return;
            }
        }

        // 检查名称
        if (name.length() > configManager.getCustomTitleMaxNameLength()) {
            callback.accept(PurchaseResult.NAME_TOO_LONG);
            return;
        }
        if (configManager.containsForbiddenWord(name)) {
            callback.accept(PurchaseResult.FORBIDDEN_WORD);
            return;
        }

        String titleId = playerName + "_" + name;

        if (cacheManager.hasTitle(playerUuid, titleId)) {
            callback.accept(PurchaseResult.NAME_DUPLICATE);
            return;
        }

        repository.titleIdExists(playerUuid, titleId, exists -> {
            if (exists) {
                callback.accept(PurchaseResult.NAME_DUPLICATE);
                return;
            }

            // 创建动态称号数据
            TitleData titleData = new TitleData();
            titleData.setContents(contents);
            titleData.setBracketLeft(configManager.getDefaultBracketLeft());
            titleData.setBracketRight(configManager.getDefaultBracketRight());
            titleData.setType(TitleType.CUSTOM);

            double priceMoney = configManager.getCustomTitleDynamicPriceMoney();
            int pricePoints = configManager.getCustomTitleDynamicPricePoints();

            boolean needMoney = priceMoney > 0;
            boolean needPoints = pricePoints > 0;

            if (needMoney && !economyManager.isEnabled()) {
                callback.accept(PurchaseResult.ECONOMY_NOT_AVAILABLE);
                return;
            }

            if (needPoints && !playerPointsManager.isEnabled()) {
                callback.accept(PurchaseResult.POINTS_NOT_AVAILABLE);
                return;
            }

            if (needMoney && !economyManager.hasEnough(player, priceMoney)) {
                callback.accept(PurchaseResult.NOT_ENOUGH_MONEY);
                return;
            }

            if (needPoints && !playerPointsManager.hasEnoughPoints(player, pricePoints)) {
                callback.accept(PurchaseResult.NOT_ENOUGH_POINTS);
                return;
            }

            // 执行支付
            if (needMoney) {
                economyManager.withdrawAsync(player, priceMoney, success -> {
                    if (!success) {
                        callback.accept(PurchaseResult.PAYMENT_FAILED);
                        return;
                    }
                    if (needPoints) {
                        playerPointsManager.takePointsAsync(player, pricePoints, pointsSuccess -> {
                            if (!pointsSuccess) {
                                economyManager.deposit(player, priceMoney);
                                callback.accept(PurchaseResult.PAYMENT_FAILED);
                                return;
                            }
                            giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                        });
                    } else {
                        giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                    }
                });
            } else if (needPoints) {
                playerPointsManager.takePointsAsync(player, pricePoints, success -> {
                    if (!success) {
                        callback.accept(PurchaseResult.PAYMENT_FAILED);
                        return;
                    }
                    giveTitleToPlayer(playerUuid, titleId, titleData, callback);
                });
            } else {
                giveTitleToPlayer(playerUuid, titleId, titleData, callback);
            }
        });
    }

    /**
     * 检查称号ID是否已存在
     */
    public void checkTitleIdExists(UUID playerUuid, String titleId, Consumer<Boolean> callback) {
        // 先检查缓存
        if (cacheManager.hasTitle(playerUuid, titleId)) {
            callback.accept(true);
            return;
        }
        // 再查数据库
        repository.titleIdExists(playerUuid, titleId, callback);
    }

    /**
     * 设置玩家当前使用的称号
     */
    public void setCurrentTitle(UUID playerUuid, String titleId, Consumer<Boolean> callback) {
        // 检查是否拥有该称号
        if (!hasTitle(playerUuid, titleId)) {
            callback.accept(false);
            return;
        }

        TitleData titleData = cacheManager.getPlayerTitles(playerUuid).get(titleId);
        if (titleData == null) {
            callback.accept(false);
            return;
        }

        repository.setCurrentTitle(playerUuid, titleId, success -> {
            if (success) {
                cacheManager.setCurrentTitle(playerUuid, titleId, titleData);
            }
            callback.accept(success);
        });
    }

    /**
     * 清除玩家当前使用的称号
     */
    public void clearCurrentTitle(UUID playerUuid, Consumer<Boolean> callback) {
        repository.clearCurrentTitle(playerUuid, success -> {
            if (success) {
                cacheManager.clearCurrentTitle(playerUuid);
            }
            callback.accept(success);
        });
    }

    /**
     * 给予玩家称号（管理员命令）
     */
    public void giveTitle(UUID playerUuid, String titleId, TitleData titleData, Consumer<Boolean> callback) {
        repository.addPlayerTitle(playerUuid, titleId, titleData, success -> {
            if (success) {
                cacheManager.addPlayerTitle(playerUuid, titleId, titleData);
            }
            callback.accept(success);
        });
    }

    /**
     * 获取所有预设称号
     */
    public Map<String, TitleData> getPresetTitles() {
        return configManager.getPresetTitles();
    }

    /**
     * 获取预设称号
     */
    public TitleData getPresetTitle(String titleId) {
        return configManager.getPresetTitle(titleId);
    }

    /**
     * 刷新玩家缓存
     */
    public void refreshPlayerCache(UUID playerUuid) {
        cacheManager.refresh(playerUuid);
    }

    /**
     * 更新玩家称号数据（修改边框等）
     */
    public void updatePlayerTitleData(UUID playerUuid, String titleId, TitleData titleData, Consumer<Boolean> callback) {
        // 更新数据库
        repository.addPlayerTitle(playerUuid, titleId, titleData, success -> {
            if (success) {
                // 更新缓存
                cacheManager.addPlayerTitle(playerUuid, titleId, titleData);
            }
            callback.accept(success);
        });
    }

    // ==================== 购买结果枚举 ====================

    public enum PurchaseResult {
        SUCCESS("购买成功"),
        NOT_FOUND("称号不存在"),
        ALREADY_OWNED("已拥有该称号"),
        NO_PERMISSION("没有权限购买"),
        NOT_ENOUGH_MONEY("金币不足"),
        NOT_ENOUGH_POINTS("点券不足"),
        ECONOMY_NOT_AVAILABLE("经济系统不可用"),
        POINTS_NOT_AVAILABLE("点券系统不可用"),
        PAYMENT_FAILED("支付失败"),
        DATABASE_ERROR("数据库错误"),
        CUSTOM_DISABLED("自定义称号功能已禁用"),
        TOO_LONG("称号内容过长"),
        NAME_TOO_LONG("名称过长"),
        NAME_DUPLICATE("名称已存在"),
        FORBIDDEN_WORD("包含敏感词");

        private final String message;

        PurchaseResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
