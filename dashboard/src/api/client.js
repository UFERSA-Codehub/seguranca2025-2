const API_BASE = '/api';

async function request(endpoint, options = {}) {
    const response = await fetch(`${API_BASE}${endpoint}`, {
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            ...options.headers,
        },
        ...options,
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        const error = new Error(data.error || 'Request failed');
        error.status = response.status;
        throw error;
    }

    return data;
}

export const api = {
    login: (username, password) =>
        request('/login', {
            method: 'POST',
            body: JSON.stringify({ username, password }),
        }),

    logout: () => request('/logout', { method: 'POST' }),

    me: () => request('/me'),

    status: () => request('/status'),

    data: (page = 1, limit = 20) => request(`/data?page=${page}&limit=${limit}`),

    alerts: (page = 1, limit = 20) => request(`/alerts?page=${page}&limit=${limit}`),

    reports: () => request('/reports'),

    report: (type) => request(`/report/${type}`),
};
