import { Github } from 'lucide-react';
import Logo from './Logo';

const COLUMNS = [
  {
    title: 'Product',
    links: [
      { label: 'Features', href: '#features' },
      { label: 'AI', href: '#ai' },
      { label: 'Roadmap', href: '#roadmap' },
      { label: 'Tech stack', href: '#stack' },
    ],
  },
  {
    title: 'Community',
    links: [
      { label: 'GitHub', href: 'https://github.com/benjaminledel/fiely' },
      { label: 'Issues', href: 'https://github.com/benjaminledel/fiely/issues' },
      { label: 'Contributing', href: 'https://github.com/benjaminledel/fiely/blob/main/CONTRIBUTING.md' },
    ],
  },
  {
    title: 'Legal',
    links: [
      { label: 'License (MIT)', href: 'https://github.com/benjaminledel/fiely/blob/main/LICENSE' },
      { label: 'Imprint', href: '#' },
      { label: 'Privacy', href: '#' },
    ],
  },
];

export default function Footer() {
  return (
    <footer className="border-t border-ink-100 bg-white dark:border-white/10 dark:bg-ink-950">
      <div className="container-narrow py-16">
        <div className="grid gap-10 lg:grid-cols-12">
          <div className="lg:col-span-5">
            <div className="flex items-center gap-2.5">
              <Logo className="h-9 w-9" />
              <span className="text-lg font-bold tracking-tight">Fiely</span>
            </div>
            <p className="mt-4 max-w-sm text-sm text-ink-600 dark:text-ink-300">
              Your files. Your rules. Your AI. A modern, open-source alternative
              to Nextcloud — built in public.
            </p>
            <a
              href="https://github.com/benjaminledel/fiely"
              target="_blank"
              rel="noreferrer"
              className="btn-ghost mt-6"
            >
              <Github className="h-4 w-4" />
              github.com/benjaminledel/fiely
            </a>
          </div>

          <div className="grid grid-cols-2 gap-8 sm:grid-cols-3 lg:col-span-7">
            {COLUMNS.map((col) => (
              <div key={col.title}>
                <p className="text-xs font-semibold uppercase tracking-wider text-ink-500">
                  {col.title}
                </p>
                <ul className="mt-4 space-y-2.5">
                  {col.links.map((l) => (
                    <li key={l.label}>
                      <a
                        href={l.href}
                        className="text-sm text-ink-700 transition-colors hover:text-brand-600 dark:text-ink-200 dark:hover:text-brand-300"
                      >
                        {l.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-12 flex flex-col items-start justify-between gap-3 border-t border-ink-100 pt-8 text-xs text-ink-500 sm:flex-row sm:items-center dark:border-white/10">
          <p>
            &copy; {new Date().getFullYear()} Fiely. Open source under the MIT License.
          </p>
          <p>
            fiely.cloud · Built with <span className="text-brand-500">❤</span> in Germany
          </p>
        </div>
      </div>
    </footer>
  );
}
