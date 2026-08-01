import { useState } from "react";
import { paymentApi, type Settlement, type LedgerHistoryEntry } from "@/api/client";

export function PaymentPage() {
  const [chargeResult, setChargeResult] = useState<{ transactionId: string; status: string } | null>(null);
  const [settlement, setSettlement] = useState<Settlement | null>(null);
  const [history, setHistory] = useState<LedgerHistoryEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleCharge(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    const form = new FormData(e.currentTarget);
    try {
      const result = await paymentApi.charge(
        String(form.get("consumerId")),
        String(form.get("workerId")),
        String(form.get("amount")),
        String(form.get("serviceType")),
      );
      setChargeResult(result);
      setSettlement(null);
      setHistory(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleSettle() {
    if (!chargeResult) return;
    setError(null);
    setBusy(true);
    try {
      const s = await paymentApi.settle(chargeResult.transactionId);
      setSettlement(s);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleHistory() {
    if (!chargeResult) return;
    setError(null);
    try {
      const h = await paymentApi.history(chargeResult.transactionId);
      setHistory(h);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">支付分账</h1>
        <p className="page-desc">交易收费、透明分账、账本审计</p>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="card" style={{ marginBottom: 16 }}>
        <h3 style={{ marginBottom: 12, fontSize: 16 }}>发起收费</h3>
        <form onSubmit={handleCharge}>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">消费者 ID</label>
              <input className="form-input" name="consumerId" required />
            </div>
            <div className="form-group">
              <label className="form-label">劳动者 ID</label>
              <input className="form-input" name="workerId" required />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">金额 (¥)</label>
              <input className="form-input" name="amount" required defaultValue="35.50" step="0.01" />
            </div>
            <div className="form-group">
              <label className="form-label">服务类型</label>
              <select className="form-select" name="serviceType" defaultValue="RIDE_HAILING">
                <option value="RIDE_HAILING">RIDE_HAILING</option>
                <option value="FOOD_DELIVERY">FOOD_DELIVERY</option>
                <option value="HOUSEKEEPING">HOUSEKEEPING</option>
              </select>
            </div>
          </div>
          <button className="btn btn--primary" type="submit" disabled={busy}>
            {busy ? "处理中…" : "收费"}
          </button>
        </form>
      </div>

      {chargeResult && (
        <div className="card" style={{ marginBottom: 16 }}>
          <p style={{ marginBottom: 12 }}>
            交易 <code>{chargeResult.transactionId.slice(0, 8)}…</code> · 状态: {chargeResult.status}
          </p>
          <div style={{ display: "flex", gap: 8 }}>
            <button className="btn btn--secondary" onClick={handleSettle} disabled={busy}>
              执行分账
            </button>
            <button className="btn btn--secondary" onClick={handleHistory}>
              查看账本
            </button>
          </div>
        </div>
      )}

      {settlement && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h3 className="section-title" style={{ marginTop: 0 }}>分账明细</h3>
          <div className="table-container">
            <table>
              <tbody>
                <tr><td>交易总额</td><td className="mono">¥{settlement.totalAmount}</td></tr>
                <tr><td>劳动者所得</td><td className="mono" style={{ color: "var(--success)" }}>¥{settlement.workerPayout}</td></tr>
                <tr><td>平台运营费</td><td className="mono">¥{settlement.platformFee}</td></tr>
                <tr><td>公积金</td><td className="mono">¥{settlement.reserve}</td></tr>
                <tr><td>劳动者占比</td><td className="mono">{(Number(settlement.workerShareRatio) * 100).toFixed(1)}%</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      )}

      {history && (
        <div className="card">
          <h3 className="section-title" style={{ marginTop: 0 }}>账本事件</h3>
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>事件 ID</th>
                  <th>类型</th>
                  <th>金额</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                {history.map((e) => (
                  <tr key={e.eventId}>
                    <td className="mono">{e.eventId.slice(0, 8)}…</td>
                    <td><span className="badge badge--neutral">{e.eventType}</span></td>
                    <td className="mono">¥{e.amount}</td>
                    <td className="mono">{e.timestamp}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
