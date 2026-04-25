import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import Logo from './Logo';
import { LogOut } from 'lucide-react';

export default function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const initial = (user?.displayName ?? user?.username ?? '?')[0].toUpperCase();

  return (
    <div className="flex min-h-screen flex-col bg-ink-50/50 dark:bg-ink-950">
      <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center justify-between border-b border-ink-200/80 bg-white/80 px-4 backdrop-blur-md dark:border-ink-800/80 dark:bg-ink-950/80">
        <div className="flex items-center gap-2.5">
          <Logo className="h-7 w-7" />
          <span className="text-base font-semibold tracking-tight text-ink-900 dark:text-ink-50">
            Fiely
          </span>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2.5">
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700 dark:bg-brand-900/40 dark:text-brand-300">
              {initial}
            </div>
            <span className="hidden text-sm font-medium text-ink-700 sm:block dark:text-ink-300">
              {user?.displayName ?? user?.username}
            </span>
          </div>
          <div className="mx-1 h-5 w-px bg-ink-200 dark:bg-ink-800" />
          <button
            onClick={handleLogout}
            title="Sign out"
            className="flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm text-ink-400 transition hover:bg-ink-100 hover:text-ink-700 dark:text-ink-500 dark:hover:bg-ink-800 dark:hover:text-ink-200"
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
