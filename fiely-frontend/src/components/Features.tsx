import {
  UploadCloud,
  Share2,
  ShieldCheck,
  History,
  Users,
  Lock,
} from 'lucide-react';

const FEATURES = [
  {
    icon: UploadCloud,
    title: 'Chunked uploads',
    desc: 'Resumable, reliable uploads of files of any size — powered by tus.io. Never lose progress on a flaky connection again.',
  },
  {
    icon: History,
    title: 'Versioning & trash',
    desc: 'Every change is tracked. Restore any prior version, or rescue a file from the trash. No more "I deleted it by accident".',
  },
  {
    icon: Share2,
    title: 'Flexible sharing',
    desc: 'Public links, guest access, password protection, expiring links. Share with anyone, on your terms.',
  },
  {
    icon: Users,
    title: 'Teams & workspaces',
    desc: 'Organize files by team or project. Granular permissions and roles that scale with your organization.',
  },
  {
    icon: ShieldCheck,
    title: 'Audit & compliance',
    desc: 'Full audit logs, SSO via Keycloak / SAML, LDAP integration. Built for the public sector from day one.',
  },
  {
    icon: Lock,
    title: 'On-premise by design',
    desc: 'Deploy on your own infrastructure with a single docker-compose. Your data never leaves your network.',
  },
];

export default function Features() {
  return (
    <section id="features" className="relative py-24 lg:py-32">
      <div className="container-narrow">
        <div className="mx-auto max-w-2xl text-center">
          <span className="eyebrow">What&rsquo;s inside</span>
          <h2 className="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">
            A file platform built for the{' '}
            <span className="gradient-text">way you actually work</span>
          </h2>
          <p className="mt-4 text-lg text-ink-600 dark:text-ink-300">
            The essentials, done right. No 15-year-old plugin system, no confusing
            admin panel. Just the features that matter — and nothing that doesn&rsquo;t.
          </p>
        </div>

        <div className="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(({ icon: Icon, title, desc }) => (
            <div key={title} className="card group">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-500/10 text-brand-600 transition-colors group-hover:bg-brand-500 group-hover:text-white dark:text-brand-300">
                <Icon className="h-5 w-5" />
              </div>
              <h3 className="mt-5 text-lg font-semibold">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-ink-600 dark:text-ink-300">
                {desc}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
