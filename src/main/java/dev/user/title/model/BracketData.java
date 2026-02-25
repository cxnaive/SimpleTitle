package dev.user.title.model;

import java.util.Objects;

/**
 * 边框数据模型
 */
public class BracketData {

    private final String bracketId;
    private String bracketLeft;
    private String bracketRight;
    private String displayName;
    private double priceMoney;
    private int pricePoints;
    private String permission;
    private String category;
    private boolean isDefault; // 是否为默认边框（所有玩家拥有）

    public BracketData(String bracketId) {
        this.bracketId = bracketId;
        this.bracketLeft = "[";
        this.bracketRight = "]";
        this.displayName = bracketId;
        this.priceMoney = 0;
        this.pricePoints = 0;
        this.permission = null;
        this.category = "default";
        this.isDefault = false;
    }

    // ==================== Getters & Setters ====================

    public String getBracketId() {
        return bracketId;
    }

    public String getBracketLeft() {
        return bracketLeft;
    }

    public void setBracketLeft(String bracketLeft) {
        this.bracketLeft = bracketLeft;
    }

    public String getBracketRight() {
        return bracketRight;
    }

    public void setBracketRight(String bracketRight) {
        this.bracketRight = bracketRight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getPriceMoney() {
        return priceMoney;
    }

    public void setPriceMoney(double priceMoney) {
        this.priceMoney = priceMoney;
    }

    public int getPricePoints() {
        return pricePoints;
    }

    public void setPricePoints(int pricePoints) {
        this.pricePoints = pricePoints;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    // ==================== Helper Methods ====================

    /**
     * 是否需要金币购买
     */
    public boolean requiresMoney() {
        return priceMoney > 0;
    }

    /**
     * 是否需要点券购买
     */
    public boolean requiresPoints() {
        return pricePoints > 0;
    }

    /**
     * 是否需要权限
     */
    public boolean requiresPermission() {
        return permission != null && !permission.isEmpty();
    }

    /**
     * 是否免费（不需要金币和点券）
     */
    public boolean isFree() {
        return priceMoney == 0 && pricePoints == 0;
    }

    /**
     * 获取预览字符串
     */
    public String getPreview() {
        return bracketLeft + "称号" + bracketRight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BracketData that = (BracketData) o;
        return Objects.equals(bracketId, that.bracketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bracketId);
    }

    @Override
    public String toString() {
        return "BracketData{" +
                "bracketId='" + bracketId + '\'' +
                ", left='" + bracketLeft + '\'' +
                ", right='" + bracketRight + '\'' +
                '}';
    }
}
