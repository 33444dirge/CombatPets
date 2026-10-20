# CombatPets · Folia 维护版

面向 Folia / Shiroha 的战斗宠物插件维护分支，保留上游项目与 GPL-3.0 许可证。

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Revision](https://img.shields.io/badge/维护修订-2.7.2--12-green.svg)](docs/BUILD-2.7.2-12.md)

## 当前维护修订

`CombatPets-2.7.2-12.jar`，插件内部版本仍为 `2.7.2`。末尾 `-12` 是本分支的成品修订号。

- 修复点击宠物背包后客户端残留旧菜单、出现可拖动假图标的问题。
- 保留传送门宠物识别的分配优化、MySQL 保存及受控热卸载保护。
- 神秘蛋品质权重可配置，默认神话权重为 0.8%；随机种类遵循已有禁用规则。
- 捕捉绳默认只产出普通、稀有品质，可通过 `Settings.Allowed_Tiers` 调整。
- 属性编辑支持输入“取消”，保留已有配置迁移与中文提示。

准确产物哈希、构建记录、测试范围及限制见 [修订验证记录](docs/BUILD-2.7.2-12.md)。

## 构建

项目使用 Maven，多模块入口为根目录 `pom.xml`。已有 `-12` 构建记录使用 Java 25.0.3、Maven 3.9.11；26.2 模块需要相应的本地服务端/NMS 依赖。第三方依赖可用后，从根目录执行：

```shell
mvn clean verify
```

聚合成品位于 `target/CombatPets-2.7.2.jar`。Maven 构建与独立回归检查分别记录，不将“编译成功”视为运行时验证。构建目录、隔离测试服、生产配置和数据库凭据不进入源码仓库。

## 来源

本仓库是 [33444dirge/CombatPets](https://github.com/33444dirge/CombatPets) 的 fork，保留原作者署名及 [GNU GPL v3](LICENSE)。本分支的运行验证针对记录中的准确 Folia 核心与依赖组合。
