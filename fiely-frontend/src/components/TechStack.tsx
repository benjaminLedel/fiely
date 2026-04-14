const STACK = [
  { layer: 'Backend', tech: 'Spring Boot · Kotlin' },
  { layer: 'Frontend', tech: 'React · Vite · Tailwind' },
  { layer: 'Mobile & Desktop', tech: 'Flutter' },
  { layer: 'Database', tech: 'PostgreSQL + pgvector' },
  { layer: 'Storage', tech: 'Local filesystem' },
  { layer: 'Chunked upload', tech: 'tus.io' },
  { layer: 'AI · local', tech: 'Ollama' },
  { layer: 'AI · cloud', tech: 'OpenAI / Claude (pluggable)' },
  { layer: 'Auth', tech: 'Keycloak / OIDC' },
];

export default function TechStack() {
  return (
    <section id="stack" className="relative py-24 lg:py-32">
      <div className="container-narrow">
        <div className="grid gap-12 lg:grid-cols-12 lg:items-start lg:gap-16">
          <div className="lg:col-span-5">
            <span className="eyebrow">Tech stack</span>
            <h2 className="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">
              A <span className="gradient-text">modern</span>, boring stack
            </h2>
            <p className="mt-5 text-lg text-ink-600 dark:text-ink-300">
              We pick tools that are proven, open, and pleasant to work with. No PHP,
              no legacy plugin spaghetti — just sensible defaults that scale from your
              homelab to the public sector.
            </p>
          </div>

          <div className="lg:col-span-7">
            <div className="overflow-hidden rounded-2xl border border-ink-100 bg-white shadow-soft dark:border-white/10 dark:bg-ink-900/60">
              <ul className="divide-y divide-ink-100 dark:divide-white/10">
                {STACK.map(({ layer, tech }) => (
                  <li
                    key={layer}
                    className="flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-ink-50/70 dark:hover:bg-white/5"
                  >
                    <span className="text-sm font-medium text-ink-500 dark:text-ink-400">
                      {layer}
                    </span>
                    <span className="text-right text-sm font-semibold">
                      {tech}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
