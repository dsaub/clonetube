"""Pruebas unitarias del algoritmo de recomendación (módulo `feed`)."""

import uuid
from datetime import UTC, datetime, timedelta

import pytest

from feed import (
    AUTHOR_DIVERSITY_PENALTY,
    FOLLOW_BOOST,
    RECENCY_HALF_LIFE_HOURS,
    FeedCandidate,
    ensure_utc,
    popularity_score,
    rank_candidates,
    recency_score,
    score_candidate,
)

NOW = datetime(2026, 7, 25, 12, 0, tzinfo=UTC)

FOLLOWED_AUTHOR = uuid.UUID("11111111-1111-1111-1111-111111111111")
OTHER_AUTHOR = uuid.UUID("22222222-2222-2222-2222-222222222222")
THIRD_AUTHOR = uuid.UUID("33333333-3333-3333-3333-333333333333")


def candidate(
    author_id: uuid.UUID,
    *,
    hours_ago: float = 0.0,
    likes: int = 0,
    video_id: uuid.UUID | None = None,
) -> FeedCandidate:
    return FeedCandidate(
        video_id=video_id or uuid.uuid4(),
        author_id=author_id,
        created_at=NOW - timedelta(hours=hours_ago),
        likes=likes,
    )


class TestRecencyScore:
    def test_brand_new_video_scores_one(self):
        assert recency_score(NOW, NOW) == pytest.approx(1.0)

    def test_half_life_halves_the_score(self):
        older = NOW - timedelta(hours=RECENCY_HALF_LIFE_HOURS)
        assert recency_score(older, NOW) == pytest.approx(0.5)

    def test_score_decreases_with_age(self):
        recent = recency_score(NOW - timedelta(hours=1), NOW)
        old = recency_score(NOW - timedelta(days=30), NOW)
        assert 0 < old < recent <= 1

    def test_future_dates_do_not_exceed_one(self):
        assert recency_score(NOW + timedelta(days=1), NOW) == pytest.approx(1.0)

    def test_naive_datetimes_are_treated_as_utc(self):
        naive = (NOW - timedelta(hours=RECENCY_HALF_LIFE_HOURS)).replace(tzinfo=None)
        assert recency_score(naive, NOW) == pytest.approx(0.5)


class TestPopularityScore:
    def test_no_likes_scores_zero(self):
        assert popularity_score(0) == pytest.approx(0.0)

    def test_grows_sublinearly(self):
        assert popularity_score(1000) < 10 * popularity_score(10)

    def test_negative_likes_are_clamped(self):
        assert popularity_score(-5) == pytest.approx(0.0)


class TestScoreCandidate:
    def test_followed_author_gets_the_boost(self):
        video = candidate(FOLLOWED_AUTHOR)
        followed = score_candidate(video, {FOLLOWED_AUTHOR}, NOW)
        not_followed = score_candidate(video, set(), NOW)
        assert followed - not_followed == pytest.approx(FOLLOW_BOOST)

    def test_followed_old_video_beats_fresh_stranger(self):
        """El objetivo del algoritmo: el seguimiento pesa más que la novedad."""
        followed_old = candidate(FOLLOWED_AUTHOR, hours_ago=24 * 14)
        stranger_new = candidate(OTHER_AUTHOR)
        assert (
            score_candidate(followed_old, {FOLLOWED_AUTHOR}, NOW)
            > score_candidate(stranger_new, {FOLLOWED_AUTHOR}, NOW)
        )

    def test_likes_break_ties_between_equally_recent_videos(self):
        popular = candidate(OTHER_AUTHOR, likes=50)
        unknown = candidate(OTHER_AUTHOR, likes=0)
        assert score_candidate(popular, set(), NOW) > score_candidate(unknown, set(), NOW)


class TestRankCandidates:
    def test_followed_videos_go_first(self):
        followed = candidate(FOLLOWED_AUTHOR, hours_ago=48)
        stranger_a = candidate(OTHER_AUTHOR, hours_ago=1)
        stranger_b = candidate(THIRD_AUTHOR, hours_ago=2)

        ranked = rank_candidates([stranger_a, stranger_b, followed], {FOLLOWED_AUTHOR}, NOW)

        assert ranked[0].candidate is followed
        assert ranked[0].from_followed_author is True
        assert [item.from_followed_author for item in ranked[1:]] == [False, False]

    def test_without_follows_falls_back_to_recency(self):
        old = candidate(OTHER_AUTHOR, hours_ago=100)
        new = candidate(THIRD_AUTHOR, hours_ago=1)

        ranked = rank_candidates([old, new], set(), NOW)

        assert [item.candidate for item in ranked] == [new, old]
        assert all(item.from_followed_author is False for item in ranked)

    def test_author_diversity_interleaves_a_prolific_followed_author(self):
        """Un autor con muchos vídeos no debe copar el bloque de los seguidos."""
        prolific = [candidate(FOLLOWED_AUTHOR, hours_ago=hours) for hours in (1, 2, 3)]
        occasional = candidate(OTHER_AUTHOR, hours_ago=10)

        ranked = rank_candidates(
            [*prolific, occasional], {FOLLOWED_AUTHOR, OTHER_AUTHOR}, NOW
        )
        authors = [item.candidate.author_id for item in ranked]

        assert authors == [FOLLOWED_AUTHOR, OTHER_AUTHOR, FOLLOWED_AUTHOR, FOLLOWED_AUTHOR]

    def test_author_diversity_also_applies_without_follows(self):
        prolific = [candidate(OTHER_AUTHOR, hours_ago=hours) for hours in (1, 2, 3)]
        occasional = candidate(THIRD_AUTHOR, hours_ago=10)

        ranked = rank_candidates([*prolific, occasional], set(), NOW)
        authors = [item.candidate.author_id for item in ranked]

        assert authors[1] == THIRD_AUTHOR

    def test_diversity_never_pushes_a_followed_author_below_a_stranger(self):
        """Seguir a alguien manda siempre sobre la variedad de autores."""
        followed_videos = [candidate(FOLLOWED_AUTHOR, hours_ago=hours) for hours in (1, 2, 3)]
        stranger = candidate(OTHER_AUTHOR, hours_ago=1, likes=100)

        ranked = rank_candidates([*followed_videos, stranger], {FOLLOWED_AUTHOR}, NOW)

        assert [item.from_followed_author for item in ranked] == [True, True, True, False]

    def test_diversity_penalty_never_reorders_across_the_follow_boost(self):
        """La penalización por autor es menor que el impulso por seguimiento."""
        assert AUTHOR_DIVERSITY_PENALTY < FOLLOW_BOOST

        followed_videos = [candidate(FOLLOWED_AUTHOR, hours_ago=hours) for hours in (1, 2)]
        strangers = [candidate(OTHER_AUTHOR, hours_ago=1), candidate(THIRD_AUTHOR, hours_ago=1)]

        ranked = rank_candidates([*strangers, *followed_videos], {FOLLOWED_AUTHOR}, NOW)

        assert [item.from_followed_author for item in ranked] == [True, True, False, False]

    def test_limit_truncates_the_result(self):
        candidates = [candidate(OTHER_AUTHOR, hours_ago=index) for index in range(10)]
        assert len(rank_candidates(candidates, set(), NOW, limit=3)) == 3

    def test_limit_larger_than_input_is_harmless(self):
        candidates = [candidate(OTHER_AUTHOR), candidate(THIRD_AUTHOR)]
        assert len(rank_candidates(candidates, set(), NOW, limit=50)) == 2

    def test_empty_input_returns_empty_list(self):
        assert rank_candidates([], {FOLLOWED_AUTHOR}, NOW) == []

    def test_ties_are_resolved_deterministically_by_video_id(self):
        first = candidate(OTHER_AUTHOR, video_id=uuid.UUID(int=1))
        second = candidate(THIRD_AUTHOR, video_id=uuid.UUID(int=2))

        forward = rank_candidates([first, second], set(), NOW)
        backward = rank_candidates([second, first], set(), NOW)

        assert [item.candidate.video_id for item in forward] == [first.video_id, second.video_id]
        assert [item.candidate.video_id for item in forward] == [
            item.candidate.video_id for item in backward
        ]

    def test_every_candidate_is_returned_exactly_once(self):
        candidates = [candidate(FOLLOWED_AUTHOR) for _ in range(3)]
        candidates += [candidate(OTHER_AUTHOR) for _ in range(4)]

        ranked = rank_candidates(candidates, {FOLLOWED_AUTHOR}, NOW)

        assert sorted(str(item.candidate.video_id) for item in ranked) == sorted(
            str(item.video_id) for item in candidates
        )


def test_ensure_utc_keeps_the_instant():
    aware = datetime(2026, 1, 1, 10, 0, tzinfo=UTC)
    assert ensure_utc(aware) == aware
    assert ensure_utc(aware.replace(tzinfo=None)) == aware
