[根目录](../../../../../../../CLAUDE.md) > [oAuth_Framework](../) > **util**

# util -- 工具类

## 模块职责

`OAuthCodeGenerator` -- 线程安全的随机码生成器，用于：
- OAuth2 CSRF state 参数（32 字符 URL-safe Base64）
- 手动验证码（8 字符易读字母数字，不含混淆字符 0/O/I/1）

## 入口与启动

在 `OAuth_Framework.onEnable()` 中构造，传入 `PendingOAuthRegistry`。

## 对外接口

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `generateState()` | `String` | 32 字符 URL-safe Base64（24 字节随机） |
| `generateLinkCode()` | `String` | 8 字符易读码（字符集：`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`） |

## 关键依赖与配置

- 使用 `java.security.SecureRandom` 保证密码学安全的随机性
- 无外部依赖，无配置项

## 数据模型

无。

## 测试与质量

- **测试状态：** `OAuthCodeGeneratorTest.java` -- 7 个测试用例
- 覆盖：state 长度/字符集/无 padding/唯一性、linkCode 长度/字符集/无混淆字符/唯一性
- 100 次批量唯一性检测确保碰撞概率可忽略

## 常见问题 (FAQ)

**Q: 为什么 linkCode 不含 0/O/I/1？**
A: 这些字符在手动输入时容易混淆（0 vs O, 1 vs I），排除后减少用户输入错误。

**Q: state 有 32 字符，linkCode 只有 8 字符，安全性够吗？**
A: state 需要抵抗 CSRF 攻击，使用 192 位熵（24 bytes）。linkCode 仅用于手动输入场景（玩家已通过浏览器授权），8 字符在 300 秒 TTL + 有限尝试次数下是安全的。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `OAuthCodeGenerator.java` | 随机码生成（41 行） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档 |
