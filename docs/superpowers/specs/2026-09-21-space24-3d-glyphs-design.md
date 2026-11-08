# Space24 立体字模 — 设计规格

- 日期：2026-09-21
- 状态：待实现
- 触发需求：为 Space24 增加空间沉浸感，让数字与运算符具备真实 3D 凹凸
- 影响面：`AndroidManifest.xml`、`Main.kt`、`content/`、新增 `app/src/main/assets/glyphs/`
- **不影响**：`game/`、`data/`（规则层零改动，现有 68 个测试必须保持全绿）

## 0. 决策记录

本规格由 2026-09-20 的头脑风暴产出，用户逐项确认：

| 决策点 | 选定 | 备选（已否决） |
|---|---|---|
| 立体深度档位 | 数字本身是挤出的 3D 几何，窗口改 Volumetric | 仅 zOffset 分层；仅浮雕着色；整个游戏进 Stage |
| 字形来源 | Spatial Editor 授作 14 个 `.usdz` 字模 | 图元拼装（七段管）；程序化挤出字体轮廓 |
| 3D 范围 | 仅卡面数值 + 4 个运算符 | 追加 HUD 数字；卡片本体也做成 3D 盒 |
| 对齐方案 | 逐元素 `AttachmentPanel`，字模作其子实体 | 三面板 + dp→米 标定；板面整体 3D + ECS 输入 |
| 动态行为 | 静态凸起 + 选中弹出 + 合并动画 + 答对爆发（四阶段） | 仅静态凸起 |
| 验证强度 | 模拟器截图 + 单测；真机交用户试玩 | androidTest 探针 + 录像 + 真机日志锚点（用户判定过重） |

## 1. 事实依据

以下均已在本工作区代码或 SDK 6.0.0 源码中核实，不是推测：

- **Volumetric 窗口可承载 ECS 内容**：SpaceVinyl 的 `DefaultWindowContainer` 将 manifest `windowcontainer.style` 置 `2` 后，内部 `SpatialView` 正常承载 `Entity` 与 `AttachmentPanel`。
- **`SpatialViewContent` 即坐标换算器**：`SpatialViewContent.kt:27` 以委托方式实现 `SpatialCoordinateSpaceConverter`。因此 `SpatialView(initial = { content, _ -> })` 的 `content` 直接提供 `convertPosition` / `convertSize`，在 `ViewCoordinateSpace`（像素）与 `SpatialCoordinateSpace`（米）之间换算。`ViewCoordinateSpace.Local` 的原点是当前 View 的左上后角。
- **`AttachmentPanel` 必须显式入树**：`attachments.entity(id)` 本身不会把面板加进渲染树，必须再调 `content.addEntity(it)`。（SpaceCube 记录的 2 号已知坑）
- **`getVisualBounds` 对 3D 模型有效、对 2D 附件面板恒返回 (0,0,0)**：前者是字模归一化的依据，后者意味着面板的米尺寸量不出来。
- **已核实存在的 API**：`Entity.loadSuspend(uriString=)`、`Entity.clone(CloneOptions)`、`Entity.addChild(Entity)`、`MeshResource.createBox(size, cornerRadius)`、`AssetBundle.load(path, LoadType)`。
- **`MeshResource` 没有文字/字形工厂**，只有 `createPlane/Sphere/Cylinder/Cone/Capsule/Box/Torus` 与 `createWithMeshModel`。这是字模必须外部授作的原因。
- **`withFrameNanos` 在 `WindowContainer` 内只触发一次**，不能用作动画时钟。

以下三条已由 Phase 0 探针（`ProbeStage.kt`，2026-09-21，emulator-5554）**运行时验证**：

- **`content.convertPosition` 的换算结果与视觉位置一致 —— 已验证，通过。** 证据：`/tmp/space24-probe.log` 中 9 条 `attached id=... dp=(...) meters=(...)` 记录，任取相邻两点算 x 方向 dp→m 比值，卡片行三对间距 (270→390→510→630 dp) 与运算符行三对间距 (342→414→486→558 dp) 共 6 组比值全部等于 `0.000800 m/dp`，证明换算是一致的线性映射而非乱数；y 方向 dp 越小换算出的 m 越大（卡片行 y=164dp→0.1248m 高于运算符行 y=284dp→0.0288m），与"dp 向下、米向上"的预期方向相符。视觉上，`space24-probe-2.png`（`am start` 后约 +2s 的第二张截图）完整显示九个面板：深色 `chrome` 底板铺满窗口，4 个蓝色卡片块横排居中于上半部，4 个橙色方块横排居中于卡片下方——与契约 §2.3 的相对布局一致。**两张截图并不是逐像素一致**：更早的 `space24-probe-1.png` 只有 chrome + 4 张卡片 + 探针盒子，运算符行尚未出现（见下一条渲染滞后说明），不构成"两张一致"。排除"拍到别的会话窗口"靠的不是两张完全相同，而是两张截图里房间背景、`chrome` 面板位置、四张卡片的相对布局在两帧之间保持一致——这足以确认两张截图都来自同一次运行、同一个窗口，只是取样时刻不同。
- **单个 `SpatialView` 支持 9 个 `AttachmentPanel` —— 已验证，通过。** 证据：日志行 `attached id=chrome/card_0..3/op_0..3` 共 9 条，无一条 `attachment MISSING`，末尾 `attachments attached=9 expected=9`；截图（`space24-probe-2.png`）中 9 个色块（1 深色 chrome + 4 蓝 + 4 橙）同时可见。工作区此前验证的最大值 2 由此被推翻，不必退回三面板方案。
- **字模实体在 `z = +0.015 m` 处未被容器前边界裁剪 —— 已验证，通过。** 证据：日志 `probe box at meters=(-0.144,0.1248,-0.177)`，即 `card_0` 的 z（-0.192）+0.015 = -0.177，与代码预期一致；截图中 `card_0` 对应的蓝色方块内清晰可见一个更小的红色方块（探针盒子），未被裁掉、未消失。

以下仍**未经运行时验证**（不在本次 Phase 0 探针范围内，留待后续阶段）：

- 用户等比缩放窗口时，`AttachmentPanel` 与字模实体是否随之缩放。`resizerestriction = 1` 允许用户缩放，但 `SpatialView` 根实体是否随窗口缩放无先例可循。若不随动，缩放后 2D 面板与 3D 字模会脱节。

## 2. 容器与场景骨架

### 2.1 manifest 改动

`pico.spatial.windowcontainer.*`：

| 键 | 现值 | 新值 | 理由 |
|---|---|---|---|
| `style` | `1` Planar | `2` Volumetric | 3D 实体需要深度 |
| `defaultsize` | `900x640` | `900x640x480` | 加深度轴 |
| `resizetype` | `2` ContentSize | `0` Automatic | `SpatialView` 铺满后无可依据的内容固有尺寸 |
| `resizerestriction` | `0` | `1` Uniform | 等比缩放，避免 3D 层与面板脱节 |
| `worldscaletype` | `1` Dynamic | `2` Fixed | 动态缩放与米空间实体的相互作用无人验证；与 SpaceVinyl 既有选择一致 |
| `materialbackground` | `1` | `0` | 底由 `chrome` 面板自绘 |
| `volumealignment` | 无 | `0` Gravity | 侧面随重力，板面不歪 |
| `volumebasepanel` | 无 | `1` None | 隐藏容器自带底板，避免与 `chrome` 面板叠成两层 |

### 2.2 Compose 结构

```
DefaultWindowContainer
└ PicoTheme(cyberColorScheme)
  └ SpatialView(Modifier.fillMaxSize())
    ├ attachments { chrome, card_0..3, op_0..3 }
    └ initial { content, attachments -> 建锚点、入树、挂字模 }
```

沿用 SpaceCube / SpaceVinyl 的既定形态：**`SpatialView` 铺满，所有 2D 内容都是 `AttachmentPanel`**。不采用"2D Compose 作为 `SpatialView` 兄弟节点"的写法——该写法在本工作区无先例、未验证。

`Main.kt` 中的 `windowConstraints(width = 900.dp, height = 640.dp)` 删除：`resizetype = 0` 下不再由它决定尺寸，尺寸来源统一为 manifest `defaultsize`。

## 3. 字模资源

### 3.1 资产清单

14 个 `.usdz`，置于 `app/src/main/assets/glyphs/`：

`digit_0` … `digit_9`、`op_plus`、`op_minus`、`op_times`、`op_divide`

负数的减号复用 `op_minus`，不单独出资产。

### 3.2 授作规范（Spatial Editor）

- 字面朝 `+Z`，pivot 居中。
- 挤出深度 ≈ 字高的 12%。
- 边缘带小倒角。**倒角不是装饰**：没有它高光无处可抓，头动时看不出立体感。
- 材质颜色对齐 cyber 主题的 `labelPrimary`；选中态由运行时改材质参数表达，不出第二套资产。

### 3.3 运行时策略

- 启动时 `Entity.loadSuspend` 建立 14 个**原型实体**，`enabled = false`，不加入渲染树。
- 显示时 `clone()` 出实例。同一字模多处出现只解析一次。
- **尺寸归一化**：用 `getVisualBounds` 按**字高**统一缩放（不按字宽，否则 `1` 会被拉胖），横向按各字实际宽度依次排布，得到自然的比例字距。
- 卡面为单个数值居中，不需要等宽。契约中"等宽数字"的要求只约束 HUD 与结算分数，二者保持 2D，不受影响。
- **超长牌面**：牌面字符数范围 1–6（理论极值 `−28561`）。字模横排后若整串宽度超过卡宽对应的米宽，整串等比缩小。此规则**替代**契约 §2.3 现行的"按字符数切 titleLarge / titleSmall / bodySmall 三档"。

## 4. 米空间布局

### 4.1 换算辅助

唯一的新坐标代码：

```kotlin
// 契约的窗口相对 dp -> SpatialView 根实体的米坐标
fun SpatialViewContent.anchorOf(xDp: Float, yDp: Float, density: Float, zMeters: Float): Vector3
```

实现为 dp × density 得像素，再经 `convertPosition(_, ViewCoordinateSpace.Local, localSpatialCoordinateSpace)` 换算。`ViewCoordinateSpace.Local` 原点是 View 左上后角，`SpatialView` 铺满窗口，故该原点**正是契约 §2.3 那套窗口相对 dp 偏移的原点**——契约的 dp 坐标表原样保留，无需改写成米坐标表。

### 4.2 面板清单

dp 数值全部直接取自契约 §2.3，不新造。`chrome` 的 900×640dp 是**固定值**——`resizetype` 改为 `0` 后窗口容器不再由内容尺寸驱动，面板不会随窗口一起长大，用户放大窗口只会让板面周围留白变多。这是从 `ContentSize` 改为 `Automatic` 的直接后果，属预期行为。


| 面板 id | 内容 | 尺寸 | 定位 | z |
|---|---|---|---|---|
| `chrome` | StartPage / Hud / 状态行 / 动作按钮 / ResultPanel | 900×640dp（固定） | 窗口中心 | 0 |
| `card_0` … `card_3` | 现 `CardTile`，移除内部 `Text` | 104×136dp | 卡片行，随张数重新居中 | 0 |
| `op_0` … `op_3` | 现运算符 `Button`，移除内部 `Text` | 56×56dp | 运算符行 | 0 |

### 4.3 面板生命周期

**9 个面板全程常驻，不做动态增删**——`initial` 只跑一次，后加的面板没有可靠挂载时机。

牌数由 4 递减至 1 时，多余的卡片面板置 `enabled = false`，其余面板重算位置使行重新居中。此机制同时为 Phase 3 的合并动画提供了"位置可动"的基础。

按游戏状态的显隐规则：

| `GamePhase` | `chrome` | `card_*` / `op_*` 面板 | 字模实体 |
|---|---|---|---|
| `NOT_STARTED` | StartPage | 全部 `enabled = false` | 全部 `enabled = false` |
| `PLAYING` | Hud + 状态行 + 动作按钮 | 按当前牌数启用 | 随面板 |
| `RESULT` | 同上 + ResultPanel 浮层 | **全部 `enabled = false`** | **全部 `enabled = false`** |

`RESULT` 态的关闭是**必须的，不是优化**：`ResultPanel` 画在 `chrome` 面板上（z = 0），而字模实体在 z = +0.015 m，若不关闭，字模会**穿过结算浮层浮在最前面**，连 scrim 一起盖掉。这是本方案把 2D 与 3D 分置两层带来的固有后果，2D-only 的旧实现里不存在。

注：`enabled = false` 未必足以完全隐藏子实体（工作区已记录 `opacity = 0` 未能完全隐藏静态子物体、与文档"级联隐藏"说法矛盾的先例）。Phase 1 完成后应截图确认结算态下字模确已消失；若未消失，追加缩放至 0 作双保险。

### 4.4 字模挂载

字模是对应面板实体的**子实体**（`panelEntity.addChild(glyph)`），局部坐标 `(0, 0, +z)`。局部原点即面板中心，**居中对齐由构造保证，不经任何换算**。

取 z 使字模背面与卡面齐平、正面凸出约 1.5cm。凹刻只需 z 取负号，工作量相同；首版按凸起实现，真机观感确认后再定。

### 4.5 已知风险与退路

`chrome` 面板需承载现 `HomeStage` 的全部 2D 内容，其中包括 `ResultPanel` 中**消费指针事件的 scrim 循环**。AGENTS.md 明确标注该段"别当成死代码删掉"——搬运时必须原样保留，否则射线会穿过遮罩点亮下层按钮。

若 Phase 0 证实面板数超限：退回三面板方案（`chrome` / 卡片行 / 运算符行），行内字模偏移用 `convertSize` 计算。该退路的风险已因 §1 发现的公开换算 API 而大幅降低——原先"标定做不准"的顾虑不再成立。

## 5. 分阶段实现

### Phase 0 — 探针（阻塞其余一切）

Volumetric 窗口 + 9 个空 `AttachmentPanel` + 1 个 `createBox` 实体。

验证三项：
1. 9 个面板是否全部渲染。
2. `convertPosition` 换算出的位置与视觉是否一致。
3. 实体在 `z = +0.015 m` 处是否被容器前边界裁剪。

取证：`adb screencap`（`pico-cli capture` 拍不到空间内容）。

### Phase 1 — 静态凸起

14 个字模授作、加载、`clone`、按字高归一化；移除卡面与运算符的 `Text`。

完成判据：截图中能看出厚度与侧面高光。同时量帧率（见 §7 预算）。

### Phase 2 — 选中态弹出 + 转动

ECS 系统驱动缓动。**不得使用 `withFrameNanos`**。

现有 2D 选中态（换底色 + 2dp `interaction` 描边）**保留**——它标明可点区域位置，3D 弹出是强化而非替代。

### Phase 3 — 合并动画

**不改动 `game/` 的规则与时序。** `GameSession` 照旧瞬时完成运算；表现层记录"上一帧牌面 → 当前牌面"的差异，由 ECS 系统播放约 350ms 的补间。

- 动画期间**不阻塞输入**（规则层已算完）。
- 被新操作打断时直接跳至终态。

此设计保住 AGENTS.md 的"规则全在 `game/`，UI 只渲染"架构约束。

### Phase 4 — 答对爆发

`feedback == CORRECT` 时结果字模放大、前冲、淡出。独立于 Phase 3，可单独实现亦可单独砍除。

## 6. 验证

### 6.1 调试启动钩子

新增一个 debug-only 的启动 extra，使应用可直接进入答题态并加载固定题面：

```
am start -e debug_start playing
```

这解决了"模拟器注入点击打不准空间窗口按钮"导致的取证困境——Phase 1–4 的视觉状态无需依赖点击即可呈现。

守卫**必须**用 `BuildConfig.DEBUG`，**不得**用 `Build.TYPE != "user"`：PICO 模拟器的 `Build.TYPE` 就是 `user`，后者在模拟器上永不触发，而模拟器恰是这个钩子唯一的使用场景——写错的话钩子会静默失效，表现得像"实现有 bug"而非"守卫没放行"。release 构建中该分支由 `BuildConfig.DEBUG` 常量折叠消除。

### 6.2 各阶段取证

| 阶段 | 取证方式 |
|---|---|
| Phase 0 | 模拟器 `adb screencap` |
| Phase 1 | 模拟器截图 |
| Phase 2–4 | 补间数学走**单元测试**；视觉只截首末两态 |

动画中间帧不取证。因此"模拟器截图可能是陈旧帧"的问题不构成本规格的风险。

**面板挂载存在渲染滞后，与上面的"陈旧帧"是两回事，Phase 1 起同样适用**：Phase 0 探针实测，`attachments attached=9 expected=9` 日志打出后约 1s 内截的图（`space24-probe-1.png`）只显示 chrome + 4 张卡片 + 探针盒子，运算符行要再等约 1s（第二张截图 `space24-probe-2.png`，+2s）才出现——`content.addEntity` 日志成功不等于该帧已经合成上屏。因此任何截图验证都必须先等日志锚点出现，再额外等待（建议 ≥2s）才截图，并且**不能把早期帧里缺失的元素直接判定为"未实现"**，需要用更晚的一帧或日志锚点交叉确认。

截图一律走 `spatial-design-first-build` 的门禁 B：抢设备锁、限时 90s、完成后立即 `app stop` + 释放锁。owner 用固定字面量。

### 6.3 单元测试

新增纯 Kotlin 测试（无 Android 依赖）：

- 字符串 → 字模序列分解：`"−28561"` → `[op_minus, digit_2, digit_8, digit_5, digit_6, digit_1]`
- 按字高归一化的缩放计算
- 牌数变化时的行居中位置计算
- 超长牌面的整串等比缩小

### 6.4 回归门禁

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest        # 现有 68 个 + 新增，必须全绿
./gradlew assembleDebug
grep -rn "androidx.compose.material\|MaterialTheme" app/src   # 必须为空
```

`game/` 零改动，因此现有 68 个测试全绿本身就是"规则未被表现层污染"的证据。

### 6.5 待真机确认（agent 不验，交用户试玩）

- 凸起量 1.5cm 的观感是否合适，是否应改为凹刻。
- 字模材质在真实环境光下的表现。
- 9 个 `AttachmentPanel` 在真机上的性能表现（模拟器与真机渲染路径不同）。
- Phase 2–4 动画的手感与时长。

真机 `screencap` 直接失败（非黑图），故真机侧无画面证据，只能靠试玩判断。

## 7. 预算与回滚

**实体预算**：最坏情形 4 卡 × 6 字符 + 4 运算符 = 28 个字模实例，外加 14 个 disabled 原型。帧率在 Phase 1 就要量，不要等 Phase 3 动画叠上去才发现。

**资产预算**：14 个 `.usdz`。授作时控制单个字模的三角面数，倒角不宜过细。

**回滚面**：改动集中于 `content/`、`AndroidManifest.xml`、`Main.kt`、`assets/glyphs/`。`game/` 与 `data/` 不动，任一阶段可独立回退。

## 8. 契约与文档改动

`.spatialsdk/design-contract.md` 升 v3，改动项：

- §1 面板清单：单一 planar `WindowContainer` → Volumetric `WindowContainer` + `SpatialView` + 9 个 `AttachmentPanel`。
- §2.3 卡片文字分档规则：三档字号规则 → 字模横排 + 整串等比缩小。
- §2.3 新增卡面与运算符的立体层描述（凸出量、选中态弹出）。
- §3 新增 Phase 2–4 的动态行为描述。

`AGENTS.md` 改动项：

- 删除"900×640 出现在两处必须同时改"一条（`windowConstraints` 已移除）。
- 新增 §1 中列出的已核实事实，特别是 `SpatialViewContent` 即坐标换算器、`attachments.entity()` 必须显式 `addEntity`。
- 更新"已知的验证边界"，补入 debug 启动钩子。
