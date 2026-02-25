# SimpleTitle 插件开发文档

## 项目概述

**插件名称**: SimpleTitle
**版本**: 1.0.0
**目标平台**: Folia/Paper 1.21+
**主要功能**: 为玩家提供称号系统，支持 PlaceholderAPI 占位符

## 依赖关系

| 依赖类型 | 插件/API | 说明 |
|---------|----------|------|
| 硬依赖 | PlaceholderAPI | 提供 placeholder 扩展 |
| 软依赖 | XConomy | 经济系统，购买称号 |
| 软依赖 | PlayerPoints | 点券系统，购买称号 |

## 项目结构

```
src/main/java/dev/user/title/
├── SimpleTitlePlugin.java          # 主插件类
├── command/
│   └── TitleCommand.java           # 命令处理
├── config/
│   └── ConfigManager.java          # 配置管理（config.yml, messages.yml, titles.yml）
├── database/
│   ├── DatabaseManager.java        # 数据库连接池（H2/MySQL）
│   ├── DatabaseQueue.java          # 异步数据库操作队列
│   └── TitleRepository.java        # 称号数据访问层
├── economy/
│   ├── EconomyManager.java         # XConomy 经济管理
│   └── PlayerPointsManager.java    # PlayerPoints 点券管理
├── manager/
│   ├── TitleManager.java           # 称号业务逻辑
│   ├── TitleCacheManager.java      # 玩家称号缓存
│   └── CustomTitleSessionManager.java # 自定义称号会话管理
├── model/
│   ├── TitleData.java              # 称号数据模型（JSON 序列化）
│   └── TitleType.java              # 称号类型枚举（PRESET/CUSTOM）
├── placeholder/
│   └── TitleExpansion.java         # PlaceholderAPI 扩展
├── listener/
│   └── PlayerListener.java         # 玩家登录/退出/聊天监听
└── util/
    └── MessageUtil.java            # 消息工具类
```

## 数据库设计

### 玩家称号表 `player_titles`
```sql
CREATE TABLE player_titles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    title_id VARCHAR(64) NOT NULL,
    title_data TEXT NOT NULL,          -- JSON 格式
    on_use BOOLEAN DEFAULT FALSE,
    obtained_at BIGINT NOT NULL,
    UNIQUE(player_uuid, title_id)
);
```

### 预设称号表 `preset_titles`
```sql
CREATE TABLE preset_titles (
    id VARCHAR(64) PRIMARY KEY,
    title_data TEXT NOT NULL,          -- JSON 格式
    enabled BOOLEAN DEFAULT TRUE
);
```

### TitleData JSON 结构
```json
{
    "content": "VIP",
    "bracketLeft": "[",
    "bracketRight": "]",
    "prefix": "&6",
    "suffix": "&r",
    "type": "PRESET",
    "displayName": "VIP称号",
    "priceMoney": 1000.0,
    "pricePoints": 100,
    "permission": "simpletitle.vip",
    "slot": 0,
    "category": "vip"
}
```

## PlaceholderAPI 占位符

| 占位符 | 说明 |
|--------|------|
| `%playertitle_use%` | 当前使用的称号（完整格式：边框+前缀+内容+后缀+边框） |
| `%playertitle_raw%` | 称号原始文本（前缀+内容+后缀） |
| `%playertitle_content%` | 称号内容（纯文本） |
| `%playertitle_bracket%` | 边框样式 |
| `%playertitle_bracket_left%` | 左边框 |
| `%playertitle_bracket_right%` | 右边框 |
| `%playertitle_prefix%` | 前缀 |
| `%playertitle_suffix%` | 后缀 |
| `%playertitle_count%` | 拥有的称号数量 |
| `%playertitle_has_title%` | 是否拥有称号（yes/no） |
| `%playertitle_has_<ID>%` | 是否拥有指定称号 |

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/title` | 打开称号选择界面 | `simpletitle.use` |
| `/title set <ID>` | 设置当前称号 | `simpletitle.set` |
| `/title clear` | 清除当前称号 | `simpletitle.clear` |
| `/title list` | 查看拥有的称号 | `simpletitle.list` |
| `/title shop` | 打开称号商店 | `simpletitle.shop` |
| `/title buy <ID>` | 购买预设称号 | `simpletitle.shop` |
| `/title custom <内容>` | 创建自定义称号 | `simpletitle.custom` |
| `/title reload` | 重载配置 | `simpletitle.reload` |
| `/title give <玩家> <ID>` | 给予玩家称号 | `simpletitle.give` |

## 自定义称号流程

### 流程图
```
/title custom 称号内容
         │
         ▼
    检查内容合法性
    (长度、敏感词)
         │
         ├─ 不合法 → 提示错误
         │
         ▼ 合法
    创建会话，提示输入名称
         │
         ▼
    玩家聊天输入名称
         │
         ├─ 超时(30s) → 取消操作
         │
         ├─ 输入"取消" → 取消操作
         │
         ▼
    检查名称合法性
    (长度、敏感词、重名)
         │
         ├─ 不合法 → 提示重新输入
         │
         ▼ 合法
    显示确认信息
         │
         ▼
    玩家输入"确认"或"取消"
         │
         ├─ 取消 → 结束
         │
         ▼ 确认
    检查余额并扣款
         │
         ├─ 余额不足 → 提示错误
         │
         ▼ 成功
    创建称号
    ID = 玩家名_名称
```

### 称号ID格式

| 称号类型 | ID 格式 | 示例 |
|---------|--------|------|
| 预设称号 | titles.yml 中的 key | `vip`, `mvp` |
| 自定义称号 | `玩家名_自定义名称` | `Steve_我的第一称号` |

### 示例
```
玩家: Steve
输入: /title custom 我是大佬
系统: 请输入称号名称...
玩家: 我的称号
系统: 确认创建？输入"确认"或"取消"
玩家: 确认
结果: 创建称号 ID = Steve_我的称号, 内容 = 我是大佬
```

## 配置文件

### config.yml
- 数据库配置（H2/MySQL）
- 默认边框设置
- 自定义称号配置（启用、最大长度、价格、敏感词过滤）

### messages.yml
- 所有消息文本，支持颜色代码和占位符

### titles.yml
- 预设称号配置

## 核心类说明

### TitleData
- 负责所有 JSON 序列化/反序列化
- 统一用于玩家称号和预设称号
- `getFormatted()` 返回完整格式称号
- `getRaw()` 返回无边框称号

### TitleManager
- 称号业务逻辑核心
- 处理购买、设置、清除称号
- `purchasePresetTitle()` - 购买预设称号
- `createCustomTitleWithName()` - 创建带名称的自定义称号
- 管理玩家登录/退出时的数据加载/卸载

### TitleCacheManager
- 缓存玩家称号数据，减少数据库查询
- 使用 ConcurrentHashMap 保证线程安全

### CustomTitleSessionManager
- 管理自定义称号创建时的多步输入流程
- 会话超时时间：30秒
- 会话状态：等待名称输入 → 等待确认

### DatabaseQueue
- 异步数据库操作队列
- 所有数据库操作通过队列异步执行
- 回调自动切换到主线程（Folia 兼容）

## Folia 兼容性

- 使用 `getGlobalRegionScheduler().execute()` 进行主线程回调
- 数据库操作全部异步执行
- 经济/点券操作使用异步队列

## 跨服支持

- 使用 MySQL 数据库支持跨服
- H2 仅用于单服测试（不支持多服务器同时访问）
- 玩家数据通过数据库同步

## 构建命令

```bash
./gradlew build
```

输出文件: `build/libs/SimpleTitle-1.0.0.jar`

## 待实现功能

- [ ] GUI 界面（称号选择、商店）
- [ ] 称号过期时间
- [ ] 称号特效
- [ ] 称号分类筛选

## 开发注意事项

1. 所有数据库操作必须通过 `TitleRepository` 进行
2. 玩家数据修改后需要同步更新缓存
3. 异步操作回调中使用 `plugin.getServer().getGlobalRegionScheduler().execute()`
4. 消息发送使用 `MessageUtil.send()` 支持颜色代码
