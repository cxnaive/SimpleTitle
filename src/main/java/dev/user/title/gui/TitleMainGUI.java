package dev.user.title.gui;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.model.TitleData;
import dev.user.title.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 称号主菜单GUI
 * 显示玩家拥有的所有称号
 */
public class TitleMainGUI extends AbstractGUI {

    private static final int GUI_SIZE = 54; // 6行
    private static final int ITEMS_PER_PAGE = 28; // 每页28个物品（中间区域）

    private final SimpleTitlePlugin plugin;
    private final int page;
    private final Map<String, TitleData> playerTitles;
    private final String currentTitleId;
    private final List<String> titleIds;

    public TitleMainGUI(SimpleTitlePlugin plugin, Player player, int page) {
        super(player, plugin.getConfigManager().getMessage("gui.main-title", "default", "&6我的称号"), GUI_SIZE);
        this.plugin = plugin;
        this.page = page;
        this.playerTitles = plugin.getTitleManager().getPlayerTitles(player.getUniqueId());
        this.currentTitleId = plugin.getTitleCacheManager().getCurrentTitleId(player.getUniqueId());
        this.titleIds = new ArrayList<>(playerTitles.keySet());
    }

    @Override
    protected void initialize() {
        // 填充边框
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        // 计算分页
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, titleIds.size());

        // 填充称号物品（从槽位10开始，跳过第一行和边框）
        int slot = 10;

        for (int i = startIndex; i < endIndex; i++) {
            String titleId = titleIds.get(i);
            TitleData titleData = playerTitles.get(titleId);

            // 跳过边框槽位
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }

            // 如果超过最后一行，停止
            if (slot >= 45) break;

            ItemStack item = createTitleItem(titleId, titleData);
            setItem(slot, item, p -> openDetailGUI(titleId, titleData));

            slot++;
        }

        // 底部导航栏
        // 上一页按钮（槽位46）
        if (page > 0) {
            ItemStack prevBtn = createItem(Material.SPECTRAL_ARROW, "§e上一页", "§7第 " + (page) + " 页");
            setItem(46, prevBtn, p -> {
                TitleMainGUI gui = new TitleMainGUI(plugin, player, page - 1);
                gui.open();
            });
        }

        // 边框商城按钮（槽位48）
        ItemStack bracketBtn = createItem(Material.ITEM_FRAME, "§d边框商城",
                "§7购买称号边框",
                "§7在称号详情页修改边框");
        setItem(48, bracketBtn, p -> {
            BracketShopGUI bracketShopGUI = new BracketShopGUI(plugin, p);
            bracketShopGUI.open();
        });

        // 称号商店按钮（槽位49）
        ItemStack shopBtn = createItem(Material.EMERALD, "§a称号商店", "§7点击浏览可购买的称号");
        setItem(49, shopBtn, p -> {
            TitleShopGUI.open(plugin, player, 0);
        });

        // 自定义称号按钮（槽位50）
        if (plugin.getConfigManager().isCustomTitleEnabled()) {
            ItemStack customBtn = createItem(Material.WRITABLE_BOOK, "§b自定义称号", "§7创建属于你的独特称号");
            setItem(50, customBtn, p -> {
                player.closeInventory();
                startCustomTitleSession(p);
            });
        }

        // 下一页按钮（槽位52）
        int totalPages = (int) Math.ceil((double) titleIds.size() / ITEMS_PER_PAGE);
        if (page < totalPages - 1) {
            ItemStack nextBtn = createItem(Material.SPECTRAL_ARROW, "§e下一页", "§7第 " + (page + 2) + " 页");
            setItem(52, nextBtn, p -> {
                TitleMainGUI gui = new TitleMainGUI(plugin, player, page + 1);
                gui.open();
            });
        }

        // 关闭按钮（槽位53）
        addCloseButton(53);
    }

    /**
     * 创建称号物品
     */
    private ItemStack createTitleItem(String titleId, TitleData titleData) {
        boolean isCurrentUse = titleId.equals(currentTitleId);
        Material material = isCurrentUse ? Material.ENCHANTED_GOLDEN_APPLE : Material.NAME_TAG;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 显示名称为格式化后的称号（使用Component解析颜色）
            meta.displayName(toComponent(titleData.getFormatted()));

            List<String> lore = new ArrayList<>();
            lore.add("§7ID: §f" + titleId);
            lore.add("§7类型: §f" + (titleData.getType() != null ? titleData.getType().getDisplayName() : "未知"));

            if (isCurrentUse) {
                lore.add("");
                lore.add("§a§l当前使用中");
            } else {
                lore.add("");
                lore.add("§e点击查看详情");
            }

            meta.lore(toComponents(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 打开称号详情GUI
     */
    private void openDetailGUI(String titleId, TitleData titleData) {
        TitleDetailGUI.open(plugin, player, titleId, titleData, page);
    }

    /**
     * 开始自定义称号会话
     */
    private void startCustomTitleSession(Player player) {
        plugin.getCustomTitleSessionManager().startSession(player);

        // 显示选择类型的提示
        MessageUtil.send(player, "&e========== 创建自定义称号 ==========");
        MessageUtil.send(player, "&7请选择称号类型：");
        MessageUtil.send(player, "&e1. 静态称号 &7- 固定内容");
        MessageUtil.send(player, "   &7价格: &e" + formatPrice(
                plugin.getConfigManager().getCustomTitlePriceMoney(),
                plugin.getConfigManager().getCustomTitlePricePoints()));
        MessageUtil.send(player, "&e2. 动态称号 &7- 内容循环切换");
        MessageUtil.send(player, "   &7价格: &e" + formatPrice(
                plugin.getConfigManager().getCustomTitleDynamicPriceMoney(),
                plugin.getConfigManager().getCustomTitleDynamicPricePoints()));
        MessageUtil.send(player, "&e请输入 1 或 2 选择类型");
        MessageUtil.send(player, plugin.getConfigManager().getMessage("custom-input-cancel"));
        MessageUtil.send(player, "&e====================================");
    }

    private String formatPrice(double money, int points) {
        StringBuilder sb = new StringBuilder();
        if (money > 0) {
            sb.append(String.format("%.0f金币", money));
        }
        if (points > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(points).append("点券");
        }
        if (sb.length() == 0) {
            sb.append("免费");
        }
        return sb.toString();
    }

    /**
     * 静态打开方法（异步加载数据）
     */
    public static void open(SimpleTitlePlugin plugin, Player player) {
        open(plugin, player, 0);
    }

    public static void open(SimpleTitlePlugin plugin, Player player, int page) {
        // 数据已在缓存中，直接在主线程打开
        player.getScheduler().execute(plugin, () -> {
            TitleMainGUI gui = new TitleMainGUI(plugin, player, page);
            gui.open();
        }, () -> {}, 0L);
    }
}
