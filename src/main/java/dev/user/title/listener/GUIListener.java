package dev.user.title.listener;

import dev.user.title.gui.AbstractGUI;
import dev.user.title.gui.BracketSelectGUI;
import dev.user.title.gui.BracketShopGUI;
import dev.user.title.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * GUI事件监听器
 */
public class GUIListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 检查点击的是否是GUI内的槽位
        if (event.getClickedInventory() == null) return;

        // 处理 AbstractGUI
        AbstractGUI gui = GUIManager.getOpenGUI(player.getUniqueId());
        if (gui != null && event.getClickedInventory().getHolder() instanceof AbstractGUI) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (gui.hasAction(slot)) {
                gui.handleClick(slot, player);
            }
            return;
        }

        // 处理 BracketShopGUI
        BracketShopGUI bracketShopGUI = BracketShopGUI.getHolder(event.getClickedInventory());
        if (bracketShopGUI != null) {
            event.setCancelled(true);
            bracketShopGUI.handleClick(event.getRawSlot());
            return;
        }

        // 处理 BracketSelectGUI
        BracketSelectGUI bracketSelectGUI = BracketSelectGUI.getHolder(event.getClickedInventory());
        if (bracketSelectGUI != null) {
            event.setCancelled(true);
            bracketSelectGUI.handleClick(event.getRawSlot());
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        AbstractGUI gui = GUIManager.getOpenGUI(player.getUniqueId());
        if (gui != null) {
            gui.onClose();
        }
    }
}
