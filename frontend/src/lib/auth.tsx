import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, TOKEN_KEY } from './api';
import type { Role, User } from './types';

interface AuthState {
  user: User | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

const storage = {
  get: () => {
    try {
      return window.sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (t: string) => {
    try {
      window.sessionStorage.setItem(TOKEN_KEY, t);
    } catch {
      /* storage unavailable - token lives for this page only */
    }
  },
  clear: () => {
    try {
      window.sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      /* ignore */
    }
  },
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      storage.clear();
      setUser(null);
    });
    if (!storage.get()) {
      setReady(true);
      return;
    }
    api.auth
      .me()
      .then(setUser)
      .catch(() => storage.clear())
      .finally(() => setReady(true));
    return () => setUnauthorizedHandler(null);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.auth.login(email, password);
    storage.set(res.token);
    setUser(res.user);
    return res.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.auth.logout();
    } catch {
      /* token may already be invalid - sign out locally regardless */
    }
    storage.clear();
    setUser(null);
  }, []);

  const hasRole = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user]);

  const value = useMemo(() => ({ user, ready, login, logout, hasRole }), [user, ready, login, logout, hasRole]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

/** Roles allowed to make routing decisions and edit records. */
export const CAN_EDIT: Role[] = ['SUPERVISOR'];

/** Where each role lands after signing in. */
export const HOME_FOR: Record<Role, string> = {
  CUSTOMER: '/customer',
  SUPERVISOR: '/',
  HANDLER: '/handler',
};
