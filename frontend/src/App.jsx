import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { WorkflowProvider } from './context/WorkflowContext';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import ProtectedRoute from './components/ProtectedRoute';
import RequireDatasetRoute from './components/RequireDatasetRoute';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import DashboardPage from './pages/DashboardPage';
import DatasetsPage from './pages/DatasetsPage';
import DatasetDetailPage from './pages/DatasetDetailPage';
import AssociationsPage from './pages/AssociationsPage';
import ResearchWorkflowPage from './pages/ResearchWorkflowPage';
import AutoMlPage from './pages/AutoMlPage';
import VisualizationsPage from './pages/VisualizationsPage';
import DriftPage from './pages/DriftPage';
import AskDatasetPage from './pages/AskDatasetPage';
import FindingsPage from './pages/FindingsPage';
import ExperimentsPage from './pages/ExperimentsPage';
import ReportsPage from './pages/ReportsPage';
import LandingPage from './pages/LandingPage';

function AppFrame() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  const publicRoutes = ['/', '/home', '/login', '/register', '/forgot-password', '/reset-password'];
  const showSidebar = isAuthenticated && !publicRoutes.includes(location.pathname);

  return (
    <div className="d-flex flex-column min-vh-100">
      <Navbar />
      <div className="d-flex flex-grow-1 align-items-stretch">
        {showSidebar && <Sidebar />}
        <main className="flex-grow-1 min-w-0 w-100">
          <Routes>
                {/* Public homepage */}
                <Route path="/" element={<LandingPage />} />
                <Route path="/home" element={<Navigate to="/" replace />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                <Route path="/reset-password" element={<ResetPasswordPage />} />

                {/* Dashboard */}
                <Route
                  path="/dashboard"
                  element={
                    <ProtectedRoute>
                      <DashboardPage />
                    </ProtectedRoute>
                  }
                />

                {/* Stage 1: Upload Dataset / List */}
                <Route
                  path="/datasets"
                  element={
                    <ProtectedRoute>
                      <DatasetsPage />
                    </ProtectedRoute>
                  }
                />

                {/* Stage 2, 3, 4: Overview, Quality, EDA */}
                <Route
                  path="/datasets/:id"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <DatasetDetailPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/overview"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <DatasetDetailPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/quality"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <DatasetDetailPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/eda"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <DatasetDetailPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Stage 5: Associations */}
                <Route
                  path="/associations"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AssociationsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/associations"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AssociationsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Stage 6, 7, 8: Research Questions, Hypotheses, Statistical Testing */}
                <Route
                  path="/research"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ResearchWorkflowPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/hypotheses"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ResearchWorkflowPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/statistics"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ResearchWorkflowPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/research"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ResearchWorkflowPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/hypotheses"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ResearchWorkflowPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/statistics"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ResearchWorkflowPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Stage 9: Machine Learning */}
                <Route
                  path="/automl"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AutoMlPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/ml"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AutoMlPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/automl"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AutoMlPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/ml"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AutoMlPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Stage 10: Visualizations */}
                <Route
                  path="/visualizations"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <VisualizationsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/visualizations"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <VisualizationsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Stage 11: Explainability/XAI */}
                <Route
                  path="/xai"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AutoMlPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/xai"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AutoMlPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Stage 12: Findings */}
                <Route
                  path="/findings"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <FindingsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/findings"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <FindingsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Stage 13: Report */}
                <Route
                  path="/reports"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ReportsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/report"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ReportsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/reports"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ReportsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Additional Autonomous Tools */}
                <Route
                  path="/drift"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <DriftPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/ask"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AskDatasetPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/datasets/:id/ask"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <AskDatasetPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/experiments"
                  element={
                    <ProtectedRoute>
                      <RequireDatasetRoute>
                        <ExperimentsPage />
                      </RequireDatasetRoute>
                    </ProtectedRoute>
                  }
                />

                {/* Catch-all redirect */}
                <Route path="*" element={<Navigate to={isAuthenticated ? "/dashboard" : "/"} replace />} />
          </Routes>
        </main>
      </div>
      {location.pathname !== '/' && (
        <footer className="bg-white border-top py-3 text-center text-muted small">
          AutoScientist AI &copy; 2026 &bull; "From Raw Data to Evidence-Based Scientific Discovery."
        </footer>
      )}
    </div>
  );
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <WorkflowProvider>
          <AppFrame />
        </WorkflowProvider>
      </AuthProvider>
    </Router>
  );
}

export default App;
