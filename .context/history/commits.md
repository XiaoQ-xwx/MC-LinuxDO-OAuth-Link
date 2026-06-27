# Commit Decision History

> 此文件是 `commits.jsonl` 的人类可读视图，可由工具重生成。
> Canonical store: `commits.jsonl` (JSONL, append-only)

| Date | Context-Id | Branch | Summary | Decisions |
|------|-----------|--------|---------|-----------|
| 2026-06-03 | `4563b6ba` | `feature/multi-platform-multi-version` | ♻️ refactor: 重构为多模块项目 | 拆分为 5 模块、OAuthPlatform 抽象、Jackson relocate、SnakeYAML 替代 |
| 2026-06-07 | `f2d3bbce` | `master` | ci(workflow): enhance CI with test reports, artifacts, and auto-release | release job、版本号注入、依赖 build、dependency-submission 条件限制 |
| 2026-06-27 | `a1b2c3d4` | `master` | feat(build): 支持 Minecraft 1.16.5 ~ 1.21.x 多版本兼容 | 编译基线 1.16.5、api-version 1.16、放弃 1.7.10、统一 Bungee Chat、单一 jar |
