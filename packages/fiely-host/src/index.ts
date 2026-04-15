/**
 * `@fiely/host` — the contract a Fiely plugin compiles against.
 *
 * This package is framework-agnostic at its core: it's just types plus a
 * couple of small helpers the Fiely frontend uses to load plugin bundles.
 * Plugins receive a live {@link FielyHost} as a prop and interact with the
 * host through that interface exclusively.
 *
 * Consumers:
 *
 * - **Plugin authors** — `import type { FielyHost, PageProps, ... } from '@fiely/host'`.
 * - **The Fiely core frontend** — additionally uses `@fiely/host/mount`
 *   helpers to load plugin bundles.
 */

export type {
  ConfirmOptions,
  FileMetadata,
  ListOptions,
  Page,
  ThemeTokens,
  ToastKind,
  UserInfo,
} from './types.js';

export type { HostEvent, HostEventOf, HostEventType } from './events.js';

export type {
  FielyFilesApi,
  FielyHost,
  FielyUiApi,
  HostApiVersion,
} from './host.js';

export { HOST_API_VERSION, PermissionError } from './host.js';

export type {
  FileActionProps,
  PageProps,
  SettingsPanelProps,
  SidebarWidgetProps,
} from './entry-points.js';

export type {
  EntryPoint,
  EntryPointBase,
  EntryPointType,
  FileActionEntryPoint,
  PageEntryPoint,
  PluginManifest,
  PluginTrust,
  SettingsPanelEntryPoint,
  SidebarWidgetEntryPoint,
} from './manifest.js';

export {
  isFileActionEntryPoint,
  isPageEntryPoint,
  isSettingsPanelEntryPoint,
} from './manifest.js';
