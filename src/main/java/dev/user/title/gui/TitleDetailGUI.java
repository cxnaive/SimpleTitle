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

/**
 * 称号详情GUI
 * 显示称号详细信息和操作选项
 */
public class TitleDetailGUI extends AbstractGUI {

    private static final int GUI_SIZE = 27; // 3行

    private final SimpleTitlePlugin plugin;
    private final String titleId;
    private final TitleData titleData;
    private final int returnPage;

    public TitleDetailGUI(SimpleTitlePlugin plugin, Player player, String titleId, TitleData titleData, int returnPage) {
        super(player, plugin.getConfigManager().getMessage("gui.detail-title", "default", "&e称号详情"), GUI_SIZE);
        this.plugin = plugin;
        this.titleId = titleId;
        this.titleData = titleData;
        this.returnPage = returnPage;
    }

    @Override
    protected void initialize() {
        // 填充边框
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        // 中间显示称号预览（槽位13）
        ItemStack previewItem = createPreviewItem();
        setItem(13, previewItem);

        // 检查是否是当前使用的称号
        String currentTitleId = plugin.getTitleCacheManager().getCurrentTitleId(player.getUniqueId());
        boolean isCurrentUse = titleId.equals(currentTitleId);

        // 使用按钮（槽位11）
        if (isCurrentUse) {
            // 取消使用按钮
            ItemStack unuseBtn = createItem(Material.REDSTONE, "§c取消使用", "§7点击取消使用此称号");
            setItem(11, unuseBtn, p -> {
                plugin.getTitleManager().clearCurrentTitle(p.getUniqueId(), success -> {
                    if (success) {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("unuse-success", "title", titleData.getFormatted()));
                    } else {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("unuse-failed"));
                    }
                    p.getScheduler().execute(plugin, () -> {
                        TitleMainGUI.open(plugin, p, returnPage);
                    }, () -> {}, 0L);
                });
            });
        } else {
            // 使用按钮
            ItemStack useBtn = createItem(Material.LIME_DYE, "§a使用", "§7点击使用此称号");
            setItem(11, useBtn, p -> {
                plugin.getTitleManager().setCurrentTitle(p.getUniqueId(), titleId, success -> {
                    if (success) {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("use-success", "title", titleData.getFormatted()));
                    } else {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("use-failed"));
                    }
                    p.getScheduler().execute(plugin, () -> {
                        TitleMainGUI.open(plugin, p, returnPage);
                    }, () -> {}, 0L);
                });
            });
        }

        // 删除按钮（槽位15）
        ItemStack deleteBtn = createItem(Material.TNT, "§c删除称号", "§7点击删除此称号", "§c警告：删除后无法恢复！");
        setItem(15, deleteBtn, p -> {
            // 直接删除，不做二次确认
            deleteTitle(p);
        });

        // 修改边框按钮（槽位12）
        ItemStack bracketBtn = createItem(Material.ITEM_FRAME, "§e修改边框", "§7点击修改此称号的边框");
        setItem(12, bracketBtn, p -> {
            BracketSelectGUI selectGUI = new BracketSelectGUI(plugin, p, titleId, titleData, returnPage);
            selectGUI.open();
        });

        // 返回按钮（槽位22）
        addBackButton(22, () -> TitleMainGUI.open(plugin, player, returnPage));
    }

    /**
     * 创建称号预览物品
     */
    private ItemStack createPreviewItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(toComponent(titleData.getFormatted()));

            List<String> lore = new ArrayList<>();
            lore.add("§7ID: §f" + titleId);
            lore.add("§7类型: §f" + (titleData.getType() != null ? titleData.getType().getDisplayName() : "未知"));

            if (titleData.getDisplayName() != null && !titleData.getDisplayName().isEmpty()) {
                lore.add("§7显示名称: §f" + titleData.getDisplayName());
            }

            // 显示边框信息
            lore.add("");
            lore.add("§7边框: §f" + titleData.getBracketLeft() + " §7和 §f" + titleData.getBracketRight());

            // 前缀和后缀
            if (titleData.getPrefix() != null && !titleData.getPrefix().isEmpty()) {
                lore.add("§7前缀: §f" + titleData.getPrefix());
            }
            if (titleData.getSuffix() != null && !titleData.getSuffix().isEmpty()) {
                lore.add("§7后缀: §f" + titleData.getSuffix());
            }

            lore.add("");
            lore.add("§7内容: §f" + titleData.getFirstContent());
            if (titleData.isDynamic()) {
                lore.add("§7动态称号: §a共 " + titleData.getContentCount() + " 个内容");
            }

            meta.lore(toComponents(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 删除称号
     */
    private void deleteTitle(Player player) {
        plugin.getTitleRepository().removePlayerTitle(player.getUniqueId(), titleId, success -> {
            if (success) {
                // 从缓存中移除
                plugin.getTitleCacheManager().removePlayerTitle(player.getUniqueId(), titleId);

                // 如果删除的是当前使用的称号，清除当前使用
                String currentTitleId = plugin.getTitleCacheManager().getCurrentTitleId(player.getUniqueId());
                if (titleId.equals(currentTitleId)) {
                    plugin.getTitleCacheManager().clearCurrentTitle(player.getUniqueId());
                }

                MessageUtil.send(player, plugin.getConfigManager().getMessage("delete-success", "title", titleData.getFormatted()));
            } else {
                MessageUtil.send(player, plugin.getConfigManager().getMessage("delete-failed"));
            }

            // 返回主菜单
            player.getScheduler().execute(plugin, () -> {
                TitleMainGUI.open(plugin, player, returnPage);
            }, () -> {}, 0L);
        });
    }

    /**
     * 静态打开方法
     */
    public static void open(SimpleTitlePlugin plugin, Player player, String titleId, TitleData titleData, int returnPage) {
        player.getScheduler().execute(plugin, () -> {
            TitleDetailGUI gui = new TitleDetailGUI(plugin, player, titleId, titleData, returnPage);
            gui.open();
        }, () -> {}, 0L);
    }
}
