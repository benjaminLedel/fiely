import { useState, type FormEvent } from 'react';
import { ArrowRight, Eye, EyeOff, Loader2, ShieldCheck } from 'lucide-react';
import Logo from './Logo';
import LegalFooter from './LegalFooter';

type FormState = { username: string; password: string; remember: boolean };
type Status = 'idle' | 'submitting';

export default function Login() {
  const [form, setForm] = useState<FormState>({
    username: '',
    password: '',
    remember: false,
  });
  const [status, setStatus] = useState<Status>('idle');
  const [error, setError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setStatus('submitting');
    setError(null);
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message ?? `Login failed (${res.status})`);
      }
      // Backend will set the session cookie and tell us where to go.
      window.location.href = '/';
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
      setStatus('idle');
    }
  };

  return (
    <div className="relative flex min-h-screen flex-col">
      {/* Subtle ambient gradient, nothing more */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[480px] bg-radial-fade"
      />

      <main className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="flex flex-col items-center">
            <Logo className="h-12 w-12 drop-shadow-sm" />
            <h1 className="mt-5 text-2xl font-semibold tracking-tight">
              Sign in to Fiely
            </h1>
            <p className="mt-1.5 text-sm text-ink-500 dark:text-ink-400">
              Your files. Your rules.
            </p>
          </div>

          <form onSubmit={submit} className="mt-8 space-y-4" noValidate>
            <div className="space-y-1.5">
              <label
                htmlFor="username"
                className="text-sm font-medium text-ink-700 dark:text-ink-200"
              >
                Email or username
              </label>
              <input
                id="username"
                type="text"
                autoComplete="username"
                required
                autoFocus
                value={form.username}
                onChange={(e) =>
                  setForm((f) => ({ ...f, username: e.target.value }))
                }
                className="block w-full rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-900 shadow-sm placeholder:text-ink-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20 dark:border-white/10 dark:bg-ink-900/60 dark:text-ink-50"
              />
            </div>

            <div className="space-y-1.5">
              <label
                htmlFor="password"
                className="text-sm font-medium text-ink-700 dark:text-ink-200"
              >
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  value={form.password}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, password: e.target.value }))
                  }
                  className="block w-full rounded-lg border border-ink-200 bg-white px-3 py-2.5 pr-10 text-sm text-ink-900 shadow-sm placeholder:text-ink-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20 dark:border-white/10 dark:bg-ink-900/60 dark:text-ink-50"
                />
                <button
                  type="button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-ink-400 hover:text-ink-700 focus:outline-none focus-visible:text-ink-700 dark:hover:text-ink-100"
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between text-sm">
              <label className="inline-flex cursor-pointer items-center gap-2 text-ink-600 dark:text-ink-300">
                <input
                  type="checkbox"
                  checked={form.remember}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, remember: e.target.checked }))
                  }
                  className="h-4 w-4 rounded border-ink-300 text-brand-600 focus:ring-brand-500 dark:border-white/20 dark:bg-ink-900"
                />
                Stay signed in
              </label>
              <a
                href="#"
                className="font-medium text-brand-600 hover:text-brand-700 dark:text-brand-300"
              >
                Forgot password?
              </a>
            </div>

            {error && (
              <div
                role="alert"
                className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-300"
              >
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={status === 'submitting'}
              className="btn-primary w-full"
            >
              {status === 'submitting' ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Signing in…
                </>
              ) : (
                <>
                  Sign in
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          <div className="my-6 flex items-center gap-3 text-xs uppercase tracking-wider text-ink-400">
            <div className="h-px flex-1 bg-ink-100 dark:bg-white/10" />
            or
            <div className="h-px flex-1 bg-ink-100 dark:bg-white/10" />
          </div>

          <button type="button" className="btn-ghost w-full" disabled>
            <ShieldCheck className="h-4 w-4" />
            Continue with SSO
          </button>
          <p className="mt-1.5 text-center text-xs text-ink-400">
            OIDC / SAML · configured by your admin
          </p>
        </div>
      </main>

      <LegalFooter />
    </div>
  );
}
