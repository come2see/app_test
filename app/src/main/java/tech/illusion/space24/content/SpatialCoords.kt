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
