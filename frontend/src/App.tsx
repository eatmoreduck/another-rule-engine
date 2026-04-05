import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import AuthGuard from './components/AuthGuard';
import PermissionGuard from './components/PermissionGuard';
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
import AuditLogPage from './pages/system/AuditLogPage';

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
      { path: '/rules', element: <PermissionGuard permission="menu:rules"><RuleListPage /></PermissionGuard> },
      { path: '/rules/new', element: <PermissionGuard permission="api:rules:create"><RuleEditPage /></PermissionGuard> },
      { path: '/rules/:ruleKey', element: <PermissionGuard permission="menu:rules"><RuleDetailPage /></PermissionGuard> },
      { path: '/rules/:ruleKey/edit', element: <PermissionGuard permission="api:rules:update"><RuleEditPage /></PermissionGuard> },
      { path: '/decision-flows', element: <PermissionGuard permission="menu:decision-flows"><DecisionFlowListPage /></PermissionGuard> },
      { path: '/decision-flows/new', element: <PermissionGuard permission="api:decision-flows:create"><DecisionFlowEditorPage /></PermissionGuard> },
      { path: '/decision-flows/:flowKey', element: <PermissionGuard permission="menu:decision-flows"><DecisionFlowDetailPage /></PermissionGuard> },
      { path: '/decision-flows/:flowKey/edit', element: <PermissionGuard permission="api:decision-flows:update"><DecisionFlowEditorPage /></PermissionGuard> },
      { path: '/monitoring', element: <MonitoringPage /> },
      { path: '/grayscale', element: <PermissionGuard permission="menu:grayscale"><GrayscalePage /></PermissionGuard> },
      { path: '/environments', element: <EnvironmentPage /> },
      { path: '/import-export', element: <ImportExportPage /> },
      { path: '/analytics', element: <AnalyticsPage /> },
      { path: '/name-list', element: <PermissionGuard permission="menu:name-list"><NameListPage /></PermissionGuard> },
      { path: '/system/users', element: <PermissionGuard permission="menu:settings"><UserManagementPage /></PermissionGuard> },
      { path: '/system/roles', element: <PermissionGuard permission="menu:settings"><RoleManagementPage /></PermissionGuard> },
      { path: '/system/audit', element: <PermissionGuard permission="menu:settings"><AuditLogPage /></PermissionGuard> },
      { path: '*', element: <Navigate to="/rules" replace /> },
    ],
  },
]);

export default router;
