package tech.illusion.space24.game

/**
 * The four binary operations of 24-point, under this app's integer-only rule:
 * division is legal only when it divides exactly, judged with an integer remainder
 * (no floating point anywhere in the game logic). Negative intermediates are legal.
 */
enum class Op(val symbol: String, val commutative: Boolean) {
    PLUS("+", true),
    MINUS("−", false),
    TIMES("×", true),
    DIV("÷", false);

    /** Returns null when the operation is illegal (divide by zero, or a non-exact division). */
    fun apply(a: Int, b: Int): Int? = when (this) {
        PLUS -> a + b
        MINUS -> a - b
        TIMES -> a * b
        DIV -> if (b != 0 && a % b == 0) a / b else null
    }
}
