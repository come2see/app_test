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
