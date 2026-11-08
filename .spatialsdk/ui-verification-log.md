# UI 验证日志 — Space24

## 第 1 轮 — 2026-08-18T00:33 (本地时间，UTC 2026-08-17T16:35Z)

**设备**：`emulator-5554`（当次唯一在线设备）
**锁**：Hold A，owner `Space24-gateB-r1a`，acquire/release 均 exit 0，持锁时长约 20s（远低于 90s 目标 / 120s 硬上限）。因 Hold A 内单次点击「开始游戏」未命中（见下），按 gate B 规则跳过 Hold B（result_panel 90s 静置流程未执行）。

**截图**：
- `.spatialsdk/ui-verify/round1-start_page.png` — **截图无效**：内容不是 Space24，而是另一个应用（"SpacePlayer" 视频资源库界面）。推测原因：启动后 6s 时 Space24 尚未渲染完成 / 共享模拟器上残留了另一应用的窗口内容（与既有记录「共享模拟器多会话抢同一台设备」「Full Space 占用会挡住别的应用启动」的现象一致）。此文件不作为任何契约元素的判定依据。
- `.spatialsdk/ui-verify/round1-playing.png` — 命名为「playing」，但实际内容是 **`start_page` 默认态**（非 `Playing`）。这证明点击「开始游戏」的坐标猜测（物理像素 1080,1239，从设计稿 900×640dp 窗口居中假设反推）**未命中**按钮，应用仍停留在开始页。因祸得福：这张图恰好是本轮唯一一张有效呈现 `start_page` 状态的截图，因此本轮对 `start_page` 清单的核对改用此文件作为依据（而非同名的 `round1-start_page.png`）。
- `round1-result_panel.png` — **未产生**。因点击未命中，按 gate B 规则跳过 Hold B，不满足"进入 Playing 后静置 90s"的前置条件。

**点击是否命中**：否。`round1-playing.png` 显示的仍是 `start_page`，不是发牌后的 `Playing` 界面。

### 差异分级

#### Blocker
无（本轮无法从有效截图中确认任何契约元素缺失/错位/颜色角色用错/文案错误）。

#### Major
无。

#### 逐元素判定 — Shot 1 `start_page`（依据 `round1-playing.png` 的实际内容）

| 元素 id | 判定 | 说明 |
|---|---|---|
| appTitleText | 通过 | 文案「Space24」存在，位于面板上部，字号明显大于周边文字，与 titleLarge/labelPrimary 的相对预期一致 |
| startDifficultySegment | 通过 | 三项「简单／普通／困难」齐全；外层胶囊容器可见；「普通」为深色实心背景+浅色文字（对应 fillPrimary+labelPrimaryLight），其余两项透明背景+灰色文字（对应 transparent+labelSecondary）；顺序与形状（胶囊）匹配 |
| startBestScoreLabel | 通过（文案）／不可判定（括号全角/半角） | 文案「历史最高（普通）」存在且位置在段控件下方；全角括号 vs 半角括号在当前截图分辨率下无法可靠区分 |
| startBestScoreValue | 通过（内容）／不可判定（titleLarge 字号核对） | 显示「--」，符合无历史记录预期；但「--」是短横线字形，缺乏上下延伸（无升部/降部），与其他文字做字号相对比例核对不可靠 |
| startBeginButton | 通过 | 深色胶囊按钮 + 浅色文字「开始游戏」，形状/配色关系与契约一致 |
| 元素纵向顺序（标题→分段→标签→数值→按钮） | 通过 | 截图中自上而下顺序与契约一致 |
| 窗口：单一 planar 窗口固定 900×640dp | 通过（单窗口事实）／不可判定（精确尺寸/比例） | 确认为单一矩形半透明面板；精确 dp 尺寸/宽高比无法从透视截图测量 |

#### 逐元素判定 — Shot 2 `Playing`（点击未命中，无法呈现该状态）

以下全部记为 **不可判定 — 待真机验证**，原因：模拟器指针注入未能命中「开始游戏」按钮，应用未进入 `Playing` 状态，无对应截图可供核对：
- hudRemainingLabel / hudRemainingValue（含「1:30」而非「1:29」的核对）
- hudDifficultyPill
- hudScoreLabel / hudScoreValue
- cardTile ×4（尺寸/圆角/字号分档/牌面数字）
- operatorButton ×4（禁用态视觉）
- 操作行：撤销／重置本题／提示−10s／换一题−10s
- statusLineText（空态占位高度）

#### 逐元素判定 — Shot 3 `result_panel`（Hold B 未执行，无对应截图）

以下全部记为 **不可判定 — 待真机验证**，原因：按 gate B 规则，点击未命中 → 跳过 90s 静置流程 → 未产生该状态截图：
- resultOverlayScrim（含"是否读作半透明遮罩而非不透明面板"的核对——这是本轮特别关注点之一，未能核对）
- resultPanelCard
- resultTitleText
- resultRecordBadge（0 分局本应缺失，未能核对）
- resultScoreLabel / resultScoreValue
- resultBestLabel / resultBestValue
- resultMissedList
- resultChangeDifficultyButton / resultRestartButton

#### 契约 §6 预先约定的「不可判定 — 待真机验证」清单（与截图无关，逐条记录）
- cardTile 选中态（selected）与提示高亮态（hinted）的实际视觉
- operatorButton 从禁用→可用→选中的状态切换过程
- 除不尽／不是 24／答对／无解 四种 statusLineText 反馈文案的实际触发
- 撤销／重置本题／提示／换一题四个操作按钮的点击反馈
- resultRecordBadge 的真实显隐条件触发
- PICO 系统字体是否携带 tnum 表（等宽数字视觉效果）

### 特别关注点核对结果
- 「开始游戏」按钮命中后新一局是否显示 `1:30`（而非 `1:29`）：**不可判定**，未进入 Playing 状态。
- 结算浮层是否呈现「半透明遮罩下可辨认的棋盘」而非不透明面板：**不可判定**，未进入 result_panel 状态。
- 开始页按钮文案是「开始游戏」还是「题库准备中」：**开始游戏**（`round1-playing.png` 中可见），说明本次设备上 `PuzzleBank.build()` 在启动后 9 秒内（6s 等待 + 3s 等待）已完成，题库生成不构成开始页可感知延迟。

### 本轮所做改动
无。本轮为验证性质，未修改任何代码。

### 结论
需修复？否——本轮未发现 Blocker/Major。但**未达成验证目的**：3 张目标截图中，1 张截图内容无效（`round1-start_page.png`，捕获到了非 Space24 内容），1 张因指针注入未命中而退化为对 `start_page` 的重复验证（`round1-playing.png`），`result_panel` 截图完全未产生。`Playing` 与 `result_panel` 两个状态、以及本轮特别关注的「1:30 倒计时」与「结算遮罩透明度」两项，均记为 **不可判定 — 待真机验证**。本轮判定：**已达 1 轮（未达 2 轮上限）；是否继续第 2 轮模拟器验证或直接转真机验证，交控制器/用户决策**——本轮遇到的两处失败（无效截图 + 点击未命中）均属指针注入/时序不可靠的既知限制，而非代码缺陷信号，重试同一坐标预期收益有限。

## 第 2 轮 — 2026-09-21 (Task 7：挂载字模，移除 2D 文字)

**设备**：`emulator-5554`（当次唯一在线设备）。锁分三段：`Space24-glyph-phase1-step3`（Step 3 降级探针，因等待另一会话释放锁，acquire 首次超时后用后台轮询等到）、`Space24-glyph-phase1-step3` 复测、`Space24-glyph-phase1-resultcheck`（Gate B + RESULT 态核对，180s）。三段均 acquire/release exit 0，用完立即 `force-stop` + release。

**本轮所做改动**：
1. 新增 `content/GlyphLoader.kt`（14 字模原型池，`preload`/`widthOf`/`capHeight`/`isComplete`/`instantiate`）。
2. `content/SpatialStage.kt` 追加 `mountGlyphs` 挂载逻辑、`applyLayout` 接入 `loader`/`mounted` 参数、`initial` 里跑 `loader.preload()` 并把 `glyphsReady` 接上 `loader.isComplete()`。
3. **相对任务提示的一处修正**（非提示原文，设备验证中发现并修复）：`GlyphLoader.instantiate()` 里 `clone()` 补了显式 `Entity.CloneOptions(recursive = true)`——SDK 默认 `CloneOptions()` 是非递归的，字模网格挂在原型的子节点下，非递归克隆会拿到空壳；姊妹项目 SpaceFlightChess/SpaceMinesweeper 对模型克隆都显式传了 `recursive = true`，予以对齐。
4. **本轮设备验证中发现并修复的降级开关缺口**：`applyLayout` 里挂字模的两段循环原先只按"这张卡/这个运算符是否可见"决定挂不挂，没有读 `loader.isComplete()`——导致 14 个字模只缺 1 个时，`glyphsReady=false`（2D 文字按预期显示），但 3D 字模仍会挂上未缺失的那些字，与 2D 文字同屏叠加（见下方 Step 3 首次截图证据）。修复：card/op 两段循环各引入 `glyphsAvailable = loader.isComplete()` 门禁，未就绪时字形列表直接置空，不再逐字尝试挂载。

### Step 3：降级开关双向验证

**探针**：临时把 `Glyph.D0` 的资产 URI 改成 `asset://glyphs/digit_0_MISSING.usdc`，其余 13 个不变。

**第一次构建（修复前）**：
- 日志：`load FAILED asset://glyphs/digit_0_MISSING.usdc: ResourceLoadingException...` + `preload done loaded=13 expected=14`。
- 截图 `/tmp/space24-step3-degraded-2.png`：卡面/运算符均显示**实心浅色矩形块**，无可读文字——这就是上面第 4 点描述的缺口：`glyphsReady=false` 本该让 2D 文字接管，但 3D 字模（13/14 成功加载的那些）仍被挂上，且挤占了原本该显示的 2D 文字位置。判定：**发现问题，已修复**（见上方改动 4）。

**第二次构建（修复后，仍处于同一降级探针状态）**：
- 日志同上（`preload done loaded=13 expected=14`）。
- 截图 `/tmp/space24-step3b-degraded-2.png`，裁剪证据 `/tmp/crop_ops2.png`（卡面清晰显示 `2`/`5`/`8` 三个 2D 数字字符）、`/tmp/crop_ops4.png`（运算符行清晰显示 `+`/`−`/`×`/`÷` 四个 2D 符号，暗色未选中态）。判定：**通过**——任一字模缺失时，卡面与运算符整体干净回落到 2D 文字，没有残留 3D 块或半 3D 半 2D 混排。

**复原**：改回正常资产路径，重新构建。

### Step 5：门禁 B —— 设备取证（14/14 完整字模）

- 日志 `/tmp/space24-phase1.log`：`preload done loaded=14 expected=14`，全程无 `load FAILED`，无异常/崩溃关键字。
- 截图 `/tmp/space24-phase1-1.png` / `/tmp/space24-phase1-2.png`（间隔 2s）：像素级 diff 仅 11×7px（倒计时数字跳动区域），其余完全一致，判定**两张截图一致**（未被共享模拟器串画面）。
- 裁剪证据 `/tmp/crop_gate_allcards_nearest.png`、`/tmp/crop_gate_contrast.png`：4 张卡各显示一个**轮廓互不相同**的浅色块（每个的顶部/底部缺口位置不同，与 4 个不同数字字模的外轮廓预期一致），且每块内部有一条与顶部边界平行的水平明暗分界线（朝向一致，符合方向光下有一定厚度物体的正面/棱边分界）。
  **判定：不可判定（部分）——待真机复核**。能确认这不是 2D 文字（对照降级态截图，2D 文字在同一相机距离下清晰可读为独立字形，见 `/tmp/crop_ops2.png`；而完整态下前述 4 个块整体过曝成近乎纯色矩形，仅轮廓缺口和明暗分界可辨），能确认 4 个字模几何互不相同（轮廓不同），但因模拟器截图相机固定且窗口在场景中的实际像素占比很小（约 95×55px/字），**无法在此分辨率下读出具体数字笔画、也无法可靠判断"厚度"以外是否有清晰的侧面高光棱线**——这与既有记录「模拟器截图相机是固定窄视角的」的限制一致，非本任务代码问题。运算符字模同理：能确认非 2D（对照降级截图的清晰 `+ − × ÷` 符号），但笔画级别不可判定。建议真机复核以确认最终视觉判据。
- 数字**无重影**：卡面上只看到一个轮廓块，未见 2D 文字与 3D 字模同屏叠加的痕迹（这正是 Step 3 发现并修复的缺口所覆盖的问题）。**判定：通过**。

### RESULT 态字模关闭核对（自评清单要求，非 brief 脚本自带步骤）

用固定种子（`-e debug_start playing`，种子牌面 2/5/8/9）等待 90s 回合计时自然耗尽触发 `endRound()`，而非依赖模拟器点击注入（已知不可靠）。

- 日志：`applyLayout phase=PLAYING cardCount=4` → 90 秒后 `applyLayout phase=RESULT cardCount=4`，全程无异常。
- 截图 `/tmp/space24-result-2.png`：结算浮层（黄色"时间到"卡片）完整覆盖原卡片/运算符区域，**未见任何 3D 字模块穿透浮层**，也未见运算符符号残留。
  **判定：通过**——`entity.enabled` 在 RESULT 态被两处独立路径拉低（卡面/运算符可见性循环里 `boardVisible=false` 直接把面板 `enabled=false`；字模挂载循环里 `visible`/`opVisible` 同样并入 `boardVisible`，字模自身 `enabled` 也被拉低），双重覆盖，设备截图为空证实无穿透。

### Step 6：帧率/性能

**证据强度：弱，仅供参考**。锁窗口时间有限，未运行 `pico-cli perf live/trace`。改用 `adb logcat -d` 全量扫描本次约 100 秒会话（含 14 字模加载 + PLAYING 90s + RESULT 触发）的 `Choreographer`/掉帧关键字：未发现 "Skipped N frames" 或 Davey 类告警。**判定：不可判定（未做正式性能采样）——待后续用 `pico-cli perf` 或真机复核**，当前唯一负向信号（掉帧告警）为空不代表已验证达标，只代表本次会话没有触发系统级掉帧日志。最坏情形（4 卡 × 单字符~6 字符 + 4 运算符，最多 24 个挂载字模实体 + 14 个 disabled 原型）在本次会话中未观察到异常。

### 契约 §6 预先约定的「不可判定 — 待真机验证」清单（追加）
- 卡面/运算符字模的具体数字笔画与侧面高光棱线清晰度（模拟器相机分辨率/距离限制）
- 正式 `pico-cli perf` 帧率/掉帧采样（本轮仅做了粗粒度 logcat 关键字扫描）
- 牌数递减时字模是否跟着重新居中（`update` 回调对 `cardCount` 变化的响应性——本任务沿用 Task 3 遗留的未验证项，Task 7 未产生新证据）

### 结论
需修复？**发现两处，均已在本轮修复并重新验证**：
1. `GlyphLoader.instantiate()` 缺 `recursive = true`（未经设备验证难以发现，源码交叉核对 SDK 默认值 + 姊妹项目先例后修复）。
2. `applyLayout` 挂字模逻辑未读 `glyphsAvailable` 门禁，导致部分字模缺失时 2D/3D 叠加而非整体回落（Step 3 设备截图直接暴露，修复后复测通过）。

Phase 1 完成判据（"卡面数值与运算符符号从 2D 文字变为可见的立体挤出字模，降级开关两个方向都真实生效，RESULT 态不穿透"）在模拟器上**部分达成**：降级开关双向、RESULT 态遮挡、无重影均已用设备证据确认通过；"看得出厚度与侧面高光"这一条因模拟器截图相机的固有分辨率/距离限制，只能确认"确实是与 2D 文字视觉上明显不同的、逐字轮廓互异的几何"，无法进一步确认笔画级清晰度与棱线高光，标记为**不可判定 — 待真机验证**，非代码缺陷信号。
