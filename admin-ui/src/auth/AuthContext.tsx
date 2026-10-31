import React, { createContext, useContext, useEffect, useState } from 'react';
import { adminApi, getStoredToken, setStoredToken } from '../api/client';
import type { AdminOperator } from '../api/types';

interface AuthContextValue {
  operator: AdminOperator | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  hasPermission: (perm: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [operator, setOperator] = useState<AdminOperator | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const token = getStoredToken();
    if (!token) {
      setLoading(false);
      return;
    }
    adminApi
      .me()
      .then((res) => setOperator(res.data))
      .catch(() => {
        setStoredToken(null);
        setOperator(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    const res = await adminApi.login(email, password);
    setStoredToken(res.data.access_token);
    setOperator(res.data.operator);
  };

  const logout = () => {
    setStoredToken(null);
    setOperator(null);
  };

  const hasPermission = (perm: string): boolean => {
    if (!operator) return false;
    return operator.permissions.includes(perm) || operator.roles.includes('SUPER_ADMIN');
  };

  return (
    <AuthContext.Provider value={{ operator, loading, login, logout, hasPermission }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return ctx;
}

