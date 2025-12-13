import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        api.me()
            .then((data) => setUser({ username: data.username, isAdmin: data.isAdmin }))
            .catch(() => setUser(null))
            .finally(() => setLoading(false));
    }, []);

    const login = useCallback(async (username, password) => {
        const result = await api.login(username, password);
        if (result.success) {
            setUser({ username: result.username, isAdmin: result.username === 'admin' });
        }
        return result;
    }, []);

    const logout = useCallback(async () => {
        await api.logout();
        setUser(null);
    }, []);

    return (
        <AuthContext.Provider value={{ user, loading, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
}
