import { useAsync } from "@/hooks/useAsync";
import { platformApi, type PlatformHealth } from "@/api/client";

export function DashboardPage() {
  const { data, loading, error } = useAsync<PlatformHealth>(() => platformApi.health());

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">平台总览</h1>
        <p className="page-desc">公地引擎全模块健康状态与运营指标</p>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {loading && <div className="loading">加载中…</div>}

      {data && (
        <>
          <div className="card-grid">
            <div className="card">
              <div className="card-label">平台状态</div>
              <div className="card-value card-value--success">
                <span className="status-dot status-dot--up" />
                {data.status}
              </div>
            </div>
            <div className="card">
              <div className="card-label">版本</div>
              <div className="card-value">{data.version}</div>
            </div>
            <div className="card">
              <div className="card-label">总成员数</div>
              <div className="card-value">
                {data.modules.identity.totalMembers}
                <span className="card-suffix">人</span>
              </div>
            </div>
            <div className="card">
              <div className="card-label">分账记录</div>
              <div className="card-value">
                {data.modules.payment.ledgerEvents}
                <span className="card-suffix">笔</span>
              </div>
            </div>
          </div>

          <h2 className="section-title">模块状态</h2>
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>模块</th>
                  <th>状态</th>
                  <th>详情</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>匹配引擎</td>
                  <td>
                    <span className={`badge badge--${badgeClass(data.modules.matching.status)}`}>
                      {data.modules.matching.status}
                    </span>
                  </td>
                  <td>
                    当前策略: <code>{data.modules.matching.currentStrategy}</code>
                    {" · "}
                    可选: {data.modules.matching.availableStrategies.join(", ")}
                  </td>
                </tr>
                <tr>
                  <td>会员系统</td>
                  <td>
                    <span className={`badge badge--${badgeClass(data.modules.identity.status)}`}>
                      {data.modules.identity.status}
                    </span>
                  </td>
                  <td>
                    {Object.entries(data.modules.identity.roleBreakdown)
                      .map(([role, count]) => `${role}: ${count}`)
                      .join(" · ")}
                  </td>
                </tr>
                <tr>
                  <td>支付分账</td>
                  <td>
                    <span className={`badge badge--${badgeClass(data.modules.payment.status)}`}>
                      {data.modules.payment.status}
                    </span>
                  </td>
                  <td>账本事件: {data.modules.payment.ledgerEvents}</td>
                </tr>
                <tr>
                  <td>信用评价</td>
                  <td>
                    <span className={`badge badge--${badgeClass(data.modules.rating.status)}`}>
                      {data.modules.rating.status}
                    </span>
                  </td>
                  <td>—</td>
                </tr>
                <tr>
                  <td>调度引擎</td>
                  <td>
                    <span className={`badge badge--${badgeClass(data.modules.dispatch.status)}`}>
                      {data.modules.dispatch.status}
                    </span>
                  </td>
                  <td>—</td>
                </tr>
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

function badgeClass(status: string): string {
  return status === "UP" ? "success" : "danger";
}
