import { Layout } from "./components/Layout";
import { useTheme } from "./hooks/useTheme";
import {
  matchAdminSection,
  matchEventDetail,
  matchTransactionDetail,
  matchWalletDetail,
  useHashRoute
} from "./lib/router";
import { OverviewPage } from "./pages/OverviewPage";
import { EventsPage } from "./pages/EventsPage";
import { EventDetailPage } from "./pages/EventDetailPage";
import { WalletDetailPage } from "./pages/WalletDetailPage";
import { TransactionDetailPage } from "./pages/TransactionDetailPage";
import { RulesPage } from "./pages/RulesPage";
import { AdminPage } from "./pages/AdminPage";

function resolvePage(route: string) {
  if (route === "/") {
    return <OverviewPage />;
  }
  if (route === "/events") {
    return <EventsPage />;
  }
  if (route === "/rules") {
    return <RulesPage />;
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

  return (
    <Layout route={route} theme={theme} onToggleTheme={toggleTheme}>
      {resolvePage(route)}
    </Layout>
  );
}
