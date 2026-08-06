package me.elordenador.clonetube.feed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Algoritmo de recomendación del feed (port de backend.old/feed.py).
 * Puro, sin BD ni S3, para poder probarlo aislado.
 *
 * Los vídeos de autores seguidos forman un bloque que siempre va delante;
 * dentro de cada bloque, selección voraz por puntuación ajustada
 * (recency + likes + boost de seguido, menos penalización por repetición
 * de autor). Empates resueltos por video id para un orden determinista.
 */
public final class FeedRanker {

    public static final double FOLLOW_BOOST = 3.0;
    public static final double RECENCY_WEIGHT = 2.0;
    public static final double POPULARITY_WEIGHT = 0.8;
    public static final double RECENCY_HALF_LIFE_HOURS = 72.0;
    public static final double AUTHOR_DIVERSITY_PENALTY = 0.45;

    private FeedRanker() {
    }

    public record FeedCandidate(int videoId, int authorId, Instant createdAt, long likes) {
        public FeedCandidate {
            likes = Math.max(likes, 0);
        }
    }

    public record ScoredVideo(FeedCandidate candidate, double score, boolean fromFollowedAuthor) {
    }

    public static double recencyScore(Instant createdAt, Instant now) {
        double ageHours = Math.max(0, (now.toEpochMilli() - createdAt.toEpochMilli()) / 3600_000.0);
        return Math.pow(0.5, ageHours / RECENCY_HALF_LIFE_HOURS);
    }

    public static double popularityScore(long likes) {
        return Math.log1p(Math.max(likes, 0));
    }

    public static double scoreCandidate(FeedCandidate candidate, Set<Integer> followedIds, Instant now) {
        double score = RECENCY_WEIGHT * recencyScore(candidate.createdAt(), now);
        score += POPULARITY_WEIGHT * popularityScore(candidate.likes());
        if (followedIds.contains(candidate.authorId())) {
            score += FOLLOW_BOOST;
        }
        return score;
    }

    public static List<ScoredVideo> rankCandidates(
            List<FeedCandidate> candidates,
            Set<Integer> followedIds,
            Instant now,
            int limit) {
        List<ScoredVideo> remaining = new ArrayList<>(candidates.size());
        for (FeedCandidate candidate : candidates) {
            boolean followed = followedIds.contains(candidate.authorId());
            remaining.add(new ScoredVideo(candidate, scoreCandidate(candidate, followedIds, now), followed));
        }
        remaining.sort(Comparator.comparing((ScoredVideo s) -> s.fromFollowedAuthor()).reversed());

        int target = Math.min(limit, remaining.size());
        List<ScoredVideo> ranked = new ArrayList<>(target);
        int[] perAuthor = new int[candidates.size() == 0 ? 1 : maxAuthorId(candidates) + 1];

        while (!remaining.isEmpty() && ranked.size() < target) {
            int bestIndex = 0;
            ScoredVideo best = remaining.get(0);
            for (int i = 1; i < remaining.size(); i++) {
                ScoredVideo item = remaining.get(i);
                if (compare(item, best, perAuthor) < 0) {
                    best = item;
                    bestIndex = i;
                }
            }
            remaining.remove(bestIndex);
            perAuthor[best.candidate().authorId()]++;
            ranked.add(best);
        }
        return ranked;
    }

    private static int maxAuthorId(List<FeedCandidate> candidates) {
        int max = 0;
        for (FeedCandidate candidate : candidates) {
            max = Math.max(max, candidate.authorId());
        }
        return max;
    }

    /** Orden: seguidos primero; luego score ajustado (mayor va antes); empate por video id. */
    private static int compare(ScoredVideo a, ScoredVideo b, int[] perAuthor) {
        if (a.fromFollowedAuthor() != b.fromFollowedAuthor()) {
            return a.fromFollowedAuthor() ? -1 : 1;
        }
        double adjustedA = a.score() - AUTHOR_DIVERSITY_PENALTY * perAuthor[a.candidate().authorId()];
        double adjustedB = b.score() - AUTHOR_DIVERSITY_PENALTY * perAuthor[b.candidate().authorId()];
        if (adjustedA != adjustedB) {
            return Double.compare(adjustedB, adjustedA);
        }
        return Integer.compare(a.candidate().videoId(), b.candidate().videoId());
    }
}
