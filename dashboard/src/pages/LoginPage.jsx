import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';

export default function LoginPage() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const from = location.state?.from?.pathname || '/';

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const result = await login(username, password);
            if (result.success) {
                navigate(from, { replace: true });
            } else {
                setError('Usuário ou senha inválidos');
            }
        } catch (err) {
            setError(err.message || 'Falha ao fazer login');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">
            <div className="login-card">
                <h1>IoT Security Dashboard</h1>
                <p className="login-subtitle">Faça login para continuar</p>

                <form onSubmit={handleSubmit}>
                    {error && <div className="login-error">{error}</div>}

                    <div className="form-group">
                        <label htmlFor="username">Usuário</label>
                        <input
                            type="text"
                            id="username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            autoComplete="username"
                            required
                            disabled={loading}
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">Senha</label>
                        <input
                            type="password"
                            id="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            autoComplete="current-password"
                            required
                            disabled={loading}
                        />
                    </div>

                    <button type="submit" className="login-btn" disabled={loading}>
                        {loading ? 'Entrando...' : 'Entrar'}
                    </button>
                </form>

                <div className="login-hint">
                    <p>Credenciais de demonstração:</p>
                    <code>admin / admin123</code>
                </div>
            </div>
        </div>
    );
}
