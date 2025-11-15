# 手机应用集成（Capacitor）步骤

本文件给出将当前网页应用封装为 Android 原生应用的可执行步骤。你已安装 Android Studio，按顺序执行即可运行与打包。

## 1. 前置准备
- 安装：Node.js（≥20）、npm、Java JDK 17、Android Studio（含 SDK/平台工具）
- Android 设备或模拟器可用（启用开发者选项与 USB 调试）
- 线上站点与接口：`https://app.shiyan.online` 与 `https://api.shiyan.online`

## 2. 安装与初始化
- 在前端目录执行：
  ```bash
  npm i @capacitor/core @capacitor/cli
  npx cap init Shiyan com.yunbq.shiyan
  npx cap add android
  ```
### Node.js 版本
- 确保 Node.js 版本为 20 或以上，否则可能会遇到兼容性问题。
- 查看当前 Node.js 版本：
  ```bash
  node -v
  ```
- 方式一： 升级 Node.js 到最新版本：
  ```bash
  winget install OpenJS.NodeJS.LTS
  ```
  或者
  ```bash
  winget upgrade OpenJS.NodeJS.LTS
  ```
- 方式二：(NVM 管理多版本 Node.js)
  - 安装 NVM：
    ```bash
    winget install CoreyButler.NVMforWindows
    ```
  - 查看可用 Node.js 版本：
    ```bash
    nvm list available
    ```
  - 安装最新 LTS 版本：
    ```bash
    nvm install lts
    ```
  - 切换到新安装的版本：
    ```bash
    nvm use lts
    ```
- 重启终端或命令行窗口，确保新安装的 Node.js 版本生效。
- 即可重新执行 上面前端目录 中的命令。
### 执行 npx cap add android 时遇到的问题
- 问题：`[error] Missing appId for new platform.`
            `Please add it in capacitor.config.json or run npx cap init.`
            `Could not find the android platform.`
            `You must install it in your project first, e.g. w/ npm install`
            `@capacitor/android`
- 原因：
  - Missing appId （需要在 capacitor.config.json 中提供或先运行 npx cap init ）
  - 未安装 @capacitor/android 平台包
- 解决：
  - 1、确认Node版本(20+)
    - node -v
  - 2、在前端目录添加配置文件
    - 创建 `capacitor.config.json` ：
      ```json
      {
        "appId": "com.yunbq.shiyan",
        "appName": "Shiyan",
        "server": { "url": "https://app.shiyan.online" }
      }
      ```
    - 如果走离线包模式,可省略 `server.url` ,但需确保后端 CORS 已配置允许 `capacitor://localhost`
  - 3、安装Android 平台包
    ```bash
    npm install @capacitor/android
    ```
  - 4、添加平台并打开工程
   - 添加 Android 平台：
     ```bash
     npx cap add android
     ```
   - 打开 Android Studio 工程：
     ```bash
     npx cap open android
     ```
  - 5、同步资源(如有变更)
   ```bash
   npx cap sync
   ```
  - 完成后即可在 Android Studio 中运行到设备/模拟器，并按“手机应用集成-Capacitor步骤.md”继续签名与打包。



## 3. 选择加载模式
- 远程加载（推荐）
  - 在项目根或前端目录创建 `capacitor.config.json`：
    ```json
    {
      "appId": "com.yunbq.shiyan",
      "appName": "Shiyan",
      "server": { "url": "https://app.shiyan.online" }
    }
    ```
  - 前端生产环境：`frontend/.env.production` 设置 `VITE_API_BASE=https://api.shiyan.online/api`
  - 后端生产 CORS：允许来源 `https://app.shiyan.online` 或通配 `https://*.shiyan.online`；方法 `GET,POST,PUT,DELETE,OPTIONS,PATCH`；头 `Authorization,Content-Type`
- 离线包（可选）
  - 生产构建并复制：
    ```bash
    cd frontend
    npm run build
    npx cap copy
    ```
  - 后端 CORS 需额外允许 `capacitor://localhost`
  - 仍建议接口指向 `https://api.shiyan.online/api`

## 4. 同步与打开原生工程
- 同步 Capacitor 资源：
  ```bash
  npx cap sync
  ```
- 打开 Android 工程：
  ```bash
  npx cap open android
  ```
- 在 Android Studio 选择设备/模拟器，点击运行

## 5. 联调与网络
- 开发期指向本机后端（仅临时）：
 - 因为已经采用了远程加载模式，所以无需指向本机后端
  - 模拟器地址：`http://10.0.2.2:<port>`；真机：`http://<局域网IP>:<port>`
  - 如必须使用 `http`，在 `AndroidManifest.xml` 启用：`android:usesCleartextTraffic="true"`（生产建议全 HTTPS）

## 6. 签名与打包
- 在 Android Studio 中生成签名密钥，配置签名后输出：
  - `APK`（直接安装分发）或 `AAB`（商店上架）
- 填写应用图标、名称与版本号（`appName/appId/版本`）

### 应用图标、名称与版本号的详细过程
- 应用名称
  - 文件：`android/app/src/main/res/values/strings.xml`
  - 修改 `app_name` 值，例如：`<string name="app_name">Shiyan</string>`
  - `AndroidManifest.xml` 通常通过 `android:label="@string/app_name"` 引用该名称
- 包名（AppId/Bundle Id）与版本
  - 文件：`android/app/build.gradle`
  - 在 `defaultConfig` 中设置：
    - `applicationId "com.yunbq.shiyan"`（与 `frontend/capacitor.config.json` 的 `appId` 保持一致）
    - `versionCode 1`（整数，每次发布递增）
    - `versionName "1.0.0"`（语义化版本号）
- 应用图标
  - 位置：`android/app/src/main/res/mipmap-*`（启动图标资源）
  - 生成方式：Android Studio → 右键 `app/src/main/res` → New → Image Asset → 选择前景/背景素材 → 生成 `ic_launcher`
  - 自适应图标文件：`res/mipmap-anydpi-v26/ic_launcher.xml` 引用前景/背景资源（`res/drawable*` 或 `res/color`）
- 配置一致性
  - `frontend/capacitor.config.json` 的 `appName` 与 `strings.xml` 的 `app_name` 保持一致
  - `applicationId` 与 `capacitor.config.json` 的 `appId` 保持一致，避免包名不匹配

### 打包输出路径（完成后在哪里找到文件）
- APK（Release）：`android/app/build/outputs/apk/release/app-release.apk`
- AAB（Release，上架用）：`android/app/build/outputs/bundle/release/app-release.aab`
- Debug 构建：
  - APK：`android/app/build/outputs/apk/debug/app-debug.apk`
  - AAB：一般不生成 Debug AAB，开发测试使用 Debug APK 即可
- 安装示例（APK）：`adb install -r android/app/build/outputs/apk/release/app-release.apk`

### 修改安装包文件名（APK/AAB）
- 默认文件名：`app-release.apk`、`app-release.aab`。
- 修改 APK 文件名（AGP 8 推荐写法）：在 `android/app/build.gradle` 追加：
```
androidComponents {
  onVariants(selector().all(), { variant ->
    variant.outputs.forEach { output ->
      def vName = variant.versionName.orNull ?: "1.0.0"
      def vCode = variant.versionCode.orNull ?: 1
      def bt = variant.buildType
      // 仅重命名 APK 输出
      output.outputFileName.set("shiyan-${bt}-v${vName}(${vCode}).apk")
    }
  })
}
```
- 修改 AAB 文件名方式（后处理复制重命名）：在 `android/app/build.gradle` 追加：
```
tasks.register('renameBundleRelease', Copy) {
  dependsOn tasks.named('bundleRelease')
  from("$buildDir/outputs/bundle/release")
  include('app-release.aab')
  rename { fileName -> fileName.replace('app-release', "shiyan-release-v${android.defaultConfig.versionName}") }
  into("$buildDir/outputs/bundle/release")
}
```
- 使用：正常执行 `Generate Signed Bundle/APK` 后，如需 AAB 重命名，运行 Gradle 任务 `renameBundleRelease`。

### 签名字段说明（向导中的四个输入）
- 也可根据gitee中我的Android-Studio-Study.md文档中查看，也有详细打包步骤
- `key store path`：签名密钥库文件路径（`.jks`/`.keystore`/`.p12`），内含一个或多个密钥条目
- `key store password`：打开密钥库所需的密码
- `key alias`：用于签名的密钥条目别名（同一 keystore 可有多个 alias）
- `key password`：该 alias 的私钥密码（可与 keystore 密码相同或不同）

### 示例填写（证书标识）
- CN（first and last name）：Shiyan App
- OU（Organizational unit）：Mobile
- O（Organization）：YunBQ
- L：Shenzhen
- ST：Guangdong
- C：CN

### 一键生成密钥库（命令行示例）
```bash
keytool -genkeypair -v \
  -keystore shiyan-release.keystore \
  -storetype pkcs12 \
  -alias shiyan \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -dname "CN=Shiyan App, OU=Mobile, O=YunBQ, L=Shenzhen, ST=Guangdong, C=CN"
```
查看证书指纹：
```bash
keytool -list -v -keystore shiyan-release.keystore -alias shiyan
```

### Gradle 自动签名（可选）
- `frontend/android/gradle.properties`（仅本地，勿提交到公共仓库）
```
MYAPP_RELEASE_STORE_FILE=../keystore/shiyan-release.keystore
MYAPP_RELEASE_KEY_ALIAS=shiyan
MYAPP_RELEASE_STORE_PASSWORD=<你的keystore密码>
MYAPP_RELEASE_KEY_PASSWORD=<你的key密码>
```
- `frontend/android/app/build.gradle` 中：
```
android {
  signingConfigs {
    release {
      storeFile file(MYAPP_RELEASE_STORE_FILE)
      storePassword MYAPP_RELEASE_STORE_PASSWORD
      keyAlias MYAPP_RELEASE_KEY_ALIAS
      keyPassword MYAPP_RELEASE_KEY_PASSWORD
    }
  }
  buildTypes {
    release {
      minifyEnabled false
      signingConfig signingConfigs.release
    }
  }
}
```

### 注意
- keystore 文件与密码不得提交到代码仓库，须安全备份；遗失会导致无法更新已发布应用
- 生产需全 HTTPS，不要启用 `android:usesCleartextTraffic="true"`

## 7. 验收清单
- 首页与导航数据加载正常，弱网下不白屏
- 登录与受保护接口正常（`Authorization: Bearer <JWT>`）
- 头像上传与 `/uploads/**` 访问正常
- CORS：`https://app.shiyan.online`（远程加载）与（如用离线包）`capacitor://localhost` 已放行
- 设备返回键、状态栏与启动画面视觉符合预期（按需使用 Capacitor 插件配置）

## 8. 常见问题
- 无数据：检查 `VITE_API_BASE` 是否为 `https://api.shiyan.online/api`；后端 CORS 是否放行来源
- Mixed Content 警告：在 HTTPS 页面请求了 `http` 接口，改为全 HTTPS
- 设备访问本机失败：模拟器用 `10.0.2.2`，真机用局域网 IP；防火墙与端口映射是否放行

## 9. Android Studio 网络与 Gradle
- 代理配置（Android Studio）
  - 打开 Settings → Appearance & Behavior → System Settings → HTTP Proxy，填写你的代理（如系统代理）并测试连接
  - 勾选 Use proxy for Gradle，应用后重新 Sync
- 使用国内镜像
  - 修改 `android/gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 为镜像地址（如 `https://mirrors.cloud.tencent.com/gradle/gradle-8.9-all.zip`），并适当提升 `networkTimeout`
  - 如需替换 Maven 仓库，可在 `repositories` 中增加国内镜像源（例如阿里云/Tencent/Huawei 的 Maven 镜像）
    - https://repo.huaweicloud.com/gradle/gradle-8.9-all.zip
    - https://mirrors.aliyun.com/gradle/gradle-8.9-all.zip
- 连接自检
  - 在终端用 `curl` 或 PowerShell `Invoke-WebRequest` 测试目标地址连通，确保访问 Gradle 包与插件仓库可达

## 10. 离线构建步骤
- 预下载并配置 Gradle 包
  - 浏览器下载 `gradle-<version>-all.zip` 到本地路径（如 `C:\gradle\gradle-8.11.1-all.zip`）
  - 在 `gradle-wrapper.properties` 设置 `distributionUrl=file:///C:/gradle/gradle-8.11.1-all.zip`
- 预下载依赖与 SDK
  - 在 Android Studio 的 SDK Manager 预安装所需平台、Build-Tools 与 Platform-Tools
  - 首次在线构建完成后，依赖会缓存到本地，后续可开启 Gradle Offline 模式
- 启用离线模式
  - Android Studio：Gradle → Toggle Offline Mode
  - 命令行：在工程根执行 `./gradlew assembleDebug --offline`
- 常见注意
  - 离线模式下需确保所有依赖已缓存，否则构建可能失败；可在首次在线构建后再切换离线

---
如需我把 `capacitor.config.json` 与后端 CORS 示例片段直接加入仓库并生成一键打包脚本，请告知，我可进一步补充。
