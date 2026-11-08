package tech.illusion.space24.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BestScoreStoreTest {

    @Test
    fun `a higher score beats the stored record`() {
        assertEquals(true, BestScoreStore.isNewBest(current = 5, candidate = 6))
    }

    @Test
    fun `an equal or lower score does not beat the stored record`() {
        assertEquals(false, BestScoreStore.isNewBest(current = 5, candidate = 5))
        assertEquals(false, BestScoreStore.isNewBest(current = 5, candidate = 4))
    }

    @Test
    fun `the first non-zero score becomes the record`() {
        assertEquals(true, BestScoreStore.isNewBest(current = null, candidate = 1))
    }

    @Test
    fun `a scoreless round never sets a record`() {
        assertEquals(false, BestScoreStore.isNewBest(current = null, candidate = 0))
        assertEquals(false, BestScoreStore.isNewBest(current = 3, candidate = 0))
    }
}
