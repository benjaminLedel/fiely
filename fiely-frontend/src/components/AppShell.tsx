import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import Logo from './Logo';
import { LogOut, User } from 'lucide-react';

export default function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex min-h-screen flex-col bg-white dark:bg-ink-950">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-ink-200 px-4 dark:border-ink-800">
        <div className="flex items-center gap-3">
          <Logo className="h-7 w-7" />
          <span className="text-lg font-semibold text-ink-900 dark:text-ink-50">
            Fiely
          </span>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 text-sm text-ink-600 dark:text-ink-400">
            <User size={16} />
            <span>{user?.displayName ?? user?.username ?? '—'}</span>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-ink-500 transition hover:bg-ink-100 hover:text-ink-900 dark:text-ink-400 dark:hover:bg-ink-800 dark:hover:text-ink-100"
          >
            <LogOut size={15} />
            <span className="hidden sm:inline">Sign out</span>
          </button>
        </div>
      </header>

      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
