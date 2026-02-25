package dev.user.title.model;

/**
 * 称号类型枚举
 */
public enum TitleType {
    /**
     * 预设称号 - 服务器预设的固定称号
     */
    PRESET("预设"),

    /**
     * 自定义称号 - 玩家自定义的称号
     */
    CUSTOM("自定义");

    private final String displayName;

    TitleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
