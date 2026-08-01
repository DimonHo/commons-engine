import { useState } from "react";
import { useAsync } from "@/hooks/useAsync";
import { disputeApi, type Dispute, type DisputeStatus } from "@/api/client";

export function DisputesPage() {
  const [refreshKey, setRefreshKey] = useState(0);
  const { data, loading, error } = useAsync<Dispute[]>(
    () => disputeApi.list(),
    [refreshKey],
  );
  const [actionError, setActionError] = useState<string | null>(null);

  async function handleScreen(id: string) {
    setActionError(null);
    const notes = window.prompt("筛查备注：");
    if (notes === null) return;
    try {
      await disputeApi.screen(id, notes);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handleArbitrate(id: string) {
    setActionError(null);
    const decision = window.prompt("裁决决定（RESOLVED / REJECTED）：", "RESOLVED");
    if (decision === null) return;
    const ruling = window.prompt("裁决理由：");
    if (ruling === null) return;
    try {
      await disputeApi.arbitrate(id, decision, ruling);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">纠纷仲裁</h1>
        <p className="page-desc">AI 初筛 + 仲裁委员会处理的纠纷全流程</p>
      </div>

      {actionError && <div className="error-banner">{actionError}</div>}
      {error && <div className="error-banner">{error}</div>}
      {loading && <div className="loading">加载中…</div>}

      {data && (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>纠纷 ID</th>
                <th>发起人 → 对方</th>
                <th>关联交易</th>
                <th>原因</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {data.map((d) => (
                <tr key={d.id.value}>
                  <td className="mono">{d.id.value.slice(0, 8)}…</td>
                  <td>{d.filedBy} → {d.against}</td>
                  <td className="mono">{d.transactionId.slice(0, 8)}…</td>
                  <td style={{ maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis" }}>
                    {d.reason}
                  </td>
                  <td>
                    <span className={`badge badge--${disputeBadge(d.status)}`}>{d.status}</span>
                  </td>
                  <td>
                    {(d.status === "FILED") && (
                      <button className="btn btn--secondary" onClick={() => handleScreen(d.id.value)}>
                        初筛
                      </button>
                    )}
                    {(d.status === "SCREENING") && (
                      <button className="btn btn--secondary" onClick={() => handleArbitrate(d.id.value)}>
                        仲裁
                      </button>
                    )}
                    {(d.status === "RESOLVED" || d.status === "REJECTED") && (
                      <span style={{ color: "var(--text-dim)" }}>—</span>
                    )}
                  </td>
                </tr>
              ))}
              {data.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: "center", color: "var(--text-dim)" }}>
                    暂无纠纷
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

function disputeBadge(status: DisputeStatus): string {
  switch (status) {
    case "FILED": return "warning";
    case "SCREENING": return "warning";
    case "ARBITRATING": return "warning";
    case "RESOLVED": return "success";
    case "REJECTED": return "danger";
    default: return "neutral";
  }
}
