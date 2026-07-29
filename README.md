# 自动点击器（安卓手机端，独立运行）

一个**不需要 root、不需要连电脑**的安卓自动点击器。用系统「无障碍服务（AccessibilityService）」的 `dispatchGesture` 在屏幕任意坐标模拟点击/滑动，录制一次，之后可重复自动运行，支持循环。

> 注：本项目在 Windows 上编写，本机没有 Android SDK，无法在此编译。请用 **Android Studio** 打开本目录编译并安装到手机（Android Studio 自带 JDK17 和 Gradle，按要求点几下即可）。

---

## 一、在电脑上编译并装到手机

1. 电脑装 **Android Studio**（https://developer.android.com/studio ）。
2. 手机开启「开发者选项 → USB 调试」（关于手机里连点版本号 7 次开启开发者选项）。
3. 数据线连电脑，手机选「传输文件 / 充电仅此设备 + 允许 USB 调试」。
4. Android Studio 选 **File → Open**，打开本目录 `安卓点击器`（含 settings.gradle 的文件夹）。
5. 等待 Gradle 同步完成（首次会自动下载 Gradle 8.0、AGP、Kotlin，需联网）。
6. 点工具栏 «Run ▶»（或 Shift+F10），选你的手机，自动编译安装并启动。
   - 若想生成独立 APK 文件：菜单 **Build → Build Bundle(s)/APK(s) → Build APK(s)**，产物在 `app/build/outputs/apk/debug/app-debug.apk`，拷到手机安装即可。

> 第一次同步若报 JDK 版本问题：Android Studio 自带 JDK17，无需另外装。编译目标 compileSdk 33 / minSdk 26（Android 8.0+）。

---

## 二、手机上首次配置（只需一次）

打开 App 后按顺序点三步：

1. **「1. 授予悬浮窗权限」** → 跳到系统设置，给本 App 打开「显示在其他应用上层 / 悬浮窗」。
2. **「2. 开启无障碍服务」** → 跳到无障碍设置，找到「创城自动点击器」，开启它（系统会警告，点允许；这是点击器能模拟点击的唯一途径）。
3. **「3. 打开悬浮控制器」** → 手机屏幕上出现一个可拖动的悬浮面板（顶部标题「≡ 拖动」）。

---

## 三、录制 + 自动运行

1. 点悬浮面板的 **「录制」**。屏幕会蒙一层极淡的遮罩（表示正在记录）。
2. **在屏幕你想点的位置轻点** —— 每点一下就记录一个点击坐标；**按住滑动**就记录一次滑动。
   - 两次操作之间的间隔也会被记录，回放时会按原节奏停顿。
3. 录完后点 **「停录」**。遮罩消失，触摸恢复正常（此时底层 App 能正常操作）。
4. **切到你真正要自动操作的目标 App**（比如某个要连点的页面）。
5. 点 **「播放」** —— 手机开始按刚才记录的坐标/节奏自动点击。
6. 想停就点 **「停播」**；点 **「清除」** 清空当前记录。
7. 勾选 **「循环」** 可让整套动作反复执行（适合挂机类场景）。

### 保存 / 载入脚本
- 主界面输入脚本名 → 点「保存当前」：把当前录制的动作存成 JSON 文件（在 App 私有目录 `Android/data/com.creation.city.clicker/files/scripts/`）。
- 列表里**点击**某脚本名 → 载入到控制器；**长按** → 删除。
- 这样不同场景可以存多套，随时切换。

---

## 四、实现原理（给李工看门道）

| 组件 | 作用 |
|---|---|
| `AutoClickerAccessibilityService` | 无障碍服务。开启后系统允许它在任意坐标 `dispatchGesture` 模拟点击/滑动，无需 root。 |
| `FloatingControlService` | 前台服务，显示一个可拖动悬浮面板 + 一个全屏透明捕获层。录制时让捕获层「可触摸」来抓坐标；空闲时设为 `NOT_TOUCHABLE` 让触摸穿透到目标 App。播放时调用无障碍服务的 `performTap/performSwipe`。 |
| `MainActivity` | 引导授权（悬浮窗 + 无障碍），脚本的保存/载入/删除列表。 |
| `ScriptStore` | 用 `org.json` 把动作序列存成 JSON，零额外依赖。 |

录制坐标用的是 `MotionEvent.rawX/rawY`（屏幕绝对像素），回放 `dispatchGesture` 的坐标系也是屏幕像素，二者一致，所以录在哪、放哪能精准命中同一控件（前提是屏幕方向/布局不变）。

---

## 五、已知限制与提醒

- **录制时底层 App 不会被执行**（捕获层在录屏模式下截获了触摸），这是非 root 方案的标准行为；录完停录、切到目标 App 再播放即可。
- 换屏幕方向（横/竖）或分辨率变化后，旧坐标会错位，需重新录制。
- 部分金融/支付类 App 会检测无障碍服务并拒绝运行，属正常防护，别用在违规场景。
- 本工具仅用于你自己的设备/合规的内部操作自动化。

---

## 六、用 GitHub Actions 云端编译（免装 Android Studio）

不想在电脑上装 Android Studio，可以推到 GitHub 让它帮你编译、出 APK，免费（公开仓库无限时长，私有仓库免费额度也够用）。

1. 注册 GitHub 账号，新建一个仓库（名字用英文，如 `android-clicker`）。
2. 把本工程目录 `安卓点击器` 里的**全部内容**（含隐藏的 `.github` 文件夹、`.gradle` 文件）推到仓库根目录：
   ```
   cd 安卓点击器
   git init
   git add .
   git commit -m "init"
   git branch -M main
   git remote add origin https://github.com/你的用户名/android-clicker.git
   git push -u origin main
   ```
   > 注意：`settings.gradle`、`build.gradle`、`app/` 必须在仓库根目录，`settings.gradle` 里 `include ':app'` 才能找到模块。
3. 进仓库 **Actions** 标签页，会看到 `Build Debug APK` 工作流。首次需在 Actions 页面点一下「Enable workflows」授权。
4. 点 **Run workflow** 手动触发（或推送代码自动触发）。等几分钟，状态变绿即成功。
5. 进该次运行记录，最下方 **Artifacts → app-debug** 下载 `app-debug.apk`，拷到手机安装即可。

**原理**：工作流（`.github/workflows/build.yml`）自动装 JDK17 + Android SDK（`android-33`、`build-tools;33.0.2`），用 Gradle 8.0 跑 `assembleDebug`。工程里没有 `gradle-wrapper.jar`（二进制无法手写），所以工作流用 `gradle-version: 8.0` 直接指定 Gradle 版本，无需 wrapper。

> 编译若报 `ANDROID_HOME` 找不到：确认 `android-actions/setup-android` 步骤已跑且成功（它会自动设置 `ANDROID_HOME` 并接受许可证）。

