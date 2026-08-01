import { useState } from "react";
import { useAsync } from "@/hooks/useAsync";
import { membersApi, type Member, type MemberRole } from "@/api/client";

export function MembersPage() {
  const [refreshKey, setRefreshKey] = useState(0);
  const { data, loading, error } = useAsync<Member[]>(
    () => membersApi.list(),
    [refreshKey],
  );
  const [showForm, setShowForm] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleRegister(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    const form = new FormData(e.currentTarget);
    try {
      await membersApi.register(
        String(form.get("name")),
        String(form.get("phone")),
        (form.getAll("roles") as string[]).map((r) => r as MemberRole),
      );
      setShowForm(false);
      setRefreshKey((k) => k + 1);
      e.currentTarget.reset();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">会员管理</h1>
        <p className="page-desc">合作社成员注册与角色管理</p>
      </div>

      <button
        className="btn btn--primary"
        onClick={() => setShowForm((s) => !s)}
        style={{ marginBottom: 16 }}
      >
        {showForm ? "取消" : "+ 注册新成员"}
      </button>

      {showForm && (
        <div className="card" style={{ marginBottom: 16 }}>
          {formError && <div className="error-banner">{formError}</div>}
          <form onSubmit={handleRegister}>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label">姓名</label>
                <input className="form-input" name="name" required />
              </div>
              <div className="form-group">
                <label className="form-label">手机号</label>
                <input className="form-input" name="phone" required placeholder="13800001111" />
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">角色</label>
              <div style={{ display: "flex", gap: 16 }}>
                {(["CONSUMER", "WORKER", "COMMUNITY_REP"] as MemberRole[]).map((r) => (
                  <label key={r} style={{ display: "flex", gap: 6, alignItems: "center" }}>
                    <input type="checkbox" name="roles" value={r} />
                    <span style={{ fontSize: 14 }}>{r}</span>
                  </label>
                ))}
              </div>
            </div>
            <button className="btn btn--primary" type="submit" disabled={submitting}>
              {submitting ? "提交中…" : "注册"}
            </button>
          </form>
        </div>
      )}

      {error && <div className="error-banner">{error}</div>}

      {loading && <div className="loading">加载中…</div>}

      {data && (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>姓名</th>
                <th>手机号</th>
                <th>角色</th>
              </tr>
            </thead>
            <tbody>
              {data.map((m) => (
                <tr key={m.id.value}>
                  <td className="mono">{m.id.value.slice(0, 8)}…</td>
                  <td>{m.name}</td>
                  <td className="mono">{m.phone}</td>
                  <td>
                    {m.roles.map((r) => (
                      <span key={r} className={`badge badge--${roleBadge(r)}`}>
                        {r}
                      </span>
                    ))}
                  </td>
                </tr>
              ))}
              {data.length === 0 && (
                <tr>
                  <td colSpan={4} style={{ textAlign: "center", color: "var(--text-dim)" }}>
                    暂无成员
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

function roleBadge(role: string): string {
  switch (role) {
    case "WORKER": return "success";
    case "CONSUMER": return "neutral";
    case "COMMUNITY_REP": return "warning";
    default: return "neutral";
  }
}
