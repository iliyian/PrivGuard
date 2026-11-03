# PrivGuard

按应用拦截剪贴板读写、过滤 PackageManager 应用列表的轻量 Xposed 模块，带 Material 3 控制面板。

## 功能

- **剪贴板保护**：拦截目标应用的 `ClipboardManager` 读取与写入，避免后台/前台应用静默读取剪贴板，或往剪贴板塞入口令、广告链接。
- **应用列表保护**：从目标应用的 `PackageManager` 结果中移除指定包名，覆盖：
  - `getInstalledPackages` / `getInstalledApplications`
  - `getPackageInfo` / `getApplicationInfo`
  - `getActivityInfo` / `getServiceInfo` / `getProviderInfo` / `getReceiverInfo`
  - `queryIntentActivities` / `queryIntentServices` / `queryIntentProviders` / `queryIntentReceivers`
  - `resolveActivity` / `resolveService`
  - `getLaunchIntentForPackage` / `getLaunchIntentForPackage`
  - `getPackagesHoldingPermissions`
- **按应用生效**：只注入 Vector 作用域内选中的 App 进程，不注入 `system_server`，不常驻后台服务。
- **控制面板**：状态卡片、分区开关、可搜索的应用选择器、深色/浅色主题适配。
- **配置同步**：通过 Vector 的 RemotePreferences 在控制面板与目标进程之间同步，改完即生效（作用域变更需强制停止目标应用）。

## 环境要求

- Android 8.1+（模块 minSdk 26，targetSdk 36）
- Root：KernelSU / SukiSU / Magisk / APatch
- Zygisk：NeoZygisk 或 Zygisk Next
- Xposed 框架：[Vector](https://github.com/JingMatrix/Vector)（兼容 LSPosed API 101/102）

## 构建

```bash
./gradlew assembleDebug
```

产物：

- 模块/控制面板：`app/build/outputs/apk/debug/app-debug.apk`
- 调试用测试 App：`testapp/build/outputs/apk/debug/testapp-debug.apk`（不需要可删除 `:testapp`）

## 安装与使用

1. 安装 `app-debug.apk`。
2. 在 Vector 管理器中启用 **PrivGuard**。
3. 把需要保护的目标应用加入 Vector 作用域，例如微信、淘宝、支付宝、银行 App。
4. 打开 PrivGuard 控制面板，按需开启剪贴板读写拦截、应用列表过滤，并选择要隐藏的包。
5. 修改作用域后强制停止目标应用再打开。

作用域也可以通过 Vector CLI 管理（需要 root）：

```bash
# 添加
su -c '/data/adb/modules/zygisk_vector/cli scope add com.iliyian.privguard com.tencent.mm/0'

# 查看
su -c '/data/adb/modules/zygisk_vector/cli scope ls com.iliyian.privguard'

# 移除
su -c '/data/adb/modules/zygisk_vector/cli scope rm com.iliyian.privguard com.tencent.mm/0'
```

> 在控制面板内添加作用域会触发 Vector 的「作用域请求」通知，需要在通知栏点「允许」；部分 ROM 的通知操作按钮可能不易点击，此时可改用上面的 CLI。

## 注意事项

- **不要全选隐藏列表**：全选会让目标应用看不到几乎所有包（包括 GMS/WebView 等系统组件），容易导致目标应用异常。建议只隐藏 root/模块相关应用或你明确要隐藏的包。目标应用自身始终可见。
- **不要全选作用域并同时开启剪贴板拦截**：目标应用的复制/粘贴会全部失效。建议只把需要防偷读的 App 加入作用域。
- HyperOS 上控制面板读取应用列表需要「获取应用列表」权限（`com.android.permission.GET_INSTALLED_APPS`），首次打开选择器时按提示授权。
- 模块只 Hook Java 层 `ApplicationPackageManager`，不覆盖 native/IPackageManager 直连检测；定位是轻量方案，不是完整的反检测/沙箱。

## 项目结构

```
app/                         模块 + 控制面板
  src/main/java/.../PrivGuardModule.java    Xposed 入口与 Hook
  src/main/java/.../MainActivity.java       控制面板
  src/main/java/.../AppPickerDialog.java    可搜索应用选择器
  src/main/resources/META-INF/xposed/       Vector/LSPosed 模块元数据
testapp/                     验证 Hook 效果的调试 App
```

## 已知限制

- 不 Hook `system_server`，避免系统级不稳定。
- 应用列表过滤只影响 Java 层 PackageManager 调用。
- 作用域变更需要重启/强制停止目标应用。
- 剪贴板拦截是静默拦截，不区分“用户主动粘贴”和“应用主动读取”；需要按次询问可考虑 ClipboardGuard / Arirang 等方案。

## License

[MIT](LICENSE)
