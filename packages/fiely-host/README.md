# `@fiely/host`

The contract a [Fiely](../../README.md) plugin compiles against.

This package is **types + two small helpers**. Plugins receive a live
`FielyHost` as a prop and interact with the Fiely core exclusively through
that interface — there is no global `window.Fiely`, no `postMessage` bridge,
no separate runtime. Plugins run in-process; isolation is established by
admin review of the plugin before install and by backend permission
enforcement on every API call.

> Host API version: **`1`** · Status: **Draft** · Source of truth for the
> contract: [`docs/plugin-architecture.md`](../../docs/plugin-architecture.md)

## Install

```bash
npm install --save-peer @fiely/host react react-dom
```

`@fiely/host`, `react`, and `react-dom` are all **peer dependencies**. The
Fiely core shell ships an [import map][importmap] that pins every plugin to
the same singletons, so your bundle must externalize them.

## For plugin authors

Every plugin is just a component that takes `{ host, ... }` as a prop. Pick
the entry-point type that matches what you're building:

### A full page

```tsx
import type { PageProps } from '@fiely/host';

export default function Dashboard({ host, params }: PageProps) {
  return (
    <div>
      <h1>Hello, {host.user.displayName}</h1>
      <button onClick={() => host.ui.toast('Clicked', 'success')}>
        Ping
      </button>
    </div>
  );
}
```

Register it in your `webapp/manifest.json`:

```json
{
  "id": "my-app",
  "name": "My App",
  "version": "0.1.0",
  "hostApiVersion": "1",
  "entryPoints": [
    {
      "type": "page",
      "id": "dashboard",
      "path": "/apps/my-app",
      "label": "My App",
      "bundle": "assets/index.abcd1234.js"
    }
  ]
}
```

### A file action

```tsx
import { useEffect, useState } from 'react';
import type { FileActionProps } from '@fiely/host';

export function AnalyzePdf({ host, file, onClose }: FileActionProps) {
  const [summary, setSummary] = useState<string | null>(null);

  useEffect(() => {
    host
      .fetch(`/api/apps/my-app/analyze/${file.id}`, { method: 'POST' })
      .then((r) => r.text())
      .then(setSummary);
  }, [file.id, host]);

  return (
    <dialog open>
      <h2>{file.name}</h2>
      <p>{summary ?? 'Analyzing…'}</p>
      <button onClick={onClose}>Close</button>
    </dialog>
  );
}
```

### Subscribing to events

```ts
useEffect(() => {
  return host.on('file.uploaded', (e) => {
    console.log('new file', e.file.name);
  });
}, [host]);
```

### Permissions

Data-touching calls (`host.files.upload`, `host.files.delete`, …) are
enforced by the backend based on the `AppPermission` set your plugin
declared in its backend manifest. If a permission wasn't granted, the call
rejects with a `PermissionError`:

```ts
import { PermissionError } from '@fiely/host';

try {
  await host.files.upload(null, blob, 'report.pdf');
} catch (err) {
  if (err instanceof PermissionError) {
    host.ui.toast('Missing WRITE_FILES permission', 'error');
  }
}
```

## The `FielyHost` surface

```ts
interface FielyHost {
  readonly user: UserInfo;
  readonly theme: ThemeTokens;
  readonly hostApiVersion: '1';
  readonly config: Readonly<Record<string, string>>;

  fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response>;

  readonly files: {
    get(id: string): Promise<FileMetadata>;
    list(folderId: string | null, opts?: ListOptions): Promise<Page<FileMetadata>>;
    download(id: string): Promise<Blob>;
    upload(folderId: string | null, file: Blob, name: string): Promise<FileMetadata>;
    delete(id: string): Promise<void>;
  };

  readonly ui: {
    toast(message: string, kind?: ToastKind): void;
    confirm(opts: ConfirmOptions): Promise<boolean>;
    navigate(path: string): void;
    openFile(id: string): void;
  };

  on<T extends HostEventType>(
    type: T,
    handler: (event: HostEventOf<T>) => void
  ): () => void;

  t(key: string, params?: Readonly<Record<string, string | number>>): string;
}
```

The surface is minimal on purpose. Anything you need that isn't here should
be proposed as an API addition rather than reached for via globals.

## Events

```ts
type HostEvent =
  | { type: 'file.uploaded'; file: FileMetadata }
  | { type: 'file.updated'; file: FileMetadata }
  | { type: 'file.deleted'; fileId: string }
  | { type: 'navigation'; path: string }
  | { type: 'theme.changed'; mode: 'light' | 'dark' };
```

A plugin only receives events it has permission to observe (for example,
`file.*` events require `READ_FILES`).

## For host implementers (the Fiely core frontend)

`@fiely/host/mount` ships two tiny helpers the core shell uses:

```ts
import {
  loadPluginBundle,
  entryPointBundleUrl,
  MOUNT_CLASS,
} from '@fiely/host/mount';

const Component = await loadPluginBundle<React.ComponentType<PageProps>>({
  pluginId: manifest.id,
  bundleUrl: entryPointBundleUrl(manifest.id, manifest.version, entry),
  exportName: entry.export,
});

return (
  <div className={MOUNT_CLASS} data-plugin-id={manifest.id}>
    <Component host={host} params={params} />
  </div>
);
```

That's it. No RPC, no handshake. Build a `FielyHost` implementation,
`loadPluginBundle`, render the component.

## Versioning

`@fiely/host` follows semver for the package, and declares the **host API
major** separately as `HOST_API_VERSION` (currently `'1'`). A plugin's
`manifest.json` must advertise the same major in `hostApiVersion`; the core
refuses to mount incompatible plugins and surfaces them as "requires host API
vN" in the admin UI.

## License

MIT — same as the rest of Fiely.

[importmap]: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/script/type/importmap
