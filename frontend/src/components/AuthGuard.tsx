import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuthStore } from '../stores/authStore';

interface AuthGuardProps {
  children: React.ReactNode;
}

/**
 * 路由守卫组件
 * 未登录用户跳转到 /login，已登录用户正常访问
 */
export default function AuthGuard({ children }: AuthGuardProps) {
  const { initialized, init } = useAuthStore();
  const token = useAuthStore((s) => s.token);
  const [initiating, setInitiating] = useState(false);

  useEffect(() => {
    if (!initialized && !initiating) {
      setInitiating(true);
      init().finally(() => setInitiating(false));
    }
  }, [initialized, init, initiating]);

  if (!initialized) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
