package dev.user.title.gui;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.manager.BracketManager;
import dev.user.title.model.BracketData;
import dev.user.title.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 边框商城 GUI
 */
public class BracketShopGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;

    private final SimpleTitlePlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private int currentPage = 0;

    private List<BracketData> brackets;

    public BracketShopGUI(SimpleTitlePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, SIZE,
                Component.text("边框商城"));
        this.brackets = new ArrayList<>(plugin.getBracketManager().getPresetBrackets().values());
        loadPage();
    }

    private void loadPage() {
        inventory.clear();

        int startIndex = currentPage * 45;
        int endIndex = Math.min(startIndex + 45, brackets.size());

        for (int i = startIndex; i < endIndex; i++) {
            BracketData bracket = brackets.get(i);
            int slot = i - startIndex;
            inventory.setItem(slot, createBracketItem(bracket));
        }

        // 上一页按钮
        if (currentPage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, createNavItem(Material.ARROW, "&e上一页"));
        }

        // 下一页按钮
        if ((currentPage + 1) * 45 < brackets.size()) {
            inventory.setItem(NEXT_PAGE_SLOT, createNavItem(Material.ARROW, "&e下一页"));
        }
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

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

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

        meta.displayName(AbstractGUI.toComponent("&6" + bracket.getDisplayName()));
        meta.lore(lore.stream().map(AbstractGUI::toComponent).toList());
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(AbstractGUI.toComponent(name));
        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(int slot) {
        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            loadPage();
            player.openInventory(inventory);
            return;
        }

        if (slot == NEXT_PAGE_SLOT && (currentPage + 1) * 45 < brackets.size()) {
            currentPage++;
            loadPage();
            player.openInventory(inventory);
            return;
        }

        int index = currentPage * 45 + slot;
        if (index >= 0 && index < brackets.size()) {
            BracketData bracket = brackets.get(index);
            handleBracketClick(bracket);
        }
    }

    private void handleBracketClick(BracketData bracket) {
        BracketManager bracketManager = plugin.getBracketManager();

        // 已拥有
        if (bracketManager.hasBracket(player.getUniqueId(), bracket.getBracketId())) {
            MessageUtil.send(player, "&c你已经拥有这个边框了！");
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
                loadPage();
                player.openInventory(inventory);
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

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static BracketShopGUI getHolder(Inventory inventory) {
        if (inventory.getHolder() instanceof BracketShopGUI) {
            return (BracketShopGUI) inventory.getHolder();
        }
        return null;
    }
}
