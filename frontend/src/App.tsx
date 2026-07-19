import { useEffect } from "react";
import { Layout } from "./components/Layout";
import { useAuth } from "./contexts/AuthContext";
import { useTheme } from "./hooks/useTheme";
import {
  isLoginRoute,
  loginNextPath,
  matchAdminSection,
  matchAgentTeamDetail,
  matchEventDetail,
  matchEventsList,
  matchOverview,
  matchTransactionDetail,
  matchWalletDetail,
  navigate,
  useHashRoute
} from "./lib/router";
import { parseEventsQuery } from "./lib/events";
import { LoginPage } from "./pages/LoginPage";
import { OverviewPage } from "./pages/OverviewPage";
import { EventsPage } from "./pages/EventsPage";
import { EventDetailPage } from "./pages/EventDetailPage";
import { WalletDetailPage } from "./pages/WalletDetailPage";
import { TransactionDetailPage } from "./pages/TransactionDetailPage";
import { RulesPage } from "./pages/RulesPage";
import { AdminPage } from "./pages/AdminPage";
import { AgentConsolePage } from "./pages/AgentConsolePage";
import { AgentTeamDetailPage } from "./pages/AgentTeamDetailPage";
import { AgentActivityPage } from "./pages/AgentActivityPage";

function resolvePage(route: string) {
  if (isLoginRoute(route)) {
    return <LoginPage nextPath={loginNextPath(route)} />;
  }
  if (matchOverview(route) != null) {
    return <OverviewPage route={route} />;
  }
  const eventsQuery = matchEventsList(route);
  if (eventsQuery != null) {
    // 쿼리가 바뀌면 key로 리마운트해 딥링크 필터가 즉시 반영되게 한다.
    return <EventsPage key={eventsQuery} initialFilters={parseEventsQuery(eventsQuery)} />;
  }
  if (route === "/rules") {
    return <RulesPage />;
  }
  if (route === "/agents") {
    return <AgentConsolePage />;
  }
  if (route === "/agents/activity") {
    return <AgentActivityPage />;
  }
  const agentTeamId = matchAgentTeamDetail(route);
  if (agentTeamId != null) {
    return <AgentTeamDetailPage teamId={agentTeamId} />;
  }
  const eventId = matchEventDetail(route);
  if (eventId != null) {
    return <EventDetailPage eventId={eventId} />;
  }
  const transactionId = matchTransactionDetail(route);
  if (transactionId != null) {
    return <TransactionDetailPage transactionId={transactionId} />;
  }
  const walletAddress = matchWalletDetail(route);
  if (walletAddress != null) {
    return <WalletDetailPage address={walletAddress} />;
  }
  const adminSection = matchAdminSection(route);
  if (adminSection != null) {
    return <AdminPage section={adminSection} />;
  }
  return (
    <section className="page-head">
      <div>
        <p className="eyebrow">404</p>
        <h1>페이지를 찾을 수 없습니다</h1>
        <p className="page-lede">
          <a href="#/">관제 현황으로 돌아가기</a>
        </p>
      </div>
    </section>
  );
}

export default function App() {
  const route = useHashRoute();
  const { theme, toggleTheme } = useTheme();
  const { user, status } = useAuth();

  // 관리자 영역 가드: 세션 확인이 끝난(ready) 뒤 익명이면 로그인으로 보낸다.
  const needsAuth = matchAdminSection(route) != null;
  useEffect(() => {
    if (needsAuth && status === "ready" && !user) {
      navigate(`/login?next=${encodeURIComponent(route)}`);
    }
  }, [needsAuth, status, user, route]);

  return (
    <Layout route={route} theme={theme} onToggleTheme={toggleTheme}>
      {needsAuth && status === "loading" ? (
        <div className="data-state">세션 확인 중…</div>
      ) : (
        resolvePage(route)
      )}
    </Layout>
  );
}
