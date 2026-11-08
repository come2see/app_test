package tech.illusion.space24.content

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tech.illusion.space24.data.BestScoreStore
import tech.illusion.space24.game.CardOnBoard
import tech.illusion.space24.game.Difficulty
import tech.illusion.space24.game.FeedbackKind
import tech.illusion.space24.game.GamePhase
import tech.illusion.space24.game.GameSession
import tech.illusion.space24.game.Op
import tech.illusion.space24.game.PuzzleBank

/**
 * 面板内容读取游戏状态的统一入口。
 *
 * 全部属性都是 `get()` 委托到 [GameSession] 的 snapshot-backed 属性——读发生在调用点，
 * 所以重组追踪照常工作。**这里不新增任何会话属性**：`GameSession` 标了 `@Stable`，
 * 新增的公开属性若不是 snapshot-backed，那个注解就变成谎言。
 *
 * [bestScore] 是 `Int?`：题库尚未生成或本难度还没有记录时为 null，`StartPage`/`ResultPanel`
 * 早已按 `Int?` 设计（见 `formatBestScore`），这里原样透传，不做非空断言。
 */
@Stable
class StageState(
    val session: GameSession?,
    val selectedDifficulty: Difficulty,
    val onSelectDifficulty: (Difficulty) -> Unit,
    val bestScore: Int?,
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
    // debug 构建下，`-e debug_start playing` 让下面的 effect 直接跳过开始页——模拟器的注入
    // 点击打不准空间窗口按钮，没有这个钩子就进不了答题页，取证无从谈起。
    val debugSeed = rememberDebugStartSeed()

    // elapsedRealtime 单调且设备睡眠时继续走，不像 currentTimeMillis 会被 NTP 或用户改钟跳变。
    val session = remember(bank) {
        bank?.let {
            GameSession(
                it,
                nowMillis = SystemClock::elapsedRealtime,
                random = debugSeed?.let { seed -> kotlin.random.Random(seed) } ?: kotlin.random.Random.Default,
            )
        }
    }

    var selectedDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var bestScore by remember(selectedDifficulty) {
        mutableStateOf(BestScoreStore.readBestScore(context, selectedDifficulty))
    }
    var isNewRecord by remember { mutableStateOf(false) }
    var recordWrittenForThisRound by remember { mutableStateOf(false) }

    // debug 钩子：题库异步就绪（session 从 null 变为非 null）后立刻自动开局，跳过开始页的点击。
    // 用 LaunchedEffect 观察而不是在 session 构造处直接调用，因为 PuzzleBank.build() 是异步的，
    // session 一开始是 null；effect 在 session 就绪后自然触发，不需要关心先后顺序。
    LaunchedEffect(session, debugSeed) {
        if (session != null && debugSeed != null && session.phase == GamePhase.NOT_STARTED) {
            session.startNewGame(selectedDifficulty)
        }
    }

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
 * `ResultPanel` 用 `BasicSheet`（真正的 Dialog 窗口）承载模态遮罩与指针拦截，本次搬家未改动
 * 该文件；不存在需要"原样保留"的手写 scrim `pointerInput` 消费循环（那个模式如今只出现在
 * `StartPage.kt` 的 `HowToPlayOverlay` 里，本任务同样未触碰该文件）。
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
 *
 * **每个 `Spacer` 高度都从 [BoardGeometry] derive，不在这里另写一份数字**（I2）：卡片行/
 * 运算符行的位置事实来源只有 `BoardGeometry` 的行中心常量——`card_i`/`op_i` 两个
 * `AttachmentPanel` 的实际挂载位置（见 `SpatialStage.kt` 的 `applyLayout`）也读同一批常量。
 * 这里如果改用独立写死的间隔数字，两边一旦只改了一处就会不一致（这正是 I2 发现时的状态：
 * 面板已经在 256dp，chrome 占位却按 288dp 计算）。`BoardGeometry` 负责数值本身，这里只负责
 * 把数值排成 Compose 布局。
 */
@Composable
private fun BoardChrome(state: StageState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = BoardGeometry.CARD_ROW_TOP_DP.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 卡片行的竖直占位 + 卡片行下沿到运算符行上沿的间隔（由行位置常量反推，契约值 24dp）
        Spacer(Modifier.height(BoardGeometry.CARD_HEIGHT_DP.dp))
        Spacer(Modifier.height(BoardGeometry.CARD_TO_OP_GAP_DP.dp))
        // 运算符行的竖直占位 + 运算符行下沿到状态行的间隔（契约值 16dp）
        Spacer(Modifier.height(BoardGeometry.OP_SIZE_DP.dp))
        Spacer(Modifier.height(BoardGeometry.OP_TO_STATUS_GAP_DP.dp))

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
