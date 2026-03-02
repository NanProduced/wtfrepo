import type { ArchiveSpecimen } from "@/shared/types/specimen";

export interface RankedArchiveSpecimen {
  specimen: ArchiveSpecimen;
  rank: number;
}

export interface ArchiveInsights {
  summary: {
    total: number;
    averageElo: number;
    averageHype: number;
    averageVotes: number;
  };
  topElo: RankedArchiveSpecimen[];
  topHype: RankedArchiveSpecimen[];
  rankingByElo: Record<string, number>;
  rankingByHype: Record<string, number>;
  momentum: {
    rising: number;
    flat: number;
    falling: number;
    topMover: RankedArchiveSpecimen | null;
  };
}

interface MetricAccessor {
  (specimen: ArchiveSpecimen): number;
}

function createRankedList(items: ArchiveSpecimen[], metricAccessor: MetricAccessor): RankedArchiveSpecimen[] {
  const sorted = [...items].sort((left, right) => {
    const scoreDiff = metricAccessor(right) - metricAccessor(left);
    if (scoreDiff !== 0) {
      return scoreDiff;
    }

    return left.repoFullName.localeCompare(right.repoFullName);
  });

  let previousScore: number | null = null;
  let previousRank = 0;

  return sorted.map((specimen, index) => {
    const currentScore = metricAccessor(specimen);
    const rank = previousScore === currentScore ? previousRank : index + 1;
    previousScore = currentScore;
    previousRank = rank;

    return { specimen, rank };
  });
}

function toRankMap(items: RankedArchiveSpecimen[]): Record<string, number> {
  return items.reduce<Record<string, number>>((accumulator, item) => {
    accumulator[item.specimen.specimenId] = item.rank;
    return accumulator;
  }, {});
}

function resolveDelta(specimen: ArchiveSpecimen): number | null {
  const delta = specimen.metrics.delta24h;
  if (typeof delta !== "number" || Number.isNaN(delta)) {
    return null;
  }
  return delta;
}

export function buildArchiveInsights(items: ArchiveSpecimen[]): ArchiveInsights {
  if (items.length === 0) {
    return {
      summary: {
        total: 0,
        averageElo: 0,
        averageHype: 0,
        averageVotes: 0,
      },
      topElo: [],
      topHype: [],
      rankingByElo: {},
      rankingByHype: {},
      momentum: {
        rising: 0,
        flat: 0,
        falling: 0,
        topMover: null,
      },
    };
  }

  const topElo = createRankedList(items, (specimen) => specimen.metrics.elo);
  const topHype = createRankedList(items, (specimen) => specimen.metrics.hype);

  let rising = 0;
  let flat = 0;
  let falling = 0;
  let eloSum = 0;
  let hypeSum = 0;
  let votesSum = 0;

  for (const item of items) {
    eloSum += item.metrics.elo;
    hypeSum += item.metrics.hype;
    votesSum += item.metrics.votes;

    const delta = resolveDelta(item);
    if (delta === null || delta === 0) {
      flat += 1;
    } else if (delta > 0) {
      rising += 1;
    } else {
      falling += 1;
    }
  }

  const topMoverCandidates = createRankedList(
    items.filter((item) => resolveDelta(item) !== null),
    (specimen) => Math.abs(resolveDelta(specimen) ?? 0)
  );

  return {
    summary: {
      total: items.length,
      averageElo: eloSum / items.length,
      averageHype: hypeSum / items.length,
      averageVotes: votesSum / items.length,
    },
    topElo,
    topHype,
    rankingByElo: toRankMap(topElo),
    rankingByHype: toRankMap(topHype),
    momentum: {
      rising,
      flat,
      falling,
      topMover: topMoverCandidates[0] ?? null,
    },
  };
}
