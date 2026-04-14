import type { HostEventOf, HostEventType } from './events.js';
import type {
  ConfirmOptions,
  FileMetadata,
  ListOptions,
  Page,
  ThemeTokens,
  ToastKind,
  UserInfo,
} from './types.js';

/**
 * The current major version of the Fiely host API.
 *
 * Plugins must declare this value in their `manifest.json` as `hostApiVersion`;
 * the core refuses to load plugins that advertise a different major.
 */
export const HOST_API_VERSION = '1' as const;
export type HostApiVersion = typeof HOST_API_VERSION;

/** Files sub-API — typed wrappers around the Fiely file service. */
export interface FielyFilesApi {
  /** Fetch metadata for a single file. */
  get(id: string): Promise<FileMetadata>;

  /** List the children of a folder. Pass `null` for the root. */
  list(folderId: string | null, opts?: ListOptions): Promise<Page<FileMetadata>>;

  /** Download the binary contents of a file. */
  download(id: string): Promise<Blob>;

  /**
   * Upload a new file into `folderId`. Requires `WRITE_FILES` permission.
   * Rejects with a `PermissionError` if the plugin does not have it.
   */
  upload(folderId: string | null, file: Blob, name: string): Promise<FileMetadata>;

  /** Permanently delete a file. Requires `WRITE_FILES`. */
  delete(id: string): Promise<void>;
}

/** UI affordances — all user-visible effects go through this surface. */
export interface FielyUiApi {
  toast(message: string, kind?: ToastKind): void;
  confirm(opts: ConfirmOptions): Promise<boolean>;
  /** Navigate to a path owned by the host router. */
  navigate(path: string): void;
  /** Open a file in the host's file viewer. */
  openFile(id: string): void;
}

/**
 * The host surface a plugin sees.
 *
 * Implementations are created by the core:
 * - in-process (first-party) plugins receive a live instance as a prop,
 * - iframe (third-party) plugins receive a proxy constructed by
 *   {@link ./client.createHostClient}.
 *
 * Plugins must not attempt to extend the host (e.g. via `window`). Anything
 * missing from this surface is missing on purpose; propose additions via an
 * RFC rather than reaching into globals.
 */
export interface FielyHost {
  /** The authenticated user. Stable for the lifetime of the plugin instance. */
  readonly user: UserInfo;

  /** Design tokens from the host. Re-emitted on `theme.changed` events. */
  readonly theme: ThemeTokens;

  /** Major version of this API. Always `'1'` for this package. */
  readonly hostApiVersion: HostApiVersion;

  /**
   * Plugin-specific configuration from `application.yml`
   * (the `fiely.plugins.<plugin-id>` map).
   */
  readonly config: Readonly<Record<string, string>>;

  /**
   * An already-authenticated `fetch` pinned to the Fiely API origin.
   * Relative URLs resolve against the core API base. Absolute URLs to other
   * origins are rejected.
   */
  fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response>;

  /** File-service sub-API. */
  readonly files: FielyFilesApi;

  /** UI affordances. */
  readonly ui: FielyUiApi;

  /**
   * Subscribe to a host event. Returns an unsubscribe function.
   *
   * @example
   * const off = host.on('file.uploaded', (e) => console.log(e.file));
   * // later:
   * off();
   */
  on<T extends HostEventType>(
    type: T,
    handler: (event: HostEventOf<T>) => void
  ): () => void;

  /** Translate a key using the host's i18n catalog, with optional interpolation. */
  t(key: string, params?: Readonly<Record<string, string | number>>): string;
}

/**
 * An error raised by host calls that violate the plugin's declared permissions.
 * RPC wire-errors with `code: 'permission_denied'` are reified as this class in
 * the client.
 */
export class PermissionError extends Error {
  readonly code = 'permission_denied' as const;
  constructor(message: string) {
    super(message);
    this.name = 'PermissionError';
  }
}
