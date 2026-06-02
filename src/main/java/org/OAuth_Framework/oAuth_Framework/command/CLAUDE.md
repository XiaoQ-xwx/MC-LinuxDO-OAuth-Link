[根目录](../../../../../../../CLAUDE.md) > [oAuth_Framework](../) > **command**

# command -- 游戏命令

## 模块职责

提供玩家和管理员使用的游戏内命令。

## 命令列表

### `/linkld`

权限: `oauth_framework.command.link`（默认所有人）

| 用法 | 说明 |
| :--- | :--- |
| `/linkld` | 已绑定 -> 显示账号信息面板（用户名、信任等级、社区分数、论坛链接、退出按钮）；未绑定 -> 发起 OAuth 授权流程，发送可点击授权链接 |
| `/linkld <验证码>` | 手动输入回调页面显示的验证码完成绑定 |
| `/linkld unlink` | 显示解除绑定确认提示（含可点击 [确认解除] 和 [取消操作] 按钮） |
| `/linkld unlink confirm` | 执行解除绑定 |

### `/oauthframework`（别名 `/oauthfw`）

权限: `oauth_framework.admin`（默认 OP）

| 用法 | 说明 |
| :--- | :--- |
| `/oauthfw reload` | 重新加载 config.yml |

## 入口与启动

- 在 `OAuth_Framework.onEnable()` 中通过 `getCommand("linkld")` 和 `getCommand("oauthframework")` 注册
- `LinkCommand` 实现 `CommandExecutor` + `TabCompleter`
- `OAuthFrameworkCommand` 仅实现 `CommandExecutor`

## 对外接口

命令通过 Bukkit 命令系统触发，调用 `OAuthFrameworkService` 方法。

## 关键依赖与配置

- 依赖 `OAuthFrameworkService`（LinkCommand）或 `JavaPlugin + OAuthFrameworkService`（OAuthFrameworkCommand）
- 使用 Bungee Chat API（`ComponentBuilder`, `ClickEvent`, `HoverEvent`）构建可交互聊天消息
- 线程安全：异步绑定结果通过 `Bukkit.getScheduler().runTask()` 切回主线程发送消息

## 数据模型

不直接操作数据模型。`LinkCommand.showProfile()` 通过 `LinkedAccount` 的派生方法（`getTrustLevelLabel()`, `getLikesReceivedLabel()`, `getProfileUrl()`）展示信息。

## 测试与质量

- **测试状态：** 无独立测试
- **建议：** 使用 MockBukkit 模拟 `Player` 和 `CommandSender`，测试各子命令路径

## 常见问题 (FAQ)

**Q: 为什么 `/link` 不再可用了？**
A: `/link` 别名已移除，避免与其它插件的 `/link` 命令冲突。请使用 `/linkld`。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `LinkCommand.java` | `/linkld` 命令（283 行，含 Tab 补全） |
| `OAuthFrameworkCommand.java` | `/oauthfw` 管理命令（67 行） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录 /linkld 重命名、信息面板、unlink 确认流程 |
