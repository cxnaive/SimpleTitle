package dev.user.title.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI管理器
 */
public class GUIManager {

    private static final Map<UUID, AbstractGUI> openGUIs = new ConcurrentHashMap<>();

    /**
     * 注册打开的GUI
     */
    public static void registerGUI(UUID playerUuid, AbstractGUI gui) {
        openGUIs.put(playerUuid, gui);
    }

    /**
     * 注销GUI
     */
    public static void unregisterGUI(UUID playerUuid) {
        openGUIs.remove(playerUuid);
    }

    /**
     * 获取玩家当前打开的GUI
     */
    public static AbstractGUI getOpenGUI(UUID playerUuid) {
        return openGUIs.get(playerUuid);
    }

    /**
     * 检查玩家是否有打开的GUI
     */
    public static boolean hasOpenGUI(UUID playerUuid) {
        return openGUIs.containsKey(playerUuid);
    }

    /**
     * 关闭所有GUI（插件禁用时调用）
     */
    public static void closeAllGUIs() {
        openGUIs.clear();
    }
}
