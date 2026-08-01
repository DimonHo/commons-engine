import { useState } from "react";
import { useAsync } from "@/hooks/useAsync";
import { matchingApi, type MatchingHealth, type MatchResponse } from "@/api/client";

export function MatchingPage() {
  const { data: health, loading, error } = useAsync<MatchingHealth>(
    () => matchingApi.health(),
  );
  const [strategyError, setStrategyError] = useState<string | null>(null);
  const [matchResult, setMatchResult] = useState<MatchResponse | null>(null);
  const [matchError, setMatchError] = useState<string | null>(null);
  const [matching, setMatching] = useState(false);

  async function handleSetStrategy(strategy: string) {
    setStrategyError(null);
    try {
      await matchingApi.setStrategy(strategy);
      window.location.reload();
    } catch (err) {
      setStrategyError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handleAutoMatch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setMatchError(null);
    setMatching(true);
    const form = new FormData(e.currentTarget);
    try {
      const result = await matchingApi.autoMatch({
        consumerId: String(form.get("consumerId")),
        serviceType: String(form.get("serviceType")),
        pickupLat: Number(form.get("pickupLat")),
        pickupLng: Number(form.get("pickupLng")),
        radiusMeters: Number(form.get("radiusMeters")) || undefined,
      });
      setMatchResult(result);
    } catch (err) {
      setMatchError(err instanceof Error ? err.message : String(err));
    } finally {
      setMatching(false);
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">匹配引擎</h1>
        <p className="page-desc">派单策略配置与自动匹配测试</p>
      </div>

      {error && <div className="error-banner">{error}</div>}
      {loading && <div className="loading">加载中…</div>}

      {health && (
        <>
          <div className="card-grid">
            <div className="card">
              <div className="card-label">引擎状态</div>
              <div className="card-value card-value--success">
                <span className="status-dot status-dot--up" />
                {health.status}
              </div>
            </div>
            <div className="card">
              <div className="card-label">当前策略</div>
              <div className="card-value">{health.currentStrategy}</div>
            </div>
          </div>

          <h2 className="section-title">可用策略</h2>
          {strategyError && <div className="error-banner">{strategyError}</div>}
          <div className="strategy-list" style={{ marginBottom: 24 }}>
            {health.availableStrategies.map((s) => (
              <button
                key={s}
                className={`strategy-chip ${s === health.currentStrategy ? "strategy-chip--active" : ""}`}
                onClick={() => handleSetStrategy(s)}
                style={{ border: "none", cursor: "pointer" }}
              >
                {s}
              </button>
            ))}
          </div>

          <h2 className="section-title">自动匹配测试</h2>
          <div className="card">
            {matchError && <div className="error-banner">{matchError}</div>}
            <form onSubmit={handleAutoMatch}>
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">消费者 ID</label>
                  <input className="form-input" name="consumerId" required placeholder="uuid" />
                </div>
                <div className="form-group">
                  <label className="form-label">服务类型</label>
                  <select className="form-select" name="serviceType" defaultValue="RIDE_HAILING">
                    <option value="RIDE_HAILING">RIDE_HAILING（打车）</option>
                    <option value="FOOD_DELIVERY">FOOD_DELIVERY（外卖）</option>
                    <option value="HOUSEKEEPING">HOUSEKEEPING（家政）</option>
                  </select>
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">纬度</label>
                  <input className="form-input" name="pickupLat" required defaultValue="39.9332" step="0.0001" />
                </div>
                <div className="form-group">
                  <label className="form-label">经度</label>
                  <input className="form-input" name="pickupLng" required defaultValue="116.4543" step="0.0001" />
                </div>
                <div className="form-group">
                  <label className="form-label">搜索半径 (米)</label>
                  <input className="form-input" name="radiusMeters" type="number" defaultValue="5000" />
                </div>
              </div>
              <button className="btn btn--primary" type="submit" disabled={matching}>
                {matching ? "匹配中…" : "执行匹配"}
              </button>
            </form>

            {matchResult && (
              <div style={{ marginTop: 16, padding: 16, background: "var(--bg)", borderRadius: 8 }}>
                <strong>匹配结果</strong>
                {matchResult.matched ? (
                  <div style={{ marginTop: 8 }}>
                    <p>✅ 匹配成功 · <code>{matchResult.strategy}</code></p>
                    <p>劳动者: {matchResult.workerName}（{matchResult.workerId}）</p>
                    <p>距离: {matchResult.distanceMeters?.toFixed(0)} 米</p>
                    <p style={{ color: "var(--text-dim)" }}>理由: {matchResult.reason}</p>
                  </div>
                ) : (
                  <p style={{ marginTop: 8, color: "var(--warning)" }}>⚠ {matchResult.reason}</p>
                )}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
