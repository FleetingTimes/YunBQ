# 安装 Java JDK 21 详细指南（Windows）

## 目标
- 为 Android Studio/Gradle 构建设置 JDK 21（满足 Capacitor v7 与部分 AGP/Gradle 组合要求）
- 保持后端项目继续使用 JDK 17，不互相影响

## 适用场景
- Android 原生构建报错“无效的源版本：21”或要求 Java 21
- 需要在同一台电脑同时保留 JDK 17（后端）与 JDK 21（Android 构建）

## 安装方式一（推荐）：winget 安装 Temurin 21
- 打开 PowerShell（管理员或普通均可）
- 安装命令：
  - `winget install EclipseAdoptium.Temurin.21.JDK`
- 验证安装：
  - `"C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe" -version`
- 典型安装路径：`C:\Program Files\Eclipse Adoptium\jdk-21`

## 安装方式二：手动安装包
- 访问 JDK 发行商下载页面（Temurin/Oracle 等），下载适用于 Windows x64 的 JDK 21 安装包（MSI/ZIP）https://www.oracle.com/cn/java/technologies/downloads/
- 安装到固定目录，如：`C:\Java\jdk-21`
- 验证：`C:\Java\jdk-21\bin\java.exe -version`

### 安装包类型区别（x64 Compressed Archive / x64 Installer / x64 MSI Installer）
- x64 Compressed Archive（ZIP 压缩包）
  - 解压即用，不写入注册表与系统路径；便于携带与多版本共存
  - 需要手动设置 `JAVA_HOME` 与 `PATH`（或在工具中选择该 JDK 路径）
  - 适合“只给 Android Studio/Gradle 指定 JDK 路径”或希望严格隔离系统环境的场景
- x64 Installer（EXE 安装程序）
  - 向导式安装，通常会写入注册表并可选更新 `PATH`/`JAVA_HOME`
  - 适合个人快速安装；静默安装支持度依发行商而定（一般 `/S` 或特定参数）
  - 卸载通过“应用和功能”或控制面板完成
- x64 MSI Installer（MSI 安装包）
  - 使用 Windows Installer（MSI），支持企业部署、修复、日志与标准化静默安装（`msiexec /i xxx.msi /qn`）
  - 更易被企业策略管理与批量分发；也会写入注册表并可选更新环境变量
  - 推荐在需要规范化安装、批量部署或可预期的静默安装时使用

### 选择建议
- 需要“便携/不改系统变量/多版本共存” → 选 ZIP（Compressed Archive）
- 个人快速安装 → 选 EXE Installer
- 企业/批量/可控静默部署 → 选 MSI Installer

### 简要步骤示例
- ZIP：解压到 `C:\Java\jdk-21` → 在 Android Studio 选择该路径作为 Gradle JDK → 完成
- EXE：双击安装 → 勾选添加到 PATH（按需）→ 在 Android Studio 选择安装路径 → 完成
- MSI：`msiexec /i <包>.msi /qn`（静默）或双击安装 → 在 Android Studio 选择安装路径 → 完成

## Android Studio 使用 JDK 21（不影响后端）
- 打开 Android Studio → `Settings` → `Build, Execution, Deployment` → `Gradle`
  - `Gradle JDK` 选择刚安装的 JDK 21 路径（例如 `C:\Program Files\Eclipse Adoptium\jdk-21`）
- 可选：`File` → `Project Structure` → `SDK Location` 中的 JDK 路径也设为 JDK 21
- 同步项目并构建：点击 `Sync` 或 `Build`

## 同机多 JDK 共存的做法
- 后端项目继续使用 JDK 17：
  - 你可以在命令行里使用 `JAVA_HOME` 指向 JDK 17，或直接调用 `C:\Java\jdk-17\bin\java.exe`
  - 启动后端命令示例：
    - `"C:\Java\jdk-17\bin\java.exe" -jar backend\target\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`
- Android Studio 单独配置为 JDK 21，用于原生构建。两者互不影响。

## 可选：设置系统环境变量（如需要命令行用 JDK 21）
- 设置 `JAVA_HOME` 为 JDK 21（管理员 PowerShell）：
  - `setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21" /M`
- 将 `PATH` 末尾追加 JDK 21 的 `bin`：
  - `setx PATH "%PATH%;%JAVA_HOME%\bin" /M`
- 重启终端后验证：`java -version`
- 说明：后端若仍需默认 JDK 17，可不改系统变量；仅在 Android Studio 中选择 JDK 21 即可。

## 验证与常见问题
- 验证 JDK 版本：
  - `java -version` 显示 `21.x` 即表示当前终端使用的是 JDK 21
  - Android Studio Gradle Console 不再报“源版本 21 无效”
- `java -version` 仍显示旧版本：
  - 关闭并重新打开终端/Android Studio
  - 检查环境变量与 `PATH` 的优先顺序
- Gradle 仍使用旧 JDK：
  - 在 Android Studio 的 `Gradle JDK` 再次确认已选中 JDK 21 路径
- 网络下载依赖慢/失败：
  - 在 Gradle Wrapper 中使用国内镜像，或在文档“手机应用集成-Capacitor步骤.md”按代理与离线构建建议执行

## 与后端的关系
- 后端（Spring Boot）仍可使用 JDK 17 构建运行；Android 原生构建使用 JDK 21，不冲突。
- 如需在命令行切换 JDK：
  - 使用绝对路径调用对应版本的 `java.exe`
  - 或借助版本管理工具（如 NVM for Windows 管理 Node；JDK 可手动切换 `JAVA_HOME`）

## 小结
- 安装并选择 JDK 21 仅用于 Android 构建即可满足 Capacitor v7 与部分 AGP/Gradle 要求，同时不影响后端继续使用 JDK 17。
- 推荐在 Android Studio 的 `Gradle JDK` 设置处选择 JDK 21，避免全局环境变量变更带来影响。