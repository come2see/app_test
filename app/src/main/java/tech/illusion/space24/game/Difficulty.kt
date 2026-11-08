package tech.illusion.space24.game

/**
 * Difficulty only selects which pre-generated puzzle bucket a round draws from — round length is
 * always [GameSession.ROUND_MILLIS]. EASY and NORMAL share the 1–9 card range and differ by how
 * many distinct solutions their hands have; HARD opens the range up to 13 (shown as digits, no J/Q/K).
 */
enum class Difficulty(val maxCardValue: Int) {
    EASY(9),
    NORMAL(9),
    HARD(13),
}
