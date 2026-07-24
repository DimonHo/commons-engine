package com.commonsengine.rating.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonsengine.rating.domain.Model.CreditProfile;
import com.commonsengine.rating.domain.Model.Rating;
import com.commonsengine.rating.domain.Model.RatingDirection;
import com.commonsengine.rating.domain.Model.RatingId;
import com.commonsengine.rating.domain.Model.RatingTag;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RatingServiceTest {

    @Autowired
    private RatingService service;

    private Rating rating(String rater, String ratee, int score,
                          RatingDirection dir, Set<RatingTag> tags) {
        return new Rating(
                RatingId.random(),
                "tx-001",
                rater,
                ratee,
                dir,
                score,
                tags,
                null,
                null
        );
    }

    @Test
    void submitAndRetrieveRating() {
        service.submit(rating("c1", "w1", 5,
                RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        List<Rating> received = service.findReceived("w1");
        assertEquals(1, received.size());
        assertEquals(5, received.get(0).score());
    }

    @Test
    void scoreMustBe1To5() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> rating("c1", "w1", 0,
                        RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> rating("c1", "w1", 6,
                        RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
    }

    @Test
    void creditProfileAggregatesScores() {
        service.submit(rating("c1", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        service.submit(rating("c2", "w1", 4, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        service.submit(rating("c3", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));

        CreditProfile profile = service.getCreditProfile("w1");
        assertEquals(3, profile.totalRatings());
        assertEquals(4.67, profile.averageScore(), 0.01);
    }

    @Test
    void creditProfileReturnsDefaultForUnknownMember() {
        CreditProfile profile = service.getCreditProfile("unknown");
        assertEquals(5.0, profile.averageScore());
        assertEquals(0, profile.totalRatings());
    }

    @Test
    void bidirectionalRatingsWork() {
        service.submit(rating("c1", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        service.submit(rating("w1", "c1", 5, RatingDirection.WORKER_TO_CONSUMER, Set.of(RatingTag.POLITE)));

        List<Rating> workerRatings = service.findReceived("w1");
        List<Rating> consumerRatings = service.findReceived("c1");
        assertEquals(1, workerRatings.size());
        assertEquals(1, consumerRatings.size());
    }

    @Test
    void exportProfileContainsMemberData() {
        service.submit(rating("c1", "w1", 5, RatingDirection.CONSUMER_TO_WORKER,
                Set.of(RatingTag.POLITE, RatingTag.PROFESSIONAL)));

        String exported = service.exportProfile("w1");
        assertTrue(exported.contains("信用记录导出"));
        assertTrue(exported.contains("POLITE"));
        assertTrue(exported.contains("可携带"));
    }

    @Test
    void tagFrequencyIsCountedCorrectly() {
        service.submit(rating("c1", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        service.submit(rating("c2", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE, RatingTag.PUNCTUAL)));
        service.submit(rating("c3", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.PUNCTUAL)));

        CreditProfile profile = service.getCreditProfile("w1");
        assertEquals(2, profile.tagFrequency().get(RatingTag.POLITE));
        assertEquals(2, profile.tagFrequency().get(RatingTag.PUNCTUAL));
    }

    @Test
    void findByTransactionReturnsBothDirections() {
        service.submit(rating("c1", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        service.submit(rating("w1", "c1", 5, RatingDirection.WORKER_TO_CONSUMER, Set.of(RatingTag.POLITE)));

        List<Rating> txRatings = service.findByTransaction("tx-001");
        assertEquals(2, txRatings.size());
    }

    @Test
    void findGivenReturnsRatingsByRater() {
        service.submit(rating("c1", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        service.submit(rating("c1", "w2", 5, RatingDirection.CONSUMER_TO_WORKER, Set.of(RatingTag.POLITE)));
        service.submit(rating("w1", "c1", 5, RatingDirection.WORKER_TO_CONSUMER, Set.of(RatingTag.POLITE)));

        List<Rating> given = service.findGiven("c1");
        assertEquals(2, given.size());
    }

    @Test
    void tagsPersistAndReloadCorrectly() {
        Set<RatingTag> tags = Set.of(RatingTag.POLITE, RatingTag.PUNCTUAL, RatingTag.PROFESSIONAL);
        service.submit(rating("c1", "w1", 5, RatingDirection.CONSUMER_TO_WORKER, tags));

        List<Rating> received = service.findReceived("w1");
        assertEquals(tags, received.get(0).tags());
    }

    @Test
    void ratingWithCommentPersists() {
        Rating r = new Rating(
                RatingId.random(),
                "tx-001",
                "c1",
                "w1",
                RatingDirection.CONSUMER_TO_WORKER,
                5,
                Set.of(RatingTag.POLITE),
                "很好",
                null
        );
        service.submit(r);

        List<Rating> received = service.findReceived("w1");
        assertEquals("很好", received.get(0).comment());
    }
}
