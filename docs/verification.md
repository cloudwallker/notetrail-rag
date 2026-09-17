# 验证记录

验证日期：2026-09-16。环境：Windows、OpenJDK 21.0.1、Maven 3.8.5。

## 已执行

- `mvn -B -ntp clean verify`：BUILD SUCCESS，17 个测试，0 失败、0 错误、0 跳过；包含 Spotless 与 JDK/Maven 版本检查。
- `python scripts/smoke.py`：解压真实 ZIP、启动其中的 JAR，验证首页和 JavaScript 资源、文档导入、检索、本地摘录问答、无命中、非法 Top K、服务重启后数据保留、删除后不再召回、历史引用快照保留。测试通过，临时进程已停止。
- 运行时 38 个原始依赖 JAR 的许可资源已提取到 `licenses/`，通过 `python scripts/collect_licenses.py --check` 校验清单和资源内容。
- JavaScript、Python、XML、PowerShell 与 Shell 脚本语法检查通过；另进行了独立源码审查。

默认 H2 URL 已设置 `WRITE_DELAY=0`，冒烟测试直接使用发行包默认数据库配置，并在请求完成后终止、重启进程；修订配置后完整构建与冒烟测试再次通过。这不构成断电或硬件故障下的数据持久性保证。参见 [H2 WRITE_DELAY](https://h2database.com/html/commands.html#set_write_delay)。

## 尚未验证

- 当前自动化环境没有可用浏览器，未执行浏览器点击或视觉验收。可按 README 的四步流程手动检查页面。
- 模型调用测试使用本地模拟 HTTP 服务，未连接真实付费模型。
- 已配置 Windows/Linux GitHub Actions，但尚未推送远端，因此不把远端 CI 或 Linux 实机验证视为已通过。

`target/smoke-report.json` 和 `target/surefire-reports/` 是本次构建的本地证据，属于忽略的构建产物。可随时重新运行上述命令生成自己的记录。
