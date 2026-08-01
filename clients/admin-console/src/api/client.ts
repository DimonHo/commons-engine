/**
 * 公地引擎后端 API 客户端
 *
 * 对应后端 8 个 REST 模块（Spring Boot 4.x + Kotlin）：
 * /api/v1/platform, /matching, /members, /payment, /rating,
 * /dispute, /dispatch, /governance
 *
 * 端点签名严格对齐 backend 各模块 Controller 的路径映射。
 *
 * — Commons Engine Chief Engineer Bot（AI），admin-console scaffold
 */

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

/** 后端统一错误响应体（GlobalExceptionHandler）。 */
export interface ApiError {
  error: string;
  message: string;
  status: number;
  timestamp?: string;
  path?: string;
  details?: Record<string, string>;
}

export class ApiException extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiError | unknown,
    message: string,
  ) {
    super(message);
    this.name = "ApiException";
  }
}

async function request<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!res.ok) {
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      body = await res.text();
    }
    throw new ApiException(res.status, body, `API ${res.status}: ${res.statusText}`);
  }

  // 204 No Content 或空 body
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

// ── 平台健康 ──────────────────────────────────────────

export interface PlatformHealth {
  status: string;
  version: string;
  modules: {
    matching: { status: string; currentStrategy: string; availableStrategies: string[] };
    identity: { status: string; totalMembers: number; roleBreakdown: Record<string, number> };
    payment: { status: string; ledgerEvents: number };
    rating: { status: string };
    dispatch: { status: string };
  };
}

export const platformApi = {
  health: () => request<PlatformHealth>("/api/v1/platform/health"),
};

// ── 匹配引擎 ──────────────────────────────────────────

export interface MatchingHealth {
  status: string;
  currentStrategy: string;
  availableStrategies: string[];
}

export interface MatchResponse {
  matched: boolean;
  workerId?: string;
  workerName?: string;
  distanceMeters?: number;
  strategy: string;
  reason: string;
}

export interface AutoMatchRequest {
  consumerId: string;
  serviceType: string; // RIDE_HAILING | FOOD_DELIVERY | HOUSEKEEPING
  pickupLat: number;
  pickupLng: number;
  radiusMeters?: number;
  maxActiveOrders?: number;
}

export const matchingApi = {
  health: () => request<MatchingHealth>("/api/v1/matching/health"),
  setStrategy: (strategy: string) =>
    request<{ status: string; currentStrategy: string }>("/api/v1/matching/strategy", {
      method: "POST",
      body: JSON.stringify({ strategy }),
    }),
  autoMatch: (req: AutoMatchRequest) =>
    request<MatchResponse>("/api/v1/matching/match/auto", {
      method: "POST",
      body: JSON.stringify(req),
    }),
};

// ── 会员系统 ──────────────────────────────────────────

export type MemberRole = "CONSUMER" | "WORKER" | "COMMUNITY_REP";

export interface Member {
  id: { value: string };
  name: string;
  phone: string;
  roles: MemberRole[];
}

export interface MemberStats {
  CONSUMER?: number;
  WORKER?: number;
  COMMUNITY_REP?: number;
  [k: string]: number | undefined;
}

export const membersApi = {
  register: (name: string, phone: string, roles: MemberRole[]) =>
    request<Member>("/api/v1/members/register", {
      method: "POST",
      body: JSON.stringify({ name, phone, roles }),
    }),
  getById: (id: string) => request<Member>(`/api/v1/members/${id}`),
  list: () => request<Member[]>("/api/v1/members"),
  stats: () => request<MemberStats>("/api/v1/members/stats"),
};

// ── 支付分账 ──────────────────────────────────────────

export interface Settlement {
  transactionId: string;
  totalAmount: string;
  workerPayout: string;
  platformFee: string;
  reserve: string;
  workerShareRatio: string;
}

export interface LedgerHistoryEntry {
  eventId: string;
  transactionId: string;
  eventType: string;
  amount: string;
  timestamp: string;
}

export const paymentApi = {
  charge: (consumerId: string, workerId: string, amount: string, serviceType: string) =>
    request<{ transactionId: string; status: string }>("/api/v1/payment/charge", {
      method: "POST",
      body: JSON.stringify({ consumerId, workerId, amount, serviceType }),
    }),
  settle: (transactionId: string) =>
    request<Settlement>(`/api/v1/payment/${transactionId}/settle`, { method: "POST" }),
  refund: (transactionId: string) =>
    request<{ transactionId: string; status: string }>(
      `/api/v1/payment/${transactionId}/refund`,
      { method: "POST" },
    ),
  history: (transactionId: string) =>
    request<LedgerHistoryEntry[]>(`/api/v1/payment/${transactionId}/history`),
};

// ── 信用评价 ──────────────────────────────────────────

export interface CreditProfile {
  memberId: string;
  totalRatings: number;
  averageScore: number;
}

export interface RatingRecord {
  id: { value: string };
  transactionId: string;
  raterId: string;
  rateeId: string;
  direction: "CONSUMER_TO_WORKER" | "WORKER_TO_CONSUMER";
  score: number;
  tags: string[];
  comment?: string;
}

export const ratingApi = {
  profile: (memberId: string) =>
    request<CreditProfile>(`/api/v1/rating/profile/${memberId}`),
  received: (memberId: string) =>
    request<RatingRecord[]>(`/api/v1/rating/received/${memberId}`),
  given: (memberId: string) =>
    request<RatingRecord[]>(`/api/v1/rating/given/${memberId}`),
};

// ── 纠纷仲裁 ──────────────────────────────────────────

export type DisputeStatus = "FILED" | "SCREENING" | "ARBITRATING" | "RESOLVED" | "REJECTED";

export interface Dispute {
  id: { value: string };
  filedBy: string;
  against: string;
  transactionId: string;
  reason: string;
  status: DisputeStatus;
}

export const disputeApi = {
  list: () => request<Dispute[]>("/api/v1/dispute"),
  getById: (id: string) => request<Dispute>(`/api/v1/dispute/${id}`),
  file: (filedBy: string, against: string, transactionId: string, reason: string) =>
    request<Dispute>("/api/v1/dispute/file", {
      method: "POST",
      body: JSON.stringify({ filedBy, against, transactionId, reason }),
    }),
  screen: (id: string, screeningNotes: string) =>
    request<Dispute>(`/api/v1/dispute/${id}/screening`, {
      method: "POST",
      body: JSON.stringify({ screeningNotes }),
    }),
  arbitrate: (id: string, decision: string, ruling: string) =>
    request<Dispute>(`/api/v1/dispute/${id}/arbitrate`, {
      method: "POST",
      body: JSON.stringify({ decision, ruling }),
    }),
};

// ── 调度引擎 ──────────────────────────────────────────

export interface DispatchTask {
  id: string;
  workerId: string;
  serviceType: string;
  status: string;
}

export interface WorkerPreferences {
  workerId: string;
  preferredServiceTypes: string[];
}

export const dispatchApi = {
  getTask: (taskId: string) => request<DispatchTask>(`/api/v1/dispatch/tasks/${taskId}`),
  getWorkerTasks: (workerId: string) =>
    request<DispatchTask[]>(`/api/v1/dispatch/workers/${workerId}/tasks`),
  setPreferences: (workerId: string, preferredServiceTypes: string[]) =>
    request<{ status: string }>(`/api/v1/dispatch/workers/${workerId}/preferences`, {
      method: "POST",
      body: JSON.stringify({ workerId, preferredServiceTypes }),
    }),
};

// ── 治理模块 ──────────────────────────────────────────

export type ProposalStatus = "DRAFT" | "VOTING" | "PASSED" | "REJECTED";

export interface Proposal {
  id: { value: string };
  title: string;
  description: string;
  status: ProposalStatus;
}

export interface VoteTally {
  proposalId: string;
  yes: number;
  no: number;
  abstain: number;
}

export const governanceApi = {
  listProposals: () => request<Proposal[]>("/api/v1/governance/proposals"),
  getProposal: (id: string) => request<Proposal>(`/api/v1/governance/proposals/${id}`),
  createProposal: (title: string, description: string) =>
    request<Proposal>("/api/v1/governance/proposals", {
      method: "POST",
      body: JSON.stringify({ title, description }),
    }),
  startVote: (id: string) =>
    request<Proposal>(`/api/v1/governance/proposals/${id}/start-vote`, { method: "POST" }),
  vote: (id: string, choice: string, voterId: string, stakeholderType: string) =>
    request<{ status: string }>(`/api/v1/governance/proposals/${id}/vote`, {
      method: "POST",
      body: JSON.stringify({ choice, voterId, stakeholderType }),
    }),
  tally: (id: string) =>
    request<VoteTally>(`/api/v1/governance/proposals/${id}/tally`, { method: "POST" }),
};
