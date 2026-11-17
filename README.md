# BaizhuGallery

<img alt="Gallery icon" src="graphics/icon.webp" width="96" />

基于 Fossify Gallery 独立维护的 Android 图库；桌面名称仍为 **Gallery**。
项目位于全新的非 fork 仓库 [baizhu945/BaizhuGallery](https://github.com/baizhu945/BaizhuGallery)，不是 Fossify 官方版本，也不代表其团队。

- **下载**：[GitHub Releases](https://github.com/baizhu945/BaizhuGallery/releases)（本项目的正式发布渠道；无官方商店上架声明）
- **反馈**：[本项目 Issues](https://github.com/baizhu945/BaizhuGallery/issues) · [贡献指南](CONTRIBUTING.md)
- **独立稳定版 1.15.0**：应用 ID、namespace 与源码包根为 `org.fossifymigration.baizhu.gallery`；发布 APK 使用 Release 构建，不带 `.debug`。

默认图标与短暂启动画面使用蓝色 `#0288D1`（Light_blue）；Green 等可配置备用图标保留，Android 主题图标仍由系统/启动器配色。“关于”的源码/反馈入口指向本项目；上游 FAQ、许可与致谢链接明确标注来源。Commons 的最小兼容调整见[说明](docs/commons-compatibility.md)。

## 相对上游的三个重点

1. **隐藏筛选与来源归属**：支持“仅显示隐藏的项目”，默认只显示本应用成功隐藏或用户明确认领的隐藏内容；“包含其它隐藏”是显式选择，不按文件大小猜测来源。[筛选说明](docs/hidden-only-items.md) · [归属与认领边界](docs/user-hidden-items.md)
2. **准备在工作线程完成**：过滤、排序与分组准备、最新请求优先的搜索、索引查询与缩略图复用等调整，减少重复工作。[实现说明](docs/performance-and-bulk-operations.md) · [历史测量](docs/performance-validation.md)。结果有改善也有回退，不承诺所有设备或所有场景加速。
3. **真实批量进度与数据保护**：按已处理项目、已知字节量显示进度，未知阶段不伪造百分比；冲突不静默覆盖，原生删除拒绝不当作成功，回收站恢复验证结果后再清理备份。[进度及安全语义](docs/performance-and-bulk-operations.md)

## 安装前必读

新包可与旧版并存，但**不会自动迁移私有设置、收藏、隐藏归属、回收站备份、密码或 SAF 授权**。卸载旧应用会丢失其私有备份：请先恢复回收站内容、导出所需数据并核对文件。共享媒体不会因安装新包自动复制；磁盘上的 `.nomedia` / 点名前缀隐藏状态仍共享。

详见[独立发布、迁移边界与构建](docs/independent-release.md)和[1.15.0 验证范围](docs/independent-validation.md)。旧验证文档仅证明其中明确标识的历史构建，不代表已对 1.15.0 重跑设备矩阵。

## 致谢与许可

感谢 [Fossify Gallery](https://github.com/FossifyOrg/Gallery)、[Fossify Commons](https://github.com/FossifyOrg/Commons) 及其贡献者，也感谢更早的 [Simple Mobile Tools](https://github.com/SimpleMobileTools) 项目传承。本项目保留原有版权声明、作者及提交历史，遵循 [GNU GPL v3](LICENSE)；独立发布不改变上游代码的版权归属。历史版本记录见 [CHANGELOG](CHANGELOG.md)。

**English:** Independently maintained Android Gallery derived from Fossify Gallery, with explicit hidden-origin policy, worker-prepared browsing and honest bulk-operation feedback. Not affiliated with Fossify. Use this repository's releases/issues; read the migration warnings before uninstalling any older app. GPL-3.0; upstream credits and history retained.
