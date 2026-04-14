import Login from './components/Login';

/**
 * Root of the unauthenticated app shell. For now there's only one view:
 * the login page. Once session handling lands this becomes a router that
 * redirects authenticated users to `/files`.
 */
export default function App() {
  return (
    <div className="min-h-screen bg-white text-ink-900 dark:bg-ink-950 dark:text-ink-50">
      <Login />
    </div>
  );
}
