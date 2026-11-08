package tech.illusion.space24.game

/** One binary merge the player performs, e.g. `3 + 5 = 8`. */
data class MergeStep(val left: Int, val op: Op, val right: Int, val result: Int) {
    fun display(): String = "$left ${op.symbol} $right = $result"
}

/**
 * [pathCount] is the number of *canonically distinct* solutions — a deliberate proxy metric used
 * only to rank hands against each other for difficulty bucketing. Canonicalization sorts the two
 * children of commutative operators; it does NOT flatten associativity, so `((6+6)+(6+6))` and
 * `(((6+6)+6)+6)` still count as two. Do not assert exact counts in tests; assert relative order.
 * [steps] is one representative solution, empty when there is none.
 */
data class SolveResult(val pathCount: Int, val steps: List<MergeStep>) {
    val hasSolution: Boolean get() = pathCount > 0
}

object Solver {

    const val TARGET = 24

    /** Expression tree node. Deliberately allocation-light: no strings, no step lists in the hot loop. */
    private class Node(val value: Int, val op: Op? = null, val left: Node? = null, val right: Node? = null)

    fun solve(cards: List<Int>, target: Int = TARGET): SolveResult {
        val canonicalSolutions = HashSet<String>()
        var representative: List<MergeStep>? = null

        fun search(nodes: List<Node>) {
            if (nodes.size == 1) {
                val only = nodes[0]
                if (only.value != target) return
                if (canonicalSolutions.add(canonicalOf(only)) && representative == null) {
                    representative = stepsOf(only)
                }
                return
            }
            for (i in nodes.indices) {
                for (j in nodes.indices) {
                    if (i == j) continue
                    val a = nodes[i]
                    val b = nodes[j]
                    for (op in Op.values()) {
                        // For commutative ops only one operand order needs exploring.
                        if (op.commutative && j < i) continue
                        val result = op.apply(a.value, b.value) ?: continue
                        val rest = ArrayList<Node>(nodes.size - 1)
                        for (k in nodes.indices) if (k != i && k != j) rest.add(nodes[k])
                        rest.add(Node(value = result, op = op, left = a, right = b))
                        search(rest)
                    }
                }
            }
        }

        // Sorting makes the representative solution deterministic regardless of deal order.
        search(cards.sorted().map { Node(it) })
        return SolveResult(pathCount = canonicalSolutions.size, steps = representative ?: emptyList())
    }

    private fun canonicalOf(node: Node): String {
        val op = node.op ?: return node.value.toString()
        val left = canonicalOf(node.left ?: return node.value.toString())
        val right = canonicalOf(node.right ?: return node.value.toString())
        return if (op.commutative && left > right) {
            "($right${op.symbol}$left)"
        } else {
            "($left${op.symbol}$right)"
        }
    }

    /** Post-order walk, so every step's operands are values already on the board when it runs. */
    private fun stepsOf(root: Node): List<MergeStep> {
        val out = ArrayList<MergeStep>()
        fun walk(node: Node) {
            val op = node.op ?: return
            val left = node.left ?: return
            val right = node.right ?: return
            walk(left)
            walk(right)
            out.add(MergeStep(left.value, op, right.value, node.value))
        }
        walk(root)
        return out
    }
}
