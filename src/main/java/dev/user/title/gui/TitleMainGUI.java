package dev.user.title.gui;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.model.TitleData;
import dev.user.title.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 称号主菜单GUI
 * 显示玩家拥有的所有称号
 */
public class TitleMainGUI extends AbstractGUI {

    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28;

    private final SimpleTitlePlugin plugin;
    private final UUID ownerUuid;
    private final int page;
    private final Map<String, TitleData> playerTitles;
    private final String currentTitleId;
    private final List<String> titleIds;

    public TitleMainGUI(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid, int page) {
        super(viewer, resolveTitle(plugin, viewer, ownerUuid), GUI_SIZE);
        this.plugin = plugin;
        this.ownerUuid = ownerUuid;
        this.page = page;
        this.playerTitles = plugin.getTitleManager().getPlayerTitles(ownerUuid);
        this.currentTitleId = plugin.getTitleCacheManager().getCurrentTitleId(ownerUuid);
        this.titleIds = new ArrayList<>(playerTitles.keySet());
    }

    public TitleMainGUI(SimpleTitlePlugin plugin, Player player, int page) {
        this(plugin, player, player.getUniqueId(), page);
    }

    private static String resolveTitle(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid) {
        if (ownerUuid.equals(viewer.getUniqueId())) {
            return plugin.getConfigManager().getMessage("gui.main-title", "default", "&6我的称号");
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(ownerUuid);
        String name = target.getName() != null ? target.getName() : ownerUuid.toString().substring(0, 8);
        return "&6管理 " + name + " 的称号";
    }

    @Override
    protected void initialize() {
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, titleIds.size());

        int slot = 10;

        for (int i = startIndex; i < endIndex; i++) {
            String titleId = titleIds.get(i);
            TitleData titleData = playerTitles.get(titleId);

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }

            if (slot >= 45) break;

            ItemStack item = createTitleItem(titleId, titleData);
            setItem(slot, item, p -> openDetailGUI(titleId, titleData));

            slot++;
        }

        boolean isAdmin = !ownerUuid.equals(player.getUniqueId());

        // 上一页
        if (page > 0) {
            ItemStack prevBtn = createItem(Material.SPECTRAL_ARROW, "§e上一页", "§7第 " + (page) + " 页");
            setItem(46, prevBtn, p -> new TitleMainGUI(plugin, player, ownerUuid, page - 1).open());
        }

        if (isAdmin) {
            // 管理员模式：给予称号按钮
            ItemStack giveBtn = createItem(Material.EMERALD, "§a给予称号",
                    "§7免费给予预设称号给该玩家");
            setItem(49, giveBtn, p -> TitleShopGUI.openAdmin(plugin, p, ownerUuid, 0));
        } else {
            // 玩家模式：边框商城、称号商店、自定义称号
            ItemStack bracketBtn = createItem(Material.ITEM_FRAME, "§d边框商城",
                    "§7购买称号边框",
                    "§7在称号详情页修改边框");
            setItem(48, bracketBtn, p -> BracketShopGUI.open(plugin, p, 0));

            ItemStack shopBtn = createItem(Material.EMERALD, "§a称号商店", "§7点击浏览可购买的称号");
            setItem(49, shopBtn, p -> TitleShopGUI.open(plugin, player, 0));

            if (plugin.getConfigManager().isCustomTitleEnabled()) {
                ItemStack customBtn = createItem(Material.WRITABLE_BOOK, "§b自定义称号", "§7创建属于你的独特称号");
                setItem(50, customBtn, p -> {
                    player.closeInventory();
                    startCustomTitleSession(p);
                });
            }
        }

        // 下一页
        int totalPages = (int) Math.ceil((double) titleIds.size() / ITEMS_PER_PAGE);
        if (page < totalPages - 1) {
            ItemStack nextBtn = createItem(Material.SPECTRAL_ARROW, "§e下一页", "§7第 " + (page + 2) + " 页");
            setItem(52, nextBtn, p -> new TitleMainGUI(plugin, player, ownerUuid, page + 1).open());
        }

        addCloseButton(53);
    }

    private ItemStack createTitleItem(String titleId, TitleData titleData) {
        boolean isCurrentUse = titleId.equals(currentTitleId);
        Material material = isCurrentUse ? Material.ENCHANTED_GOLDEN_APPLE : Material.NAME_TAG;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
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

    private void openDetailGUI(String titleId, TitleData titleData) {
        TitleDetailGUI.open(plugin, player, ownerUuid, titleId, titleData, page);
    }

    private void startCustomTitleSession(Player player) {
        plugin.getCustomTitleSessionManager().startSession(player);

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

    public static void open(SimpleTitlePlugin plugin, Player player) {
        open(plugin, player, player.getUniqueId(), 0);
    }

    public static void open(SimpleTitlePlugin plugin, Player player, int page) {
        open(plugin, player, player.getUniqueId(), page);
    }

    public static void open(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid, int page) {
        viewer.getScheduler().execute(plugin, () -> {
            TitleMainGUI gui = new TitleMainGUI(plugin, viewer, ownerUuid, page);
            gui.open();
        }, () -> {}, 0L);
    }
}
