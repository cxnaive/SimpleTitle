package dev.user.title.gui;

import dev.user.title.SimpleTitlePlugin;
import dev.user.title.manager.BracketManager;
import dev.user.title.manager.TitleManager;
import dev.user.title.model.BracketData;
import dev.user.title.model.TitleData;
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
 * 边框选择 GUI - 用于修改称号的边框
 */
public class BracketSelectGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 49;

    private final SimpleTitlePlugin plugin;
    private final Player player;
    private final String titleId;
    private TitleData titleData;
    private final int returnPage;
    private final Inventory inventory;
    private int currentPage = 0;

    private List<BracketData> ownedBrackets;

    public BracketSelectGUI(SimpleTitlePlugin plugin, Player player, String titleId, TitleData titleData, int returnPage) {
        this.plugin = plugin;
        this.player = player;
        this.titleId = titleId;
        this.titleData = titleData;
        this.returnPage = returnPage;
        this.inventory = Bukkit.createInventory(this, SIZE,
                Component.text("选择边框 - " + titleId));
        this.ownedBrackets = plugin.getBracketManager().getPlayerBrackets(player.getUniqueId());
        loadPage();
    }

    private void loadPage() {
        inventory.clear();

        int startIndex = currentPage * 45;
        int endIndex = Math.min(startIndex + 45, ownedBrackets.size());

        String currentLeft = titleData.getBracketLeft();
        String currentRight = titleData.getBracketRight();

        for (int i = startIndex; i < endIndex; i++) {
            BracketData bracket = ownedBrackets.get(i);
            int slot = i - startIndex;

            boolean isCurrent = bracket.getBracketLeft().equals(currentLeft) &&
                    bracket.getBracketRight().equals(currentRight);

            inventory.setItem(slot, createBracketItem(bracket, isCurrent));
        }

        // 上一页按钮
        if (currentPage > 0) {
            inventory.setItem(45, createNavItem(Material.ARROW, "&e上一页"));
        }

        // 下一页按钮
        if ((currentPage + 1) * 45 < ownedBrackets.size()) {
            inventory.setItem(53, createNavItem(Material.ARROW, "&e下一页"));
        }

        // 返回按钮
        inventory.setItem(BACK_SLOT, createNavItem(Material.BARRIER, "&c返回"));
    }

    private ItemStack createBracketItem(BracketData bracket, boolean isCurrent) {
        Material material = isCurrent ? Material.LIME_DYE : Material.NAME_TAG;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        List<String> lore = new ArrayList<>();
        lore.add("&7预览: &f" + bracket.getPreview());
        lore.add("");
        lore.add("&7左边框: &f" + bracket.getBracketLeft());
        lore.add("&7右边框: &f" + bracket.getBracketRight());
        lore.add("");

        if (isCurrent) {
            lore.add("&a当前使用中");
        } else {
            lore.add("&e点击应用此边框");
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
        // 上一页
        if (slot == 45 && currentPage > 0) {
            currentPage--;
            loadPage();
            player.openInventory(inventory);
            return;
        }

        // 下一页
        if (slot == 53 && (currentPage + 1) * 45 < ownedBrackets.size()) {
            currentPage++;
            loadPage();
            player.openInventory(inventory);
            return;
        }

        // 返回
        if (slot == BACK_SLOT) {
            TitleDetailGUI.open(plugin, player, titleId, titleData, returnPage);
            return;
        }

        int index = currentPage * 45 + slot;
        if (index >= 0 && index < ownedBrackets.size()) {
            BracketData bracket = ownedBrackets.get(index);
            handleBracketSelect(bracket);
        }
    }

    private void handleBracketSelect(BracketData bracket) {
        String currentLeft = titleData.getBracketLeft();
        String currentRight = titleData.getBracketRight();

        // 已经是当前边框
        if (bracket.getBracketLeft().equals(currentLeft) && bracket.getBracketRight().equals(currentRight)) {
            MessageUtil.send(player, "&c这已经是当前使用的边框了！");
            return;
        }

        // 更新边框
        titleData.setBracketLeft(bracket.getBracketLeft());
        titleData.setBracketRight(bracket.getBracketRight());

        // 保存到数据库
        TitleManager titleManager = plugin.getTitleManager();
        titleManager.updatePlayerTitleData(player.getUniqueId(), titleId, titleData, success -> {
            if (success) {
                MessageUtil.send(player, "&a边框已更新为: " + bracket.getDisplayName());
                MessageUtil.send(player, "&7预览: " + bracket.getPreview());

                // 刷新 GUI
                loadPage();
                player.openInventory(inventory);
            } else {
                MessageUtil.send(player, "&c边框更新失败！");
            }
        });
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String getTitleId() {
        return titleId;
    }

    public TitleData getTitleData() {
        return titleData;
    }

    public static BracketSelectGUI getHolder(Inventory inventory) {
        if (inventory.getHolder() instanceof BracketSelectGUI) {
            return (BracketSelectGUI) inventory.getHolder();
        }
        return null;
    }
}
