import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { authService } from '../services/authService';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(() => {
    try {
      return localStorage.getItem('autoscientist_token');
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(true);

  // Request counter to ensure stale async responses never override newer state
  const authRequestId = useRef(0);

  const initAuth = useCallback(async () => {
    const currentReq = ++authRequestId.current;
    let savedToken = null;
    try {
      savedToken = localStorage.getItem('autoscientist_token');
    } catch {
      savedToken = null;
    }

    if (!savedToken) {
      if (currentReq === authRequestId.current) {
        setUser(null);
        setToken(null);
        setLoading(false);
      }
      return;
    }

    try {
      const res = await authService.getCurrentUser();
      if (currentReq === authRequestId.current) {
        if (res && res.data) {
          setUser(res.data);
          setToken(savedToken);
        } else {
          setUser(null);
          setToken(null);
          try {
            localStorage.removeItem('autoscientist_token');
            localStorage.removeItem('autoscientist_user');
          } catch {
            // ignore
          }
        }
      }
    } catch (err) {
      if (currentReq === authRequestId.current) {
        console.warn('Failed to restore session:', err);
        try {
          localStorage.removeItem('autoscientist_token');
          localStorage.removeItem('autoscientist_user');
        } catch {
          // ignore storage errors
        }
        setUser(null);
        setToken(null);
      }
    } finally {
      if (currentReq === authRequestId.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    initAuth();
  }, [initAuth]);

  // Synchronize authentication changes across browser tabs
  useEffect(() => {
    const handleStorageChange = (e) => {
      if (e.key === 'autoscientist_token') {
        if (!e.newValue) {
          // Logged out in another tab
          setUser(null);
          setToken(null);
        } else if (e.newValue !== token) {
          // Logged in or changed token in another tab
          setToken(e.newValue);
          initAuth();
        }
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, [token, initAuth]);

  const login = async (email, password) => {
    authRequestId.current++; // Invalidate in-flight initAuth
    const res = await authService.login(email, password);
    if (res && res.data) {
      const { token: newToken, user: userData } = res.data;
      try {
        localStorage.setItem('autoscientist_token', newToken);
      } catch {
        // ignore
      }
      setToken(newToken);
      setUser(userData);
      setLoading(false);
      return userData;
    }
    throw new Error(res.message || 'Login failed');
  };

  const register = async (name, email, password) => {
    authRequestId.current++; // Invalidate in-flight initAuth
    const res = await authService.register(name, email, password);
    if (res && res.data) {
      const { token: newToken, user: userData } = res.data;
      try {
        localStorage.setItem('autoscientist_token', newToken);
      } catch {
        // ignore
      }
      setToken(newToken);
      setUser(userData);
      setLoading(false);
      return userData;
    }
    throw new Error(res.message || 'Registration failed');
  };

  const logout = async () => {
    authRequestId.current++; // Invalidate in-flight initAuth
    try {
      await authService.logout();
    } finally {
      setToken(null);
      setUser(null);
      setLoading(false);
    }
  };

  const value = {
    user,
    token,
    isAuthenticated: !!token && !!user,
    loading,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
