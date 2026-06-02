[根目录](../../../../../../../CLAUDE.md) > [oAuth_Framework](../) > **config**

# config -- 配置管理

## 模块职责

- 从 `config.yml` 加载配置
- 校验关键字段（client-id、client-secret、端口范围、TTL 下限）
- 提供不可变运行时配置对象

## 入口与启动

- `OAuthConfig.load(plugin)` -- 在 `OAuth_Framework.onEnable()` 和 `/oauthfw reload` 中调用
- 调用 `plugin.saveDefaultConfig()` 确保默认配置文件存在
- 构造后立即调用 `validate()`

## 对外接口

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `load(JavaPlugin)` | `OAuthConfig` | 静态工厂，加载并校验 |
| `validate()` | `void` | 校验配置，不合法抛 `OAuthException` |

### 校验规则

| 检查项 | 条件 | 错误 |
| :--- | :--- | :--- |
| `oauth.client-id` | 非空且非 `your_id_here` | `CONFIG_INVALID` |
| `oauth.client-secret` | 非空且非 `your_secret_here` | `CONFIG_INVALID` |
| `callback.port` | 1-65535 | `CONFIG_INVALID` |
| `security.state-ttl-seconds` | >= 30 | `CONFIG_INVALID` |
| `security.link-code-ttl-seconds` | >= 30 | `CONFIG_INVALID` |

### 配置项获取

所有字段通过 getter 访问（`getClientId()`, `getCallbackPort()` 等），共 13 个 getter。

## 关键依赖与配置

- 依赖 Bukkit `FileConfiguration` API
- `OAuthConfig` 对象创建后不可变（所有字段 final）
- 默认值在构造函数中指定（如 `authorization-url` 默认 `https://connect.linux.do/oauth2/authorize`）

## 数据模型

纯配置对象，不操作持久化数据。

## 测试与质量

- **测试状态：** `OAuthConfigTest.java` -- 15 个测试用例，覆盖全面
- 覆盖：合法配置、null/blank/placeholder client-id、null/blank/placeholder client-secret、端口 0/负数/超范围、TTL 过低、自定义值覆盖默认值、默认值应用

## 常见问题 (FAQ)

**Q: 为什么 `validate()` 检查 `your_id_here` 和 `your_secret_here`？**
A: 防止用户忘记修改示例配置。这些 sentinel 值直接引发明确的错误消息。

**Q: reload 命令会创建新的 `OAuthConfig` 吗？**
A: 是的，`/oauthfw reload` 重新调用 `OAuthConfig.load()`，但当前实现中新的 config 对象仅用于验证提示，不会重新注入到已运行的服务中（已知限制）。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `OAuthConfig.java` | 配置类（91 行，含校验） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录 sentinel 值检测和 reload 局限性 |
