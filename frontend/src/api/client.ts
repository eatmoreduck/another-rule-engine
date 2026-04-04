import axios from 'axios';

const apiClient = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  // Attach auth token if available
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers['Authorization'] = token;
  }
  // Keep existing operator header for backward compatibility
  const operator = localStorage.getItem('operator') || 'system';
  config.headers['X-Operator'] = operator;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || error.message || '请求失败';

    // If 401 Unauthorized, clear token and redirect to login
    if (status === 401) {
      localStorage.removeItem('auth_token');
      // Only redirect if not already on login page
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }

    console.error('API Error:', message);
    return Promise.reject(error);
  },
);

export default apiClient;
