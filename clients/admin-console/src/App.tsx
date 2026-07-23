import { NavLink, Route, Routes } from "react-router-dom";
import { DashboardPage } from "@/pages/DashboardPage";
import { MembersPage } from "@/pages/MembersPage";
import { MatchingPage } from "@/pages/MatchingPage";
import { PaymentPage } from "@/pages/PaymentPage";
import { DisputesPage } from "@/pages/DisputesPage";
import { GovernancePage } from "@/pages/GovernancePage";

const navItems = [
  { to: "/", label: "总览", end: true },
  { to: "/members", label: "会员" },
  { to: "/matching", label: "匹配引擎" },
  { to: "/payment", label: "支付分账" },
  { to: "/disputes", label: "纠纷仲裁" },
  { to: "/governance", label: "治理议事" },
];

export function App() {
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-mark">●</span>
          <div>
            <div className="brand-title">公地引擎</div>
            <div className="brand-sub">合作社管理后台</div>
          </div>
        </div>
        <nav className="sidebar-nav">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `nav-link ${isActive ? "nav-link--active" : ""}`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="version-tag">v0.1.0-SNAPSHOT</span>
        </div>
      </aside>

      <main className="main-content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/members" element={<MembersPage />} />
          <Route path="/matching" element={<MatchingPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/disputes" element={<DisputesPage />} />
          <Route path="/governance" element={<GovernancePage />} />
        </Routes>
      </main>
    </div>
  );
}
