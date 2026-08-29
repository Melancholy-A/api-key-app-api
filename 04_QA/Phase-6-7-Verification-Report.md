# 第 6-7 阶段验证报告

日期：2026-08-29
版本：1.10.1（versionCode 90）

## 实现范围

- 历史面板接入聊天 JSON 备份与恢复。
- 导出使用 `ACTION_CREATE_DOCUMENT`，导入使用 `ACTION_OPEN_DOCUMENT`。
- 备份保存聊天正文、全部回答分支元数据、标题、时间和置顶状态；不保存 API key、Base URL、模型/应用设置、生成图片或 Office 文件。导出与导入都限制为最多 50 个会话、100,000 条消息和 16 MiB。
- 文件读写、JSON 编解码和 SQLite 恢复事务在后台串行执行，主线程只更新状态和显示确认。
- 恢复先解析并显示聊天数、消息数，再以 SQLite 事务覆盖同 ID 会话，其他本地会话保留；若新增导入会使总数超过 50，恢复会在写入前拒绝，避免静默删除本地聊天。结构不完整、解析失败不会写入历史。
- SQLite 保存使用 `MessageDeltaPlanner`：新增消息只插入尾部新增位置，分支切换只更新变化位置，截断只删除过期尾部；保留历史分页、分支和 50 个会话上限。

## 自动化验证

| 检查 | 命令/证据 | 结果 |
|---|---|---|
| 完整单元测试 | `.tools/gradle-8.10.2/bin/gradle.bat testDebugUnitTest --rerun-tasks --console=plain` | 49 tests，0 failures，0 errors |
| Lint | `.tools/gradle-8.10.2/bin/gradle.bat lintDebug --rerun-tasks --console=plain` | BUILD SUCCESSFUL，0 errors，34 warnings（均为既有项目 warning） |
| Debug 构建 | `.tools/gradle-8.10.2/bin/gradle.bat assembleDebug --rerun-tasks --console=plain` | BUILD SUCCESSFUL |
| APK 元数据 | `aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk` | `versionCode='90' versionName='1.10.1'` |
| 数学渲染 | bundled Node 执行 `scripts/test-chat-math-normalization.js` | `chat math normalization ok` |
| 密钥扫描 | `rg -n -I -P ...`（排除 build/dist/.tools/输出目录） | `NO_SECRET_MATCHES` |
| 工作区格式 | `git diff --check` | 通过 |

## APK

- `03_Output/CodexMobile-1.10.1-debug.apk`：1,284,326 bytes，SHA-256 `34839DF9F9C25990C14C1DB24E7320BBC3374C1C7290D99F667C9EF8946CB879`
- `03_Output/CodexMobile-debug.apk`：1,284,326 bytes，SHA-256 `34839DF9F9C25990C14C1DB24E7320BBC3374C1C7290D99F667C9EF8946CB879`

## 真机状态

本轮 `adb devices` 未发现已连接设备，因此未确认 APK 在具体华为 HarmonyOS 机型上的安装、文件选择器、备份恢复、相机和附件行为。以下步骤供手机实测并回填结果。

## 待执行真机用例

1. 安装 `CodexMobile-1.10.1-debug.apk`，填写测试 API key 和 Base URL，发送至少两轮消息。
2. 在历史面板点“备份”，保存 `codex-chat-backup.json`；用文本查看器确认没有 `api_key`、`baseUrl`、`data:image`、`generated_office_files`、本地生成文件路径或 `![生成图片](https://...)` 的远程生成图片 URL。
3. 新建一个本地聊天，再恢复备份；确认恢复前显示聊天数/消息数，同 ID 聊天被覆盖，新建的本地聊天仍存在。若总数已达 50 且备份包含新会话，确认显示恢复被拒绝且所有原有聊天仍存在。
4. 选择损坏 JSON 或手工改错 `schemaVersion`，确认提示失败且原历史仍可打开。
5. 对同一用户消息生成两个回答分支，切换分支并重新打开历史，确认旧分支和分页加载仍在。
6. 连续创建超过 50 个有消息会话，确认置顶会话优先保留且总数不超过 50。
7. 在 HarmonyOS 文件管理器中验证两个 APK 均可安装和启动；记录纯净模式、未知来源权限或企业策略导致的拦截信息。
