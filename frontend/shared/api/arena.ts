import {
  ArenaDuelResponse,
  ArenaVotePayload,
  ArenaVoteResponse,
} from "@/shared/types/arena";

export async function getArenaDuel() {
  const res = await fetch("/api/arena/duel", {
    cache: "no-store",
  });

  if (!res.ok) {
    throw await res.json();
  }

  return res.json() as Promise<ArenaDuelResponse>;
}

export async function submitArenaVote(payload: ArenaVotePayload) {
  const res = await fetch("/api/arena/vote", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      battleId: payload.battleId,
      winner: payload.winner,
      idempotencyKey: payload.idempotencyKey || crypto.randomUUID(),
    }),
  });

  if (!res.ok) {
    throw await res.json();
  }

  return res.json() as Promise<ArenaVoteResponse>;
}
