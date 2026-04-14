import type { FielyHost } from './host.js';
import type { FileMetadata } from './types.js';

/**
 * Props supplied to a `"page"` entry-point component when the host mounts it
 * under the plugin's declared base path.
 */
export interface PageProps {
  readonly host: FielyHost;
  /** React-router params matched under the plugin's base path. */
  readonly params: Readonly<Record<string, string>>;
}

/**
 * Props supplied to a `"file-action"` entry-point component.
 *
 * The host invokes the action when the user triggers it from a file's
 * context menu for a matching MIME type. The component is responsible for
 * rendering any UI it needs (dialog, inline panel, etc.) and for calling
 * `onClose` when it is done.
 */
export interface FileActionProps {
  readonly host: FielyHost;
  readonly file: FileMetadata;
  /** Dismiss the action. The host will unmount the component. */
  onClose(): void;
  /** Optional: notify the host that the action produced a replacement file. */
  onReplaced?(newFile: FileMetadata): void;
}

/**
 * Props supplied to a `"settings-panel"` entry-point component.
 *
 * Rendered inside the host's settings shell. `scope === 'admin'` panels are
 * only shown to users with admin roles; the plugin still receives it as a
 * prop for convenience.
 */
export interface SettingsPanelProps {
  readonly host: FielyHost;
  readonly scope: 'user' | 'admin';
}

/**
 * Props supplied to a `"sidebar-widget"` entry-point component.
 *
 * Reserved for a future version — not yet rendered by the core.
 */
export interface SidebarWidgetProps {
  readonly host: FielyHost;
}
