import type { HostApiVersion } from './host.js';

/**
 * Provenance label — set by the core based on plugin origin, never by the
 * plugin itself. All plugins run in-process; trust is enforced by admin
 * review and the backend permission model, not by a runtime sandbox.
 *
 * The value is surfaced in the admin UI (install banner, badge on nav
 * entries) and may drive which permissions are auto-granted.
 */
export type PluginTrust = 'core' | 'first-party' | 'third-party';

/** Common shape of every entry point. */
export interface EntryPointBase {
  /** Unique within the plugin. Used by the core for stable keys / deep links. */
  readonly id: string;
  /** Human-readable label. */
  readonly label: string;
  /** Path relative to `webapp/` — e.g. `icons/app.svg`. */
  readonly icon?: string;
  /** Path relative to `webapp/` — e.g. `assets/index.abcd1234.js`. */
  readonly bundle: string;
  /** Named export to mount. Defaults to `default`. */
  readonly export?: string;
}

export interface PageEntryPoint extends EntryPointBase {
  readonly type: 'page';
  /** Absolute URL path the host will mount this page under. */
  readonly path: string;
  /** Where to surface the nav entry. Default: `sidebar`. */
  readonly navLocation?: 'sidebar' | 'header' | 'none';
}

export interface FileActionEntryPoint extends EntryPointBase {
  readonly type: 'file-action';
  /** MIME types the action applies to. Supports `type/*` globs. */
  readonly mimeTypes: readonly string[];
}

export interface SettingsPanelEntryPoint extends EntryPointBase {
  readonly type: 'settings-panel';
  readonly scope: 'user' | 'admin';
}

export interface SidebarWidgetEntryPoint extends EntryPointBase {
  readonly type: 'sidebar-widget';
}

export type EntryPoint =
  | PageEntryPoint
  | FileActionEntryPoint
  | SettingsPanelEntryPoint
  | SidebarWidgetEntryPoint;

export type EntryPointType = EntryPoint['type'];

/**
 * The JSON document a plugin ships at `webapp/manifest.json`, and that the
 * core returns from `GET /api/plugins/manifests`.
 */
export interface PluginManifest {
  readonly id: string;
  readonly name: string;
  /** Semantic version of the plugin. */
  readonly version: string;
  /** Major version of `@fiely/host` the plugin was built against. */
  readonly hostApiVersion: HostApiVersion;
  /** Set by the core. Plugins may omit it in `webapp/manifest.json`. */
  readonly trust?: PluginTrust;
  /** Optional minimum Fiely core version (semver). */
  readonly minFielyVersion?: string;
  readonly icon?: string;
  readonly entryPoints: readonly EntryPoint[];
}

/** Type guard for {@link PageEntryPoint}. */
export function isPageEntryPoint(e: EntryPoint): e is PageEntryPoint {
  return e.type === 'page';
}

/** Type guard for {@link FileActionEntryPoint}. */
export function isFileActionEntryPoint(e: EntryPoint): e is FileActionEntryPoint {
  return e.type === 'file-action';
}

/** Type guard for {@link SettingsPanelEntryPoint}. */
export function isSettingsPanelEntryPoint(e: EntryPoint): e is SettingsPanelEntryPoint {
  return e.type === 'settings-panel';
}
