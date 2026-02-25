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
 * 称号商店GUI
 * 显示可购买的预设称号
 */
public class TitleShopGUI extends AbstractGUI {

    private static final int GUI_SIZE = 54; // 6行
    private static final int ITEMS_PER_PAGE = 28; // 每页28个物品

    private final SimpleTitlePlugin plugin;
    private final int page;
    private final Map<String, TitleData> presetTitles;
    private final List<String> titleIds;

    public TitleShopGUI(SimpleTitlePlugin plugin, Player player, int page) {
        super(player, plugin.getConfigManager().getMessage("gui.shop-title", "default", "&b称号商店"), GUI_SIZE);
        this.plugin = plugin;
        this.page = page;
        this.presetTitles = plugin.getTitleManager().getPresetTitles();
        this.titleIds = new ArrayList<>(presetTitles.keySet());
    }

    @Override
    protected void initialize() {
        // 填充边框
        fillBorder(Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // 计算分页
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, titleIds.size());

        // 填充称号物品
        int slot = 10;

        for (int i = startIndex; i < endIndex; i++) {
            String titleId = titleIds.get(i);
            TitleData titleData = presetTitles.get(titleId);

            // 跳过边框槽位
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }

            // 如果超过最后一行，停止
            if (slot >= 45) break;

            boolean owned = plugin.getTitleManager().hasTitle(player.getUniqueId(), titleId);
            ItemStack item = createShopItem(titleId, titleData, owned);

            if (!owned) {
                setItem(slot, item, p -> purchaseTitle(p, titleId, titleData));
            } else {
                setItem(slot, item); // 已拥有，无点击动作
            }

            slot++;
        }

        // 底部导航栏
        // 上一页按钮（槽位48）
        if (page > 0) {
            ItemStack prevBtn = createItem(Material.SPECTRAL_ARROW, "§e上一页", "§7第 " + page + " 页");
            setItem(48, prevBtn, p -> {
                TitleShopGUI gui = new TitleShopGUI(plugin, player, page - 1);
                gui.open();
            });
        }

        // 返回按钮（槽位49）
        ItemStack backBtn = createItem(Material.ARROW, "§e返回我的称号", "§7点击返回称号列表");
        setItem(49, backBtn, p -> TitleMainGUI.open(plugin, p, 0));

        // 下一页按钮（槽位51）
        int totalPages = (int) Math.ceil((double) titleIds.size() / ITEMS_PER_PAGE);
        if (page < totalPages - 1) {
            ItemStack nextBtn = createItem(Material.SPECTRAL_ARROW, "§e下一页", "§7第 " + (page + 2) + " 页");
            setItem(51, nextBtn, p -> {
                TitleShopGUI gui = new TitleShopGUI(plugin, player, page + 1);
                gui.open();
            });
        }

        // 关闭按钮（槽位53）
        addCloseButton(53);
    }

    /**
     * 创建商店物品
     */
    private ItemStack createShopItem(String titleId, TitleData titleData, boolean owned) {
        Material material = owned ? Material.EMERALD : Material.DIAMOND;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 显示名称（使用Component解析颜色）
            meta.displayName(toComponent(titleData.getFormatted()));

            List<String> lore = new ArrayList<>();
            lore.add("§7ID: §f" + titleId);

            if (titleData.getDisplayName() != null && !titleData.getDisplayName().isEmpty()) {
                lore.add("§7名称: §f" + titleData.getDisplayName());
            }

            lore.add("");

            if (owned) {
                lore.add("§a§l已拥有");
            } else {
                // 显示价格
                boolean hasPrice = false;
                if (titleData.getPriceMoney() > 0) {
                    lore.add("§6金币: §e" + String.format("%.0f", titleData.getPriceMoney()));
                    hasPrice = true;
                }
                if (titleData.getPricePoints() > 0) {
                    lore.add("§b点券: §f" + titleData.getPricePoints());
                    hasPrice = true;
                }

                if (!hasPrice) {
                    lore.add("§a免费");
                }

                // 权限检查
                if (titleData.requiresPermission()) {
                    boolean hasPerm = player.hasPermission(titleData.getPermission());
                    if (hasPerm) {
                        lore.add("§a§l点击购买");
                    } else {
                        lore.add("§c需要权限: " + titleData.getPermission());
                    }
                } else {
                    lore.add("§e点击购买");
                }
            }

            meta.lore(toComponents(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 购买称号
     */
    private void purchaseTitle(Player player, String titleId, TitleData titleData) {
        // 检查权限
        if (titleData.requiresPermission() && !player.hasPermission(titleData.getPermission())) {
            MessageUtil.send(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        // 调用购买逻辑
        plugin.getTitleManager().purchasePresetTitle(player, titleId, result -> {
            switch (result) {
                case SUCCESS:
                    String formattedTitle = titleData.getFormatted();
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("buy-success", "title", formattedTitle));
                    // 刷新当前GUI
                    player.getScheduler().execute(plugin, () -> {
                        TitleShopGUI gui = new TitleShopGUI(plugin, player, page);
                        gui.open();
                    }, () -> {}, 0L);
                    break;
                case ALREADY_OWNED:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("already-owned"));
                    break;
                case NOT_ENOUGH_MONEY:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("not-enough-money"));
                    break;
                case NOT_ENOUGH_POINTS:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("not-enough-points"));
                    break;
                case ECONOMY_NOT_AVAILABLE:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("economy-not-available"));
                    break;
                case POINTS_NOT_AVAILABLE:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("points-not-available"));
                    break;
                default:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("buy-failed"));
                    break;
            }
        });
    }

    /**
     * 静态打开方法
     */
    public static void open(SimpleTitlePlugin plugin, Player player, int page) {
        player.getScheduler().execute(plugin, () -> {
            TitleShopGUI gui = new TitleShopGUI(plugin, player, page);
            gui.open();
        }, () -> {}, 0L);
    }
}
