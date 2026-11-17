import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { PageSkeleton } from './components/common/States';
import { HOME_FOR, useAuth } from './lib/auth';
import type { Role } from './lib/types';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/auth/SignupPage';
import StaffSignupPage from './pages/auth/StaffSignupPage';
import DashboardPage from './pages/DashboardPage';
import ClaimsPage from './pages/claims/ClaimsPage';
import ClaimDetailPage from './pages/claims/ClaimDetailPage';
import TriageQueuePage from './pages/triage/TriageQueuePage';
import HandlersPage from './pages/handlers/HandlersPage';
import HandlerDetailPage from './pages/handlers/HandlerDetailPage';
import RoutingPage from './pages/routing/RoutingPage';
import AuditPage from './pages/AuditPage';
import FairnessPage from './pages/FairnessPage';
import SettingsPage from './pages/SettingsPage';
import NotFoundPage from './pages/NotFoundPage';
import CustomerClaimsPage from './pages/customer/CustomerClaimsPage';
import ReportClaimPage from './pages/customer/ReportClaimPage';
import CustomerClaimDetailPage from './pages/customer/CustomerClaimDetailPage';
import HandlerClaimsPage from './pages/handler/HandlerClaimsPage';
import HandlerClaimDetailPage from './pages/handler/HandlerClaimDetailPage';
import HandlerProfilePage from './pages/handler/HandlerProfilePage';

/** Signed-in users only; a user who opens another portal's page is sent to their own portal. */
function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, ready } = useAuth();
  const location = useLocation();
  if (!ready) return <div className="p-8"><PageSkeleton /></div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (user.role !== role) return <Navigate to={HOME_FOR[user.role]} replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/staff-signup" element={<StaffSignupPage />} />

      {/* Customer portal */}
      <Route path="/customer" element={<RequireRole role="CUSTOMER"><AppShell /></RequireRole>}>
        <Route index element={<CustomerClaimsPage />} />
        <Route path="new" element={<ReportClaimPage />} />
        <Route path="claims/:id" element={<CustomerClaimDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      {/* Handler portal */}
      <Route path="/handler" element={<RequireRole role="HANDLER"><AppShell /></RequireRole>}>
        <Route index element={<HandlerClaimsPage view="open" />} />
        <Route path="resolved" element={<HandlerClaimsPage view="resolved" />} />
        <Route path="profile" element={<HandlerProfilePage />} />
        <Route path="claims/:id" element={<HandlerClaimDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      {/* Supervisor portal */}
      <Route element={<RequireRole role="SUPERVISOR"><AppShell /></RequireRole>}>
        <Route index element={<DashboardPage />} />
        <Route path="triage" element={<TriageQueuePage />} />
        <Route path="claims" element={<ClaimsPage />} />
        <Route path="claims/:id" element={<ClaimDetailPage />} />
        <Route path="handlers" element={<HandlersPage />} />
        <Route path="handlers/:id" element={<HandlerDetailPage />} />
        <Route path="routing" element={<RoutingPage />} />
        <Route path="audit" element={<AuditPage />} />
        <Route path="fairness" element={<FairnessPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
