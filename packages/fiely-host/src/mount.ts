/**
 * Helpers the Fiely core uses to load and mount plugin bundles.
 *
 * Plugins run **in-process**: there is no iframe sandbox, no RPC. The core
 * builds a `FielyHost` implementation (already scoped to the plugin's granted
 * permissions on the backend side) and passes it as a prop to whatever
 * component the plugin's entry-point exports.
 *
 * Isolation instead relies on:
 *
 * - **Provenance** — only bundles served by the core at
 *   `/apps/{id}/{version}/...` are loaded; the import map on the core shell
 *   pins `react`, `react-dom` and `@fiely/host` so plugins can't ship their
 *   own copies.
 * - **Admin review** of third-party plugin code before install, plus explicit
 *   approval of the `AppPermission` set the plugin declares.
 * - **Backend permission enforcement** on every data-touching API call.
 *   `host.files.upload` is not privileged just because it's a function — the
 *   backend rejects the underlying request if the plugin doesn't have
 *   `WRITE_FILES`.
 * - **CSS scoping**: plugins must render inside the host-provided mount point
 *   (see `MOUNT_CLASS`) and must not attach global stylesheets. The core
 *   enforces this at bundle-load time.
 */

import type { EntryPoint } from './manifest.js';

/**
 * The CSS class the core wraps every plugin mount point in. Plugin authors
 * should style *children* of this class, never globally, so that multiple
 * plugins can coexist without stepping on each other.
 */
export const MOUNT_CLASS = 'fiely-plugin-root';

export interface LoadPluginBundleOptions {
  /** Plugin ID, used purely for nicer errors. */
  readonly pluginId: string;
  /** Absolute URL of the bundle, already content-hashed. */
  readonly bundleUrl: string;
  /** Named export to pull out. Defaults to `'default'`. */
  readonly exportName?: string;
  /**
   * Optional resolver so tests and storybook-style previews can stub the
   * import. In production, leave this unset — it defaults to native `import()`.
   */
  readonly importer?: (url: string) => Promise<Record<string, unknown>>;
}

/**
 * Dynamically imports a plugin bundle and returns the requested export.
 *
 * The caller is responsible for remembering the returned reference (React
 * component, handler, etc.) so repeated mounts don't re-fetch. Browsers
 * already de-duplicate identical `import()` specifiers.
 */
export async function loadPluginBundle<T = unknown>(
  opts: LoadPluginBundleOptions
): Promise<T> {
  const importer = opts.importer ?? ((url: string) => import(/* @vite-ignore */ url));
  const exportName = opts.exportName ?? 'default';

  let module: Record<string, unknown>;
  try {
    module = await importer(opts.bundleUrl);
  } catch (err) {
    throw new Error(
      `[fiely] Failed to load plugin '${opts.pluginId}' from ${opts.bundleUrl}: ${
        err instanceof Error ? err.message : String(err)
      }`
    );
  }

  if (!(exportName in module)) {
    throw new Error(
      `[fiely] Plugin '${opts.pluginId}' bundle does not export '${exportName}' (got: ${Object.keys(
        module
      ).join(', ') || '∅'})`
    );
  }

  return module[exportName] as T;
}

/**
 * Build the URL for a plugin asset served by the core. The core exposes
 * everything under `/apps/{pluginId}/{version}/...`; bundle/icon paths in the
 * manifest are relative to that prefix.
 */
export function pluginAssetUrl(
  pluginId: string,
  version: string,
  relativePath: string
): string {
  // Defensive trim so the manifest can write "assets/x" or "/assets/x".
  const clean = relativePath.replace(/^\/+/, '');
  return `/apps/${encodeURIComponent(pluginId)}/${encodeURIComponent(version)}/${clean}`;
}

/**
 * Resolve the absolute bundle URL for an entry point, given the plugin's
 * ID + version from its manifest.
 */
export function entryPointBundleUrl(
  pluginId: string,
  version: string,
  entry: EntryPoint
): string {
  return pluginAssetUrl(pluginId, version, entry.bundle);
}
