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
 * 称号商店GUI
 * 显示可购买的预设称号
 */
public class TitleShopGUI extends AbstractGUI {

    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28;

    private final SimpleTitlePlugin plugin;
    private final UUID ownerUuid;
    private final int page;
    private final Map<String, TitleData> presetTitles;
    private final List<String> titleIds;

    public TitleShopGUI(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid, int page) {
        super(viewer, resolveTitle(plugin, viewer, ownerUuid), GUI_SIZE);
        this.plugin = plugin;
        this.ownerUuid = ownerUuid;
        this.page = page;
        this.presetTitles = plugin.getTitleManager().getPresetTitles();
        this.titleIds = new ArrayList<>(presetTitles.keySet());
    }

    public TitleShopGUI(SimpleTitlePlugin plugin, Player player, int page) {
        this(plugin, player, player.getUniqueId(), page);
    }

    private static String resolveTitle(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid) {
        if (ownerUuid.equals(viewer.getUniqueId())) {
            return plugin.getConfigManager().getMessage("gui.shop-title", "default", "&b称号商店");
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(ownerUuid);
        String name = target.getName() != null ? target.getName() : ownerUuid.toString().substring(0, 8);
        return "&b给予称号 - " + name;
    }

    @Override
    protected void initialize() {
        fillBorder(Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        boolean isAdmin = !ownerUuid.equals(player.getUniqueId());

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, titleIds.size());

        int slot = 10;

        for (int i = startIndex; i < endIndex; i++) {
            String titleId = titleIds.get(i);
            TitleData titleData = presetTitles.get(titleId);

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }

            if (slot >= 45) break;

            boolean owned = plugin.getTitleManager().hasTitle(ownerUuid, titleId);
            ItemStack item = createShopItem(titleId, titleData, owned);

            if (!owned) {
                setItem(slot, item, p -> {
                    if (isAdmin) {
                        giveTitleToPlayer(p, titleId, titleData);
                    } else {
                        purchaseTitle(p, titleId, titleData);
                    }
                });
            } else {
                setItem(slot, item);
            }

            slot++;
        }

        // 上一页
        if (page > 0) {
            ItemStack prevBtn = createItem(Material.SPECTRAL_ARROW, "§e上一页", "§7第 " + page + " 页");
            setItem(48, prevBtn, p -> new TitleShopGUI(plugin, p, ownerUuid, page - 1).open());
        }

        // 返回按钮
        ItemStack backBtn = createItem(Material.ARROW, "§e返回",
                isAdmin ? "§7返回管理界面" : "§7点击返回称号列表");
        setItem(49, backBtn, p -> TitleMainGUI.open(plugin, p, ownerUuid, 0));

        // 下一页
        int totalPages = (int) Math.ceil((double) titleIds.size() / ITEMS_PER_PAGE);
        if (page < totalPages - 1) {
            ItemStack nextBtn = createItem(Material.SPECTRAL_ARROW, "§e下一页", "§7第 " + (page + 2) + " 页");
            setItem(51, nextBtn, p -> new TitleShopGUI(plugin, p, ownerUuid, page + 1).open());
        }

        addCloseButton(53);
    }

    private ItemStack createShopItem(String titleId, TitleData titleData, boolean owned) {
        Material material = owned ? Material.EMERALD : Material.DIAMOND;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
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

    private void purchaseTitle(Player player, String titleId, TitleData titleData) {
        if (titleData.requiresPermission() && !player.hasPermission(titleData.getPermission())) {
            MessageUtil.send(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        plugin.getTitleManager().purchasePresetTitle(player, titleId, result -> {
            switch (result) {
                case SUCCESS:
                    String formattedTitle = titleData.getFormatted();
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("buy-success", "title", formattedTitle));
                    player.getScheduler().execute(plugin, () -> {
                        TitleShopGUI gui = new TitleShopGUI(plugin, player, page);
                        gui.open();
                    }, () -> {}, 0L);
                    break;
                case ALREADY_OWNED:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("already-owned"));
                    break;
                case NOT_ENOUGH_MONEY:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("not-enough-money",
                            "price", String.format("%.0f", titleData.getPriceMoney())));
                    break;
                case NOT_ENOUGH_POINTS:
                    MessageUtil.send(player, plugin.getConfigManager().getMessage("not-enough-points",
                            "price", String.valueOf(titleData.getPricePoints())));
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

    private void giveTitleToPlayer(Player viewer, String titleId, TitleData titleData) {
        plugin.getTitleManager().giveTitle(ownerUuid, titleId, titleData.copy(), success -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(ownerUuid);
            String targetName = target.getName() != null ? target.getName() : ownerUuid.toString().substring(0, 8);

            if (success) {
                MessageUtil.send(viewer, "&a已给予 " + targetName + " 称号: " + titleData.getFormatted());
            } else {
                MessageUtil.send(viewer, "&c给予称号失败！");
            }

            viewer.getScheduler().execute(plugin, () -> {
                TitleShopGUI gui = new TitleShopGUI(plugin, viewer, ownerUuid, page);
                gui.open();
            }, () -> {}, 0L);
        });
    }

    public static void open(SimpleTitlePlugin plugin, Player player, int page) {
        openAdmin(plugin, player, player.getUniqueId(), page);
    }

    public static void openAdmin(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid, int page) {
        viewer.getScheduler().execute(plugin, () -> {
            TitleShopGUI gui = new TitleShopGUI(plugin, viewer, ownerUuid, page);
            gui.open();
        }, () -> {}, 0L);
    }
}
