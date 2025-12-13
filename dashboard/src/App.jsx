import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '@/contexts/AuthContext';
import { TraceProvider } from '@/contexts/TraceContext';
import ProtectedRoute from '@/components/ProtectedRoute';
import Layout from '@/components/Layout';
import LoginPage from '@/pages/LoginPage';
import HomePage from '@/pages/HomePage';
import './App.css';

const DataPage = lazy(() => import('@/pages/DataPage'));
const AlertsPage = lazy(() => import('@/pages/AlertsPage'));
const ReportsPage = lazy(() => import('@/pages/ReportsPage'));
const ReportPage = lazy(() => import('@/pages/ReportPage'));
const TopologyPage = lazy(() => import('@/pages/TopologyPage'));
const ArchitecturePage = lazy(() => import('@/pages/ArchitecturePage'));

function PageLoading() {
    return (
        <div className="page-loading">
            <div className="loading-spinner" />
        </div>
    );
}

function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <TraceProvider>
                    <Routes>
                        <Route path="/login" element={<LoginPage />} />
                        <Route
                            path="/"
                            element={
                                <ProtectedRoute>
                                    <Layout />
                                </ProtectedRoute>
                            }
                        >
                            <Route index element={<HomePage />} />
                            <Route path="data" element={
                                <Suspense fallback={<PageLoading />}>
                                    <DataPage />
                                </Suspense>
                            } />
                            <Route path="alerts" element={
                                <Suspense fallback={<PageLoading />}>
                                    <AlertsPage />
                                </Suspense>
                            } />
                            <Route path="reports" element={
                                <Suspense fallback={<PageLoading />}>
                                    <ReportsPage />
                                </Suspense>
                            } />
                            <Route path="reports/:type" element={
                                <Suspense fallback={<PageLoading />}>
                                    <ReportPage />
                                </Suspense>
                            } />
                            <Route path="architecture" element={
                                <Suspense fallback={<PageLoading />}>
                                    <ArchitecturePage />
                                </Suspense>
                            } />
                            <Route
                                path="topology"
                                element={
                                    <ProtectedRoute adminOnly>
                                        <Suspense fallback={<PageLoading />}>
                                            <TopologyPage />
                                        </Suspense>
                                    </ProtectedRoute>
                                }
                            />
                        </Route>
                    </Routes>
                </TraceProvider>
            </AuthProvider>
        </BrowserRouter>
    );
}

export default App;
