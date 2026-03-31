import { Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import RuleListPage from './pages/RuleListPage';
import RuleDetailPage from './pages/RuleDetailPage';
import RuleEditPage from './pages/RuleEditPage';
import FlowEditorPage from './pages/FlowEditorPage';
import MonitoringPage from './pages/MonitoringPage';
import GrayscalePage from './pages/GrayscalePage';
import TestPage from './pages/TestPage';
import AnalyticsPage from './pages/AnalyticsPage';
import DecisionTablePage from './pages/DecisionTablePage';
import EnvironmentPage from './pages/EnvironmentPage';
import ImportExportPage from './pages/ImportExportPage';
import TemplatePage from './pages/TemplatePage';

export default function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route index element={<Navigate to="/rules" replace />} />
        <Route path="/rules" element={<RuleListPage />} />
        <Route path="/rules/new" element={<RuleEditPage />} />
        <Route path="/rules/new/flow" element={<FlowEditorPage />} />
        <Route path="/rules/:ruleKey" element={<RuleDetailPage />} />
        <Route path="/rules/:ruleKey/edit" element={<RuleEditPage />} />
        <Route path="/rules/:ruleKey/edit/flow" element={<FlowEditorPage />} />
        <Route path="/monitoring" element={<MonitoringPage />} />
        <Route path="/grayscale" element={<GrayscalePage />} />
        <Route path="/decision-table" element={<DecisionTablePage />} />
        <Route path="/environments" element={<EnvironmentPage />} />
        <Route path="/import-export" element={<ImportExportPage />} />
        <Route path="/templates" element={<TemplatePage />} />
        <Route path="/test" element={<TestPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="*" element={<Navigate to="/rules" replace />} />
      </Route>
    </Routes>
  );
}
