package dev.user.title.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 称号数据模型 - 负责所有 JSON 解析
 * 统一用于玩家称号和预设称号
 */
public class TitleData {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    // ==================== 基础字段（所有称号共用） ====================

    /**
     * 称号内容列表
     * 静态称号：只有一个元素
     * 动态称号：多个元素，循环切换显示
     */
    private List<String> contents;

    /**
     * 左边框
     */
    @SerializedName("bracketLeft")
    private String bracketLeft;

    /**
     * 右边框
     */
    @SerializedName("bracketRight")
    private String bracketRight;

    /**
     * 前缀（颜色代码等）
     */
    private String prefix;

    /**
     * 后缀
     */
    private String suffix;

    /**
     * 称号类型
     */
    private TitleType type;

    // ==================== 扩展字段（仅预设称号使用） ====================

    /**
     * 显示名称（商店显示用）
     */
    @SerializedName("displayName")
    private String displayName;

    /**
     * 金币价格
     */
    @SerializedName("priceMoney")
    private double priceMoney;

    /**
     * 点券价格
     */
    @SerializedName("pricePoints")
    private int pricePoints;

    /**
     * 所需权限
     */
    private String permission;

    /**
     * GUI 位置
     */
    private int slot;

    /**
     * 分类
     */
    private String category;

    // ==================== 构造方法 ====================

    public TitleData() {
        this.contents = new ArrayList<>();
        this.bracketLeft = "[";
        this.bracketRight = "]";
        this.prefix = "";
        this.suffix = "";
        this.type = TitleType.CUSTOM;
        this.displayName = "";
        this.priceMoney = 0;
        this.pricePoints = 0;
        this.permission = null;
        this.slot = 0;
        this.category = "default";
    }

    /**
     * 创建预设称号的快捷构造方法（静态）
     */
    public static TitleData createPreset(String content, String bracketLeft, String bracketRight,
                                         String prefix, String suffix) {
        TitleData data = new TitleData();
        data.setContents(Arrays.asList(content));
        data.setBracketLeft(bracketLeft);
        data.setBracketRight(bracketRight);
        data.setPrefix(prefix);
        data.setSuffix(suffix);
        data.setType(TitleType.PRESET);
        return data;
    }

    /**
     * 创建自定义称号的快捷构造方法（静态）
     */
    public static TitleData createCustom(String content, String bracketLeft, String bracketRight,
                                         String prefix, String suffix) {
        TitleData data = new TitleData();
        data.setContents(Arrays.asList(content));
        data.setBracketLeft(bracketLeft);
        data.setBracketRight(bracketRight);
        data.setPrefix(prefix);
        data.setSuffix(suffix);
        data.setType(TitleType.CUSTOM);
        return data;
    }

    // ==================== JSON 序列化/反序列化 ====================

    /**
     * 序列化为 JSON 字符串
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * 从 JSON 字符串解析
     */
    public static TitleData fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new TitleData();
        }
        TitleData data = GSON.fromJson(json, TitleData.class);
        // 设置默认值
        if (data.contents == null) data.contents = new ArrayList<>();
        if (data.bracketLeft == null) data.bracketLeft = "[";
        if (data.bracketRight == null) data.bracketRight = "]";
        if (data.prefix == null) data.prefix = "";
        if (data.suffix == null) data.suffix = "";
        if (data.type == null) data.type = TitleType.CUSTOM;
        if (data.displayName == null) data.displayName = "";
        if (data.category == null) data.category = "default";
        return data;
    }

    // ==================== 动态称号相关 ====================

    /**
     * 是否为动态称号
     */
    public boolean isDynamic() {
        return contents != null && contents.size() > 1;
    }

    /**
     * 获取内容数量
     */
    public int getContentCount() {
        return contents == null ? 0 : contents.size();
    }

    /**
     * 获取指定索引的内容
     */
    public String getContent(int index) {
        if (contents == null || contents.isEmpty()) return "";
        return contents.get(Math.max(0, Math.min(index, contents.size() - 1)));
    }

    /**
     * 获取第一个内容（用于静态称号或默认显示）
     */
    public String getFirstContent() {
        return getContent(0);
    }

    // ==================== 格式化输出 ====================

    /**
     * 获取完整格式的称号字符串（使用第一个内容）
     * 格式：重置 + 边框左 + 重置 + 前缀 + 内容 + 后缀 + 重置 + 边框右 + 重置
     */
    public String getFormatted() {
        return getFormatted(0);
    }

    /**
     * 获取完整格式的称号字符串（指定内容索引）
     */
    public String getFormatted(int contentIndex) {
        String content = getContent(contentIndex);
        return "&r" + bracketLeft + "&r" + prefix + content + suffix + "&r" + bracketRight + "&r";
    }

    /**
     * 获取不带边框的称号字符串
     * 格式：前缀 + 内容 + 后缀
     */
    public String getRaw() {
        return prefix + getFirstContent() + suffix;
    }

    // ==================== Getters & Setters ====================

    public List<String> getContents() {
        return contents;
    }

    public void setContents(List<String> contents) {
        this.contents = contents;
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

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public TitleType getType() {
        return type;
    }

    public void setType(TitleType type) {
        this.type = type;
    }

    public String getDisplayName() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        return getFirstContent();
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

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * 检查是否需要金币购买
     */
    public boolean requiresMoney() {
        return priceMoney > 0;
    }

    /**
     * 检查是否需要点券购买
     */
    public boolean requiresPoints() {
        return pricePoints > 0;
    }

    /**
     * 检查是否需要权限
     */
    public boolean requiresPermission() {
        return permission != null && !permission.isEmpty();
    }

    @Override
    public String toString() {
        return "TitleData{" +
                "contents=" + contents +
                ", bracketLeft='" + bracketLeft + '\'' +
                ", bracketRight='" + bracketRight + '\'' +
                ", prefix='" + prefix + '\'' +
                ", suffix='" + suffix + '\'' +
                ", type=" + type +
                '}';
    }
}
