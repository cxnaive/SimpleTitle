# SimpleTitle

一个简单而强大的 Minecraft 玩家称号系统插件，支持 Folia/Paper 服务器。

## 功能特性

- **预设称号系统** - 管理员可配置预设称号，支持金币/点券购买
- **自定义称号** - 玩家可创建专属称号，支持静态和动态效果
- **动态称号** - 多内容循环显示，打造炫酷彩虹效果
- **边框系统** - 多种边框可选，自由搭配称号风格
- **GUI 界面** - 直观的图形界面，操作简单
- **PlaceholderAPI** - 支持 PAPI 变量显示称号
- **数据库支持** - 支持 H2 本地存储和 MySQL 跨服同步
- **Folia 兼容** - 完美支持 Folia 多线程区块调度

## 依赖

**必需:**
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)

**可选:**
- [XConomy](https://www.spigotmc.org/resources/xconomy.75669/) - 金币经济系统
- [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.40870/) - 点券系统

## 安装

1. 下载最新的 JAR 文件
2. 放入服务器的 `plugins` 目录
3. 重启服务器
4. 根据需要修改配置文件

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/title` | 打开称号主菜单 | `simpletitle.gui` |
| `/title set <ID>` | 设置当前称号 | `simpletitle.set` |
| `/title clear` | 清除当前称号 | `simpletitle.clear` |
| `/title list` | 查看拥有称号 | `simpletitle.list` |
| `/title shop` | 打开称号商店 | `simpletitle.shop` |
| `/title custom` | 创建自定义称号 | `simpletitle.custom` |
| `/title brackets` | 打开边框商城 | `simpletitle.bracket` |
| `/title give <玩家> <ID>` | 给予玩家称号 | `simpletitle.give` |
| `/title import plt <文件>` | 导入 PLT 格式数据 | `simpletitle.import` |
| `/title reload` | 重载配置 | `simpletitle.reload` |

## 权限

| 权限 | 说明 | 默认 |
|------|------|------|
| `simpletitle.use` | 使用基础命令 | 所有人 |
| `simpletitle.gui` | 打开GUI界面 | 所有人 |
| `simpletitle.set` | 设置称号 | 所有人 |
| `simpletitle.clear` | 清除称号 | 所有人 |
| `simpletitle.list` | 查看称号列表 | 所有人 |
| `simpletitle.shop` | 打开称号商店 | 所有人 |
| `simpletitle.custom` | 自定义称号 | 所有人 |
| `simpletitle.bracket` | 边框相关 | 所有人 |
| `simpletitle.admin` | 管理员权限 | OP |
| `simpletitle.reload` | 重载配置 | OP |
| `simpletitle.give` | 给予称号 | OP |
| `simpletitle.import` | 导入数据 | OP |

## PlaceholderAPI 变量

| 变量 | 说明 |
|------|------|
| `%simpletitle_current%` | 当前使用的称号（带边框和颜色） |
| `%simpletitle_current_id%` | 当前称号ID |
| `%simpletitle_count%` | 拥有的称号数量 |

## 配置文件

### config.yml

```yaml
# 数据库配置
database:
  type: h2  # h2 或 mysql
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: password

# 自定义称号配置
custom-title:
  enabled: true
  max-length: 8
  max-name-length: 10
  price-money: 5000
  price-points: 0
  dynamic-price-money: 10000
  dynamic-price-points: 50
  session-timeout: 120

# 动态称号配置
dynamic-title:
  switch-interval: 2  # 切换间隔(tick)

# 称号边距
title-padding:
  left: ''
  right: ' '

# 默认边框
default-bracket:
  left: '『'
  right: '』'
```

### titles.yml

预设称号配置：

```yaml
titles:
  newbie:
    display-name: "萌新"
    content: "&b萌新"
    price-money: 1000
    price-points: 0
    category: "coin"

  rainbow:
    display-name: "彩虹"
    contents:
      - "&crainbow"
      - "&6rainbow"
      - "&erainbow"
      - "&arainbow"
      - "&brainbow"
    price-money: 0
    price-points: 200
    category: "special"
```

### brackets.yml

预设边框配置：

```yaml
brackets:
  default:
    display-name: "默认"
    bracket-left: "『"
    bracket-right: "』"
    is-default: true

  star:
    display-name: "星星"
    bracket-left: "&e★"
    bracket-right: "&e★"
    price-points: 50
```

## 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/SimpleTitle-1.0.0.jar`

## 数据库表结构

### player_titles
| 字段 | 类型 | 说明 |
|------|------|------|
| player_uuid | VARCHAR(36) | 玩家UUID |
| title_id | VARCHAR(64) | 称号ID |
| title_data | TEXT | 称号数据(JSON) |
| on_use | BOOLEAN | 是否使用中 |
| obtained_at | BIGINT | 获得时间戳 |

### player_brackets
| 字段 | 类型 | 说明 |
|------|------|------|
| player_uuid | VARCHAR(36) | 玩家UUID |
| bracket_id | VARCHAR(64) | 边框ID |
| obtained_at | BIGINT | 获得时间戳 |

## 开源协议

[MIT License](LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request！
