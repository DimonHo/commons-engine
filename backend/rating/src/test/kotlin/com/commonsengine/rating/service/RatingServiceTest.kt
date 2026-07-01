package com.commonsengine.rating.service

import com.commonsengine.rating.domain.Rating
import com.commonsengine.rating.domain.RatingDirection
import com.commonsengine.rating.domain.RatingId
import com.commonsengine.rating.domain.RatingTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RatingServiceTest {

    private val service = RatingService()

    private fun rating(
        rater: String = "c1",
        ratee: String = "w1",
        score: Int = 5,
        dir: RatingDirection = RatingDirection.CONSUMER_TO_WORKER,
        tags: Set<RatingTag> = setOf(RatingTag.POLITE),
    ) = Rating(
        id = RatingId.random(),
        transactionId = "tx-001",
        raterId = rater,
        rateeId = ratee,
        direction = dir,
        score = score,
        tags = tags,
    )

    @Test
    fun `submit and retrieve rating`() {
        val r = service.submit(rating(ratee = "w1", score = 5))
        val received = service.findReceived("w1")
        assertEquals(1, received.size)
        assertEquals(5, received[0].score)
    }

    @Test
    fun `score must be 1 to 5`() {
        assertThrows<IllegalArgumentException> { rating(score = 0) }
        assertThrows<IllegalArgumentException> { rating(score = 6) }
    }

    @Test
    fun `credit profile aggregates scores`() {
        service.submit(rating(ratee = "w1", score = 5))
        service.submit(rating(ratee = "w1", score = 4))
        service.submit(rating(ratee = "w1", score = 5))

        val profile = service.getCreditProfile("w1")
        assertEquals(3, profile.totalRatings)
        assertEquals(4.67, profile.averageScore, 0.01)
    }

    @Test
    fun `credit profile returns default for unknown member`() {
        val profile = service.getCreditProfile("unknown")
        assertEquals(5.0, profile.averageScore)
        assertEquals(0, profile.totalRatings)
    }

    @Test
    fun `bidirectional ratings work`() {
        service.submit(rating(rater = "c1", ratee = "w1", dir = RatingDirection.CONSUMER_TO_WORKER))
        service.submit(rating(rater = "w1", ratee = "c1", dir = RatingDirection.WORKER_TO_CONSUMER))

        val workerRatings = service.findReceived("w1")
        val consumerRatings = service.findReceived("c1")
        assertEquals(1, workerRatings.size)
        assertEquals(1, consumerRatings.size)
    }

    @Test
    fun `export profile contains member data`() {
        service.submit(rating(ratee = "w1", score = 5, tags = setOf(RatingTag.POLITE, RatingTag.PROFESSIONAL)))

        val exported = service.exportProfile("w1")
        assertTrue(exported.contains("信用记录导出"))
        assertTrue(exported.contains("POLITE"))
        assertTrue(exported.contains("可携带"))
    }

    @Test
    fun `tag frequency is counted correctly`() {
        service.submit(rating(ratee = "w1", tags = setOf(RatingTag.POLITE)))
        service.submit(rating(ratee = "w1", tags = setOf(RatingTag.POLITE, RatingTag.PUNCTUAL)))
        service.submit(rating(ratee = "w1", tags = setOf(RatingTag.PUNCTUAL)))

        val profile = service.getCreditProfile("w1")
        assertEquals(3, profile.tagFrequency[RatingTag.POLITE])
        assertEquals(2, profile.tagFrequency[RatingTag.PUNCTUAL])
    }
}
