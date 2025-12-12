import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';

export default function ProtectedRoute({ children, adminOnly = false }) {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) {
        return (
            <div className="loading-overlay">
                <div className="loading-spinner" />
            </div>
        );
    }

    if (!user) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    if (adminOnly && !user.isAdmin) {
        return <Navigate to="/" replace />;
    }

    return children;
}
