import type { FileMetadata } from './types.js';

/**
 * Events that the host publishes to subscribed plugins.
 *
 * A plugin only receives events it has permission to observe — for example,
 * `file.*` events require `READ_FILES` on the backend `AppPermission` set.
 */
export type HostEvent =
  | { readonly type: 'file.uploaded'; readonly file: FileMetadata }
  | { readonly type: 'file.updated'; readonly file: FileMetadata }
  | { readonly type: 'file.deleted'; readonly fileId: string }
  | { readonly type: 'navigation'; readonly path: string }
  | { readonly type: 'theme.changed'; readonly mode: 'light' | 'dark' };

/** String literal union of every event `type`. */
export type HostEventType = HostEvent['type'];

/** Narrow `HostEvent` to a specific `type`. */
export type HostEventOf<T extends HostEventType> = Extract<HostEvent, { type: T }>;
