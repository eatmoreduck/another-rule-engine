import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import AuthGuard from './components/AuthGuard';
import LoginPage from './pages/LoginPage';
import RuleListPage from './pages/RuleListPage';
import RuleDetailPage from './pages/RuleDetailPage';
import RuleEditPage from './pages/RuleEditPage';
import DecisionFlowListPage from './pages/DecisionFlowListPage';
import DecisionFlowDetailPage from './pages/DecisionFlowDetailPage';
import DecisionFlowEditorPage from './pages/DecisionFlowEditorPage';
import MonitoringPage from './pages/MonitoringPage';
import GrayscalePage from './pages/GrayscalePage';
import AnalyticsPage from './pages/AnalyticsPage';
import EnvironmentPage from './pages/EnvironmentPage';
import ImportExportPage from './pages/ImportExportPage';
import NameListPage from './pages/NameListPage';
import UserManagementPage from './pages/system/UserManagementPage';
import RoleManagementPage from './pages/system/RoleManagementPage';

const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/rules" replace /> },
      { path: '/rules', element: <RuleListPage /> },
      { path: '/rules/new', element: <RuleEditPage /> },
      { path: '/rules/:ruleKey', element: <RuleDetailPage /> },
      { path: '/rules/:ruleKey/edit', element: <RuleEditPage /> },
      { path: '/decision-flows', element: <DecisionFlowListPage /> },
      { path: '/decision-flows/new', element: <DecisionFlowEditorPage /> },
      { path: '/decision-flows/:flowKey', element: <DecisionFlowDetailPage /> },
      { path: '/decision-flows/:flowKey/edit', element: <DecisionFlowEditorPage /> },
      { path: '/monitoring', element: <MonitoringPage /> },
      { path: '/grayscale', element: <GrayscalePage /> },
      { path: '/environments', element: <EnvironmentPage /> },
      { path: '/import-export', element: <ImportExportPage /> },
      { path: '/analytics', element: <AnalyticsPage /> },
      { path: '/name-list', element: <NameListPage /> },
      { path: '/system/users', element: <UserManagementPage /> },
      { path: '/system/roles', element: <RoleManagementPage /> },
      { path: '*', element: <Navigate to="/rules" replace /> },
    ],
  },
]);

export default router;
