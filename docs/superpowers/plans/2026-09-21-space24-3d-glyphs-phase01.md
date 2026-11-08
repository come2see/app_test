# Space24 立体字模 Phase 0–1 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Space24 的卡面数值与四个运算符从平面文字换成真实挤出的 3D 字模，并先用一个探针证实所依赖的两条未验证假设。

**Architecture:** 窗口从 planar 改为 Volumetric，`SpatialView` 铺满窗口，全部 2D 内容改由 9 个 `AttachmentPanel` 承载（chrome + 4 卡片 + 4 运算符）。Spatial Editor 授作的 14 个挤出 `.usdc` 字模作为**对应面板实体的子实体**挂在其前方，因此居中对齐由构造保证。面板在米空间中的位置由 `SpatialViewContent` 自带的公开像素↔米换算器从契约的 dp 坐标算出。

**Tech Stack:** Kotlin、Jetpack Compose、PICO Spatial SDK 6.0.0（`com.pico.spatial.core` ECS + `com.pico.spatial.ui.*` SpatialUI）、Spatial Editor（字模授作）、JUnit4（纯 JVM 单测）。

**依据规格：** `docs/superpowers/specs/2026-09-21-space24-3d-glyphs-design.md`

**本计划范围：** 规格中的 Phase 0（探针）与 Phase 1（静态凸起）。Phase 2–4（选中弹出、合并动画、答对爆发）待 Phase 1 落地并由用户在真机试玩后另出计划——Phase 0 若证伪，后续计划需重写，故不提前编写。

## Global Constraints

- **SpatialUI 专用**：所有 2D UI 用 `com.pico.spatial.ui.*` 并包在 `PicoTheme` 内。`androidx.compose.material` 与 `MaterialTheme` 禁止出现；门禁命令见下。
- **`game/` 与 `data/` 零改动**：规则层不得修改。现有 68 个测试（11 个套件）必须保持全绿——这本身就是"规则未被表现层污染"的证据。
- **不得使用 `withFrameNanos`**：它在 `WindowContainer` 内只触发一次。
- **调试守卫必须用 `BuildConfig.DEBUG`**，不得用 `Build.TYPE != "user"`：PICO 模拟器的 `Build.TYPE` 就是 `user`，后者在模拟器上永不触发。
- **每条命令自行 export 环境变量**，环境变量不跨 Bash 调用持续：
  `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"`
- **设备锁脚本用绝对路径**：本分支在 git worktree 里，相对路径 `../.claude/...` 会解析到 `.worktrees/.claude/` 而失效。
- **设备是多会话共享的**：任何装机/截图前先抢锁，完事立刻 `app stop` + `release`。锁的 owner 必须是固定字面量，不得用 `$$` 之类每次调用都变的值。`acquire` 返回 1 是等待超时——报告并跳过，不要死等也不要重试。
- **读设备日志一律加 `grep -a`**：一个控制字节就会让 grep 把 logcat 当二进制静默跳过，零输出会被误判成"应用没打日志"。
- **`Log.d` 在本机 `adb logcat` 里不显示**，临时打点用 `Log.e` / `Log.w` / `Log.i`。
- **包名** `tech.illusion.space24`，Spatial SDK **6.0.0**。
- **契约优先**：`.spatialsdk/design-contract.md` 是视觉唯一基准。本计划所用的一切 dp 数值均取自契约 §2.3，不新造。

### 门禁命令

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest
./gradlew assembleDebug
grep -rn "androidx.compose.material\|MaterialTheme" app/src   # 必须为空
```

### 设备取证模板

```bash
LOCK=/Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh
$LOCK acquire Space24-glyph-probe 120 emulator-5554 || echo "LOCK TIMEOUT — 报告并跳过"
# ... 装机、启动、截图 ...
adb -s emulator-5554 shell am force-stop tech.illusion.space24
$LOCK release Space24-glyph-probe emulator-5554
```

`PICO_LOCK_MAX_WAIT` 默认 300s 会吃光超时预算，抢锁时按需下调。

### 契约几何速查（全部取自契约 §2.3，窗口相对 dp）

- 窗口 900×640dp
- 卡片：104×136dp，间距 16dp，行上沿 y=96 → **行中心 y=164**
- 运算符：56×56dp，间距 16dp，行上沿 y=256 → **行中心 y=284**

---

### Task 1: 板面几何（纯 Kotlin，无设备依赖）

把"N 张卡横排居中"和"4 个运算符横排居中"的坐标算成可测的纯函数。探针与正式实现共用同一份，避免两处各算一遍算出不同结果。

**Files:**
- Create: `app/src/main/java/tech/illusion/space24/content/BoardGeometry.kt`
- Test: `app/src/test/java/tech/illusion/space24/content/BoardGeometryTest.kt`

**Interfaces:**
- Consumes: 无
- Produces:
  - `object BoardGeometry`
  - `const val WINDOW_WIDTH_DP: Float = 900f`
  - `const val WINDOW_HEIGHT_DP: Float = 640f`
  - `const val CARD_WIDTH_DP: Float = 104f`
  - `const val CARD_HEIGHT_DP: Float = 136f`
  - `const val CARD_ROW_CENTER_Y_DP: Float = 164f`
  - `const val OP_SIZE_DP: Float = 56f`
  - `const val OP_ROW_CENTER_Y_DP: Float = 284f`
  - `const val MAX_CARDS: Int = 4`
  - `const val OP_COUNT: Int = 4`
  - `fun cardCenterXDp(index: Int, cardCount: Int): Float`
  - `fun opCenterXDp(index: Int): Float`

- [ ] **Step 1: 写失败的测试**

创建 `app/src/test/java/tech/illusion/space24/content/BoardGeometryTest.kt`：

```kotlin
package tech.illusion.space24.content

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardGeometryTest {

    /** 4 张卡：总宽 4*104 + 3*16 = 464，左边距 (900-464)/2 = 218，首张中心 218+52 = 270，步长 120。 */
    @Test
    fun `four cards are centred with 16dp gaps`() {
        assertEquals(270f, BoardGeometry.cardCenterXDp(0, 4), 0.01f)
        assertEquals(390f, BoardGeometry.cardCenterXDp(1, 4), 0.01f)
        assertEquals(510f, BoardGeometry.cardCenterXDp(2, 4), 0.01f)
        assertEquals(630f, BoardGeometry.cardCenterXDp(3, 4), 0.01f)
    }

    /** 牌数递减时整行重新居中，不是留着原位的空洞。 */
    @Test
    fun `row recentres as cards are consumed`() {
        // 1 张：正中
        assertEquals(450f, BoardGeometry.cardCenterXDp(0, 1), 0.01f)
        // 2 张：总宽 2*104+16 = 224，左边距 338，中心 390 与 510
        assertEquals(390f, BoardGeometry.cardCenterXDp(0, 2), 0.01f)
        assertEquals(510f, BoardGeometry.cardCenterXDp(1, 2), 0.01f)
        // 3 张：总宽 3*104+2*16 = 344，左边距 278，中心 330 / 450 / 570
        assertEquals(330f, BoardGeometry.cardCenterXDp(0, 3), 0.01f)
        assertEquals(450f, BoardGeometry.cardCenterXDp(1, 3), 0.01f)
        assertEquals(570f, BoardGeometry.cardCenterXDp(2, 3), 0.01f)
    }

    /** 每一种牌数下整行都关于窗口中线对称。 */
    @Test
    fun `row is symmetric about the window centre for every card count`() {
        for (count in 1..BoardGeometry.MAX_CARDS) {
            val first = BoardGeometry.cardCenterXDp(0, count)
            val last = BoardGeometry.cardCenterXDp(count - 1, count)
            assertEquals(BoardGeometry.WINDOW_WIDTH_DP, first + last, 0.01f)
        }
    }

    /** 4 个运算符：总宽 4*56 + 3*16 = 272，左边距 314，首个中心 342，步长 72。 */
    @Test
    fun `operators are centred with 16dp gaps`() {
        assertEquals(342f, BoardGeometry.opCenterXDp(0), 0.01f)
        assertEquals(414f, BoardGeometry.opCenterXDp(1), 0.01f)
        assertEquals(486f, BoardGeometry.opCenterXDp(2), 0.01f)
        assertEquals(558f, BoardGeometry.opCenterXDp(3), 0.01f)
    }

    /** 越界索引直接抛，不静默返回一个看似合理的坐标。 */
    @Test(expected = IllegalArgumentException::class)
    fun `card index beyond count is rejected`() {
        BoardGeometry.cardCenterXDp(2, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `card count beyond max is rejected`() {
        BoardGeometry.cardCenterXDp(0, 5)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest --tests '*BoardGeometryTest*'
```

预期：编译失败，`Unresolved reference: BoardGeometry`。

- [ ] **Step 3: 写最小实现**

创建 `app/src/main/java/tech/illusion/space24/content/BoardGeometry.kt`：

```kotlin
package tech.illusion.space24.content

/**
 * 板面元素在**窗口相对 dp** 坐标系里的中心位置，坐标原点是窗口左上角。
 *
 * 数值全部取自设计契约 §2.3，不在此处新造。这些是纯函数，不碰 Android 也不碰 Compose，
 * 因此 Phase 0 的探针和正式实现可以共用同一份——两处各算一遍是错位的常见来源。
 *
 * 米空间的换算不在这里做：那需要运行时的 `SpatialViewContent`，见 `SpatialCoords.kt`。
 */
object BoardGeometry {

    const val WINDOW_WIDTH_DP: Float = 900f
    const val WINDOW_HEIGHT_DP: Float = 640f

    const val CARD_WIDTH_DP: Float = 104f
    const val CARD_HEIGHT_DP: Float = 136f
    const val CARD_GAP_DP: Float = 16f

    /** 契约 §2.3：卡片行上沿在窗口下方 96dp 处，卡高 136 → 中心 96 + 68。 */
    const val CARD_ROW_CENTER_Y_DP: Float = 164f

    const val OP_SIZE_DP: Float = 56f
    const val OP_GAP_DP: Float = 16f

    /** 契约 §2.3：运算符行上沿 256dp，边长 56 → 中心 256 + 28。 */
    const val OP_ROW_CENTER_Y_DP: Float = 284f

    const val MAX_CARDS: Int = 4
    const val OP_COUNT: Int = 4

    /** 一行 [count] 个宽 [itemWidth]、间距 [gap] 的元素里，第 [index] 个的中心 x（整行居中）。 */
    private fun centeredRowX(index: Int, count: Int, itemWidth: Float, gap: Float): Float {
        val totalWidth = count * itemWidth + (count - 1) * gap
        val left = (WINDOW_WIDTH_DP - totalWidth) / 2f
        return left + itemWidth / 2f + index * (itemWidth + gap)
    }

    /**
     * 第 [index] 张卡的中心 x，当前共 [cardCount] 张。
     *
     * 牌数随合并递减时整行**重新居中**——留着原位的空洞会让板面看起来是坏的。
     */
    fun cardCenterXDp(index: Int, cardCount: Int): Float {
        require(cardCount in 1..MAX_CARDS) { "cardCount=$cardCount 超出 1..$MAX_CARDS" }
        require(index in 0 until cardCount) { "index=$index 超出 0..<$cardCount" }
        return centeredRowX(index, cardCount, CARD_WIDTH_DP, CARD_GAP_DP)
    }

    /** 第 [index] 个运算符的中心 x。运算符恒为 4 个，不随牌数变化。 */
    fun opCenterXDp(index: Int): Float {
        require(index in 0 until OP_COUNT) { "index=$index 超出 0..<$OP_COUNT" }
        return centeredRowX(index, OP_COUNT, OP_SIZE_DP, OP_GAP_DP)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest --tests '*BoardGeometryTest*'
```

预期：6 个测试全部 PASS。

- [ ] **Step 5: 跑全量回归**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest
```

预期：原有 68 个 + 新增 6 个全绿。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/tech/illusion/space24/content/BoardGeometry.kt \
        app/src/test/java/tech/illusion/space24/content/BoardGeometryTest.kt
git commit -m "feat: 板面元素的窗口相对 dp 几何，供探针与正式实现共用"
```

---

### Task 2: Phase 0 探针 — Volumetric 容器与 9 个附件面板

回答规格 §1 列出的未验证假设。这是**阻塞任务**：结论决定后续是否需要改走三面板退路。

探针是临时代码，Task 3 会删掉它。

**Files:**
- Modify: `app/build.gradle.kts`（开 `buildConfig`）
- Modify: `app/src/main/AndroidManifest.xml`（planar → Volumetric）
- Modify: `app/src/main/java/tech/illusion/space24/Main.kt`（临时指向探针）
- Create: `app/src/main/java/tech/illusion/space24/content/ProbeStage.kt`（临时，Task 3 删除）
- Modify: `docs/superpowers/specs/2026-09-21-space24-3d-glyphs-design.md`（回填探针结论）

**Interfaces:**
- Consumes: Task 1 的 `BoardGeometry`
- Produces: 无代码产物（探针会被删除）。产出的是写回 spec 的三条结论。

- [ ] **Step 1: 开启 buildConfig**

AGP 8.x 起 `buildConfig` 默认关闭，不开这一行就没有 `BuildConfig` 类，Task 4 的调试守卫无从谈起。

修改 `app/build.gradle.kts` 的 `buildFeatures` 块：

```kotlin
    buildFeatures {
        compose = true
        // AGP 8.x 起 buildConfig 默认关闭，不开这一行就没有 BuildConfig 类。
        // 调试守卫必须用 BuildConfig.DEBUG：PICO 模拟器的 Build.TYPE 是 "user"，
        // 用 Build.TYPE != "user" 做守卫在模拟器上永不触发，而模拟器正是唯一的使用场景。
        buildConfig = true
    }
```

- [ ] **Step 2: manifest 转 Volumetric**

修改 `app/src/main/AndroidManifest.xml` 中 `LaunchActivity` 的 `pico.spatial.windowcontainer.*` meta-data。逐项改为：

| `android:name` | 新 `android:value` |
|---|---|
| `pico.spatial.windowcontainer.style` | `2` |
| `pico.spatial.windowcontainer.defaultsize` | `900x640x480` |
| `pico.spatial.windowcontainer.resizetype` | `0` |
| `pico.spatial.windowcontainer.resizerestriction` | `1` |
| `pico.spatial.windowcontainer.worldscaletype` | `2` |
| `pico.spatial.windowcontainer.materialbackground` | `0` |

并**新增**两条（原文件没有）：

```xml
            <!-- 体积窗口的对齐方式："0" Gravity 侧面随重力，板面不歪 -->
            <meta-data
                android:name="pico.spatial.windowcontainer.volumealignment"
                android:value="0" />

            <!-- 体积窗口的底板："1" None 隐藏容器自带底板，避免与 chrome 面板叠成两层 -->
            <meta-data
                android:name="pico.spatial.windowcontainer.volumebasepanel"
                android:value="1" />
```

`pico.spatial.windowcontainer.id` 的值 `YourPlanarWindowContainer` 保持不变——它只是个标识符，改它没有收益且会引入无谓的差异。

- [ ] **Step 3: 写探针**

创建 `app/src/main/java/tech/illusion/space24/content/ProbeStage.kt`：

```kotlin
package tech.illusion.space24.content

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView

/**
 * Phase 0 探针 —— **临时代码，Task 3 删除**。
 *
 * 只回答三个问题，不实现任何产品功能：
 *  1. 单个 SpatialView 是否支持 9 个 AttachmentPanel（工作区已验证的最大值是 2）。
 *  2. `content.convertPosition` 的换算结果与视觉位置是否一致。
 *  3. 实体在 z=+0.015m 处是否被体积容器的前边界裁掉。
 *
 * 用 Log.w 而不是 Log.d：Log.d 在本机 adb logcat 里不显示。
 */
private const val PROBE_TAG = "Space24Probe"

/** 探针盒子的边长（米）。取 3cm，与字模的量级相当。 */
private const val PROBE_BOX_SIZE_M = 0.03f

/** 规格 §4.4 的字模凸出量，探针用同一个值验证前边界裁剪。 */
private const val PROBE_BOX_Z_M = 0.015f

@Composable
fun ProbeStage() {
    val density = LocalDensity.current.density
    fun px(dp: Float): Int = (dp * density).toInt()

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            AttachmentPanel(
                id = "chrome",
                size = IntSize(px(BoardGeometry.WINDOW_WIDTH_DP), px(BoardGeometry.WINDOW_HEIGHT_DP)),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF101820)),
                    contentAlignment = Alignment.TopCenter,
                ) { Text(text = "chrome 900x640") }
            }
            for (i in 0 until BoardGeometry.MAX_CARDS) {
                AttachmentPanel(
                    id = "card_$i",
                    size = IntSize(px(BoardGeometry.CARD_WIDTH_DP), px(BoardGeometry.CARD_HEIGHT_DP)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF1E6FFF)),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = "C$i") }
                }
            }
            for (i in 0 until BoardGeometry.OP_COUNT) {
                AttachmentPanel(
                    id = "op_$i",
                    size = IntSize(px(BoardGeometry.OP_SIZE_DP), px(BoardGeometry.OP_SIZE_DP)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFFF8A00)),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = "O$i") }
                }
            }
        },
        initial = { content, attachments ->
            // 窗口相对 dp -> 像素 -> 米。ViewCoordinateSpace.Local 的原点是当前 View 的
            // 左上后角；SpatialView 铺满窗口，所以那正是契约 dp 坐标的原点。
            fun anchorOf(xDp: Float, yDp: Float, zMeters: Float): Vector3 {
                val p = content.convertPosition(
                    Vector3(xDp * density, yDp * density, 0f),
                    ViewCoordinateSpace.Local,
                    content.localSpatialCoordinateSpace,
                )
                return Vector3(p.x, p.y, p.z + zMeters)
            }

            var attached = 0
            fun place(id: String, xDp: Float, yDp: Float) {
                val entity = attachments.entity(id)
                if (entity == null) {
                    Log.w(PROBE_TAG, "attachment MISSING id=$id")
                    return
                }
                val pos = anchorOf(xDp, yDp, 0f)
                entity.components[TransformComponent::class.java]?.setPosition(pos)
                // attachments.entity(id) 本身不会把面板加进渲染树（SpaceCube 记录的 2 号坑）。
                content.addEntity(entity)
                attached++
                Log.w(PROBE_TAG, "attached id=$id dp=($xDp,$yDp) meters=(${pos.x},${pos.y},${pos.z})")
            }

            place("chrome", BoardGeometry.WINDOW_WIDTH_DP / 2f, BoardGeometry.WINDOW_HEIGHT_DP / 2f)
            for (i in 0 until BoardGeometry.MAX_CARDS) {
                place(
                    "card_$i",
                    BoardGeometry.cardCenterXDp(i, BoardGeometry.MAX_CARDS),
                    BoardGeometry.CARD_ROW_CENTER_Y_DP,
                )
            }
            for (i in 0 until BoardGeometry.OP_COUNT) {
                place("op_$i", BoardGeometry.opCenterXDp(i), BoardGeometry.OP_ROW_CENTER_Y_DP)
            }
            Log.w(PROBE_TAG, "attachments attached=$attached expected=9")

            // 问题 3：z=+0.015m 的实体是否被前边界裁掉。盒子摆在首张卡正前方，
            // 若可见则说明该深度可用，且顺带给出面板与实体的前后关系。
            val mesh = MeshResource.createBox(
                Vector3(PROBE_BOX_SIZE_M, PROBE_BOX_SIZE_M, PROBE_BOX_SIZE_M),
                cornerRadius = 0f,
            )
            val material = UnlitMaterial.create().apply { setBaseColor(Color4(1f, 0.15f, 0.35f, 1f)) }
            val box: Entity = ModelEntity(mesh, material)
            box.setName("ProbeBox")
            val boxPos = anchorOf(
                BoardGeometry.cardCenterXDp(0, BoardGeometry.MAX_CARDS),
                BoardGeometry.CARD_ROW_CENTER_Y_DP,
                PROBE_BOX_Z_M,
            )
            box.components[TransformComponent::class.java]?.setPosition(boxPos)
            content.addEntity(box)
            Log.w(PROBE_TAG, "probe box at meters=(${boxPos.x},${boxPos.y},${boxPos.z})")
        },
    )
}
```

- [ ] **Step 4: 临时把 Main.kt 指向探针**

修改 `app/src/main/java/tech/illusion/space24/Main.kt`，把 `HomeStage(...)` 一行换成 `ProbeStage()`，并移除 `windowConstraints`（`resizetype=0` 下尺寸来自 manifest）：

```kotlin
package tech.illusion.space24

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import tech.illusion.space24.content.ProbeStage
import tech.illusion.space24.content.cyberColorScheme

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme(colorScheme = cyberColorScheme()) {
                // Phase 0 探针，Task 3 换回 SpatialStage。
                ProbeStage()
            }
        }
    }
```

- [ ] **Step 5: 构建**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew assembleDebug
```

预期：BUILD SUCCESSFUL。若 `SpatialView` / `AttachmentPanel` / `convertPosition` 有签名不符，在此暴露。

- [ ] **Step 6: 装机并取证**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
LOCK=/Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh
PICO_LOCK_MAX_WAIT=90 $LOCK acquire Space24-glyph-probe 120 emulator-5554 || { echo "LOCK TIMEOUT — 报告并跳过本步"; exit 0; }

adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n tech.illusion.space24/.platform.LaunchActivity
# am start 后固定 sleep 会拍到空房间：轮询等日志锚点出现再截图
for i in $(seq 1 30); do
  adb -s emulator-5554 logcat -d -s Space24Probe 2>/dev/null | grep -a "attachments attached=" && break
  sleep 1
done
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-probe-1.png
sleep 2
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-probe-2.png
adb -s emulator-5554 logcat -d -s Space24Probe 2>/dev/null | grep -a . > /tmp/space24-probe.log

adb -s emulator-5554 shell am force-stop tech.illusion.space24
$LOCK release Space24-glyph-probe emulator-5554
```

共享模拟器会串画面，第一张截图可能拍到别的会话的窗口——**所以拍两张**，两张一致才作数。

- [ ] **Step 7: 判读并回填 spec**

读 `/tmp/space24-probe.log` 与两张截图，回答：

1. **面板数**：日志里 `attachments attached=N expected=9`。`N == 9` 且截图中 9 个色块都在 → 通过。有 `attachment MISSING id=...` 行 → 记下缺哪几个。
2. **换算一致性**：截图中 chrome 深色底板应占满窗口，4 个蓝色卡片块在其上方偏上位置横排居中，4 个橙色方块在卡片下方横排居中。若位置明显偏移或全部堆在角落 → `convertPosition` 的语义与假设不符。
3. **前边界裁剪**：首张卡片正前方应有一个红色小方块。看不到 → `z=+0.015m` 被裁，需下调。

把三条结论写进 `docs/superpowers/specs/2026-09-21-space24-3d-glyphs-design.md` 的 §1"以下未经运行时验证"一节，把每条改写为已验证的结论并附证据（日志行或截图判读）。

**若面板数不足 9**：按 spec §4.5 改走三面板退路——`chrome` / 卡片行 / 运算符行三个面板，行内字模偏移用 `BoardGeometry` 的 dp 差值经 `convertSize` 换算。此时 Task 3 起的面板结构随之调整，但 `BoardGeometry` 与后续任务不变。

- [ ] **Step 8: 提交**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/tech/illusion/space24/Main.kt \
        app/src/main/java/tech/illusion/space24/content/ProbeStage.kt \
        docs/superpowers/specs/2026-09-21-space24-3d-glyphs-design.md
git commit -m "feat: Phase 0 探针 — Volumetric 容器与 9 个附件面板的可行性取证"
```

---

### Task 3: SpatialStage 骨架 — 现有 2D 内容搬进附件面板

把 `HomeStage` 的内容拆进 chrome / card / op 三类面板，删除探针。**此任务不引入任何 3D 字模**：卡面和运算符仍是 2D `Text`。这样"搬家"与"换字模"两件事可以分别被否决。

**Files:**
- Create: `app/src/main/java/tech/illusion/space24/content/SpatialCoords.kt`
- Create: `app/src/main/java/tech/illusion/space24/content/SpatialStage.kt`
- Modify: `app/src/main/java/tech/illusion/space24/content/GameBoard.kt`
- Modify: `app/src/main/java/tech/illusion/space24/content/HomeStage.kt`
- Modify: `app/src/main/java/tech/illusion/space24/Main.kt`
- Delete: `app/src/main/java/tech/illusion/space24/content/ProbeStage.kt`

**Interfaces:**
- Consumes: Task 1 的 `BoardGeometry`；Task 2 回填 spec 的探针结论
- Produces:
  - `fun SpatialViewContent.anchorOf(xDp: Float, yDp: Float, density: Float, zMeters: Float): Vector3`
  - `@Composable fun SpatialStage()`
  - `@Composable fun CardFace(card: CardOnBoard, selected: Boolean, hinted: Boolean, showValue: Boolean, onClick: () -> Unit)` —— 单张卡的面板内容；`showValue = false` 时不画 `Text`。调用方传 `!glyphsReady`，所以字模没就位时自动回落到 2D 文字
  - `@Composable fun OperatorFace(op: Op, state: OperatorState, showSymbol: Boolean, onClick: () -> Unit)`
  - `enum class OperatorState { SELECTED, ENABLED, DIMMED }`
  - `@Stable class StageState` 及其只读属性 `session` / `cards` / `selectedCardId` / `hintedCardIds` / `selectedOp` / `phase` 与动作 `onCardTap(Int)` / `onOpTap(Op)`
  - `@Composable fun rememberStageState(): StageState`
  - `@Composable fun ChromeContent(state: StageState)` —— 原 `HomeStage` 的全部 2D 内容，去掉卡片行与运算符行

- [ ] **Step 1: 写坐标换算**

创建 `app/src/main/java/tech/illusion/space24/content/SpatialCoords.kt`：

```kotlin
package tech.illusion.space24.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3

/**
 * 把契约的**窗口相对 dp** 坐标换算成 SpatialView 根实体的米坐标。
 *
 * `SpatialViewContent` 以委托实现了 `SpatialCoordinateSpaceConverter`（SDK 6.0.0 的
 * `container/SpatialViewContent.kt`），所以 `initial` 拿到的 `content` 本身就是换算器，
 * 不需要手工标定 dp→米 的比例。
 *
 * `ViewCoordinateSpace.Local` 的原点是**当前 View 的左上后角**；`SpatialView` 铺满窗口，
 * 因此那正是契约 §2.3 那套窗口相对 dp 偏移的原点，dp 坐标表可以原样沿用。
 *
 * View 销毁后调用会抛 `IllegalStateException`。
 */
fun SpatialViewContent.anchorOf(xDp: Float, yDp: Float, density: Float, zMeters: Float): Vector3 {
    val p = convertPosition(
        Vector3(xDp * density, yDp * density, 0f),
        ViewCoordinateSpace.Local,
        localSpatialCoordinateSpace,
    )
    return Vector3(p.x, p.y, p.z + zMeters)
}
```

- [ ] **Step 2: 从 GameBoard 拆出单元素面板内容**

修改 `app/src/main/java/tech/illusion/space24/content/GameBoard.kt`。

保留文件顶部现有的 `CardTextSize`、`cardTextSizeFor`、`statusTextFor`、`statusColorFor` 不动（它们已有测试覆盖）。

把私有的 `CardTile` 改写为**公开的** `CardFace`，语义变化：不再自带 `size`（面板已经定了尺寸），改为 `fillMaxSize`；新增 `showValue` 开关：

```kotlin
/**
 * 一张卡的面板内容。尺寸由承载它的 AttachmentPanel 决定，所以这里 `fillMaxSize` 而不是
 * 自带 `size` —— 两处都写尺寸会在面板尺寸调整时静默错位。
 *
 * 契约 §3：`selected` 换填色并加 2dp 描边；`hinted` 保持默认填色、加 3dp `interaction` 描边，
 * 两态因此视觉可分。契约还要求 hinted 边框带轻微辉光，未找到可核实的 SpatialUI API，未实现。
 *
 * [showValue] 为 false 时不画数值文字——3D 字模接管之后由字模显示，留着 2D 文字会和字模重影。
 */
@Composable
fun CardFace(
    card: CardOnBoard,
    selected: Boolean,
    hinted: Boolean,
    showValue: Boolean,
    onClick: () -> Unit,
) {
    val face = card.value.toString()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (selected) surfaceAccent() else surfaceElevated())
            .then(
                when {
                    selected -> Modifier.border(2.dp, onSurfacePrimary(), shape)
                    hinted -> Modifier.border(3.dp, PicoTheme.colorScheme.interaction, shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            // 手写 clickable：不像 SpatialUI 的 Button 那样自带 hover 和触感，两者都要显式加。
            .spatialHoverEffect(SpatialHoverStyle.Highlight)
            .controllerHapticFeedback(),
        contentAlignment = Alignment.Center,
    ) {
        if (showValue) {
            Text(
                text = face,
                style = when (cardTextSizeFor(face)) {
                    CardTextSize.LARGE -> PicoTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
                    CardTextSize.MEDIUM -> PicoTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
                    CardTextSize.SMALL -> PicoTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                },
                color = if (selected) onAccent() else onSurfacePrimary(),
            )
        }
    }
}

/** 运算符按钮的三态。契约 §2.3：选中 / 可用 / 未选牌时的禁用。 */
enum class OperatorState { SELECTED, ENABLED, DIMMED }

/**
 * 一个运算符的面板内容。尺寸同样由 AttachmentPanel 决定。
 *
 * [showSymbol] 为 false 时不画符号文字，理由同 [CardFace] 的 `showValue`。
 */
@Composable
fun OperatorFace(op: Op, state: OperatorState, showSymbol: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        enabled = state != OperatorState.DIMMED,
        shape = RoundedCornerShape(16.dp),
        colors = when (state) {
            OperatorState.SELECTED -> primaryButtonColors()
            OperatorState.ENABLED -> secondaryButtonColors()
            OperatorState.DIMMED -> dimmedButtonColors()
        },
    ) {
        if (showSymbol) {
            Text(text = op.symbol, style = PicoTheme.typography.titleSmall)
        }
    }
}
```

删除原 `GameBoard` 可组合函数中的卡片行 `Row` 与运算符行 `Row`（它们搬去了各自的面板），其余部分——状态行、动作按钮行——移入 Task 3 Step 3 的 `ChromeContent`。`GameBoard` 函数本身随之删除。

`Modifier.size` 与 `androidx.compose.foundation.layout.size` 的 import 若变为未使用需手工删除：Kotlin 对未使用的 import **不报 warning**，编译通过和 grep 零输出都证明不了 import 干净。

- [ ] **Step 3: 写 chrome 内容与 SpatialStage**

创建 `app/src/main/java/tech/illusion/space24/content/SpatialStage.kt`。

`ChromeContent` 承载原 `HomeStage` 除卡片行/运算符行之外的一切：开始页、HUD、状态行、动作按钮行、结算浮层。**`ResultPanel` 里消费指针事件的 scrim 循环必须原样保留**——AGENTS.md 标注了"别当成死代码删掉"，它是遮罩挡住下层按钮的唯一机制。

`SpatialStage` 负责：建 9 个面板、在 `initial` 里定位并入树、在 `update` 里随牌数与 `GamePhase` 更新位置与 `enabled`。

```kotlin
package tech.illusion.space24.content

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.ui.foundation.content.SpatialView
import tech.illusion.space24.game.GamePhase
import tech.illusion.space24.game.Op

private const val STAGE_TAG = "Space24Stage"

@Composable
fun SpatialStage() {
    val density = LocalDensity.current.density
    fun px(dp: Float): Int = (dp * density).toInt()

    // 会话与其余应用状态仍由 HomeStage 持有；这里只取它暴露的快照供面板布局使用。
    val state = rememberStageState()

    // 字模是否已全部就位。Task 3 里恒为 false（还没有字模），Task 7 接上真实加载结果。
    // 它同时是降级开关：字模没挂上时卡面自动回落到 2D 文字，而不是空白。
    var glyphsReady by remember { mutableStateOf(false) }

    // 面板实体缓存：initial 只跑一次，update 每次重组都跑，两者通过它通信。
    val panels = remember { mutableMapOf<String, Entity>() }

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            AttachmentPanel(
                id = "chrome",
                size = IntSize(px(BoardGeometry.WINDOW_WIDTH_DP), px(BoardGeometry.WINDOW_HEIGHT_DP)),
            ) { ChromeContent(state) }

            for (i in 0 until BoardGeometry.MAX_CARDS) {
                AttachmentPanel(
                    id = "card_$i",
                    size = IntSize(px(BoardGeometry.CARD_WIDTH_DP), px(BoardGeometry.CARD_HEIGHT_DP)),
                ) {
                    val card = state.cards.getOrNull(i)
                    if (card != null) {
                        CardFace(
                            card = card,
                            selected = card.id == state.selectedCardId,
                            hinted = card.id in state.hintedCardIds,
                            showValue = !glyphsReady,   // Task 7 引入 glyphsReady；本任务里它恒为 false
                            onClick = { state.onCardTap(card.id) },
                        )
                    }
                }
            }

            for (i in 0 until BoardGeometry.OP_COUNT) {
                val op = Op.values()[i]
                AttachmentPanel(
                    id = "op_$i",
                    size = IntSize(px(BoardGeometry.OP_SIZE_DP), px(BoardGeometry.OP_SIZE_DP)),
                ) {
                    OperatorFace(
                        op = op,
                        state = when {
                            op == state.selectedOp -> OperatorState.SELECTED
                            state.selectedCardId != null -> OperatorState.ENABLED
                            else -> OperatorState.DIMMED
                        },
                        showSymbol = !glyphsReady,  // 同上
                        onClick = { state.onOpTap(op) },
                    )
                }
            }
        },
        initial = { content, attachments ->
            var attached = 0
            for (id in panelIds()) {
                val entity = attachments.entity(id)
                if (entity == null) {
                    Log.w(STAGE_TAG, "attachment MISSING id=$id")
                    continue
                }
                // attachments.entity(id) 本身不会把面板加进渲染树（SpaceCube 记录的 2 号坑）。
                content.addEntity(entity)
                panels[id] = entity
                attached++
            }
            Log.w(STAGE_TAG, "attachments attached=$attached expected=${panelIds().size}")
            applyLayout(content, panels, state, density)
        },
        update = { content, _ ->
            applyLayout(content, panels, state, density)
        },
    )
}

/** 9 个面板的 id，顺序固定：chrome、4 张卡、4 个运算符。 */
private fun panelIds(): List<String> =
    listOf("chrome") +
        (0 until BoardGeometry.MAX_CARDS).map { "card_$it" } +
        (0 until BoardGeometry.OP_COUNT).map { "op_$it" }
```

`applyLayout` 与 `rememberStageState` / `ChromeContent` 在 Step 4 定义。

- [ ] **Step 4: 写布局应用与状态桥接**

在同一文件追加：

```kotlin
/**
 * 把面板摆到契约 dp 位置对应的米坐标上，并按游戏状态设 `enabled`。
 *
 * 每次重组都会跑一遍——牌数递减时整行要重新居中，所以位置不能只在 initial 算一次。
 */
private fun applyLayout(
    content: com.pico.spatial.core.container.SpatialViewContent,
    panels: Map<String, Entity>,
    state: StageState,
    density: Float,
) {
    val cardCount = state.cards.size
    val boardVisible = state.phase == GamePhase.PLAYING

    panels["chrome"]?.let { chrome ->
        chrome.enabled = true
        chrome.components[TransformComponent::class.java]?.setPosition(
            content.anchorOf(
                BoardGeometry.WINDOW_WIDTH_DP / 2f,
                BoardGeometry.WINDOW_HEIGHT_DP / 2f,
                density,
                0f,
            )
        )
    }

    for (i in 0 until BoardGeometry.MAX_CARDS) {
        val entity = panels["card_$i"] ?: continue
        val visible = boardVisible && i < cardCount
        entity.enabled = visible
        if (!visible) continue
        entity.components[TransformComponent::class.java]?.setPosition(
            content.anchorOf(
                BoardGeometry.cardCenterXDp(i, cardCount),
                BoardGeometry.CARD_ROW_CENTER_Y_DP,
                density,
                0f,
            )
        )
    }

    for (i in 0 until BoardGeometry.OP_COUNT) {
        val entity = panels["op_$i"] ?: continue
        entity.enabled = boardVisible
        if (!boardVisible) continue
        entity.components[TransformComponent::class.java]?.setPosition(
            content.anchorOf(
                BoardGeometry.opCenterXDp(i),
                BoardGeometry.OP_ROW_CENTER_Y_DP,
                density,
                0f,
            )
        )
    }
}
```

`boardVisible` 只在 `PLAYING` 为真，因此 `NOT_STARTED`（开始页）与 `RESULT`（结算浮层）两态下卡片与运算符面板都关闭。**`RESULT` 态的关闭是必须的，不是优化**：结算浮层画在 z=0 的 chrome 面板上，卡片面板同在 z=0，Task 7 挂上字模后字模在 z=+0.015m 会穿过浮层浮在最前，连 scrim 一起盖掉。

`StageState` 是把 `HomeStage` 现有状态打包给面板用的只读视图 + 动作回调，`rememberStageState()` 与 `ChromeContent(state)` 由 `HomeStage.kt` 提供（Step 5）。

- [ ] **Step 5: 改造 HomeStage**

修改 `app/src/main/java/tech/illusion/space24/content/HomeStage.kt`。**保留全部现有状态逻辑不动**（`bank` / `session` / 计时 `LaunchedEffect` / 最佳分写入）——这些都已在跑，别重写。把原 `HomeStage` 函数体里 `Box` 之前的所有状态代码原样搬进 `rememberStageState()`，`Box` 内容搬进 `ChromeContent`。

在文件中新增：

```kotlin
/**
 * 面板内容读取游戏状态的统一入口。
 *
 * 全部属性都是 `get()` 委托到 [GameSession] 的 snapshot-backed 属性——读发生在调用点，
 * 所以重组追踪照常工作。**这里不新增任何会话属性**：`GameSession` 标了 `@Stable`，
 * 新增的公开属性若不是 snapshot-backed，那个注解就变成谎言。
 */
@Stable
class StageState(
    val session: GameSession?,
    val selectedDifficulty: Difficulty,
    val onSelectDifficulty: (Difficulty) -> Unit,
    val bestScore: Int,
    val isNewRecord: Boolean,
    val onStart: () -> Unit,
    val onChangeDifficulty: () -> Unit,
    val onRestart: () -> Unit,
) {
    val phase: GamePhase get() = session?.phase ?: GamePhase.NOT_STARTED
    val cards: List<CardOnBoard> get() = session?.cards ?: emptyList()
    val selectedCardId: Int? get() = session?.selectedCardId
    val hintedCardIds: Set<Int> get() = session?.hintedCardIds ?: emptySet()
    val selectedOp: Op? get() = session?.selectedOp
    val feedback: FeedbackKind get() = session?.feedback ?: FeedbackKind.NONE

    /** 题库尚未就绪时 session 为 null，开始页据此显示"题库准备中"。 */
    val bankReady: Boolean get() = session != null

    fun onCardTap(id: Int) { session?.onCardTap(id) }
    fun onOpTap(op: Op) { session?.onOpTap(op) }
}

/**
 * 持有会话与其余应用状态。函数体是原 `HomeStage` 中 `Box` 之前的全部代码，原样搬来，
 * 只是末尾改为返回 [StageState] 而不是直接画 UI。
 */
@Composable
fun rememberStageState(): StageState {
    val context = LocalContext.current

    // 题库在玩家停留在开始页期间于主线程外预生成一次。
    var bank by remember { mutableStateOf<PuzzleBank?>(null) }
    LaunchedEffect(Unit) {
        bank = withContext(Dispatchers.Default) { PuzzleBank.build() }
    }
    // elapsedRealtime 单调且设备睡眠时继续走，不像 currentTimeMillis 会被 NTP 或用户改钟跳变。
    val session = remember(bank) { bank?.let { GameSession(it, nowMillis = SystemClock::elapsedRealtime) } }

    var selectedDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var bestScore by remember(selectedDifficulty) {
        mutableStateOf(BestScoreStore.readBestScore(context, selectedDifficulty))
    }
    var isNewRecord by remember { mutableStateOf(false) }
    var recordWrittenForThisRound by remember { mutableStateOf(false) }

    // 每段 PLAYING 一个 ticker：刷新时钟、让 GameSession 结束回合，
    // 然后在回合落到 RESULT 时恰好写一次最佳分。
    LaunchedEffect(session, session?.phase) {
        val active = session ?: return@LaunchedEffect
        while (active.phase == GamePhase.PLAYING) {
            delay(100)
            active.onTick()
        }
        if (active.phase == GamePhase.RESULT && !recordWrittenForThisRound) {
            recordWrittenForThisRound = true
            val candidate = active.score
            val updated = BestScoreStore.writeBestScoreIfBetter(context, active.currentDifficulty, candidate)
            isNewRecord = updated
            if (updated) bestScore = candidate
        }
    }

    return StageState(
        session = session,
        selectedDifficulty = selectedDifficulty,
        onSelectDifficulty = { selectedDifficulty = it },
        bestScore = bestScore,
        isNewRecord = isNewRecord,
        onStart = {
            isNewRecord = false
            recordWrittenForThisRound = false
            session?.startNewGame(selectedDifficulty)
        },
        onChangeDifficulty = {
            session?.let {
                selectedDifficulty = it.currentDifficulty
                it.returnToStart()
            }
        },
        onRestart = {
            isNewRecord = false
            recordWrittenForThisRound = false
            session?.let { it.startNewGame(it.currentDifficulty) }
        },
    )
}

/**
 * chrome 面板的内容：开始页、HUD、状态行、动作按钮行、结算浮层。
 *
 * **卡片行与运算符行不在这里**——它们各自是独立的 AttachmentPanel。
 *
 * `ResultPanel` 里消费指针事件的 scrim 循环必须原样保留：`background` 不装 pointer node，
 * Compose 命中测试按位置走，删掉它射线会穿过遮罩点亮下面的按钮（连触感一起）。
 */
@Composable
fun ChromeContent(state: StageState) {
    // 整个窗口画一层不透明底色。除 fillPrimary 外的 PicoTheme fill 角色都是要压在窗口玻璃上
    // 的 alpha 色调，没有这层，上面的着色表面会透，内容在层间互相渗。
    Box(modifier = Modifier.fillMaxSize().background(surfaceBase())) {
        val active = state.session
        if (active == null || state.phase == GamePhase.NOT_STARTED) {
            StartPage(
                selectedDifficulty = state.selectedDifficulty,
                onSelectDifficulty = state.onSelectDifficulty,
                bestScore = state.bestScore,
                bankReady = state.bankReady,
                onStart = state.onStart,
            )
        } else {
            BoardChrome(state)
            Hud(
                remainingMillis = active.remainingMillis,
                score = active.score,
                difficultyLabel = active.currentDifficulty.label(),
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (state.phase == GamePhase.RESULT) {
                ResultPanel(
                    score = active.score,
                    bestScore = state.bestScore,
                    isNewRecord = state.isNewRecord,
                    difficultyLabel = active.currentDifficulty.label(),
                    missedPuzzles = active.missedPuzzles,
                    onChangeDifficulty = state.onChangeDifficulty,
                    onRestart = state.onRestart,
                )
            }
        }
    }
}

/**
 * 原 `GameBoard` 中留在 chrome 上的部分：状态行与动作按钮行。
 *
 * 卡片行与运算符行已搬去各自的面板，所以这里用等高的 `Spacer` 占住它们原来的竖直位置，
 * 让状态行仍落在契约 §2.3 的 328dp 处——契约的偏移是窗口相对的，不能因为少画了两行就上移。
 */
@Composable
private fun BoardChrome(state: StageState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 96.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 卡片行的竖直占位（136dp）+ 契约的 56dp 间隔
        Spacer(Modifier.height(BoardGeometry.CARD_HEIGHT_DP.dp))
        Spacer(Modifier.height(56.dp))
        // 运算符行的竖直占位（56dp）+ 契约的 16dp 间隔
        Spacer(Modifier.height(BoardGeometry.OP_SIZE_DP.dp))
        Spacer(Modifier.height(16.dp))

        // 预留高度，无消息时不塌陷、不移动下方布局。
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            statusTextFor(state.feedback)?.let { status ->
                Text(
                    text = status,
                    style = PicoTheme.typography.titleSmall,
                    color = statusColorFor(state.feedback),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        val session = state.session ?: return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val actionShape = RoundedCornerShape(999.dp)
            val actionPadding = PaddingValues(vertical = 10.dp, horizontal = 20.dp)
            Button(
                onClick = { session.onUndo() },
                colors = secondaryButtonColors(),
                shape = actionShape,
                contentPadding = actionPadding,
            ) { Text(text = "撤销", style = PicoTheme.typography.bodySmall) }
            Button(
                onClick = { session.onResetPuzzle() },
                colors = secondaryButtonColors(),
                shape = actionShape,
                contentPadding = actionPadding,
            ) { Text(text = "重置本题", style = PicoTheme.typography.bodySmall) }
            Button(
                onClick = { session.onHint() },
                colors = secondaryButtonColors(),
                shape = actionShape,
                contentPadding = actionPadding,
            ) { Text(text = "提示 −10s", style = PicoTheme.typography.bodySmall) }
            Button(
                onClick = { session.onSkip() },
                colors = secondaryButtonColors(),
                shape = actionShape,
                contentPadding = actionPadding,
            ) { Text(text = "换一题 −10s", style = PicoTheme.typography.bodySmall) }
        }
    }
}
```

原 `HomeStage` 可组合函数删除——它的职责已完整拆进 `rememberStageState` 与 `ChromeContent`，留着会是第二份会腐烂的状态逻辑。`Main.kt` 已改为调用 `SpatialStage`（Step 6），无人再引用它。

- [ ] **Step 6: 切回 Main.kt 并删除探针**

```bash
rm app/src/main/java/tech/illusion/space24/content/ProbeStage.kt
```

修改 `Main.kt`，把 `ProbeStage()` 换成 `SpatialStage()`，import 同步改为 `tech.illusion.space24.content.SpatialStage`。

- [ ] **Step 7: 构建并跑回归**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest
./gradlew assembleDebug
grep -rn "androidx.compose.material\|MaterialTheme" app/src
```

预期：测试全绿（原 68 + Task 1 的 6）；构建成功；grep 零输出。

注意 `ResultPanelTest` / `GameBoardTest` / `FormattingTest` 引用的符号若因拆分而改名，必须同步更新测试而**不是**删掉断言。

- [ ] **Step 8: 设备验证 —— 搬家没搞坏任何东西**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
LOCK=/Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh
PICO_LOCK_MAX_WAIT=90 $LOCK acquire Space24-stage-skeleton 120 emulator-5554 || { echo "LOCK TIMEOUT — 报告并跳过"; exit 0; }
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n tech.illusion.space24/.platform.LaunchActivity
for i in $(seq 1 30); do
  adb -s emulator-5554 logcat -d -s Space24Stage 2>/dev/null | grep -a "attachments attached=" && break
  sleep 1
done
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-skeleton-1.png
sleep 2
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-skeleton-2.png
adb -s emulator-5554 shell am force-stop tech.illusion.space24
$LOCK release Space24-stage-skeleton emulator-5554
```

判读：开始页应与改造前**视觉一致**（难度分段、最佳分、开始按钮、右上角玩法入口）。日志 `attached=9`。

- [ ] **Step 9: 提交**

```bash
git add -A app/src app/build.gradle.kts
git commit -m "refactor: 2D 内容搬进 9 个附件面板，删除 Phase 0 探针"
```

---

### Task 4: debug 启动钩子

让后续每个任务都能不依赖点击就进入答题态取证——模拟器的注入点击打不准空间窗口按钮，这是 Space24 已记录的验证边界。

**Files:**
- Create: `app/src/main/java/tech/illusion/space24/content/DebugLaunch.kt`
- Modify: `app/src/main/java/tech/illusion/space24/content/HomeStage.kt`

**Interfaces:**
- Consumes: Task 3 的 `HomeStage` 会话管理
- Produces:
  - `@Composable fun rememberDebugStartSeed(): Long?` —— debug 构建下读到 `-e debug_start playing` 时返回固定种子，否则返回 null

- [ ] **Step 1: 写读取逻辑**

创建 `app/src/main/java/tech/illusion/space24/content/DebugLaunch.kt`：

```kotlin
package tech.illusion.space24.content

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import tech.illusion.space24.BuildConfig

/** 固定种子：同一颗种子每次发出同一副牌，截图之间可比。 */
private const val DEBUG_PUZZLE_SEED = 24L

/**
 * 把 Compose 的 [Context] 一路解包到宿主 Activity。
 *
 * `LocalContext.current` 在 `SpatialView` 的内容里**不保证**是裸 Activity（和 Android 别处一样
 * 可能是包装过的），所以要走 [ContextWrapper] 链，不能直接强转。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * debug 构建下，若启动 intent 带了 `-e debug_start playing`，返回固定的题面种子；否则 null。
 *
 * 用途：模拟器的注入点击打不准空间窗口按钮，没有这个钩子就进不了答题页，Phase 1 之后的
 * 视觉全部无从取证。
 *
 * 守卫用 [BuildConfig.DEBUG]，**不用** `Build.TYPE != "user"`：PICO 模拟器的 `Build.TYPE`
 * 就是 `"user"`，后者在模拟器上永不触发，而模拟器正是这个钩子唯一的使用场景——写错的话
 * 钩子会静默失效，看起来像实现有 bug 而不是守卫没放行。
 *
 * release 构建里 `BuildConfig.DEBUG` 是编译期常量 false，整个分支被常量折叠消除。
 */
@Composable
fun rememberDebugStartSeed(): Long? {
    if (!BuildConfig.DEBUG) return null
    val context = LocalContext.current
    return remember(context) {
        val extra = context.findActivity()?.intent?.getStringExtra("debug_start")
        if (extra == "playing") DEBUG_PUZZLE_SEED else null
    }
}
```

- [ ] **Step 2: 在 HomeStage 接上**

修改 `HomeStage.kt`：

- 顶部取 `val debugSeed = rememberDebugStartSeed()`。
- 构造 `GameSession` 时，种子非空则传 `random = kotlin.random.Random(debugSeed)`；`GameSession` 的构造参数已有 `random: Random = Random.Default`，**无需改动 `game/`**。
- 新增一个 `LaunchedEffect(session, debugSeed)`：当 `session != null && debugSeed != null && session.phase == GamePhase.NOT_STARTED` 时调用 `session.startNewGame(selectedDifficulty)`。

用 `LaunchedEffect` 观察而不是在构造处直接调用，是因为题库 `PuzzleBank.build()` 是异步的，`session` 一开始为 null；effect 在 `session` 就绪后自然触发，不需要关心先后顺序。

- [ ] **Step 3: 构建并验证钩子生效**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew assembleDebug
LOCK=/Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh
PICO_LOCK_MAX_WAIT=90 $LOCK acquire Space24-debug-hook 120 emulator-5554 || { echo "LOCK TIMEOUT — 报告并跳过"; exit 0; }
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n tech.illusion.space24/.platform.LaunchActivity -e debug_start playing
# 题库 JVM 上 122ms，真机/模拟器启动后约 9 秒内就绪
sleep 12
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-hook-1.png
sleep 2
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-hook-2.png
adb -s emulator-5554 shell am force-stop tech.illusion.space24
$LOCK release Space24-debug-hook emulator-5554
```

判读：截图应直接是答题页（HUD 计时器在走、4 张卡、4 个运算符、动作按钮行），而**不是**开始页。

再验一次不带 extra 启动仍是开始页——否则这个钩子会污染正常启动路径。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/tech/illusion/space24/content/DebugLaunch.kt \
        app/src/main/java/tech/illusion/space24/content/HomeStage.kt
git commit -m "feat: debug 启动钩子，直接进入固定题面的答题态以便取证"
```

---

### Task 5: 字模分解与归一化（纯 Kotlin，无设备依赖）

**Files:**
- Create: `app/src/main/java/tech/illusion/space24/content/GlyphCatalog.kt`
- Test: `app/src/test/java/tech/illusion/space24/content/GlyphCatalogTest.kt`

**Interfaces:**
- Consumes: 无
- Produces:
  - `enum class Glyph(val assetName: String)` —— `D0`…`D9`、`PLUS`、`MINUS`、`TIMES`、`DIVIDE`
  - `object GlyphCatalog`
  - `fun decompose(text: String): List<Glyph>`
  - `fun glyphFor(op: Op): Glyph`
  - `data class GlyphLayout(val scale: Float, val offsetsX: List<Float>)`
  - `fun layout(glyphWidths: List<Float>, capHeight: Float, targetHeight: Float, maxWidth: Float, tracking: Float): GlyphLayout`

- [ ] **Step 1: 写失败的测试**

创建 `app/src/test/java/tech/illusion/space24/content/GlyphCatalogTest.kt`：

```kotlin
package tech.illusion.space24.content

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.illusion.space24.game.Op

class GlyphCatalogTest {

    @Test
    fun `positive value decomposes digit by digit`() {
        assertEquals(listOf(Glyph.D7), GlyphCatalog.decompose("7"))
        assertEquals(listOf(Glyph.D2, Glyph.D4), GlyphCatalog.decompose("24"))
    }

    /**
     * 负号复用运算符的减号字模，不单独出资产。牌面理论极值 −28561 是 6 字符。
     * Int.toString() 产生的是 ASCII 连字符 '-'，不是 U+2212，两者都要认。
     */
    @Test
    fun `negative value uses the operator minus glyph`() {
        val expected = listOf(Glyph.MINUS, Glyph.D2, Glyph.D8, Glyph.D5, Glyph.D6, Glyph.D1)
        assertEquals(expected, GlyphCatalog.decompose("-28561"))
        assertEquals(expected, GlyphCatalog.decompose("−28561"))
    }

    @Test
    fun `every operator maps to a glyph`() {
        for (op in Op.values()) {
            // 不抛异常即可；具体映射由下一个测试锁定
            GlyphCatalog.glyphFor(op)
        }
        assertEquals(Glyph.MINUS, GlyphCatalog.glyphFor(Op.values().first { it.symbol == "−" }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unmappable character is rejected loudly`() {
        GlyphCatalog.decompose("7a")
    }

    /** 字高归一化：按目标字高求缩放，不按字宽——按宽会把窄的 1 拉胖。 */
    @Test
    fun `scale is derived from cap height not width`() {
        val layout = GlyphCatalog.layout(
            glyphWidths = listOf(20f, 60f),
            capHeight = 100f,
            targetHeight = 50f,
            maxWidth = 1000f,
            tracking = 4f,
        )
        assertEquals(0.5f, layout.scale, 1e-4f)
    }

    /** 横向按各字实际宽度依次排布，整串关于原点居中。 */
    @Test
    fun `glyphs are laid out proportionally and centred on the origin`() {
        val layout = GlyphCatalog.layout(
            glyphWidths = listOf(20f, 60f),
            capHeight = 100f,
            targetHeight = 100f,   // scale = 1
            maxWidth = 1000f,
            tracking = 0f,
        )
        // 总宽 80，整串居中跨 [-40,+40]：
        //   宽 20 的占 [-40,-20]，中心 -30
        //   宽 60 的占 [-20,+40]，中心 +10
        // 两个中心不对称，正是比例字距的体现——等宽排布才会对称。
        assertEquals(-30f, layout.offsetsX[0], 1e-4f)
        assertEquals(10f, layout.offsetsX[1], 1e-4f)
    }

    /** 整串超过卡宽时等比缩小，这替代了旧的"按字符数切三档字号"规则。 */
    @Test
    fun `overlong string is scaled down to fit`() {
        val widths = List(6) { 30f }   // scale=1 时总宽 180
        val layout = GlyphCatalog.layout(
            glyphWidths = widths,
            capHeight = 100f,
            targetHeight = 100f,
            maxWidth = 90f,          // 只有一半放得下
            tracking = 0f,
        )
        assertEquals(0.5f, layout.scale, 1e-4f)
        // 缩放后总宽 90，首字中心 -37.5
        assertEquals(-37.5f, layout.offsetsX[0], 1e-4f)
    }

    @Test
    fun `single glyph sits on the origin`() {
        val layout = GlyphCatalog.layout(
            glyphWidths = listOf(40f),
            capHeight = 100f,
            targetHeight = 100f,
            maxWidth = 1000f,
            tracking = 6f,
        )
        assertEquals(0f, layout.offsetsX[0], 1e-4f)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest --tests '*GlyphCatalogTest*'
```

预期：编译失败，`Unresolved reference: Glyph`。

- [ ] **Step 3: 写实现**

创建 `app/src/main/java/tech/illusion/space24/content/GlyphCatalog.kt`：

```kotlin
package tech.illusion.space24.content

import tech.illusion.space24.game.Op

/**
 * 一个立体字模资产。[assetName] 是 `app/src/main/assets/glyphs/` 下的文件名（不含扩展名）。
 *
 * 负数的减号复用 [MINUS]，不单独出资产——字形一致，多一个资产只是多一份体积。
 */
enum class Glyph(val assetName: String) {
    D0("digit_0"), D1("digit_1"), D2("digit_2"), D3("digit_3"), D4("digit_4"),
    D5("digit_5"), D6("digit_6"), D7("digit_7"), D8("digit_8"), D9("digit_9"),
    PLUS("op_plus"), MINUS("op_minus"), TIMES("op_times"), DIVIDE("op_divide"),
}

/**
 * 一串字模的排布结果：统一缩放系数，以及各字相对串中心的横向偏移。
 *
 * 偏移与入参**同单位**——这个函数是纯比例运算，不关心单位是 dp 还是米。调用方传米就得米。
 */
data class GlyphLayout(val scale: Float, val offsetsX: List<Float>)

/**
 * 牌面/运算符文本与立体字模之间的映射与排布。纯函数，不碰 Android，可在 JVM 上直接测。
 */
object GlyphCatalog {

    /**
     * 把一段牌面文本拆成字模序列。
     *
     * ASCII 连字符与 U+2212 减号都映射到 [Glyph.MINUS]：`Int.toString()` 给的是前者，
     * 契约文案里用的是后者。认不出的字符直接抛——静默跳过会让卡面少一位数字而毫无提示。
     */
    fun decompose(text: String): List<Glyph> = text.map { ch ->
        when (ch) {
            '0' -> Glyph.D0; '1' -> Glyph.D1; '2' -> Glyph.D2; '3' -> Glyph.D3; '4' -> Glyph.D4
            '5' -> Glyph.D5; '6' -> Glyph.D6; '7' -> Glyph.D7; '8' -> Glyph.D8; '9' -> Glyph.D9
            '-', '−' -> Glyph.MINUS
            else -> throw IllegalArgumentException("无法映射为字模的字符: '$ch' (U+%04X)".format(ch.code))
        }
    }

    /** 运算符到字模。按 [Op.symbol] 匹配，避免依赖枚举声明顺序。 */
    fun glyphFor(op: Op): Glyph = when (op.symbol) {
        "+" -> Glyph.PLUS
        "−", "-" -> Glyph.MINUS
        "×", "*" -> Glyph.TIMES
        "÷", "/" -> Glyph.DIVIDE
        else -> throw IllegalArgumentException("无法映射为字模的运算符: ${op.symbol}")
    }

    /**
     * 算出一串字模的统一缩放与各字横向偏移。
     *
     * @param glyphWidths 各字模在**模型原始单位**下的宽度，由运行时 `getVisualBounds` 量得
     * @param capHeight 字模在模型原始单位下的字高（各字统一，取自授作规范）
     * @param targetHeight 期望的显示字高，**单位由调用方决定**（挂进 3D 场景时是米）
     * @param maxWidth 可用宽度，与 [targetHeight] 同单位；整串超出时在字高缩放之上再等比缩小
     * @param tracking 字距，与 [targetHeight] 同单位
     *
     * 缩放按**字高**而非字宽求：按宽会让窄的 `1` 被拉到和 `8` 一样宽。
     */
    fun layout(
        glyphWidths: List<Float>,
        capHeight: Float,
        targetHeight: Float,
        maxWidth: Float,
        tracking: Float,
    ): GlyphLayout {
        require(glyphWidths.isNotEmpty()) { "glyphWidths 不能为空" }
        require(capHeight > 0f) { "capHeight 必须为正，实际 $capHeight" }

        val heightScale = targetHeight / capHeight
        val scaledWidths = glyphWidths.map { it * heightScale }
        val totalTracking = tracking * (glyphWidths.size - 1)
        val naturalWidth = scaledWidths.sum() + totalTracking

        // 超宽时整串再等比缩小。这替代了契约旧版"按字符数切三档字号"的规则。
        val fitScale = if (naturalWidth > maxWidth) maxWidth / naturalWidth else 1f
        val scale = heightScale * fitScale

        val finalWidths = glyphWidths.map { it * scale }
        val finalTracking = tracking * fitScale
        val totalWidth = finalWidths.sum() + finalTracking * (finalWidths.size - 1)

        var cursor = -totalWidth / 2f
        val offsets = finalWidths.map { w ->
            val centre = cursor + w / 2f
            cursor += w + finalTracking
            centre
        }
        return GlyphLayout(scale = scale, offsetsX = offsets)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest --tests '*GlyphCatalogTest*'
```

预期：8 个测试全 PASS。若 `Op.symbol` 的实际取值与 `glyphFor` 的分支不符，`every operator maps to a glyph` 会抛——按 `game/Op.kt` 的真实取值修正 `glyphFor`，**不要**改 `Op`。

- [ ] **Step 5: 跑全量回归并提交**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest
git add app/src/main/java/tech/illusion/space24/content/GlyphCatalog.kt \
        app/src/test/java/tech/illusion/space24/content/GlyphCatalogTest.kt
git commit -m "feat: 字模分解与按字高归一化的排布计算"
```

---

### Task 6: 字模资产授作（Blender 无头）

授作 14 个挤出字模。这是本计划唯一的非代码任务。

**为什么不用 Spatial Editor（CLAUDE.md 例外 2，附运行时证据）**

CLAUDE.md 规定 3D 内容生产默认走 Spatial Editor，只有"运行时能力检查表明它无法满足"才可绕开，且必须记录证据。证据已取：

- `get_editor_capabilities` 返回的完整工具表里**没有任何文字/字形/挤出能力**——只有 AI 生成、资产导入、材质、打包、截图。
- `get_spatial_editor_docs` 的图元清单只有 `Cube / Sphere / Plane / Cylinder / Cone / Capsule`。

在编辑器里造数字只剩两条路：拿 Cube 拼（即设计阶段已被用户否决的七段管方案），或 `generate_3d_model` AI 生成（14 个数字字形各不相同，且其策略明确禁止静默回退到图元重建）。两条都不满足"真实字体轮廓挤出"这个已确认的需求。

**替代工具已实测可行**（不是推测）：Blender 3.6.5 装在 `/Applications/Blender.app`，无头可用，`bpy.ops.object.text_add` 与 `bpy.ops.wm.usd_export` 都在。概念验证已产出带倒角的挤出字模并导出 `.usdc`。

**不需要 usdz 打包**：`Entity.loadSuspend` 接受 `usdz/usda/usdc/glb/gltf/obj`（`core-6.0.0-sources.jar` 的 `Entity.kt:2584-2592`），直接用 Blender 导出的 `.usdc`。注意本机 usdpython 不可用（x86_64，无 Rosetta），`usdcat` 直接 `bad CPU type`；且 `import pxr` 是**假阳性**（纯 Python 命名空间包），要 `from pxr import Usd` 才暴露。所以不要试图用 USD 命令行工具做任何校验。

**Files:**
- Create: `app/src/main/assets/glyphs/digit_0.usdc` … `digit_9.usdc`
- Create: `app/src/main/assets/glyphs/op_plus.usdc`、`op_minus.usdc`、`op_times.usdc`、`op_divide.usdc`
- Create: `docs/superpowers/notes/2026-09-21-glyph-authoring.md`（授作参数记录，供日后复现）

**Interfaces:**
- Consumes: Task 5 的 `Glyph.assetName`（文件名必须与之逐一对应）
- Produces: 14 个 `.usdc`，各自满足下列规范

- [ ] **Step 1: 用 Blender 无头脚本批量授作**

授作参数（字体与分辨率已由 controller 实测确定，不要改动，除非 Step 2 的目视检查发现问题）：

| 项 | 值 | 依据 |
|---|---|---|
| 字体 | `/System/Library/Fonts/SFNSMono.ttf` | 用户选定。Blender 载入后报名为 `.SF NS Mono Light` |
| `align_x` / `align_y` | `CENTER` / `CENTER` | pivot 居中 |
| `extrude` | `0.06` | 前后各 0.06，总厚 0.12 ≈ 字高的 12% |
| `bevel_depth` | `0.012` | **倒角不是装饰**：没有它高光无处可抓，头动时看不出立体感 |
| `bevel_resolution` | `1` | 见下方三角预算 |
| `resolution_u` | `4` | 见下方三角预算 |

导出：`bpy.ops.wm.usd_export`，每个字模一个 `.usdc`。

**三角预算（controller 实测，按 `resolution_u`/`bevel_resolution` 组合）**：

| res_u / bevel_res | "8" 三角数 | 0/1/7/8 合计 |
|---|---|---|
| 12 / 2（Blender 默认） | 6948 | 10298 |
| 6 / 1 | 2620 | 4010 |
| **4 / 1（选定）** | **1756** | **2782** |
| 3 / 0 | 884 | 1436 |

选 4/1 的理由：最坏情况（4 张卡各 6 字符）约 24 个卡面字模，按 "8" 的 1756 算约 4.2 万三角，对独立头显可接受，且保留圆倒角。默认的 12/2 会到 16 万三角，不可用。

**字高不完全统一，这是预期的**：实测 `8`=0.5064、`0`=0.5068、`1`=0.4853、`−`=0.0611（减号本就是一根短横）。运行时 `GlyphLoader.capHeight()` 取各字模包围盒高度的**最大值**，所以圆形数字的轻微 overshoot 会定义字高，其余略矮——这正是真实排版的行为。减号按同一比例缩放，保持其真实比例。

**pivot 用各字自身包围盒中心**（`origin_set(type='ORIGIN_GEOMETRY', center='BOUNDS')`），不是基线对齐。对只有 cap-height、无降部的数字来说两者差异在 1% 以内；而对减号，包围盒居中恰好让它落在数字行的垂直中线上，是正确的。

材质：在 Blender 里给一个 Principled BSDF，基色对齐 cyber 主题的 `labelPrimary`。选中态由运行时改材质参数表达，**不出第二套资产**。材质观感的最终调整属于 Task 7 的设备验证范围，本任务只要保证导出的 `.usdc` 带一个可见的 PBR 材质。

- [ ] **Step 2: 在编辑器里验字模确实立体**

Spatial Editor 的视口时间**可能整个冻结**，导致时间驱动的效果全被测成"没变化"。验字模之前先拿一个已知在动的东西做阳性对照，确认视口在走；否则会把"视口冻了"误判成"字模有问题"。

对每个字模确认：从斜侧看有可见的侧壁与倒角高光，不是一块贴了图的平板。

- [ ] **Step 3: 导出到 assets 并记录参数**

导出 14 个 `.usdc` 到 `app/src/main/assets/glyphs/`，文件名严格匹配 `Glyph.assetName`（`digit_0` … `digit_9`、`op_plus`、`op_minus`、`op_times`、`op_divide`）。

创建 `docs/superpowers/notes/2026-09-21-glyph-authoring.md`，记录：所用字体、字高原始单位值、挤出深度、倒角半径、材质基色、各字模的三角面数。字高原始值是 `GlyphCatalog.layout` 的 `capHeight` 入参来源，不记下来下次改字模时无从对齐。

- [ ] **Step 4: 核对文件名与体积**

```bash
ls -la app/src/main/assets/glyphs/
# 14 个文件，文件名必须与 Glyph.assetName 逐一对应
ls app/src/main/assets/glyphs/ | wc -l    # 预期 14
du -sh app/src/main/assets/glyphs/
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/assets/glyphs/ docs/superpowers/notes/2026-09-21-glyph-authoring.md
git commit -m "feat: 14 个挤出字模资产与授作参数记录"
```

---

### Task 7: 挂载字模，移除 2D 文字

Phase 1 的交付物。完成后卡面数值与运算符符号是真实的挤出几何。

**Files:**
- Create: `app/src/main/java/tech/illusion/space24/content/GlyphLoader.kt`
- Modify: `app/src/main/java/tech/illusion/space24/content/SpatialStage.kt`
- Modify: `app/src/main/java/tech/illusion/space24/content/SpatialStage.kt`（`glyphsReady` 接上 `loader.isComplete()`）

**Interfaces:**
- Consumes: Task 5 的 `Glyph` / `GlyphCatalog`；Task 6 的资产；Task 3 的 `SpatialStage` 与 `anchorOf`
- Produces:
  - `class GlyphLoader`
  - `suspend fun GlyphLoader.preload()` —— 加载 14 个原型实体
  - `fun GlyphLoader.widthOf(glyph: Glyph): Float` / `fun GlyphLoader.capHeight(): Float`
  - `fun GlyphLoader.instantiate(glyph: Glyph): Entity?` —— `clone()` 出一个实例
  - `fun GlyphLoader.isComplete(): Boolean` —— 14 个原型是否全部加载成功；驱动 2D 文字降级开关

- [ ] **Step 1: 写字模加载器**

创建 `app/src/main/java/tech/illusion/space24/content/GlyphLoader.kt`：

```kotlin
package tech.illusion.space24.content

import android.util.Log
import com.pico.spatial.core.ecs.Entity

private const val GLYPH_TAG = "Space24Glyph"

/**
 * 14 个字模的原型实体池。
 *
 * 每个字模只 `loadSuspend` 一次建立原型（`enabled = false`、不入渲染树），需要显示时
 * `clone()` 出实例——同一字模在同一副牌里可能出现多次，重复解析同一个 `.usdc` 是白付的开销。
 *
 * 原型的包围盒在加载时量一次并缓存：`getVisualBounds` 对 3D 模型有效（对 2D 附件面板才恒返回 0）。
 */
class GlyphLoader {

    private val prototypes = mutableMapOf<Glyph, Entity>()
    private val widths = mutableMapOf<Glyph, Float>()
    private var capHeightValue = 0f

    /** 加载全部字模原型。任一字模失败只记日志并跳过，不让整个板面崩掉。 */
    suspend fun preload() {
        for (glyph in Glyph.values()) {
            val uri = "asset://glyphs/${glyph.assetName}.usdc"
            try {
                val entity = Entity.loadSuspend(uriString = uri)
                entity.setName("GlyphProto_${glyph.assetName}")
                entity.enabled = false
                val bounds = entity.getVisualBounds(relativeTo = null, recursive = true)
                prototypes[glyph] = entity
                widths[glyph] = bounds.size.x
                // 各字模字高统一（授作规范），取最大值以防个别字模有微小溢出。
                if (bounds.size.y > capHeightValue) capHeightValue = bounds.size.y
                Log.w(GLYPH_TAG, "loaded ${glyph.assetName} w=${bounds.size.x} h=${bounds.size.y}")
            } catch (t: Throwable) {
                // 只 catch 具体异常会被别的异常打崩进程——这是 SpaceVinyl 吃过的亏。
                Log.w(GLYPH_TAG, "load FAILED $uri: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        Log.w(GLYPH_TAG, "preload done loaded=${prototypes.size} expected=${Glyph.values().size}")
    }

    /** 字模在原始单位下的宽度；未加载成功的返回 0。 */
    fun widthOf(glyph: Glyph): Float = widths[glyph] ?: 0f

    /** 各字统一的字高（原始单位）。preload 之前为 0。 */
    fun capHeight(): Float = capHeightValue

    /**
     * 14 个原型是否全部加载成功。
     *
     * 这是**降级开关**：任一字模缺失就让卡面与运算符回落到 2D 文字，而不是显示空白。
     * 缺一个字模就整体回落而非逐字混排，是因为混排出来的数字（一半立体一半平面）
     * 比统一的 2D 文字更难读，也更像是坏了。
     */
    fun isComplete(): Boolean = prototypes.size == Glyph.values().size

    /** 克隆一个可显示的实例；原型缺失时返回 null。 */
    fun instantiate(glyph: Glyph): Entity? {
        val proto = prototypes[glyph] ?: return null
        return proto.clone()?.apply { enabled = true }
    }
}
```

- [ ] **Step 2: 在 SpatialStage 里挂载字模**

在 `SpatialStage.kt` 追加字模挂载逻辑：

```kotlin
/** 规格 §4.4：字模背面与卡面齐平、正面凸出约 1.5cm。做成凹刻只需把这个值取负。 */
private const val GLYPH_Z_M = 0.015f

/** 卡面字高占卡高的比例。初值凭观感给，真机看过再调。 */
private const val CARD_GLYPH_HEIGHT_RATIO = 0.42f

/** 卡面左右各留 8dp 内边距，整串超出剩余宽度时等比缩小。 */
private const val CARD_GLYPH_PADDING_DP = 16f

/** 运算符字高占按钮边长的比例。 */
private const val OP_GLYPH_HEIGHT_RATIO = 0.55f

/** 字距（dp），调用方换算成米后再传给 `mountGlyphs`。 */
private const val GLYPH_TRACKING_DP = 2f

/**
 * 一个面板上当前挂着的字模实例。用它判断"牌面没变就别重建"——
 * 每次重组都重挂会在合并动画（Phase 3）到来前就先把帧率吃光。
 */
private class MountedGlyphs(val key: String, val entities: List<Entity>)

/**
 * 把 [glyphs] 挂到 [panel] 上，先清掉上一批。
 *
 * 字模是面板实体的**子实体**，局部坐标 `(offsetX, 0, GLYPH_Z_M)`——局部原点就是面板中心，
 * 所以横向居中由构造保证，不经任何 dp→米 换算。只有 x 偏移和缩放需要算。
 *
 * **单位**：[targetHeight] 与 [maxWidth] 必须是**米**——字模是场景实体的子节点，局部坐标
 * 就是米。调用方用 `content.convertSize` 把契约的 dp 换成米，不要直接把 dp 数值传进来。
 * `glyphWidths` / `capHeight` 则是 `getVisualBounds` 量出的模型原始单位；`layout` 只用它们
 * 求比值，所以两组单位不同不矛盾，返回的 `scale` 正是"模型单位 → 米"的缩放。
 */
private fun mountGlyphs(
    panel: Entity,
    previous: MountedGlyphs?,
    key: String,
    glyphs: List<Glyph>,
    loader: GlyphLoader,
    targetHeight: Float,
    maxWidth: Float,
    tracking: Float,
): MountedGlyphs? {
    if (previous != null && previous.key == key) return previous
    previous?.entities?.forEach { it.enabled = false }

    if (glyphs.isEmpty() || loader.capHeight() <= 0f) return MountedGlyphs(key, emptyList())

    val layout = GlyphCatalog.layout(
        glyphWidths = glyphs.map { loader.widthOf(it) },
        capHeight = loader.capHeight(),
        targetHeight = targetHeight,
        maxWidth = maxWidth,
        tracking = tracking,
    )

    val mounted = glyphs.mapIndexedNotNull { i, glyph ->
        val entity = loader.instantiate(glyph) ?: run {
            Log.w(STAGE_TAG, "instantiate failed for ${glyph.assetName}")
            return@mapIndexedNotNull null
        }
        entity.components[TransformComponent::class.java]?.apply {
            setScaleVector(Vector3(layout.scale, layout.scale, layout.scale))
            setPosition(Vector3(layout.offsetsX[i], 0f, GLYPH_Z_M))
        }
        panel.addChild(entity)
        entity
    }
    return MountedGlyphs(key, mounted)
}
```

在 `SpatialStage` 的函数体里加：

```kotlin
    val loader = remember { GlyphLoader() }
    val mounted = remember { mutableMapOf<String, MountedGlyphs>() }
```

在 `initial` 的开头（建面板**之前**）加载原型，并把结果写进降级开关：

```kotlin
            loader.preload()
            // 写 Compose state 会触发重组，让 CardFace/OperatorFace 的 showValue/showSymbol
            // 跟着翻——字模全就位则关掉 2D 文字，任一缺失则保持 2D 文字可见。
            glyphsReady = loader.isComplete()
```

在 `applyLayout` 末尾追加字模同步。`key` 用牌面文本，牌面没变就整段跳过：

```kotlin
    // dp -> 米。字模是场景实体的子节点，局部坐标是米，所以这三个尺寸必须换算，
    // 不能把契约的 dp 数值直接当成场景单位用。
    fun metersOf(dp: Float): Float =
        content.convertSize(
            Vector3(0f, dp * density, 0f),
            ViewCoordinateSpace.Local,
            content.localSpatialCoordinateSpace,
        ).y

    val trackingM = metersOf(GLYPH_TRACKING_DP)

    // 卡面字模
    val cardTargetHeightM = metersOf(BoardGeometry.CARD_HEIGHT_DP * CARD_GLYPH_HEIGHT_RATIO)
    val cardMaxWidthM = metersOf(BoardGeometry.CARD_WIDTH_DP - CARD_GLYPH_PADDING_DP)
    for (i in 0 until BoardGeometry.MAX_CARDS) {
        val panel = panels["card_$i"] ?: continue
        val card = state.cards.getOrNull(i)
        val visible = boardVisible && card != null
        val key = if (visible) card!!.value.toString() else ""
        val glyphs = if (visible) GlyphCatalog.decompose(key) else emptyList()
        mountGlyphs(
            panel = panel,
            previous = mounted["card_$i"],
            key = key,
            glyphs = glyphs,
            loader = loader,
            targetHeight = cardTargetHeightM,
            maxWidth = cardMaxWidthM,
            tracking = trackingM,
        )?.let { mounted["card_$i"] = it }
        mounted["card_$i"]?.entities?.forEach { it.enabled = visible }
    }

    // 运算符字模
    val opTargetHeightM = metersOf(BoardGeometry.OP_SIZE_DP * OP_GLYPH_HEIGHT_RATIO)
    val opMaxWidthM = metersOf(BoardGeometry.OP_SIZE_DP)
    for (i in 0 until BoardGeometry.OP_COUNT) {
        val panel = panels["op_$i"] ?: continue
        val op = Op.values()[i]
        val key = if (boardVisible) op.symbol else ""
        val glyphs = if (boardVisible) listOf(GlyphCatalog.glyphFor(op)) else emptyList()
        mountGlyphs(
            panel = panel,
            previous = mounted["op_$i"],
            key = key,
            glyphs = glyphs,
            loader = loader,
            targetHeight = opTargetHeightM,
            maxWidth = opMaxWidthM,
            tracking = trackingM,
        )?.let { mounted["op_$i"] = it }
        mounted["op_$i"]?.entities?.forEach { it.enabled = boardVisible }
    }
```

`applyLayout` 的签名相应增加 `loader: GlyphLoader` 与 `mounted: MutableMap<String, MountedGlyphs>` 两个参数，两处调用点（`initial` 与 `update`）同步传入。

`SpatialStage.kt` 需要补两个 import：`com.pico.spatial.core.math.Vector3` 与 `com.pico.spatial.core.ecs.ViewCoordinateSpace`。Kotlin 对未使用的 import 不报 warning，所以反过来也要手工确认没有留下多余的。

**两条必须留意的风险：**

`enabled = false` 未必足以完全隐藏子实体——工作区已记录过 `opacity = 0` 未能完全隐藏静态子物体、与文档"级联隐藏"说法矛盾的先例。Step 5 的截图要专门确认结算态下字模确已消失；若未消失，在 `mountGlyphs` 的隐藏路径上追加 `setScaleVector(Vector3(0f, 0f, 0f))` 作双保险。

`SpatialView` 的 `update` 回调**是否会在 `state.cards` 变化时触发，未经验证**。若 Step 5 的截图显示牌数递减后整行没有重新居中、或字模没跟着换，说明 `update` 没有订阅到 snapshot 读取。退路是 SpaceVinyl 用过的 `contentRef` 模式：在 `initial` 里把 `content` 存进一个 `remember { arrayOfNulls<SpatialViewContent>(1) }`，再加一个 `LaunchedEffect(state.cards, state.phase) { contentRef[0]?.let { applyLayout(it, ...) } }` 显式驱动。

- [ ] **Step 3: 验证降级开关两个方向都活**

Task 3 已经把两处调用写成 `showValue = !glyphsReady` / `showSymbol = !glyphsReady`，Step 2 让 `glyphsReady` 接上了 `loader.isComplete()`。所以本步不改调用点，只验证开关**两个方向都真的生效**——一个从不翻转的开关和一个写死的常量没有区别。

写一个临时验证：把 `GlyphLoader.preload()` 里的资产路径改成一个不存在的名字（例如 `glyphs/digit_0_MISSING.usdc`），构建并在模拟器上启动，确认：

1. 日志出现 `load FAILED` 与 `preload done loaded=13 expected=14`。
2. 卡面显示的是 **2D 文字**，不是空白卡。

然后把路径改回去，确认卡面回到立体字模。两次都截图留证。

这一步证明了 `cardTextSizeFor` / `CardTextSize` 不是死代码——它们是降级路径的实际执行分支，`GameBoardTest` 对它们的三个测试也因此是有意义的。

- [ ] **Step 4: 构建并跑回归**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest
./gradlew assembleDebug
grep -rn "androidx.compose.material\|MaterialTheme" app/src
```

预期：测试全绿；构建成功；grep 零输出。

- [ ] **Step 5: 门禁 B —— 设备取证**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
LOCK=/Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh
PICO_LOCK_MAX_WAIT=90 $LOCK acquire Space24-glyph-phase1 120 emulator-5554 || { echo "LOCK TIMEOUT — 报告并跳过"; exit 0; }
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n tech.illusion.space24/.platform.LaunchActivity -e debug_start playing
for i in $(seq 1 40); do
  adb -s emulator-5554 logcat -d -s Space24Glyph 2>/dev/null | grep -a "preload done" && break
  sleep 1
done
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-phase1-1.png
sleep 2
adb -s emulator-5554 exec-out screencap -p > /tmp/space24-phase1-2.png
adb -s emulator-5554 logcat -d -s Space24Glyph -s Space24Stage 2>/dev/null | grep -a . > /tmp/space24-phase1.log
adb -s emulator-5554 shell am force-stop tech.illusion.space24
$LOCK release Space24-glyph-phase1 emulator-5554
```

判读清单：

1. 日志 `preload done loaded=14 expected=14`，无 `load FAILED`。
2. 两张截图一致（共享模拟器会串画面，不一致就重拍）。
3. 卡面数字可见，且**看得出厚度与侧面高光**——这是 Phase 1 的完成判据。只是平面字就说明字模没挂上或 `GLYPH_Z_M` 被裁。
4. 数字**没有重影**——有重影说明 2D `Text` 没关干净。
5. 四个运算符符号同样立体。

- [ ] **Step 6: 量帧率**

不要等到 Phase 3 动画叠上去才发现性能问题。最坏情形是 4 卡 × 6 字符 + 4 运算符 = 28 个字模实例外加 14 个 disabled 原型。

在上一步的锁窗口内用 `pico-cli perf` 采一段，或读 logcat 中的掉帧告警，把读数记进 Step 7 的报告。

- [ ] **Step 7: 写验证记录并提交**

把判读结果写进 `.spatialsdk/ui-verification-log.md`，明确区分"已核到"与"不可判定 — 待真机验证"。依赖交互的视觉（卡片选中、提示高亮、运算符三态）在模拟器上点不出来，一律标为待真机。

```bash
git add app/src/main/java/tech/illusion/space24/content/GlyphLoader.kt \
        app/src/main/java/tech/illusion/space24/content/SpatialStage.kt \
        app/src/main/java/tech/illusion/space24/content/GameBoard.kt \
        .spatialsdk/ui-verification-log.md
git commit -m "feat: 卡面数值与运算符换为挤出字模，关闭 2D 文字"
```

---

### Task 8: 契约与 AGENTS.md 更新

代码改完不更文档，下一个会话会按旧契约把改动改回去。

**Files:**
- Modify: `.spatialsdk/design-contract.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: Task 1–7 的全部结论
- Produces: 无代码产物

- [ ] **Step 1: 契约升 v3**

修改 `.spatialsdk/design-contract.md`：

- §0 元信息：契约版本改 v3，记录变更时间 2026-09-21、变更内容、以及本轮的用户确认依据（2026-09-20 的设计确认对话）。**不要删除 v1/v2 的历史记录**。
- §1 面板清单：单一 planar `WindowContainer` → Volumetric `WindowContainer` + `SpatialView` + 9 个 `AttachmentPanel`（chrome + card_0..3 + op_0..3）。保留"容器类型是架构决策"这句话——它现在保护的是新的容器选择。
- §2.3 卡片文字分档规则：删除"按字符数切 titleLarge / titleSmall / bodySmall 三档"，替换为"字模按字高归一化、横向比例排布、整串超宽时等比缩小"。
- §2.3 新增卡面与运算符的立体层描述：凸出量 1.5cm、字模资产清单、选中态仍由 2D 填色与描边表达。
- 记录 Phase 2–4（选中弹出、合并动画、答对爆发）为**已确认但尚未实现**，避免下一个会话把"没有动画"当成缺陷去补。

- [ ] **Step 2: 更新 AGENTS.md**

修改 `AGENTS.md`：

- **删除**"窗口尺寸 900×640dp 出现在两处，必须同时改"一条——`windowConstraints` 已从 `Main.kt` 移除，尺寸来源只剩 manifest `defaultsize`。
- 在"经实证的 SpatialUI 事实"一节**新增**：
  - `SpatialViewContent` 以委托实现 `SpatialCoordinateSpaceConverter`，`initial` 的 `content` 本身就是像素↔米换算器，不需要手工标定 dp→米。
  - `attachments.entity(id)` 本身不会把面板加进渲染树，必须再 `content.addEntity(it)`。
  - `getVisualBounds` 对 3D 模型有效，对 2D 附件面板恒返回 (0,0,0)。
  - Task 2 探针实测到的面板数量上限与前边界深度余量。
- 在"构建与验证"一节新增 debug 启动钩子的用法：`am start -n tech.illusion.space24/.platform.LaunchActivity -e debug_start playing`，并注明守卫用 `BuildConfig.DEBUG` 而非 `Build.TYPE`。
- 在"已知的验证边界"一节更新：Phase 1 已核到哪些、哪些仍待真机。
- 在"刻意延后，不是忘了"一节新增 Phase 2–4。

- [ ] **Step 3: 最终全量门禁**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew testDebugUnitTest
./gradlew assembleDebug
grep -rn "androidx.compose.material\|MaterialTheme" app/src
git status --short
```

预期：测试全绿、构建成功、grep 零输出、工作区干净。

- [ ] **Step 4: 提交**

```bash
git add .spatialsdk/design-contract.md AGENTS.md
git commit -m "docs: 契约升 v3，AGENTS.md 补入本轮核实的 SpatialUI 事实"
```
