import { useState, useCallback } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { useTrace } from '@/contexts/TraceContext';
import { ChevronLeft } from 'lucide-react';

export default function Layout() {
    const { user, logout } = useAuth();
    const { connected } = useTrace();
    const [isNavCollapsed, setIsNavCollapsed] = useState(false);

    const handleLogout = async () => {
        await logout();
    };

    const handleToggleNav = useCallback(() => {
        setIsNavCollapsed(prev => !prev);
    }, []);

    return (
        <div className="layout">
            <div className="nav-wrapper">
                {/* Toggle Handle - OUTSIDE nav for visibility when collapsed */}
                <button 
                    className={`nav-toggle-handle ${isNavCollapsed ? 'collapsed' : ''}`}
                    onClick={handleToggleNav}
                    title={isNavCollapsed ? 'Expandir menu' : 'Recolher menu'}
                >
                    <ChevronLeft className={`toggle-icon ${isNavCollapsed ? 'collapsed' : ''}`} />
                </button>

                <nav className={`layout-nav ${isNavCollapsed ? 'collapsed' : ''}`}>
                    <div className="nav-header">
                    <h1>IoT Dashboard</h1>
                    <div className={`connection-indicator ${connected ? 'connected' : 'disconnected'}`}>
                        <span className="indicator-dot" />
                        {connected ? 'Trace Conectado' : 'Trace Desconectado'}
                    </div>
                </div>

                <div className="nav-links">
                    <NavLink to="/" end>Início</NavLink>
                    <NavLink to="/architecture">Arquitetura</NavLink>
                    <NavLink to="/data">Dados</NavLink>
                    <NavLink to="/alerts">Alertas</NavLink>
                    <NavLink to="/reports">Relatórios</NavLink>
                    {user?.isAdmin && (
                        <NavLink to="/topology">Topologia</NavLink>
                    )}
                </div>

                <div className="nav-footer">
                    <span className="user-info">
                        {user?.username}
                        {user?.isAdmin && <span className="admin-badge">Admin</span>}
                    </span>
                    <button onClick={handleLogout} className="logout-btn">Sair</button>
                </div>
                </nav>
            </div>

            <main className="layout-content">
                <Outlet />
            </main>
        </div>
    );
}
