import { useEffect } from "react";
import { Layout } from "./components/Layout";
import { ErrorBoundary } from "./components/ErrorBoundary";
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
  roleHomePath,
  useHashRoute
} from "./lib/router";
import { parseEventsQuery } from "./lib/events";
import { LoginPage } from "./pages/LoginPage";
import { WorkQueuePage } from "./pages/WorkQueuePage";
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

import { DemoGuidePage } from "./pages/DemoGuidePage";
import { StakingPage } from "./pages/StakingPage";

function resolvePage(route: string) {
  if (route === "/demo") return <DemoGuidePage />;
  if (route === "/staking") return <StakingPage />;
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
  if (route === "/work") {
    return <WorkQueuePage />;
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
          <a href="#/">대시보드로 돌아가기</a>
        </p>
      </div>
    </section>
  );
}

export default function App() {
  const route = useHashRoute();
  const { theme, toggleTheme } = useTheme();
  const { user, status } = useAuth();

  const onLogin = isLoginRoute(route);
  const adminSection = matchAdminSection(route);
  // 조회자는 운영 화면에 들어갈 수 없다. 서버도 같은 규칙으로 막지만 빈 화면 대신 첫 화면으로 돌려보낸다.
  const forbidden =
    user?.role === "VIEWER" && (adminSection != null || route.startsWith("/agents") || route === "/staking" || route === "/work");

  // 모든 화면은 로그인이 필요하다. 세션 확인이 끝난(ready) 뒤에만 판단한다.
  useEffect(() => {
    if (status !== "ready") {
      return;
    }
    if (!user && !onLogin) {
      navigate(route === "/" ? "/login" : `/login?next=${encodeURIComponent(route)}`);
    } else if (user && onLogin) {
      // 이미 로그인한 상태로 로그인 화면에 오면 역할별 첫 화면으로 보낸다.
      navigate(roleHomePath(user.role));
    } else if (forbidden) {
      navigate(roleHomePath(user?.role));
    }
  }, [status, user, onLogin, forbidden, route]);

  const waiting = status === "loading" || (!user && !onLogin) || (user != null && onLogin) || forbidden;

  return (
    <Layout route={route} theme={theme} onToggleTheme={toggleTheme}>
      {waiting ? (
        <div className="data-state">세션 확인 중…</div>
      ) : (
        <ErrorBoundary resetKey={route}>{resolvePage(route)}</ErrorBoundary>
      )}
    </Layout>
  );
}
