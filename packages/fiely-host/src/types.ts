/**
 * Value types exchanged between the Fiely host and plugins.
 *
 * These are the wire-compatible shapes: anything passed over `postMessage`
 * for iframe plugins, or across the in-process host boundary, must be a
 * structured-cloneable subset of these types.
 */

/** The authenticated user the plugin is running on behalf of. */
export interface UserInfo {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
  readonly roles: readonly string[];
}

/** Metadata describing a single file in the user's storage. */
export interface FileMetadata {
  readonly id: string;
  readonly name: string;
  readonly size: number;
  readonly mimeType: string;
  /** `null` for files at the user's root. */
  readonly folderId: string | null;
  /** ISO-8601 timestamp. */
  readonly createdAt: string;
  /** ISO-8601 timestamp. */
  readonly updatedAt: string;
  readonly ownerId: string;
  readonly tags: readonly string[];
}

/** Options for listing the contents of a folder. */
export interface ListOptions {
  /** Opaque cursor from a previous `Page.nextCursor`. */
  cursor?: string;
  /** Maximum items to return. The host may clamp this. */
  limit?: number;
  sort?: 'name' | 'updatedAt' | 'createdAt' | 'size';
  order?: 'asc' | 'desc';
}

/** A single page of results. */
export interface Page<T> {
  readonly items: readonly T[];
  /** `null` when there are no more pages. */
  readonly nextCursor: string | null;
}

/** Design tokens exposed by the host so plugins can theme themselves consistently. */
export interface ThemeTokens {
  readonly mode: 'light' | 'dark';
  readonly colors: Readonly<Record<string, string>>;
  readonly radii: Readonly<Record<string, string>>;
  readonly spacing: Readonly<Record<string, string>>;
}

/** Severity of a toast notification. */
export type ToastKind = 'info' | 'success' | 'warning' | 'error';

/** Options for {@link FielyHost.ui.confirm}. */
export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'default' | 'danger';
}
