package dev.user.title.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GUI基类
 */
public abstract class AbstractGUI implements InventoryHolder {

    protected final Player player;
    protected final String title;
    protected final int size;
    protected Inventory inventory;
    protected final Map<Integer, GUIAction> actions;

    // 解析 & 格式颜色代码
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * 静态方法：将颜色代码字符串转换为Component（支持 & 格式）
     */
    public static Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * 静态方法：将字符串列表转换为Component列表
     */
    public static List<Component> toComponents(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        return lines.stream()
                .map(AbstractGUI::toComponent)
                .collect(Collectors.toList());
    }

    public interface GUIAction {
        void execute(Player player);
    }

    public AbstractGUI(Player player, String title, int size) {
        this.player = player;
        this.title = title;
        this.size = size;
        this.actions = new HashMap<>();
    }

    public void open() {
        Component titleComponent = SERIALIZER.deserialize(title);
        inventory = Bukkit.createInventory(this, size, titleComponent);
        initialize();
        player.openInventory(inventory);
        GUIManager.registerGUI(player.getUniqueId(), this);
    }

    protected abstract void initialize();

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void handleClick(int slot, Player player) {
        GUIAction action = actions.get(slot);
        if (action != null) {
            action.execute(player);
        }
    }

    /**
     * 检查指定槽位是否有绑定的点击动作
     */
    public boolean hasAction(int slot) {
        return actions.containsKey(slot);
    }

    protected void setItem(int slot, ItemStack item, GUIAction action) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
            if (action != null) {
                actions.put(slot, action);
            }
        }
    }

    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * 填充边框
     */
    protected void fillBorder(Material material) {
        ItemStack border = createDecoration(material, " ");
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                if (inventory.getItem(i) == null) {
                    setItem(i, border);
                }
            }
        }
    }

    /**
     * 实例方法：将颜色代码字符串转换为Component（支持 & 格式）
     */
    protected Component toComponentInst(String text) {
        return toComponent(text);
    }

    /**
     * 实例方法：将字符串列表转换为Component列表
     */
    protected List<Component> toComponentsInst(List<String> lines) {
        return toComponents(lines);
    }

    /**
     * 创建装饰物品
     */
    protected ItemStack createDecoration(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(toComponent(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建物品
     */
    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(toComponent(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(toComponents(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建物品（单个Lore）
     */
    protected ItemStack createItem(Material material, String name, String lore) {
        List<String> loreList = new ArrayList<>();
        loreList.add(lore);
        return createItem(material, name, loreList);
    }

    /**
     * 创建物品（多个Lore参数）
     */
    protected ItemStack createItem(Material material, String name, String... loreLines) {
        List<String> loreList = new ArrayList<>();
        for (String line : loreLines) {
            loreList.add(line);
        }
        return createItem(material, name, loreList);
    }

    /**
     * 创建物品（无Lore）
     */
    protected ItemStack createItem(Material material, String name) {
        return createItem(material, name, (List<String>) null);
    }

    /**
     * 添加关闭按钮
     */
    protected void addCloseButton(int slot) {
        ItemStack closeBtn = createItem(Material.BARRIER, "§c关闭");
        setItem(slot, closeBtn, p -> p.closeInventory());
    }

    /**
     * 添加返回按钮
     */
    protected void addBackButton(int slot, Runnable onBack) {
        ItemStack backBtn = createItem(Material.ARROW, "§e返回");
        setItem(slot, backBtn, p -> onBack.run());
    }

    public void onClose() {
        GUIManager.unregisterGUI(player.getUniqueId());
    }
}
