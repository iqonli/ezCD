# ezCD

离线日期计数与 1×1 桌面小组件。应用名为 **ezCD**，Android 包名为 `com.iqonli.ezcd`，项目地址为 https://github.com/iqonli/ezCD 。版本 1.0.17，最低 Android 8.0。

## 这一版

版本 1.0.17（versionCode 18）。为添加后无法再次编辑小组件的桌面提供两种入口：

- 添加到桌面页在桌面布局说明下增加兼容提示及“添加占位小组件到桌面”。占位小组件整块显示 ezCD+，点击进入编辑小组件；支持标准重配置的桌面也可长按后编辑。
- 小组件排版库长按菜单在“复制 JSON”之前增加“添加到桌面”。打开的尺寸页先显示事件概要，再显示静态排版预览，可选择事件；添加时把本次选定的排版副本应用到新小组件，不覆盖旧小组件或事件默认排版。

保留原签名、资源精简、尺寸与权限检测、字体与位图渲染等既有功能。桌面是否允许添加以及是否显示长按编辑仍由系统决定；占位小组件的点按编辑提供额外入口。详见 `docs/PLACEHOLDER_AND_LIBRARY_PIN.md`。

## 计数规则

不再提供正数/倒数模式选择。普通事件显示与所选日期相差的天数；过去显示已过，未来显示还有，当天显示今天。勾选“计入当日”时，在日期差的绝对值上加1。

| 所选日期 | 未勾选 | 勾选 |
| --- | ---: | ---: |
| 昨天 | 1 | 2 |
| 今天 | 0 | 1 |
| 明天 | 1 | 2 |

所选日期当天的状态始终是“今天”，不会因为勾选而改成未来。
按本地日历日期计算，不通过毫秒除以24小时计算日期差。重复事件先解析实际的下一次日期，再应用计数规则。通知仍按实际事件日及提前天数调度，不因“计入当日”而提前或延后。

备份格式schema11，新增前景色/背景色的独立默认状态，兼容读取schema1–10；旧记录保持显式颜色，不根据白色推断默认。旧版本不能读取schema11，请保留旧备份。1.0.7导出使用ezcd-backup v1完整封装，包含schema11事件、原始添加时间、排序方式和排版库；可继续导入旧版schema1–11事件备份。完整新备份不能由1.0.6及更早版本读取。详见`docs/BACKUPS_AND_LIBRARY.md`。

## 已编译的安装包

`ezCD-1.0.17.apk`为通用release包，支持arm64-v8a，可覆盖1.0.6新签名版；不能覆盖1.0.5及更早的旧签名版。验证记录见`docs/VALIDATION.md`，签名说明见`docs/SIGNING.md`。

## 编译：先复用缓存

按用户已有环境配置：**Gradle 9.6.1、AGP 9.2.1、AGP 内置 Kotlin、compileSdk 35、targetSdk 34**。适用于 Android Studio Quail 1（2026.1.1）；Gradle JDK 直接选择 Studio 自带的 JBR 21，无需额外安装 JDK。Java / Kotlin 输出字节码仍为17。

AGP 9.2 要求 Gradle 至少9.4.1、SDK Build Tools 36.0.0、JDK 至少17。compileSdk 35 与 Build Tools 36.0.0 是两个独立组件；已有 Gradle 9.6.1 不等于已缓存 AGP、Kotlin 编译器、标准库和 Build Tools。目标 API 34 不代表可直接满足届时应用商店的新应用上架政策。

应用运行依赖只有 Kotlin 标准库；日期、JSON、UI、文件选择、小组件和提醒均使用 Android 平台 API。JUnit 与 org.json 只用于 JVM 测试，不会进入 APK，也不会在仅 assembleDebug 时解析测试依赖。

**不要为了这个项目先清空缓存、运行 clean，或升级整套 Android Studio。** Gradle、AGP、Kotlin 和 SDK 的版本分别有兼容关系；“已有 Gradle”不代表对应构建插件也已缓存。

Windows 已有 Gradle 时，在项目目录运行：

```bat
build-local.bat
```

它仅调用本机 Gradle，并带 `--offline`；不会调用 Wrapper 下载 Gradle。如果 Gradle 未加入 PATH：

```bat
set "EZCD_GRADLE=T:\Gradle\gradle-9.6.1\bin\gradle.bat"
build-local.bat
```

也可以指向 Android Studio 中配置的 Gradle user home 下 `wrapper\dists` 内已有9.6.1发行版的 `bin\gradle.bat`。上面的 T 盘路径只是示例，请换成你实际已解压的路径。Android Studio 自身通常不附带任意版本的完整 Gradle 发行版。

通过 Android Studio 编译时，保留你现有的 T 盘 Gradle user home 和 JBR 21。Distribution 可继续选 Wrapper；若之前缓存的是9.6.1的 `all` 发行版，或解压在其他位置，可改选 Local installation 并指向该9.6.1目录，避免 Wrapper 再取 `bin` 发行版。不要把 Gradle user home 和 Gradle installation 目录混为一处。

Linux/macOS：

```sh
EZCD_GRADLE=/path/to/gradle ./build-local.sh
```

若 API 35 未安装但已有 API 33 或 34，可以先显式指定现有 SDK：

```bat
build-local.bat -PezcdCompileSdk=34
```

源码使用的最高平台 API 为33，compileSdk 至少33。降低 compileSdk 不会免除 AGP 对 Build Tools 36.0.0 的要求。需要显式指定本版默认值时：

```bat
build-local.bat -PezcdAgp=9.2.1 -PezcdCompileSdk=35
```

项目使用 AGP 9 的内置 Kotlin，不再单独声明 `org.jetbrains.kotlin.android`，也不再提供 `ezcdKotlin` 参数；不要直接换回 AGP 8.x。SDK 路径可通过 Android Studio 生成的 `local.properties` 中 `sdk.dir=...` 指定，或使用已有的 ANDROID_HOME。

只有确认允许下载缺少的包后，才运行：

```bat
gradlew.bat :app:assembleDebug
```

Wrapper 指定 Gradle 9.6.1 的 `bin` 发行版，仅在对应发行版未缓存时下载；Gradle 会获取缺少的构建插件和工具。该命令不是离线命令，也不承诺零下载。

APK：`app/build/outputs/apk/debug/app-debug.apk`。debug APK 自动使用本机调试密钥签名，可安装；release 包需自行配置签名，不能使用上游作者的签名覆盖原应用。ezCD 与 CountAway 包名不同，可以共存。

测试：`gradlew.bat :app:testDebugUnitTest`；可选的无下载源码检查：`python tools/check-source.py`。本次构建工具调整见 `docs/BUILD_TOOLCHAIN.md`。完整验证范围见 `docs/VALIDATION.md`，上游 issue 检查见 `docs/UPSTREAM_ISSUES.md`，小组件实现与排版扩展见 `docs/WIDGETS.md`。

## 许可证

本项目新增及修改代码使用 MIT（`LICENSE`），版权主体为 IQ Online Studio（iqonli）。原 CountAway 部分保留 Apache-2.0（`licenses/Apache-2.0-CountAway.txt`）；`NOTICE`、`UPSTREAM.md` 和应用内“关于”保留归属声明。Google Material 图形遵循 Apache-2.0，归属见 NOTICE；Noto 字形使用 OFL-1.1（`licenses/OFL-NotoSans.txt`）。根目录写 MIT 不会抹去上游代码已有的许可证义务。

同版本资源精简重编译：保留所有尺寸、字重和语言资源，进一步压缩资源表；复现与验证见 [RESOURCE_PACKAGING.md](docs/RESOURCE_PACKAGING.md)。
