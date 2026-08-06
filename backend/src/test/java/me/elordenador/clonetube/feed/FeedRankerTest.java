package me.elordenador.clonetube.feed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static me.elordenador.clonetube.feed.FeedRanker.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Port de backend.old/tests/test_feed.py.
 */
class FeedRankerTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final int FOLLOWED_AUTHOR = 1;
    private static final int OTHER_AUTHOR = 2;
    private static final int THIRD_AUTHOR = 3;

    private static FeedCandidate candidate(int authorId, double hoursAgo, int videoId, long likes) {
        return new FeedCandidate(videoId, authorId, NOW.minus((long) (hoursAgo * 3600), ChronoUnit.SECONDS), likes);
    }

    private static FeedCandidate candidate(int authorId, double hoursAgo, int videoId) {
        return candidate(authorId, hoursAgo, videoId, 0);
    }

    // ---------- recency ----------

    @Test
    void brand_new_video_scores_one() {
        assertEquals(1.0, recencyScore(NOW, NOW), 1e-9);
    }

    @Test
    void half_life_halves_the_score() {
        assertEquals(0.5, recencyScore(NOW.minus(72, ChronoUnit.HOURS), NOW), 1e-9);
    }

    @Test
    void score_decreases_with_age() {
        double recent = recencyScore(NOW.minus(1, ChronoUnit.HOURS), NOW);
        double old = recencyScore(NOW.minus(30, ChronoUnit.DAYS), NOW);
        assertTrue(0 < old && old < recent && recent <= 1);
    }

    @Test
    void future_dates_do_not_exceed_one() {
        assertEquals(1.0, recencyScore(NOW.plus(1, ChronoUnit.DAYS), NOW), 1e-9);
    }

    // ---------- popularity ----------

    @Test
    void no_likes_scores_zero() {
        assertEquals(0.0, popularityScore(0), 1e-9);
    }

    @Test
    void grows_sublinearly() {
        assertTrue(popularityScore(1000) < 10 * popularityScore(10));
    }

    @Test
    void negative_likes_are_clamped() {
        assertEquals(0.0, popularityScore(-5), 1e-9);
    }

    // ---------- score ----------

    @Test
    void followed_author_gets_the_boost() {
        FeedCandidate video = candidate(FOLLOWED_AUTHOR, 0, 1);
        double followed = scoreCandidate(video, Set.of(FOLLOWED_AUTHOR), NOW);
        double notFollowed = scoreCandidate(video, Set.of(), NOW);
        assertEquals(FOLLOW_BOOST, followed - notFollowed, 1e-9);
    }

    @Test
    void followed_old_video_beats_fresh_stranger() {
        FeedCandidate followedOld = candidate(FOLLOWED_AUTHOR, 24 * 14, 1);
        FeedCandidate strangerNew = candidate(OTHER_AUTHOR, 0, 2);
        assertTrue(scoreCandidate(followedOld, Set.of(FOLLOWED_AUTHOR), NOW)
                > scoreCandidate(strangerNew, Set.of(FOLLOWED_AUTHOR), NOW));
    }

    @Test
    void likes_break_ties_between_equally_recent_videos() {
        FeedCandidate popular = candidate(OTHER_AUTHOR, 0, 1, 50);
        FeedCandidate unknown = candidate(OTHER_AUTHOR, 0, 2, 0);
        assertTrue(scoreCandidate(popular, Set.of(), NOW) > scoreCandidate(unknown, Set.of(), NOW));
    }

    // ---------- rank ----------

    @Test
    void followed_videos_go_first() {
        FeedCandidate followed = candidate(FOLLOWED_AUTHOR, 48, 1);
        FeedCandidate strangerA = candidate(OTHER_AUTHOR, 1, 2);
        FeedCandidate strangerB = candidate(THIRD_AUTHOR, 2, 3);

        List<ScoredVideo> ranked = rankCandidates(List.of(strangerA, strangerB, followed),
                Set.of(FOLLOWED_AUTHOR), NOW, 10);

        assertTrue(ranked.get(0).fromFollowedAuthor());
        assertFalse(ranked.get(1).fromFollowedAuthor());
        assertFalse(ranked.get(2).fromFollowedAuthor());
    }

    @Test
    void without_follows_falls_back_to_recency() {
        FeedCandidate old = candidate(OTHER_AUTHOR, 100, 1);
        FeedCandidate fresh = candidate(THIRD_AUTHOR, 1, 2);

        List<ScoredVideo> ranked = rankCandidates(List.of(old, fresh), Set.of(), NOW, 10);

        assertEquals(List.of(fresh.videoId(), old.videoId()),
                ranked.stream().map(s -> s.candidate().videoId()).toList());
        assertTrue(ranked.stream().noneMatch(ScoredVideo::fromFollowedAuthor));
    }

    @Test
    void author_diversity_interleaves_a_prolific_followed_author() {
        List<FeedCandidate> prolific = List.of(
                candidate(FOLLOWED_AUTHOR, 1, 1),
                candidate(FOLLOWED_AUTHOR, 2, 2),
                candidate(FOLLOWED_AUTHOR, 3, 3));
        FeedCandidate occasional = candidate(OTHER_AUTHOR, 10, 4);

        List<Integer> authors = rankCandidates(
                new ArrayList<>(List.of(prolific.get(0), prolific.get(1), prolific.get(2), occasional)),
                Set.of(FOLLOWED_AUTHOR, OTHER_AUTHOR), NOW, 10).stream()
                .map(s -> s.candidate().authorId()).toList();

        assertEquals(List.of(FOLLOWED_AUTHOR, OTHER_AUTHOR, FOLLOWED_AUTHOR, FOLLOWED_AUTHOR), authors);
    }

    @Test
    void diversity_never_pushes_a_followed_author_below_a_stranger() {
        List<FeedCandidate> followedVideos = List.of(
                candidate(FOLLOWED_AUTHOR, 1, 1),
                candidate(FOLLOWED_AUTHOR, 2, 2),
                candidate(FOLLOWED_AUTHOR, 3, 3));
        FeedCandidate stranger = candidate(OTHER_AUTHOR, 1, 4, 100);

        List<ScoredVideo> ranked = rankCandidates(
                new ArrayList<>(List.of(followedVideos.get(0), followedVideos.get(1), followedVideos.get(2), stranger)),
                Set.of(FOLLOWED_AUTHOR), NOW, 10);

        assertEquals(List.of(true, true, true, false),
                ranked.stream().map(ScoredVideo::fromFollowedAuthor).toList());
    }

    @Test
    void limit_truncates_the_result() {
        List<FeedCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candidates.add(candidate(OTHER_AUTHOR, i, i));
        }
        assertEquals(3, rankCandidates(candidates, Set.of(), NOW, 3).size());
    }

    @Test
    void empty_input_returns_empty_list() {
        assertTrue(rankCandidates(List.of(), Set.of(FOLLOWED_AUTHOR), NOW, 10).isEmpty());
    }

    @Test
    void ties_are_resolved_deterministically_by_video_id() {
        FeedCandidate first = candidate(OTHER_AUTHOR, 0, 1);
        FeedCandidate second = candidate(THIRD_AUTHOR, 0, 2);

        List<Integer> forward = rankCandidates(List.of(first, second), Set.of(), NOW, 10)
                .stream().map(s -> s.candidate().videoId()).toList();
        List<Integer> backward = rankCandidates(List.of(second, first), Set.of(), NOW, 10)
                .stream().map(s -> s.candidate().videoId()).toList();

        assertEquals(List.of(1, 2), forward);
        assertEquals(forward, backward);
    }

    @Test
    void every_candidate_is_returned_exactly_once() {
        List<FeedCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 3; i++) candidates.add(candidate(FOLLOWED_AUTHOR, 0, 10 + i));
        for (int i = 0; i < 4; i++) candidates.add(candidate(OTHER_AUTHOR, 0, 20 + i));

        List<Integer> returned = rankCandidates(candidates, Set.of(FOLLOWED_AUTHOR), NOW, 10)
                .stream().map(s -> s.candidate().videoId()).sorted().toList();
        List<Integer> expected = candidates.stream().map(FeedCandidate::videoId).sorted().toList();

        assertEquals(expected, returned);
    }
}
