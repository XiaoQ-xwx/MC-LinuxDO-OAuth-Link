[根目录](../../../../../../../CLAUDE.md) > [oAuth_Framework](../) > **storage**

# storage -- 持久化存储

## 模块职责

- `LinkRepository` -- 存储接口，定义 CRUD 操作契约
- `YamlLinkRepository` -- 基于 YAML 的实现，使用 Minecraft `YamlConfiguration`

## 入口与启动

- 在 `OAuth_Framework.onEnable()` 中构造，立即调用 `repository.load()` 加载已有数据
- `onDisable()` 中调用 `repository.flush()` 确保数据落盘

## 对外接口

### LinkRepository

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `load()` | `CompletableFuture<Void>` | 从磁盘加载所有绑定数据到内存 |
| `findByPlayer(UUID)` | `Optional<LinkedAccount>` | 同步查询（内存缓存） |
| `findByLinuxDoId(String)` | `Optional<LinkedAccount>` | 按 LinuxDO ID 查询 |
| `save(LinkedAccount)` | `CompletableFuture<Void>` | 异步保存（覆写同一玩家旧条目） |
| `delete(UUID)` | `CompletableFuture<Void>` | 异步删除 |
| `flush()` | `CompletableFuture<Void>` | 强制写盘 |

## 关键依赖与配置

- 存储文件路径由 `config.getStorageFile()` 决定（默认 `data.yml`）
- 使用 `ConcurrentHashMap` 保证线程安全读写
- 写盘策略：**原子写入**（先写 temp 文件，再 `ATOMIC_MOVE` 替换，防止写一半崩溃导致数据损坏）
- 所有 I/O 操作在专用 `ioExecutor` 单线程池执行

## 数据模型

持久化结构（YAML 格式）：
```yaml
accounts:
  <player-uuid>:
    player-name: "PlayerName"
    linuxdo-id: "12345"
    linuxdo-username: "username"
    linuxdo-display-name: "Display Name"
    trust-level: 2
    likes-received: 42
    raw-profile-json: '{"id":12345,...}'
    linked-at: 1717200000
    token-expires-at: 1717203600
```

## 测试与质量

- **测试状态：** `YamlLinkRepositoryTest.java` -- 10 个测试用例
- 覆盖：load（文件不存在）、findByPlayer/findByLinuxDoId、save（覆写）、delete（级联删除双向索引）、delete 后重新 save、并发 save、flush
- 使用 JUnit 5 `@TempDir` 进行文件隔离

## 常见问题 (FAQ)

**Q: 为什么读是同步的但写是异步的？**
A: 读操作从 `ConcurrentHashMap` 内存获取，零 I/O，可以同步。写操作需要序列化 YAML 并写入磁盘，使用异步避免阻塞调用线程。

**Q: `rawProfileJson` 字段很大怎么办？**
A: 这是设计权衡 -- 存储完整 JSON 允许下游插件解析任意字段，但会增加 data.yml 体积。如果未来成为性能问题，可考虑仅存储必要字段的快照。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `LinkRepository.java` | 接口定义（44 行） |
| `YamlLinkRepository.java` | YAML 实现（150 行，含原子写入） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录原子写入策略和 rawProfileJson 扩展字段 |
