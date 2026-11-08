# 设计契约 — Space24

## 0. 元信息

- 应用名 / applicationId：Space24 / `tech.illusion.space24`
- 设计源类型：文字 PRD（Claude 生成视觉稿）
- 设计源位置：`design-ref/space24-mockup.html`；Artifact https://claude.ai/code/artifact/b08571e6-eac4-4fe1-a082-165be1c8fe86
- 契约版本：v3
- 用户确认时间：**2026-08-17，用户明确确认**（"按照这版走"，针对 v1 视觉稿 https://claude.ai/code/artifact/b08571e6-eac4-4fe1-a082-165be1c8fe86 ）
- v2 变更时间：2026-08-26；v2 变更内容：开始页新增 `startHowToPlayButton` 入口按钮与 `howto_play_overlay`（`startHowToPlayScrim` + `startHowToPlayCard`）说明弹层；用户确认时间：2026-08-26，通过门禁 A 确认 —— 本次实现任务由编排方以"已经通过门禁 A 用户确认的设计"下达，并随任务附带了一份门禁 A 确认过的按钮视觉规格终稿（容器色复用 startBeginButton 同一角色但降 alpha、内容色同角色保持满不透明度、不描边、问号圆形徽标、SDK 预设尺寸档位、胶囊圆角），该终稿在容器颜色角色上与本文件下方 §2.1 新增行最初的草案（`fillLight`/`secondaryButtonColors()`）不同，实现以门禁 A 终稿（`surfaceAccent()`/`interaction` 角色）为准，此处如实记录两者差异，不代表本文件曾自相矛盾。
- v3 变更时间：2026-09-21；v3 变更内容：卡面数值与运算符符号从 2D 文字改为立体挤出字模（"Space24 3D Glyphs Phase 1"）。容器模型由单一 planar `WindowContainer` 改为 Volumetric `WindowContainer` + `SpatialView` + 9 个 `AttachmentPanel`；§2.3 的"按字符数分档字号"规则废弃，改为按字高归一化的字模排布规则；新增字模立体层描述与资产清单。用户确认依据：2026-09-20 的设计确认对话（"Space24 3D Glyphs Phase 1"设计稿），实现过程中的两项实现期决策（详见下方 §1/§2.3）——容器类型由运行时探针（Task 2）验证选定，字体在实现中途因 Blender 宿主版本变化被展示证据后由用户拍板改回 SF Mono——均已征得用户确认。完整过程记录在 `docs/superpowers/notes/2026-09-21-glyph-authoring.md`（已提交，长期可用）；实现过程中产生的任务级报告是编排方的 SDD 过程 scratch 工作区（`.superpowers/`，未跟踪 git，随本计划工作合并后即被清理），本契约与 `AGENTS.md` 已把其中对本文档有效的具体事实摘录内联在各自对应段落，不再引用该目录下的具体文件路径。v1/v2 的历史记录（上方两条）保留，不删除。

## 1. 面板清单

| 面板 id | 容器类型 | 面板尺寸 | 层级/父面板 | 出现时机 |
|---|---|---|---|---|
| chrome | AttachmentPanel（`SpatialView` 子面板） | 900×640dp（固定，不随牌数/状态变化） | 根 | 启动即显示 |
| card_0..card_3 | AttachmentPanel | 104×136dp（固定） | 挂在 `SpatialView` 场景实体下，非 chrome 子节点 | `PLAYING` 态按 `cardCount` 显示 0–4 个 |
| op_0..op_3 | AttachmentPanel | 56×56dp（固定） | 同上 | `PLAYING` 态常驻 4 个 |

**（v3）容器模型由 v1/v2 的单一 planar `WindowContainer` 改为 Volumetric `WindowContainer`（`AndroidManifest.xml` 的 `pico.spatial.windowcontainer.style="2"`）+ 单个 `SpatialView` + 9 个 `AttachmentPanel`**（1 个 `chrome` + 4 张卡 + 4 个运算符）。原因：卡面/运算符数值改为立体挤出字模后，字模需要挂在有深度的场景实体下（字模中心挂在面板表面之上半个自身厚度处，使背面与面板齐平、正面凸出字模自身厚度），planar 容器没有可用深度。`chrome` 面板承载 v1/v2 描述的全部 2D 内容（`start_page`/`hud`+`status_line`/`result_panel`，仍在同一窗口内按游戏状态切换，逻辑不变，见下方 §2.1–§2.5）；`card_0..3`/`op_0..3` 是新增的、只承载牌面数字/运算符 2D 降级文字与挂载字模的独立面板。`resultChangeDifficultyButton` 把状态切回 `NOT_STARTED`；`resultRestartButton` 沿用当前难度直接开新局，均不受本次容器改动影响。

单个 `SpatialView` 同时挂载 9 个 `AttachmentPanel` 已由 Task 2 的运行时探针验证可行（`attachments attached=9 expected=9`，此前工作区记录的验证上限是 2，本次探针一次性验证到 9，未见任何"第二个面板不支持"的既有告警）；`z = +0.015m` 处的实体经探针确认**未被容积容器前边界裁剪**（`card_0` 的探针盒子在绝对 `z=-0.177m` 处清晰可见）；更深处的裁剪边界未测，仅确认这一具体深度值有余量，不代表容器深度没有上限。容器类型是架构决策，不得为了让代码编译通过而更改——这句话现在保护的是本节描述的 Volumetric + 9 面板选型，不是 v1/v2 的单一 planar 窗口。

## 2. 元素表

颜色角色全部对应本工作区已核实存在的 `PicoTheme` 语义角色：`fillPrimary` / `fillSecondary` / `fillTertiary` / `fillLight` / `labelPrimary` / `labelPrimaryLight` / `labelSecondary` / `interaction` / `error` / `passable`。字体角色全部对应已在同工作区编译代码中核实存在的：`titleLarge` / `titleSmall` / `bodySmall`。本契约不使用 `labelLarge`/`labelMedium`/`labelSmall`（未核实），全部按钮/标签文案改用 `bodySmall` 或 `titleSmall` 承载。数字类文本（HUD 计时器、得分、卡牌数值）统一走等宽/表格数字（`tabular figures`），保证纵向对齐，具体实现方式在 SpatialUI 中的等宽字重 API 于实现阶段用 `spatial-ui-ability` 核实。

### 2.1 start_page

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| appTitleText | Text | start_page | 上中 | 0,64 | wrap×wrap | - | 下 20 | labelPrimary | titleLarge | - |
| startDifficultySegment | 分段控件（3 项） | start_page | 居中 | 0,-64 | wrap×wrap | 4（外层）/ 8,20（每项） | 下 24 | fillSecondary（外层背景）/ 选中项 fillPrimary（背景）+labelPrimaryLight（文字）/ 未选中项 transparent+labelSecondary（文字） | bodySmall | 999（外层+每项均为胶囊） |
| startBestScoreLabel | Text | start_page | 居中 | 0,-8 | wrap×wrap | - | 下 6 | labelSecondary | bodySmall | - |
| startBestScoreValue | Text | start_page | 居中 | 0,20 | wrap×wrap | - | 下 32 | labelPrimary | titleLarge | - |
| startBeginButton | Button | start_page | 居中 | 0,80 | wrap×wrap | 14,32 | - | fillPrimary（背景）/labelPrimaryLight（文字） | titleSmall | 999（胶囊） |
| startHowToPlayButton | Button（次要，leadingIcon 圆形徽标"?" + 文字"玩法"） | start_page | 右上 | -24,24 | wrap×wrap | 由 `ButtonDefaults.Small` 尺寸档位派生（未单独指定） | - | 容器：`interaction`（即 `surfaceAccent()`，与 startBeginButton 启用态同一角色）@alpha≈0.3，无描边；内容（图标+文字）：同角色，接近满不透明度；徽标底色：同角色@alpha≈0.68；徽标文字：`onAccent()`（`fillPrimary`，本项目"填色圆点上文字"的既定角色） | bodySmall | 999（胶囊，随 `ButtonDefaults.Small` 预设自带，未单独指定 shape） |

`appTitleText` 是本契约新增的装饰性元素（产品设计文档未强制要求应用标题），仅用于开始页品牌识别，不影响任何玩法元素。

`startHowToPlayButton` 是本增量（v2）新增的常驻入口元素，点击后切换一个纯 Compose UI 层面的布尔状态 `showHowToPlay`（不是 `GamePhase`，不写入 `GameSession`），显示/隐藏下面 §2.5 的浮层；点击它本身不调用 `onStart`，不读写 `bankReady`。它固定悬浮在 start_page 右上角（`Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp)`），视觉上是独立角标，不挤入现有那条居中竖排列——实现上是给整个 start_page 套一层外层 `Box`，原有的居中 `Column`（appTitleText…startBeginButton）整体不动，此按钮与浮层是该 `Box` 的另外两个直接子项，按 z 序画在 `Column` 之上。图标徽标走 `Button` 的 `leadingIcon` 插槽（`com.pico.spatial.ui.design.Button` 的公开参数，"a leading icon before the content"，接受任意 Composable，不限于 `Icon`）实现，不是把问号字符塞进同一个 `Text`，也不是在 `content` 里手搓 `Row`——`leadingIcon`/`content` 已经由 SDK 内部放进同一个 `Row`（`RowScope`）并按 `size` 预设自动给出图标间距（`gap`），这是比增量草案设想的"同一个 Text"或"手搓 Row-in-Button"更贴合 SDK 设计意图、且同样不需要任何本项目未验证过的新模式的实现方式。

### 2.2 hud（`PLAYING` 与 `RESULT` 状态常驻；`RESULT` 态整体降低不透明度、只读）

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| hudRemainingLabel | Text | hud | 左上 | 24,14 | wrap×wrap | - | 下 2 | labelSecondary | bodySmall | - |
| hudRemainingValue | Text | hud | 左上 | 24,32 | wrap×wrap | - | - | labelPrimary | titleSmall（等宽数字，`M:SS` 格式） | - |
| hudDifficultyPill | Text（药丸） | hud | 上中 | 0,20 | wrap×wrap | 5,16 | - | fillSecondary（背景）/labelPrimary（文字） | bodySmall | 999 |
| hudScoreLabel | Text | hud | 右上 | -24,14 | wrap×wrap | - | 下 2 | labelSecondary | bodySmall | - |
| hudScoreValue | Text | hud | 右上 | -24,32 | wrap×wrap | - | - | labelPrimary | titleSmall（等宽数字） | - |

`hud` 面板本身：高度 64dp、宽度铺满窗口、锚在 `main` 顶部，下边框用 1dp 极浅描边（视觉细节，实现阶段可用 `fillSecondary` 的低不透明度描边或 SpatialUI 分隔线组件表达，不强制指定具体 API）。

### 2.3 board（`PLAYING` 状态）

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| cardTile（逐张，1–4 张，横排居中，牌间距 16dp） | Box+Text | board | 上中（相对 hud 下方） | 0,96 | **104×136dp（固定）** | - | - | 默认 fillSecondary（背景）/labelPrimary（文字） | 按字符数分档，见下 | 14 |
| operatorButton（逐个，4 个：`+ − × ÷`，横排居中，间距 16dp） | Button | board | 上中 | 0,256（卡片行下方 24dp） | 56×56dp（固定） | - | - | 默认 fillSecondary（背景）/labelPrimary（文字）；禁用 fillTertiary（背景）/labelSecondary（文字）；选中 fillPrimary（背景）/labelPrimaryLight（文字） | titleSmall | 16 |
| statusLineText | Text | board | 上中 | 0,328（运算符行下方 16dp） | fill×wrap（单行，最小高度 22dp 占位，无消息时留空不塌陷布局） | - | - | 默认 labelSecondary；否定反馈/无解 error；肯定反馈 passable | titleSmall | - |
| actionUndoButton / actionResetButton / actionHintButton / actionSkipButton（横排居中，间距 14dp） | Button | board | 下中（相对窗口底部） | 0,-32 | wrap×wrap | 10,20 | - | fillSecondary（背景）/labelPrimary（文字） | bodySmall | 999 |

**（v3）卡面数值与运算符符号改为立体挤出字模，废弃 v1/v2 的按字符数分档字号规则。** 牌面字符数范围仍是 1–6（理论极值 `−28561` 为 6 字符：`−`、`2`、`8`、`5`、`6`、`1`）；困难档 `11`/`12`/`13` 仍一律显示为阿拉伯数字，不出现 `J`/`Q`/`K`、不出现花色图案；单行显示，禁止换行。**废弃的规则**（不再适用，保留于此仅为对照 v1/v2 历史）：按字符数 ≤2/=3/≥4 分别用 `titleLarge`/`titleSmall`/`bodySmall` 三档字体角色。**替代规则**：字模按统一字高（`capHeight`，取全部字模包围盒高度的最大值）归一化缩放，各字按缩放后的原始宽度横向排列（不是等宽字符格），整串宽度（含字距）超过卡面/按钮的可用宽度时，在字高缩放的基础上再整体等比缩小，保证不裁切、不换行（实现见 `content/GlyphCatalog.kt` 的 `layout()`）。

**立体层描述（v3 新增，精度修正后更新）**：字模授作 pivot 在几何中心（含 Z 轴），面板表面本身在 `z = 0`。挂载时把字模中心放在其自身缩放后厚度的一半处（`depthMeters / 2`，`depthMeters = 字模原始厚度 × 缩放`），使**背面**精确落在 `z = 0`、与卡面/运算符按钮面（同在 `z = 0`）齐平，**正面**落在 `z = depthMeters` 处凸出。凸出量因此**不是**一个单独选定的常量，而是每个字模自己的实际厚度换算成米之后的值——对典型卡面数字（1–2 字符）约为 **1.3cm**，接近规格 §4.4"约 1.5cm"的原始预期；运算符按同一公式算出约 **0.7cm**，长牌面（3 字符以上触发整串等比缩小）会更薄——具体数值由资产几何与当前缩放决定，不是可调常量。**做凹刻效果不能简单取该值的负数**：pivot-在中心 + 挂载点固定在 `z = 0` 的这套摆放公式只保证背面贴合表面，取负会把整个字模推到不透明面板背后被整体遮住，而不是刻进面板表面；凹刻需要一套不同的摆放公式，本阶段未设计，留待需要时再定。字模是场景实体的子实体，挂在对应 `card_i`/`op_i` `AttachmentPanel` 之下，局部坐标以面板中心为原点。

**字模资产清单（14 个，全部 SF Mono，均为 `app/src/main/assets/glyphs/*.usdc`）**：`digit_0`..`digit_9`（数字 0–9）、`op_plus`（`+`）、`op_minus`（`−`，同时复用于 ASCII 连字符 `-`）、`op_times`（`×`）、`op_divide`（`÷`）。字模由 `tools/glyphs/make_glyphs.py`（Blender 5.2.2 LTS 无头脚本，可重复执行）授作，**不是** Spatial Editor 手工创建的内容——Spatial Editor 的运行时能力检查（`get_editor_capabilities`）返回的工具表只有 `Cube`/`Sphere`/`Plane`/`Cylinder`/`Cone`/`Capsule` 等基础几何体、AI 模型生成、资产导入、材质、打包，零文字/字形/挤出能力，构成 CLAUDE.md「Spatial Editor 激活」一节的例外 2（运行时能力检查证明无法满足）。授作参数、测量值与已知的 `digit_0` 残留几何限制记录在 `docs/superpowers/notes/2026-09-21-glyph-authoring.md`，不在本契约重复。

**选中态仍由 2D 表达（v3 明确）**：`cardTile` selected/hinted、`operatorButton` 禁用/可用/选中三态，仍按 §3 的规则用 2D 面板背景色与描边表达（`fillPrimary`/`fillSecondary`/`fillTertiary` 等角色切换、`interaction` 色描边），**不通过改变字模颜色或材质来表达选中/禁用状态**——字模颜色统一固定（`labelPrimary` 对应色值），不随游戏状态变化。

**Phase 2–4（已确认设计，尚未实现，不是缺陷）**：卡片选中后的弹出反馈、两张牌合并成结果牌的动画、以及答对 24 点时的爆发效果，均已在设计对话中确认为产品意图的一部分，但本轮（Phase 1）范围只做立体字模的静态挂载与降级开关，不包含任何动画。下一个读到这份契约的会话**不应**把"卡片/运算符没有动画"当成本轮实现遗漏的缺陷去补——这是刻意排期到后续 Phase 的范围外事项，具体动画形态（尤其是牌面替换时旧字模的销毁时机）仍待设计，见 `content/SpatialStage.kt` 中 `MountedGlyphs` 的文档注释与本文件 §5 "不做清单"。

### 2.4 result_panel（`RESULT` 状态，浮层叠加在 board 之上）

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| resultOverlayScrim | Box | main | 填满 | 0,0 | 900×640（fill） | - | - | fillTertiary（半透明遮罩，实现时按需叠加不透明度） | - | - |
| resultPanelCard | Box | main | 居中 | 0,0 | 560×420dp（最小高度，内容多时可增高，宽度固定） | 32 | - | fillSecondary（背景，玻璃质感） | - | 20 |
| resultTitleText（"时间到"） | Text | resultPanelCard | 上中 | 0,0 | wrap×wrap | - | 下 16 | labelPrimary | titleLarge | - |
| resultRecordBadge（"新纪录"） | Text（药丸，描边） | resultPanelCard | 上中 | 0,44 | wrap×wrap | 4,16 | 下 8 | passable（描边+文字） | bodySmall | 999 |
| resultScoreLabel / resultScoreValue（"本局得分"） | Text×2 | resultPanelCard | - | - | fill×wrap | - | 下 8 | labelSecondary（标签）/labelPrimary（数值） | bodySmall（标签）/titleLarge（数值，等宽数字） | - |
| resultBestLabel / resultBestValue（"历史最高（{难度}）"） | Text×2 | resultPanelCard | - | - | fill×wrap | - | 下 16 | labelSecondary（标签）/labelPrimary（数值） | bodySmall（标签）/titleLarge（数值，等宽数字） | - |
| resultMissedList | 可滚动列表 | resultPanelCard | - | - | fill×130dp（固定可视高度，超出滚动） | 4 | 下 20 | fillTertiary（列表容器背景） | - | 12 |
| resultMissedListItem（逐行） | Text | resultMissedList | 左 | - | fill×wrap | 10,12 | - | labelPrimary | bodySmall | - |
| resultChangeDifficultyButton（"更换难度"） | Button（次要/ghost） | resultPanelCard | 下，靠左（与 resultRestartButton 并排） | 0,-8（相对面板底部） | wrap×wrap | 10,22 | - | fillTertiary（背景）/labelPrimary（文字） | bodySmall | 999 |
| resultRestartButton（"再来一局"） | Button | resultPanelCard | 下，靠右 | 0,-8 | wrap×wrap | 10,22 | - | fillPrimary（背景）/labelPrimaryLight（文字） | bodySmall | 999 |

`resultRecordBadge` 的显隐条件是**逻辑条件**，不是固定出现：仅当「本局得分 > 更新前该难度历史最高分」时显示；否则不渲染该元素（不是隐藏占位，是不存在）。视觉稿中该徽标与示例数值（本局得分 9 / 历史最高 9）同框出现——应用在结算面板读取历史最高之前已把新纪录写入，所以 pill 出现时本局得分必然等于刷新后的历史最高，视觉稿据此展示的是 pill 真实会渲染出的那个状态，不是与之矛盾的示意组合。

`resultMissedListItem` 文案格式（逐条，`，`为全角逗号）：

```
{牌1} {牌2} {牌3} {牌4} → {牌a} {运算符} {牌b} = {结果1}，{结果1} {运算符} {牌c} = {结果2}，{结果2} {运算符} {牌d} = 24
```

例：`1 1 1 8 → 1 + 1 = 2，2 + 1 = 3，3 × 8 = 24`

### 2.5 howto_play_overlay（v2 新增；点击 startHowToPlayButton 后叠加在 start_page 之上的浮层）

浮层只在 start_page 可见期间才可能出现（`PLAYING`/`RESULT` 阶段 start_page 本身不渲染，浮层也无从触发）。父容器记为 start_page（与 §2.4 resultOverlayScrim 记 `main` 为父容器同一种记法），尺寸以 900×640 为基准。

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| startHowToPlayScrim | Box | start_page | 填满 | 0,0 | 900×640（fill） | - | - | fillTertiary @alpha≈0.72（半透明遮罩） | - | - |
| startHowToPlayCard | Column | start_page | 居中 | 0,0 | 480dp（固定宽）×wrap，上限 480dp（内容纵向滚动） | 28 | - | `surfacePanel()`（不透明琥珀色面板，与 resultPanelCard 同一实际实现） | - | 20 |
| startHowToPlayTitleText（"玩法说明"） | Text | startHowToPlayCard | 左（标题行内，与关闭按钮同行） | - | fill×wrap | - | 下 16（整个标题行） | onPanelPrimary() | titleSmall | - |
| startHowToPlayCloseButton（"×"） | Button（次要） | startHowToPlayCard | 右（标题行内，与标题同行右端） | - | wrap×wrap | 6,14 | - | fillLight（背景，`secondaryButtonColors()`）/labelPrimary（文字） | bodySmall | 999 |
| startHowToPlayIntroHeading（"简介"） | Text | startHowToPlayCard（滚动区） | 左 | - | fill×wrap | - | 下 6 | onPanelPrimary() | titleSmall | - |
| startHowToPlayIntroBody | Text | startHowToPlayCard（滚动区） | 左 | - | fill×wrap | - | 下 16 | onPanelSecondary() | bodySmall | - |
| startHowToPlayControlsHeading（"操作"） | Text | startHowToPlayCard（滚动区） | 左 | - | fill×wrap | - | 下 6 | onPanelPrimary() | titleSmall | - |
| startHowToPlayControlsItem（2 条，纵向排列，条间距 6dp） | Text | startHowToPlayCard（滚动区） | 左 | - | fill×wrap | - | 末条后下 16 | onPanelSecondary() | bodySmall | - |
| startHowToPlayRulesHeading（"规则"） | Text | startHowToPlayCard（滚动区） | 左 | - | fill×wrap | - | 下 6 | onPanelPrimary() | titleSmall | - |
| startHowToPlayRulesItem（1 条） | Text | startHowToPlayCard（滚动区） | 左 | - | fill×wrap | - | - | onPanelSecondary() | bodySmall | - |

**相对 v2 设计草案（本文件 §2.1 之前版本、门禁 A 阶段产出的增量文本）的三处实现期决定**（逐一记录，供后续读这份契约的人核对，而非契约自相矛盾）：

1. **卡片背景不用 `backgroundMaterial(true, Material.Regular)`，改用 `surfacePanel()` 不透明面板**。原因：`Material.*` 玻璃质感在本仓库零使用先例，唯一一次真机验证记录（`ResultPanel.kt` 注释）是 `Material.Thick` 把这个项目自定义的深色/琥珀配色洗成银灰色，因此那次改动放弃玻璃质感、换成不透明 `surfacePanel()`。`Material.Regular` 本身没有单独被验证过是否同样洗色；本次是纯编译期实现任务，没有真机截图核对手段，因此保守地直接复用 `ResultPanel` 已验证的 `surfacePanel()` 方案，不去赌一个未验证的参数组合。**如果后续要恢复玻璃质感，需要先在真机上用截图核对 `Material.Regular` 在这套深色/琥珀配色下是否洗色，再决定是否替换本节这行。**
2. **标题行用 `Box` + `Modifier.align(CenterStart/CenterEnd)`，不用 `Row` + `Modifier.weight(1f)`**。原因：本仓库目前只在 `ColumnScope` 里验证过 `weight`（`GameBoard.kt` 的 `Spacer(Modifier.weight(1f))`），`RowScope.weight` 没有先例；契约草案本身也把 `Box+align` 列为"不确定就退回"的保守写法，本次直接采用，不做本项目里第一次的 `RowScope.weight` 试验。
3. **浮层用手搓 `Box`（scrim）+ `pointerInput/detectTapGestures` 消费点击，不用 `com.pico.spatial.ui.design.windows.BasicSheet`**（`ResultPanel.kt` 用的那个真实 `Dialog` 原语）。原因：本次任务书面规格明确、具体地要求了"scrim 用 pointerInput 消费指针事件""卡片自己也要消费一次指针事件"这类手搓遮罩层面的机制，且手搓方案能保证浮层严格贴在 start_page 自身的 900×640 边界内，不会像 `BasicSheet` 那样另开一个独立的平台 `Dialog` 窗口。`BasicSheet` 仍是后续如果要重构这块的一个可选项（`onDismissRequest` 天然支持点外部关闭），留作独立任务，不在本次范围内切换。

`startHowToPlayScrim` 用 `pointerInput(Unit) { detectTapGestures { onDismiss() } }` 消费点击事件（不只是视觉遮罩），点击 scrim 空白处（卡片以外区域）与点击 `startHowToPlayCloseButton` 效果完全一致——都是关闭浮层，不做其它副作用。`startHowToPlayCard` 自身也用 `pointerInput(Unit) { detectTapGestures {} }` 消费一次指针事件（card 是 scrim 的子节点，事件按"先子后父"的 Main pass 派发，card 消费后 scrim 收到的 change 已被标记为 consumed，`detectTapGestures` 内部据此不再触发 `onTap`），防止点卡片内部空白处（非关闭按钮的区域）误穿透触发 scrim 的关闭逻辑。

由于浮层在 z 序上排在 start_page 现有元素（含 v2 新增的 startHowToPlayButton）之后（后声明的子项画在上层），浮层打开期间会自然盖住并挡住 `startBeginButton` 等下层元素的指针事件，不需要再给这些元素单独加"禁用"标记；`startBeginButton` 的 `onClick` 里仍额外补了一行 `showHowToPlay = false` 防御性重置（浮层开着时这条路径本就摸不到，纯粹是第二层保险，不改变 `bankReady` 为 false 或浮层未开启时的既有行为）。

浮层展开这件事本身不出现在 §6 截图验证计划的既有 3 张名额里；如果团队希望单独留一条截图证据，这是一次单点确定性点击就能到达、且用 `startHowToPlayCloseButton` 或点 scrim 也能确定性关闭的状态，可以按需单独追加，不属于"不可判定 — 待真机验证"那一类。

## 3. 状态清单

| 元素 id | 状态 | 视觉变化 |
|---|---|---|
| startBeginButton | 禁用（题库尚未生成完成） | 背景 fillPrimary→fillTertiary，文字 labelPrimaryLight→labelSecondary，文案由「开始游戏」变为「题库准备中」，点击无效 |
| startBeginButton | hover | 背景提亮一档 |
| startDifficultySegment 单项 | 选中 | 背景切换为 fillPrimary，文字切换为 labelPrimaryLight（已在元素表体现为默认视觉，此行仅重申） |
| startDifficultySegment 单项 | hover（未选中项） | 背景提亮一档 |
| cardTile | 选中（selected） | 背景 fillSecondary→fillPrimary，文字 labelPrimary→labelPrimaryLight，外描边 2dp interaction 色 |
| cardTile | 提示高亮（hinted） | 外描边 3dp interaction 色 + 轻微发光，背景与文字色**不变**（与 selected 的背景切换区分开，二者视觉互斥：hinted 只在玩家点击「提示」后、尚未做出下一次有效操作前出现，且不会与该牌自身的 selected 状态同时出现） |
| operatorButton | 禁用（未选中任何牌时） | 背景 fillSecondary→fillTertiary，文字 labelPrimary→labelSecondary，整体不透明度降至约 50% |
| operatorButton | 可用（已选中牌 A，未选运算符） | 背景 fillSecondary，文字 labelPrimary（元素表默认态） |
| operatorButton | 选中（已点击，等待牌 B） | 背景 fillPrimary，文字 labelPrimaryLight |
| operatorButton | hover（可用态） | 背景提亮一档 |
| statusLineText | 无消息 | 整行留空，占位高度不塌陷（22dp 最小高度） |
| statusLineText | 不是 24 | 文字色 error |
| statusLineText | 除不尽 | 文字色 error |
| statusLineText | 答对 | 文字色 passable |
| statusLineText | 当前牌面已无解 | 文字色 error |
| actionUndoButton / actionResetButton / actionHintButton / actionSkipButton | hover | 背景提亮一档 |
| resultChangeDifficultyButton / resultRestartButton | hover | 背景提亮一档 |
| startHowToPlayButton | hover | 背景提亮一档（沿用 SDK Button 内建 hover 行为） |
| startHowToPlayCloseButton | hover | 背景提亮一档（`secondaryButtonColors()` 既有 hover 行为） |
| startHowToPlayScrim | 点击（卡片以外的空白区域） | 关闭浮层，效果与点击 startHowToPlayCloseButton 完全一致；scrim 必须消费该次指针事件，不得穿透点亮下层 startBeginButton / startDifficultySegment 等 start_page 元素 |

## 4. 文案清单

| 元素 id | 文案 |
|---|---|
| appTitleText | Space24 |
| startDifficultySegment 三项 | 简单 / 普通 / 困难 |
| startBestScoreLabel | 历史最高（{当前选中难度}）——例：历史最高（普通） |
| startBestScoreValue（空态，该难度从未开过局） | -- |
| startBeginButton（默认） | 开始游戏 |
| startBeginButton（题库未就绪） | 题库准备中 |
| hudRemainingLabel | 剩余 |
| hudScoreLabel | 得分 |
| hudDifficultyPill | {当前难度}——简单 / 普通 / 困难 |
| actionUndoButton | 撤销 |
| actionResetButton | 重置本题 |
| actionHintButton | 提示 −10s |
| actionSkipButton | 换一题 −10s |
| statusLineText（不是 24） | 不是 24，可撤销或重置 |
| statusLineText（除不尽） | 除不尽，换个运算或换张牌 |
| statusLineText（答对） | 答对！+1 |
| statusLineText（无解） | 当前牌面已无解，请撤销或重置 |
| resultTitleText | 时间到 |
| resultRecordBadge | 新纪录 |
| resultScoreLabel | 本局得分 |
| resultBestLabel | 历史最高（{本局所选难度}）——与 startBestScoreLabel 同一措辞规则 |
| resultMissedListItem | 见 §2.4 格式说明，例：`1 1 1 8 → 1 + 1 = 2，2 + 1 = 3，3 × 8 = 24` |
| resultChangeDifficultyButton | 更换难度 |
| resultRestartButton | 再来一局 |
| startHowToPlayButton | 玩法（"?"徽标经 `leadingIcon` 插槽渲染，不算在这个 Text 文案里） |
| startHowToPlayTitleText | 玩法说明 |
| startHowToPlayCloseButton | × |
| startHowToPlayIntroHeading | 简介 |
| startHowToPlayIntroBody | 见下方"howto_play_overlay 文案（权威来源，禁止改写）" |
| startHowToPlayControlsHeading | 操作 |
| startHowToPlayControlsItem（2 条） | 见下方"howto_play_overlay 文案（权威来源，禁止改写）" |
| startHowToPlayRulesHeading | 规则 |
| startHowToPlayRulesItem（1 条） | 见下方"howto_play_overlay 文案（权威来源，禁止改写）" |

上述四条 `statusLineText` 文案是产品设计文档规定的唯一四种反馈文案，实现阶段不得增删或改写措辞。

#### howto_play_overlay 文案（v2 新增，权威来源，禁止改写／转述／精简，原样嵌入实现代码——半角逗号/括号/分号、全角冒号、全角破折号、en-dash 均按原文照抄）

【简介】（startHowToPlayIntroBody，单段）
```
在90秒倒计时结束前,把每道题发到的4张数字牌,通过加减乘除一步步合并,尽可能多地拼出24点——解出的题目数就是你这一局的分数。
```

【操作】（startHowToPlayControlsItem，共 2 条，每条独立一个 Text，纵向排列，前缀短横线保留在文案内不单独渲染 bullet 符号）
```
- 点数字牌：点一张牌选中它作为操作数,再点另一张牌准备把两张牌合并。
- 点运算符：选中一张牌后,点 + − × ÷ 中的一个,决定接下来怎么合并这两张牌。
```

【规则】（startHowToPlayRulesItem，当前仅 1 条，数字编号已内嵌在文案里，不要再额外加自动编号）
```
1. 每局限时90秒, 每道题会发给你4张数字牌(简单、普通难度都是1–9点;困难难度会混入10–13点,一律用阿拉伯数字标出,不是花牌),目标是用加减乘除把这4张牌一步步合并成一张24点的牌。
```

## 5. 不做清单（YAGNI 边界）

- 有理数 / 分数中间值展示或运算（整数约束模式，需要分数的经典题目本就不在题库内）
- 表达式键盘输入、括号管理 UI
- 选择题模式
- 扑克花色美术、`J`/`Q`/`K` 字面显示（困难档 `11`/`12`/`13` 一律用阿拉伯数字）
- 无级难度调节、可配置局时长（三档固定 90 秒）
- 连胜 / 连击倍率计分展示
- 星球皮肤、额外主题包装（视觉走 `PicoTheme` 默认深色玻璃质感，不做本契约描述之外的装饰主题）
- 弹窗式状态提示（`statusLineText` 是单行文字，不用 Dialog/Modal/Toast）
- 音效 / 触感反馈的具体规格（实现阶段按 SpatialUI 组件默认交互反馈接入，本契约不额外规定）
- 对齐参考线 / 合并路径动画的具体运动曲线规格（实现阶段自行选择合理过渡，只要不引入本契约未列出的常驻视觉元素）
- （v2）不改动、不重排 start_page 现有 5 个元素的位置或逻辑
- （v2）不新增图标矢量资源；"?"与"×"都是文字字符，不是 drawable
- （v2）不把 `showHowToPlay` 写入 `GameSession`/`GamePhase`；它是纯展示层状态
- （v2）不在 §6 截图验证计划里强制新增第 4 张截图（可选，见 §2.5 说明）
- （v2）不因为本增量而修正 `SecondaryButtonStyle.kt` 文档注释与实现不符的既有出入（`secondaryButtonColors()` 文档写 `fillSecondary`，实际读 `fillLight`；与本次改动无关，留作独立任务）
- （v3）Phase 1 不实现选中弹出反馈、合并动画、答对爆发效果（Phase 2–4，见 §2.3 说明）——已确认是产品意图，不是本轮遗漏
- （v3）Phase 1 不清理牌面替换时的旧字模实例（`entity.enabled = false` 但不 `destroy`）——清理形态依赖尚未设计的 Phase 3 合并动画，见 §2.3 与 `content/SpatialStage.kt` 的 `MountedGlyphs` 文档注释
- （v3）已知限制，暂不处理：字模颜色固定，不随选中/禁用状态变化（见 §2.3"选中态仍由 2D 表达"，这是既有的、未变的 v3 决策）。后果：选中卡片的近白字模（`labelPrimary`）落在选中态亮青色背景上，实测对比度约 1.3:1；运算符禁用态的调暗是 2D 面板层的 `alpha` 效果，不会传导到挂在该面板下的 3D 字模子实体，因此禁用运算符的符号会以正常亮度渲染，与视觉上已调暗的按钮面不一致。已就具体对比度数字向用户说明并由用户确认接受为本阶段已知限制，不在本轮排期修复，也不是本轮遗漏的缺陷

## 6. 截图验证计划

模拟器指针注入在本工作区不可靠（已有记录：`adb shell input tap` 能摸到管线但点不准按钮，甚至可能误关闭沉浸态）。因此本计划只选取**不依赖精确点击序列**即可到达的状态；凡是依赖「选中某张特定牌 / 选中某个特定运算符 / 触发提示高亮 / 触发否定反馈」等多步精确点击才能呈现的交互态，一律记为**不可判定 — 待真机验证**，不得声称已通过模拟器截图验证。

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | `start_page` 默认态（默认选中"普通"） | 启动后等待渲染 | appTitleText, startDifficultySegment（三项+选中态视觉）, startBestScoreLabel, startBestScoreValue, startBeginButton |
| 2 | `Playing` 刚发牌态（4 张牌，未做任何选择） | 点「开始游戏」→ 等待渲染（不点任何牌/运算符，只需一次确定性点击即可到达，不依赖精确命中卡片/运算符坐标） | hudRemainingLabel/Value, hudDifficultyPill, hudScoreLabel/Value, cardTile（4 张，默认态，核对固定尺寸 104×136dp、圆角、按字符数分档字号）, operatorButton（4 个，禁用态视觉）, actionUndoButton/actionResetButton/actionHintButton/actionSkipButton |
| 3 | `result_panel` 时间到态 | 进入 `Playing` 后**不做任何操作，静置等满 90 秒**直至时间归零自动结算（不依赖任何精确点击，规避指针注入不可靠问题） | resultOverlayScrim, resultPanelCard, resultTitleText, resultScoreLabel/Value, resultBestLabel/Value, resultMissedList（含至少一条未答出题目——本状态下当前唯一在做的题会因时间到而记为未答出）, resultChangeDifficultyButton, resultRestartButton |

**明确记为「不可判定 — 待真机验证」、不进入上表 3 张截图名额的交互态**：

- `cardTile` 选中态（selected）与提示高亮态（hinted）的实际视觉
- `operatorButton` 从禁用→可用→选中的状态切换过程
- 除不尽 / 不是 24 / 答对 / 无解 四种 `statusLineText` 否定/肯定反馈文案的实际触发
- 撤销 / 重置本题 / 提示 / 换一题四个操作按钮的点击反馈
- `resultRecordBadge` 的真实显隐条件触发（是否在本局得分刷新历史最高时正确出现/消失）

以上状态在实现阶段以单元测试（`GameSession`/`Solver` 等纯 Kotlin 逻辑）为主要正确性证据，视觉核对留待真机试玩验证，报告中不得因为「模拟器截图看起来对」而声称这些交互态已验证通过。
