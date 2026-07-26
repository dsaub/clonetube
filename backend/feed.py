"""Algoritmo de recomendación del feed principal.

El objetivo es priorizar los vídeos de los autores que sigue el usuario sin
convertir la portada en una lista monotemática: la puntuación combina el
seguimiento, la novedad y la popularidad, y después se penaliza la repetición
de un mismo autor para mantener variedad.

Este módulo es deliberadamente puro (sin acceso a base de datos ni a S3) para
poder probarlo de forma aislada.
"""

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from math import log1p

# Peso de cada señal dentro de la puntuación final.
FOLLOW_BOOST = 3.0
RECENCY_WEIGHT = 2.0
POPULARITY_WEIGHT = 0.8

# Horas que tarda la señal de novedad en reducirse a la mitad (3 días).
RECENCY_HALF_LIFE_HOURS = 72.0

# Penalización acumulativa por cada vídeo previo del mismo autor ya colocado.
AUTHOR_DIVERSITY_PENALTY = 0.45


@dataclass(frozen=True)
class FeedCandidate:
    """Vídeo elegible para el feed con las señales necesarias para ordenarlo."""

    video_id: uuid.UUID
    author_id: uuid.UUID
    created_at: datetime
    likes: int = 0


@dataclass(frozen=True)
class ScoredVideo:
    """Candidato con su puntuación final y el motivo principal de su posición."""

    candidate: FeedCandidate
    score: float
    from_followed_author: bool


def ensure_utc(value: datetime) -> datetime:
    """Normaliza a UTC: SQLite y MariaDB devuelven fechas sin zona horaria."""
    return value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)


def recency_score(created_at: datetime, now: datetime) -> float:
    """Decaimiento exponencial en (0, 1]: 1 recién subido, 0.5 tras la semivida."""
    age_hours = max((ensure_utc(now) - ensure_utc(created_at)).total_seconds(), 0.0) / 3600
    return 0.5 ** (age_hours / RECENCY_HALF_LIFE_HOURS)


def popularity_score(likes: int) -> float:
    """Crecimiento logarítmico para que unos pocos vídeos virales no lo copen todo."""
    return log1p(max(likes, 0))


def score_candidate(
    candidate: FeedCandidate,
    followed_ids: set[uuid.UUID],
    now: datetime,
) -> float:
    """Puntuación base de un vídeo: seguimiento + novedad + popularidad."""
    score = RECENCY_WEIGHT * recency_score(candidate.created_at, now)
    score += POPULARITY_WEIGHT * popularity_score(candidate.likes)
    if candidate.author_id in followed_ids:
        score += FOLLOW_BOOST
    return score


def rank_candidates(
    candidates: list[FeedCandidate],
    followed_ids: set[uuid.UUID],
    now: datetime,
    limit: int | None = None,
) -> list[ScoredVideo]:
    """Ordena los candidatos priorizando a los autores seguidos.

    Los vídeos de autores seguidos forman un bloque que siempre va delante: es
    un criterio de orden, no un sumando, así que ni un vídeo viral ni la
    penalización por diversidad pueden colar a un desconocido por delante.

    Dentro de cada bloque la selección es voraz: en cada paso se elige el
    candidato con mayor puntuación ajustada, donde el ajuste resta
    `AUTHOR_DIVERSITY_PENALTY` por cada vídeo del mismo autor ya seleccionado.
    Los empates se resuelven por el identificador del vídeo para que el orden
    sea determinista.
    """
    remaining = [
        ScoredVideo(
            candidate=candidate,
            score=score_candidate(candidate, followed_ids, now),
            from_followed_author=candidate.author_id in followed_ids,
        )
        for candidate in candidates
    ]
    remaining.sort(key=lambda item: _sort_key(item, {}))

    target = len(remaining) if limit is None else min(limit, len(remaining))
    ranked: list[ScoredVideo] = []
    per_author: dict[uuid.UUID, int] = {}

    while remaining and len(ranked) < target:
        best_index = min(
            range(len(remaining)),
            key=lambda index: _sort_key(remaining[index], per_author),
        )
        chosen = remaining.pop(best_index)
        per_author[chosen.candidate.author_id] = per_author.get(chosen.candidate.author_id, 0) + 1
        ranked.append(chosen)

    return ranked


def _sort_key(item: ScoredVideo, per_author: dict[uuid.UUID, int]) -> tuple[bool, float, str]:
    """Bloque de seguidos primero, luego puntuación ajustada y, al empatar, el id."""
    already_placed = per_author.get(item.candidate.author_id, 0)
    adjusted = item.score - AUTHOR_DIVERSITY_PENALTY * already_placed
    return (not item.from_followed_author, -adjusted, str(item.candidate.video_id))
