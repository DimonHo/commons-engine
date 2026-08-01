import { useState } from "react";
import { useAsync } from "@/hooks/useAsync";
import {
  governanceApi,
  type Proposal,
  type ProposalStatus,
  type VoteTally,
} from "@/api/client";

export function GovernancePage() {
  const [refreshKey, setRefreshKey] = useState(0);
  const { data, loading, error } = useAsync<Proposal[]>(
    () => governanceApi.listProposals(),
    [refreshKey],
  );
  const [actionError, setActionError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [tallies, setTallies] = useState<Record<string, VoteTally>>({});

  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setActionError(null);
    setSubmitting(true);
    const form = new FormData(e.currentTarget);
    try {
      await governanceApi.createProposal(
        String(form.get("title")),
        String(form.get("description")),
      );
      setShowForm(false);
      setRefreshKey((k) => k + 1);
      e.currentTarget.reset();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleStartVote(id: string) {
    setActionError(null);
    try {
      await governanceApi.startVote(id);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handleTally(id: string) {
    setActionError(null);
    try {
      const t = await governanceApi.tally(id);
      setTallies((prev) => ({ ...prev, [id]: t }));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">治理议事</h1>
        <p className="page-desc">合作社提案、投票、表决、透明账目</p>
      </div>

      {actionError && <div className="error-banner">{actionError}</div>}
      {error && <div className="error-banner">{error}</div>}

      <button
        className="btn btn--primary"
        onClick={() => setShowForm((s) => !s)}
        style={{ marginBottom: 16 }}
      >
        {showForm ? "取消" : "+ 新建提案"}
      </button>

      {showForm && (
        <div className="card" style={{ marginBottom: 16 }}>
          <form onSubmit={handleCreate}>
            <div className="form-group">
              <label className="form-label">提案标题</label>
              <input className="form-input" name="title" required />
            </div>
            <div className="form-group">
              <label className="form-label">提案描述</label>
              <textarea
                className="form-input"
                name="description"
                rows={4}
                required
                style={{ resize: "vertical" }}
              />
            </div>
            <button className="btn btn--primary" type="submit" disabled={submitting}>
              {submitting ? "提交中…" : "创建提案"}
            </button>
          </form>
        </div>
      )}

      {loading && <div className="loading">加载中…</div>}

      {data && (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>提案</th>
                <th>状态</th>
                <th>投票统计</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {data.map((p) => (
                <tr key={p.id.value}>
                  <td>
                    <strong>{p.title}</strong>
                    <div style={{ fontSize: 12, color: "var(--text-dim)", marginTop: 2 }}>
                      {p.description.slice(0, 80)}
                      {p.description.length > 80 ? "…" : ""}
                    </div>
                  </td>
                  <td>
                    <span className={`badge badge--${proposalBadge(p.status)}`}>{p.status}</span>
                  </td>
                  <td>
                    {tallies[p.id.value] ? (
                      <span className="mono">
                        ✅ {tallies[p.id.value].yes} · ❌ {tallies[p.id.value].no} · ⊘ {tallies[p.id.value].abstain}
                      </span>
                    ) : (
                      <span style={{ color: "var(--text-dim)" }}>—</span>
                    )}
                  </td>
                  <td>
                    {p.status === "DRAFT" && (
                      <button className="btn btn--secondary" onClick={() => handleStartVote(p.id.value)}>
                        发起投票
                      </button>
                    )}
                    {p.status === "VOTING" && (
                      <button className="btn btn--secondary" onClick={() => handleTally(p.id.value)}>
                        统计票数
                      </button>
                    )}
                    {(p.status === "PASSED" || p.status === "REJECTED") && (
                      <button className="btn btn--secondary" onClick={() => handleTally(p.id.value)}>
                        查看票数
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {data.length === 0 && (
                <tr>
                  <td colSpan={4} style={{ textAlign: "center", color: "var(--text-dim)" }}>
                    暂无提案
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function proposalBadge(status: ProposalStatus): string {
  switch (status) {
    case "DRAFT": return "neutral";
    case "VOTING": return "warning";
    case "PASSED": return "success";
    case "REJECTED": return "danger";
    default: return "neutral";
  }
}
