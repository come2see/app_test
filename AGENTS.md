# Space24 — agent guide

24 点心算游戏，PICO Swan / PICO OS 6，Spatial SDK **6.0.0**。
包名 `tech.illusion.space24`。工程由 `pico-cli project create --template planar` 生成，但容器已在 Phase 1（3D 字模）改造为 Volumetric `WindowContainer` + 单个 `SpatialView` + 9 个 `AttachmentPanel`，不再是单一 planar 窗口——见 `.spatialsdk/design-contract.md` §1。

先读 `PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md`（工作区根）的通用规则，本文件只写 Space24 特有的东西，冲突时**本文件优先**。

## 设计契约是视觉唯一基准

`.spatialsdk/design-contract.md`（当前 v3，2026-09-21；v1 于 2026-08-17 用户确认，v2/v3 各自的变更与确认依据见该文件 §0）**优先于任何计划文档和示例代码**。改任何 UI 前先读它；契约与代码不一致时改代码，不是改契约。视觉稿在 `.spatialsdk/design-ref/space24-mockup.html`。

契约的板面坐标是**窗口相对**，不是相对 HUD：卡片行上沿 96dp → +136 卡高 +24 = 运算符行 256 → +56 +16 = 状态行 328。**`GameBoard` 已不存在**（Phase 1 3D 字模改造后，它被拆成了 `CardFace`/`OperatorFace`/纯辅助函数，不再是持有这套偏移的组合函数）：这套偏移的数值现在由 `content/BoardGeometry.kt` 持有（`CARD_ROW_CENTER_Y_DP`/`OP_ROW_CENTER_Y_DP` 等常量与由它们反推的 `CARD_ROW_TOP_DP`/`CARD_TO_OP_GAP_DP`/`STATUS_LINE_TOP_DP` 等），`content/HomeStage.kt` 的 `BoardChrome` 只读这些常量把 `Spacer`/`padding` 排出对应的竖直占位，不独立写死任何一个偏移数字。同理 `Hud` 自带 `fillMaxWidth().height(64.dp)` 和内部 24dp 横向内边距，外面**不要再加 padding**。

窗口尺寸 900×640dp 的来源只剩 `AndroidManifest.xml` 的 `pico.spatial.windowcontainer.defaultsize` 一处——`Main.kt` 的 `windowConstraints` 已随 Volumetric 容器改造移除，别再假设有两处要同步改。

## 架构：规则全在 game/，UI 只渲染

- `game/` 纯 Kotlin，零 Android/Compose 依赖，**唯一例外**是 `GameSession` 用 `mutableStateOf`（不用它 UI 不重组，JVM 测试却照样过——这是刻意的）。
- `content/` 只读 `GameSession` 的状态、只调它的动作。**不要在 UI 层重新推导任何规则**（合法性、计分、死局判定都在 `game/`）。
- `GameSession` 标了 `@Stable`：它的公开属性全是 `by mutableStateOf` + `private set`，五个普通 `private var` 从不在组合期被读。**新增公开属性必须是 snapshot-backed**，否则这个注解就变成谎言，且 `content/SpatialStage.kt` 里读 `state`（`GameSession` 的快照包装）的每个 `AttachmentPanel` 内容 lambda（`ChromeContent`、逐张 `CardFace`、逐个 `OperatorFace`）都会退回每 100ms（`HomeStage.kt` 的计时 `LaunchedEffect` 周期）重跑一次，而不是只有真正读到那个变化字段的部分重组——`GameBoard` 这个组合函数已不存在，见上方"设计契约是视觉唯一基准"一节。
- 时间是**推导**出来的（`ROUND_MILLIS - (now - start) - penalty`），不是累加的，所以漏 tick 不会漂。`HomeStage` 注入 `SystemClock::elapsedRealtime`（单调、且设备睡眠时继续走）；`GameSession` 的默认参数留着 `System::currentTimeMillis` 只为让 `game/` 不引入 Android。

## 经实证的 SpatialUI 事实（别再查一遍，也别改成别的写法）

这些都是查过 `design-6.0.0-sources.jar` / 反编译 aar 得出的，不是猜的：

- `com.pico.spatial.ui.design.Button` 接受 `onClick`、`modifier`、`enabled: Boolean`、`colors: ButtonColors`、`contentPadding: PaddingValues`、`shape: Shape`。
- `ButtonColors` **只有** `containerColor`/`contentColor`，没有 disabled 变体；禁用态由 `graphicsLayer { alpha = LocalDisableAlpha.current }` 实现（默认 **0.3f**）。所以和 Material3 不同，`enabled = false` 时自定义 colors **仍然生效**，降透明度是白送的。
- `Button` 不传 `style` 时用的是 `titleMedium`。契约要 `bodySmall` 的地方必须显式写，本项目所有 `Button` 调用点都显式写 style。
- 等宽数字：`PicoTheme.typography.<role>.copy(fontFeatureSettings = "tnum")`。`Text` 的 `style` 收真正的 `androidx.compose.ui.text.TextStyle`，`fontFeatureSettings` 能穿过内部 merge 到 Android 的 `TextPaint`。**未确认**：PICO 系统字体是否真带 `tnum` 表——只能真机看数字宽度抖不抖。
- 已核实存在的角色：`titleLarge`/`titleSmall`/`bodySmall`；`fillPrimary`/`fillSecondary`/`fillTertiary`/`fillLight`/`labelPrimary`/`labelPrimaryLight`/`labelSecondary`/`interaction`/`error`/`passable`。`labelLarge`/`labelMedium`/`labelSmall` **未核实**，契约刻意不用。
- 颜色角色是**不透明**的。要遮罩/压暗效果必须自己 `.copy(alpha = ...)`。
- `androidx.compose.foundation` 那个 artifact 被全局 exclude（SpatialUI 自带），但 `.background` / `.clickable` / `.border` / `verticalScroll` / `rememberScrollState` 都能用，已实测编译通过。
- 覆盖层要挡住下层交互必须自己吃指针事件——`background` 不装 pointer node，Compose 命中测试是按位置的，否则射线会穿过遮罩点亮下面的按钮（连触感一起）。**`ResultPanel` 现在用 `BasicSheet`**（真正的 Dialog 窗口，本身自带模态遮罩与指针拦截），不再是手写 scrim + `pointerInput` 消费循环；那个手写模式如今**只**出现在 `content/StartPage.kt` 的 `HowToPlayOverlay` 里，**别当成死代码删掉**。
- `SpatialViewContent` 以委托实现 `SpatialCoordinateSpaceConverter`（SDK 6.0.0 `container/SpatialViewContent.kt`），`initial`/`update` 拿到的 `content` 本身就是像素↔米换算器，不需要手工标定 dp→米比例。**位置换算和尺寸换算是两个不同的调用，别用一个顶另一个**：`content.anchorOf(xDp, yDp, density, zMeters)`（本项目在 `content/SpatialCoords.kt` 里包的 `convertPosition` 封装）换算的是**位置**；字模挂载还需要换算**尺寸**（目标字高、可用最大宽度、字距），走的是 `content.convertSize(...)`（SDK 原生方法，本项目未再包一层），两者签名和坐标空间参数虽然形似，混用会静默算出错误数值，编译期不会报错，见 `content/SpatialStage.kt` 的 `metersOf()` 私有函数。
- `attachments.entity(id)` 本身不会把面板加进渲染树，必须再显式 `content.addEntity(it)`——只拿到 `Entity` 引用不代表它已经在场景里。
- `getVisualBounds` 对 3D 模型（如字模 `.usdc`）有效，能量出真实包围盒；对 2D `AttachmentPanel` 恒返回 `(0,0,0)`，不能用它来测面板尺寸。
- 单个 `SpatialView` 同时挂载的 `AttachmentPanel` 数量：Task 2 探针实测验证到 **9 个**（1 chrome + 4 卡 + 4 运算符）同帧渲染，`attachments attached=9 expected=9`，无一个"第二个面板不支持"的告警；此前工作区记录的验证上限是 2，已被推翻。Task 2 探针在场景实体 `z = +0.015m` 处放的测试盒子**未被容积容器前边界裁剪**，探针里 `card_0` 对应实体在绝对 `z=-0.177m` 处清晰可见——这只确认了这一具体深度值有余量，不代表已知更深处也不会被裁，更深的边界未测。字模实际挂载点是 `depthMeters/2`（见 §2.3 立体层描述），不是这个固定值，但小于它，因此同样在这段已验证的余量内。
- **字模内容走 Blender 无头脚本，不是 Spatial Editor**：`get_editor_capabilities` 返回的工具表只有 `Cube/Sphere/Plane/Cylinder/Cone/Capsule` 等基础几何体、AI 模型生成、资产导入、材质、打包——零文字/字形/挤出能力，构成 CLAUDE.md「Spatial Editor 激活」例外 2（运行时能力检查证明无法满足）。14 个字模资产由 `tools/glyphs/make_glyphs.py`（`Blender --background --python tools/glyphs/make_glyphs.py -- build`）授作，已提交、可重复执行，不要为字模相关工作再去调 `spatial-editor` 技能。
- **字模字体：全部 14 个 SF Mono，Blender 5.2.2 LTS 为当前基线，不要改回混字体**。`make_glyphs.py` 里残留的 `_menlo_scaled`/`MENLO_PATH`/`SF_MONO_REFERENCE_CAP_HEIGHT` 是刻意保留的历史脚手架，不是死代码要清理，也不代表资产里有字体混用；详情见 `docs/superpowers/notes/2026-09-21-glyph-authoring.md`。
- **曲线转网格后必须焊接重合顶点**：`_build_glyph_mesh` 在 `bpy.ops.object.convert(target='MESH')` 之后跑 `bpy.ops.mesh.remove_doubles(threshold=0.0001)`，修的是倒角轮廓在拐角处留下的坐标重合但未焊接的顶点（`digit_1` 竖笔与底座衬线交界处的豁口是典型例子）。这条已验证对全部 14 个字符生效（边界边数量普遍大幅下降，如 `digit_8` 584→0），**别当成多余步骤删掉**。已知残留：`digit_0` 焊接后在斜杠与外圈交界处仍有 4 组更小的豁口（间距 0.012–0.02，超出焊接阈值两个数量级），已用背光测试确认是真实缝隙但常规光照下不可见，裁定为非阻塞、暂不修（不盲目调大全局阈值，避免误焊 `6`/`8`/`9` 镂空边缘），细节见上述 notes 文档。
- **Blender 5.2.2 的 `usd_export` 必须显式传 `convert_world_material=False`**：默认值 `True` 会把场景默认 World 背景色烘焙成一张跟材质无关的 `textures/*.exr` side-file，随每个 `.usdc` 一起导出。这是 Blender 5.2.2 版本本身的导出器行为，不是本项目材质配置的问题；改脚本时如果这行参数被删掉，字模资产会重新带上一堆用不上的旁生纹理文件。

## 构建与验证

每条命令自己 export，环境变量不跨调用持续：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest   # 84 个测试 / 13 个套件，必须全绿
./gradlew assembleDebug
grep -rn "androidx.compose.material\|MaterialTheme" app/src   # 必须为空
```

`Log.d` 在本机 `adb logcat` 里不显示，临时打点用 `Log.e`/`Log.w`。

设备是多会话共享的。装机/截图前先抢锁，完事立刻 `app stop` + `release`：

```bash
LOCK=/Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh
$LOCK acquire Space24-<用途> 120 emulator-5554   # owner 必须是固定字面量，别用 $$
```

用绝对路径，不要用 `../.claude/skills/...`：这个相对路径只在 cwd 是 Space24 仓库根时才解析得对；在 `.worktrees/<name>/` 里跑（本项目日常工作区）时会解析成不存在的 `.worktrees/.claude/...`，脚本找不到但不一定会明显报错成"路径问题"。

`acquire` 返回 1 是等待超时：报告并跳过，**不要死等也不要重试**。

模拟器指针注入打不准空间窗口按钮，答题页/结算页取证改走 debug 启动钩子：

```bash
adb shell am start -n tech.illusion.space24/.platform.LaunchActivity -e debug_start playing
```

固定种子直接进 `PLAYING`，牌面每次一致，跳过点「开始游戏」这一步不可靠的点击。守卫用的是 `BuildConfig.DEBUG`（`content/DebugLaunch.kt` 的 `rememberDebugStartSeed()`），**不是** `Build.TYPE != "user"`——PICO 模拟器的 `Build.TYPE` 本来就是 `"user"`，用它当守卫这个钩子在模拟器上永远不会触发，release 包里 `BuildConfig.DEBUG` 是编译期常量会被整段折叠掉。

## 已知的验证边界（别假装通过）

- 模拟器的注入点击**打不准**空间窗口按钮。门禁 B 第 1 轮只核到了开始页；答题页、结算页、以及所有依赖交互的视觉（卡片选中/提示高亮/运算符三态/四种反馈文案/新纪录徽标）全部标记为**不可判定 — 待真机验证**，记录在 `.spatialsdk/ui-verification-log.md`。
- 共享模拟器会串画面：启动后第一张截图可能拍到别的会话的窗口。**多拍一帧再信**。
- `adb logcat -d` 事后补拍是不可靠的：ring buffer 在高负载下会把几秒前才打的行挤出去，表现得和"应用压根没打日志"一模一样。对启动时刻或任何时间敏感的日志证据，要在触发事件**之前**先起后台采集：`adb logcat -c && adb logcat -s <TAG>:W "*:S" > out.log & CAP=$!`，触发事件、等够时间后再 `kill $CAP`，不要靠事后 `-d` 一次性 dump。
- 题库 `PuzzleBank.build()` JVM 上 122ms，真机启动后约 9 秒内就绪（开始页按钮直接显示"开始游戏"而不是"题库准备中"）。
- **Phase 1（立体字模）设备验证现状**：模拟器截图确认了 4 张卡面 + 4 个运算符共 8 个字模轮廓互不相同、位置数量正确，且每块有与方向光一致的明暗分界线（判定为"确认：3D 几何已存在且位置/数量正确"）；具体数字笔画细节与侧面高光棱线的清晰度**不可判定 — 待真机验证**，原因是模拟器截图相机固定且窄视角，字模在画面里每字约只有 95×55px，不是代码缺陷（既有限制 `emulator-screenshot-camera-is-fixed-and-narrow` 在此再次适用）。RESULT 态字模不穿透结算浮层**已在设备上确认干净**（零穿透）。详见 `.spatialsdk/ui-verification-log.md` 第 2 轮。

## 刻意延后，不是忘了

- **暂停/恢复**：头显休眠或切走应用而窗口未销毁时，回合仍会在后台走完并写入分数。spec 和契约都没要求暂停，故延后。
- 提示的"轻微发光"效果：没找到可核实的 API，契约这条未实现。
- 契约 §3 写禁用态 ~50% 透明，平台默认是 30%；采用平台默认。
- **Phase 2–4（选中弹出反馈、合并动画、答对爆发效果）**：已在设计对话中确认是产品意图的一部分，Phase 1 只做字模的静态挂载与降级开关，不含任何动画。不要把"没有动画"当成 Phase 1 的实现缺陷去补，见 `.spatialsdk/design-contract.md` §2.3。
- **牌面替换时的旧字模实例未销毁**：`SpatialStage.kt` 的 `mountGlyphs` 换批时只把旧实例 `entity.enabled = false`，从不 `removeChild`/`destroy`，真实对局里每次牌数/牌面变化都会在面板下累积一批永久存在的禁用态实体，不止发生一次。没当场修的原因：正确的清理形态取决于还没设计出来的 Phase 3 合并动画（大概率需要旧字模先播完动画再销毁，而不是重新布局时立即销毁），现在修很可能等 Phase 3 落地后又要重做一遍。细节见 `content/SpatialStage.kt` 中 `MountedGlyphs` 的文档注释与 `.spatialsdk/ui-verification-log.md` 第 2 轮。

## 玩法规则要点（改逻辑前先看清）

整数约束：除法只在整除时合法（余数判定，**全程不用浮点**），中间值允许负数。非法除法**不产生结果牌**，且**保留玩家已选的牌和运算符**。

边界规则的**顺序**本身就是需求：①任何操作先查剩余时间 >0，否则不执行并结算；②扣秒操作若扣完 ≤0，则罚时照扣、立即结算、**效果不生效**；③答对加分前再查一次时间；④时间归零则当前题记为未答出。

提示每题一次，"重置本题"不返还、换新题返还；死局（当前牌面已无解）**不扣秒不消耗次数**，只提示。
