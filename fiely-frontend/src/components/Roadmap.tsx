import { Sprout, Leaf, TreePine, Check } from 'lucide-react';

const PHASES = [
  {
    icon: Sprout,
    phase: 'Phase 1',
    title: 'Foundation',
    status: 'In progress',
    tone: 'emerald',
    items: [
      'Core file management (upload, download, folders)',
      'User management and authentication',
      'Basic sharing with public links',
      'React web UI',
      'Docker deployment',
    ],
  },
  {
    icon: Leaf,
    phase: 'Phase 2',
    title: 'Mobile & AI',
    status: 'Up next',
    tone: 'brand',
    items: [
      'Flutter mobile app (iOS + Android)',
      'Semantic search with pgvector',
      'Auto-tagging',
      'Ollama integration',
    ],
  },
  {
    icon: TreePine,
    phase: 'Phase 3',
    title: 'Enterprise',
    status: 'Planned',
    tone: 'violet',
    items: [
      'LDAP / SSO integration',
      'Multi-tenancy',
      'Audit logs',
      'Chat with files (RAG)',
      'Enterprise admin dashboard',
    ],
  },
] as const;

const TONES = {
  emerald:
    'bg-emerald-500/10 text-emerald-700 border-emerald-500/30 dark:text-emerald-300',
  brand: 'bg-brand-500/10 text-brand-700 border-brand-500/30 dark:text-brand-300',
  violet:
    'bg-violet-500/10 text-violet-700 border-violet-500/30 dark:text-violet-300',
} as const;

export default function Roadmap() {
  return (
    <section id="roadmap" className="relative py-24 lg:py-32">
      <div className="container-narrow">
        <div className="mx-auto max-w-2xl text-center">
          <span className="eyebrow">Roadmap</span>
          <h2 className="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">
            Building in public,{' '}
            <span className="gradient-text">one phase at a time</span>
          </h2>
          <p className="mt-4 text-lg text-ink-600 dark:text-ink-300">
            We ship small, we ship often, and we sweat the details. Here&rsquo;s where
            we&rsquo;re headed.
          </p>
        </div>

        <div className="mt-16 grid gap-6 lg:grid-cols-3">
          {PHASES.map(({ icon: Icon, phase, title, status, tone, items }) => (
            <div key={phase} className="card flex flex-col">
              <div className="flex items-center justify-between">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-500/10 text-brand-600 dark:text-brand-300">
                  <Icon className="h-5 w-5" />
                </div>
                <span
                  className={[
                    'rounded-full border px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider',
                    TONES[tone],
                  ].join(' ')}
                >
                  {status}
                </span>
              </div>
              <p className="mt-5 text-xs font-semibold uppercase tracking-wider text-ink-500">
                {phase}
              </p>
              <h3 className="mt-1 text-xl font-bold">{title}</h3>
              <ul className="mt-5 space-y-2.5">
                {items.map((item) => (
                  <li key={item} className="flex items-start gap-2.5 text-sm">
                    <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-500/10 text-brand-600 dark:text-brand-300">
                      <Check className="h-3 w-3" />
                    </span>
                    <span className="text-ink-700 dark:text-ink-200">{item}</span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
