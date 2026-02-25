package dev.user.title.gui;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.manager.BracketManager;
import dev.user.title.model.BracketData;
import dev.user.title.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 边框商城 GUI
 */
public class BracketShopGUI extends AbstractGUI {

    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28;

    private final SimpleTitlePlugin plugin;
    private final int page;
    private final List<BracketData> brackets;

    public BracketShopGUI(SimpleTitlePlugin plugin, Player player, int page) {
        super(player, "&b边框商城", GUI_SIZE);
        this.plugin = plugin;
        this.page = page;
        this.brackets = new ArrayList<>(plugin.getBracketManager().getPresetBrackets().values());
    }

    @Override
    protected void initialize() {
        // 填充边框
        fillBorder(Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // 计算分页
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, brackets.size());

        // 填充边框物品
        int slot = 10;

        for (int i = startIndex; i < endIndex; i++) {
            BracketData bracket = brackets.get(i);

            // 跳过边框槽位
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }

            // 如果超过最后一行，停止
            if (slot >= 45) break;

            ItemStack item = createBracketItem(bracket);
            setItem(slot, item, p -> handleBracketClick(bracket));

            slot++;
        }

        // 底部导航栏
        // 上一页按钮（槽位48）
        if (page > 0) {
            ItemStack prevBtn = createItem(Material.SPECTRAL_ARROW, "§e上一页", "§7第 " + page + " 页");
            setItem(48, prevBtn, p -> {
                BracketShopGUI gui = new BracketShopGUI(plugin, p, page - 1);
                gui.open();
            });
        }

        // 返回按钮（槽位49）
        ItemStack backBtn = createItem(Material.ARROW, "§e返回我的称号", "§7点击返回称号列表");
        setItem(49, backBtn, p -> TitleMainGUI.open(plugin, p, 0));

        // 下一页按钮（槽位51）
        int totalPages = (int) Math.ceil((double) brackets.size() / ITEMS_PER_PAGE);
        if (page < totalPages - 1) {
            ItemStack nextBtn = createItem(Material.SPECTRAL_ARROW, "§e下一页", "§7第 " + (page + 2) + " 页");
            setItem(51, nextBtn, p -> {
                BracketShopGUI gui = new BracketShopGUI(plugin, p, page + 1);
                gui.open();
            });
        }

        // 关闭按钮（槽位53）
        addCloseButton(53);
    }

    private ItemStack createBracketItem(BracketData bracket) {
        BracketManager bracketManager = plugin.getBracketManager();
        boolean owned = bracketManager.hasBracket(player.getUniqueId(), bracket.getBracketId());
        boolean canBuy = !owned &&
                (!bracket.requiresPermission() || player.hasPermission(bracket.getPermission()));

        Material material;
        if (owned) {
            material = Material.LIME_DYE;
        } else if (canBuy) {
            material = Material.NAME_TAG;
        } else {
            material = Material.GRAY_DYE;
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7预览: &f" + bracket.getPreview());
        lore.add("");
        lore.add("&7左边框: &f" + bracket.getBracketLeft());
        lore.add("&7右边框: &f" + bracket.getBracketRight());
        lore.add("");

        if (bracket.isDefault()) {
            lore.add("&a默认边框（所有玩家拥有）");
        } else if (owned) {
            lore.add("&a已拥有");
            lore.add("&7在称号详情处修改边框");
        } else {
            // 价格
            if (bracket.requiresMoney() || bracket.requiresPoints()) {
                StringBuilder price = new StringBuilder("&7价格: ");
                if (bracket.requiresMoney()) {
                    price.append("&e").append(String.format("%.0f", bracket.getPriceMoney())).append("金币");
                }
                if (bracket.requiresPoints()) {
                    if (bracket.requiresMoney()) price.append(" ");
                    price.append("&b").append(bracket.getPricePoints()).append("点券");
                }
                lore.add(price.toString());
            } else {
                lore.add("&7价格: &a免费");
            }

            // 权限
            if (bracket.requiresPermission()) {
                if (player.hasPermission(bracket.getPermission())) {
                    lore.add("&a拥有购买权限");
                } else {
                    lore.add("&c没有购买权限");
                }
            }

            lore.add("");
            if (canBuy) {
                lore.add("&e点击购买");
            } else {
                lore.add("&c无法购买");
            }
        }

        return createItem(material, "&6" + bracket.getDisplayName(), lore);
    }

    private void handleBracketClick(BracketData bracket) {
        BracketManager bracketManager = plugin.getBracketManager();

        // 已拥有
        if (bracketManager.hasBracket(player.getUniqueId(), bracket.getBracketId())) {
            MessageUtil.send(player, "&c你已经拥有这个边框了！");
            MessageUtil.send(player, "&7在称号详情处修改边框");
            return;
        }

        // 默认边框
        if (bracket.isDefault()) {
            MessageUtil.send(player, "&c这是默认边框，你已自动拥有！");
            return;
        }

        // 无权限
        if (bracket.requiresPermission() && !player.hasPermission(bracket.getPermission())) {
            MessageUtil.send(player, "&c你没有购买这个边框的权限！");
            return;
        }

        // 购买
        BracketManager.PurchaseResult result = bracketManager.purchaseBracket(player, bracket.getBracketId());

        switch (result) {
            case SUCCESS:
                MessageUtil.send(player, "&a成功购买边框: " + bracket.getDisplayName());
                MessageUtil.send(player, "&7预览: " + bracket.getPreview());
                MessageUtil.send(player, "&7在称号详情处修改边框");
                // 刷新当前页
                BracketShopGUI gui = new BracketShopGUI(plugin, player, page);
                gui.open();
                break;
            case ALREADY_OWNED:
                MessageUtil.send(player, "&c你已经拥有这个边框了！");
                break;
            case NOT_ENOUGH_MONEY:
                MessageUtil.send(player, "&c金币不足！需要 " + String.format("%.0f", bracket.getPriceMoney()) + " 金币");
                break;
            case NOT_ENOUGH_POINTS:
                MessageUtil.send(player, "&c点券不足！需要 " + bracket.getPricePoints() + " 点券");
                break;
            case ECONOMY_NOT_AVAILABLE:
                MessageUtil.send(player, "&c经济系统不可用！");
                break;
            case POINTS_NOT_AVAILABLE:
                MessageUtil.send(player, "&c点券系统不可用！");
                break;
            default:
                MessageUtil.send(player, "&c购买失败！");
                break;
        }
    }

    /**
     * 静态打开方法
     */
    public static void open(SimpleTitlePlugin plugin, Player player, int page) {
        BracketShopGUI gui = new BracketShopGUI(plugin, player, page);
        gui.open();
    }
}
