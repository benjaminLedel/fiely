# Fiely Frontend

The web UI for [Fiely](../README.md) — a modern, open-source alternative to
Nextcloud. Built with **React + Vite + TypeScript + Tailwind CSS**.

This package currently contains the public marketing site. The authenticated
file management UI will live here as well, progressively introduced as the
backend API stabilises.

## Requirements

- Node.js 20+ (Node 22 recommended)
- npm 10+ (or pnpm / yarn — up to you)

## Getting started

```bash
# From repo root
cd fiely-frontend

# Install dependencies
npm install

# Start the dev server on http://localhost:5173
npm run dev
```

## Scripts

| Script | Description |
|---|---|
| `npm run dev` | Start Vite dev server with HMR |
| `npm run build` | Type-check and produce a production build in `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | Run ESLint (wire up when adding ESLint config) |

## Project structure

```
fiely-frontend/
├── index.html
├── public/                 # Static assets served as-is
├── src/
│   ├── main.tsx            # App entry point
│   ├── App.tsx             # Page composition
│   ├── index.css           # Tailwind layers + design tokens
│   └── components/         # Section components (Hero, Features, …)
├── tailwind.config.js      # Design tokens, colors, animations
├── postcss.config.js
├── vite.config.ts
└── tsconfig*.json
```

## Design system

- **Font:** Inter (loaded via Google Fonts in `index.html`)
- **Primary color:** `brand.*` (blue gradient, defined in `tailwind.config.js`)
- **Neutrals:** `ink.*`
- **Dark mode:** class-based (`darkMode: 'class'`), ready to wire to a toggle

Component classes like `.btn-primary`, `.card`, `.eyebrow` and `.gradient-text`
live in `src/index.css` and are the quickest way to stay consistent.

## Contributing

See the top-level [CONTRIBUTING.md](../CONTRIBUTING.md).
