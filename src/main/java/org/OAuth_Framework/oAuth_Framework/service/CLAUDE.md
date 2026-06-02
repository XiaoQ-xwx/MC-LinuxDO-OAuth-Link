[根目录](../../../../../../../CLAUDE.md) > [oAuth_Framework](../) > **service**

# service -- 核心业务编排层

## 模块职责

`OAuthFrameworkService` 是插件的业务中枢，负责：
- 实现 `OAuthFrameworkProvider` 接口
- 协调 OAuth 流程（创建授权 URL、自动绑定、手动验证码绑定）
- 管理存储读写与 Bukkit 事件触发
- 插件生命周期回调（onEnable/onDisable）

## 入口与启动

- 在 `OAuth_Framework.onEnable()` 中构造，随后调用 `service.onEnable()`
- `onEnable()` 执行：加载持久化数据 -> 启动回调 HTTP 服务器 -> 注册 API
- `onDisable()` 执行：注销 API -> 停止 HTTP 服务器 -> 刷新持久化数据

## 对外接口

### OAuth 绑定流程

| 方法 | 说明 |
| :--- | :--- |
| `createAuthorizationUri(UUID, String)` | 创建带 state 的 LinuxDO 授权 URL |
| `onAutoBind(UUID, String, LinuxDoProfile, OAuthTokens)` | 回调自动绑定（由 HTTP handler 异步调用） |
| `linkPlayer(UUID, String, String)` | 手动验证码绑定（由 LinkCommand 调用） |

### 绑定安全检查

- 同一玩家已绑定不同 LinuxDO 账号 -> 拒绝（`ALREADY_LINKED`）
- 同一 LinuxDO 账号已绑定不同玩家 -> 拒绝（`ALREADY_LINKED`）

### 事件触发

- 绑定成功 -> `PlayerOAuthSuccessEvent`（主线程，可取消）
- 绑定失败 -> `PlayerOAuthFailEvent`（主线程，可取消）
- 解除绑定 -> `PlayerOAuthUnlinkEvent`（主线程，不可取消）

## 关键依赖与配置

- 依赖全部内层模块：`config`, `storage`, `oauth`, `http`, `event`, `model`, `api`
- 使用 `Bukkit.getScheduler().runTask()` 将回调线程结果切回主线程触发事件
- Token 过期容忍偏差通过 `config.getTokenExpirySkewSeconds()` 配置

## 数据模型

通过 `LinkRepository` 接口操作 `LinkedAccount` 持久化数据。不在本模块直接定义数据结构。

## 测试与质量

- **测试状态：** 无独立测试（最需要测试的模块）
- **建议：**
  1. Mock `LinkRepository`、`PendingOAuthRegistry`、`LinuxDoOAuthClient`、`CallbackHttpServer`
  2. 测试 `onAutoBind` 的重复绑定检测逻辑
  3. 测试 `linkPlayer` 的验证码过期/无效/重复绑定路径
  4. 使用 MockBukkit 模拟 Bukkit 调度器

## 常见问题 (FAQ)

**Q: 为什么 `onAutoBind` 和 `linkPlayer` 有重复的绑定校验逻辑？**
A: 两条路径的调用上下文不同（HTTP 线程 vs 命令线程），且 `linkPlayer` 需要通过 `CompletableFuture` 返回结果给命令处理器，暂时未做统一抽象。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `OAuthFrameworkService.java` | 核心业务编排（299 行，本模块唯一文件） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录自动绑定与手动绑定两条路径 |
