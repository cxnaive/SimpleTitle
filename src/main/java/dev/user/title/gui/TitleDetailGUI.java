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
import java.util.UUID;

/**
 * 称号详情GUI
 * 显示称号详细信息和操作选项
 */
public class TitleDetailGUI extends AbstractGUI {

    private static final int GUI_SIZE = 27;

    private final SimpleTitlePlugin plugin;
    private final UUID ownerUuid;
    private final String titleId;
    private final TitleData titleData;
    private final int returnPage;

    public TitleDetailGUI(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid, String titleId, TitleData titleData, int returnPage) {
        super(viewer, plugin.getConfigManager().getMessage("gui.detail-title", "default", "&e称号详情"), GUI_SIZE);
        this.plugin = plugin;
        this.ownerUuid = ownerUuid;
        this.titleId = titleId;
        this.titleData = titleData;
        this.returnPage = returnPage;
    }

    public TitleDetailGUI(SimpleTitlePlugin plugin, Player player, String titleId, TitleData titleData, int returnPage) {
        this(plugin, player, player.getUniqueId(), titleId, titleData, returnPage);
    }

    @Override
    protected void initialize() {
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        // 中间显示称号预览
        ItemStack previewItem = createPreviewItem();
        setItem(13, previewItem);

        String currentTitleId = plugin.getTitleCacheManager().getCurrentTitleId(ownerUuid);
        boolean isCurrentUse = titleId.equals(currentTitleId);

        // 使用/取消使用按钮
        if (isCurrentUse) {
            ItemStack unuseBtn = createItem(Material.REDSTONE, "§c取消使用", "§7点击取消使用此称号");
            setItem(11, unuseBtn, p -> {
                plugin.getTitleManager().clearCurrentTitle(ownerUuid, success -> {
                    if (success) {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("unuse-success", "title", titleData.getFormatted()));
                    } else {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("unuse-failed"));
                    }
                    p.getScheduler().execute(plugin, () -> {
                        TitleMainGUI.open(plugin, p, ownerUuid, returnPage);
                    }, () -> {}, 0L);
                });
            });
        } else {
            ItemStack useBtn = createItem(Material.LIME_DYE, "§a使用", "§7点击使用此称号");
            setItem(11, useBtn, p -> {
                plugin.getTitleManager().setCurrentTitle(ownerUuid, titleId, success -> {
                    if (success) {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("use-success", "title", titleData.getFormatted()));
                    } else {
                        MessageUtil.send(p, plugin.getConfigManager().getMessage("use-failed"));
                    }
                    p.getScheduler().execute(plugin, () -> {
                        TitleMainGUI.open(plugin, p, ownerUuid, returnPage);
                    }, () -> {}, 0L);
                });
            });
        }

        // 删除按钮
        ItemStack deleteBtn = createItem(Material.TNT, "§c删除称号", "§7点击删除此称号", "§c警告：删除后无法恢复！");
        setItem(15, deleteBtn, p -> deleteTitle(p));

        // 修改边框按钮
        ItemStack bracketBtn = createItem(Material.ITEM_FRAME, "§e修改边框", "§7点击修改此称号的边框");
        setItem(12, bracketBtn, p -> {
            BracketSelectGUI selectGUI = new BracketSelectGUI(plugin, p, ownerUuid, titleId, titleData, returnPage);
            selectGUI.open();
        });

        // 返回按钮
        addBackButton(22, () -> TitleMainGUI.open(plugin, player, ownerUuid, returnPage));
    }

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

            lore.add("");
            lore.add("§7边框: §f" + titleData.getBracketLeft() + " §7和 §f" + titleData.getBracketRight());

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

    private void deleteTitle(Player player) {
        plugin.getTitleRepository().removePlayerTitle(ownerUuid, titleId, success -> {
            if (success) {
                plugin.getTitleCacheManager().removePlayerTitle(ownerUuid, titleId);

                String currentTitleId = plugin.getTitleCacheManager().getCurrentTitleId(ownerUuid);
                if (titleId.equals(currentTitleId)) {
                    plugin.getTitleCacheManager().clearCurrentTitle(ownerUuid);
                }

                MessageUtil.send(player, plugin.getConfigManager().getMessage("delete-success", "title", titleData.getFormatted()));
            } else {
                MessageUtil.send(player, plugin.getConfigManager().getMessage("delete-failed"));
            }

            player.getScheduler().execute(plugin, () -> {
                TitleMainGUI.open(plugin, player, ownerUuid, returnPage);
            }, () -> {}, 0L);
        });
    }

    public static void open(SimpleTitlePlugin plugin, Player player, String titleId, TitleData titleData, int returnPage) {
        open(plugin, player, player.getUniqueId(), titleId, titleData, returnPage);
    }

    public static void open(SimpleTitlePlugin plugin, Player viewer, UUID ownerUuid, String titleId, TitleData titleData, int returnPage) {
        viewer.getScheduler().execute(plugin, () -> {
            TitleDetailGUI gui = new TitleDetailGUI(plugin, viewer, ownerUuid, titleId, titleData, returnPage);
            gui.open();
        }, () -> {}, 0L);
    }
}
