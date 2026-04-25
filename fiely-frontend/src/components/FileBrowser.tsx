import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiFetch } from '../api';
import type { FileNode } from '../types';
import Modal from './Modal';
import ConfirmDialog from './ConfirmDialog';
import {
  ChevronRight,
  Download,
  File,
  Folder,
  FolderPlus,
  Home,
  Loader2,
  Pencil,
  Trash2,
  Upload,
  UploadCloud,
} from 'lucide-react';

interface BreadcrumbEntry {
  id: string;
  name: string;
}

export default function FileBrowser() {
  const { folderId } = useParams<{ folderId?: string }>();
  const navigate = useNavigate();

  const [files, setFiles] = useState<FileNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [trail, setTrail] = useState<BreadcrumbEntry[]>([]);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);

  // Modal state
  const [folderModalOpen, setFolderModalOpen] = useState(false);
  const [folderName, setFolderName] = useState('');
  const [renameTarget, setRenameTarget] = useState<FileNode | null>(null);
  const [renameName, setRenameName] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<FileNode | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const dragCounter = useRef(0);

  const fetchFiles = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = folderId ? `/api/files?parentId=${folderId}` : '/api/files';
      const res = await apiFetch(url);
      if (!res.ok) throw new Error('Failed to load files');
      setFiles(await res.json());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, [folderId]);

  useEffect(() => {
    fetchFiles();
  }, [fetchFiles]);

  // Breadcrumb resolution on deep-link.
  useEffect(() => {
    if (!folderId) {
      setTrail([]);
      return;
    }
    const idx = trail.findIndex((b) => b.id === folderId);
    if (idx >= 0) {
      setTrail((prev) => prev.slice(0, idx + 1));
      return;
    }
    (async () => {
      const chain: BreadcrumbEntry[] = [];
      let cursor: string | null = folderId;
      while (cursor) {
        const res = await apiFetch(`/api/files/${cursor}`);
        if (!res.ok) break;
        const node: FileNode = await res.json();
        chain.unshift({ id: node.id, name: node.name });
        cursor = node.parentId;
      }
      setTrail(chain);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [folderId]);

  // --- Actions ---------------------------------------------------------------

  function openFolder(folder: FileNode) {
    setTrail((prev) => [...prev, { id: folder.id, name: folder.name }]);
    navigate(`/files/${folder.id}`);
  }

  function navigateToBreadcrumb(index: number) {
    if (index < 0) {
      setTrail([]);
      navigate('/files');
    } else {
      setTrail((prev) => prev.slice(0, index + 1));
      navigate(`/files/${trail[index].id}`);
    }
  }

  async function handleUpload(fileList: FileList | null) {
    if (!fileList?.length) return;
    setUploading(true);
    try {
      for (const file of Array.from(fileList)) {
        const form = new FormData();
        form.append('file', file);
        if (folderId) form.append('parentId', folderId);
        const res = await apiFetch('/api/files', { method: 'POST', body: form });
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          alert(`Failed to upload ${file.name}: ${body?.error ?? res.status}`);
        }
      }
      await fetchFiles();
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function submitCreateFolder() {
    if (!folderName.trim()) return;
    const res = await apiFetch('/api/files/folder', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: folderName.trim(), parentId: folderId ?? null }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      alert(body?.error ?? 'Failed to create folder');
      return;
    }
    setFolderModalOpen(false);
    setFolderName('');
    await fetchFiles();
  }

  async function submitRename() {
    if (!renameTarget || !renameName.trim() || renameName.trim() === renameTarget.name) return;
    const res = await apiFetch(`/api/files/${renameTarget.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: renameName.trim() }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      alert(body?.error ?? 'Rename failed');
      return;
    }
    setRenameTarget(null);
    await fetchFiles();
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    const res = await apiFetch(`/api/files/${deleteTarget.id}`, { method: 'DELETE' });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      alert(body?.error ?? 'Delete failed');
    }
    setDeleteTarget(null);
    await fetchFiles();
  }

  async function downloadFile(node: FileNode) {
    const res = await apiFetch(`/api/files/${node.id}/content`);
    if (!res.ok) {
      alert('Download failed');
      return;
    }
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = node.name;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  // --- Drag & Drop -----------------------------------------------------------

  function onDragEnter(e: DragEvent) {
    e.preventDefault();
    dragCounter.current++;
    if (e.dataTransfer.types.includes('Files')) setDragging(true);
  }

  function onDragLeave(e: DragEvent) {
    e.preventDefault();
    dragCounter.current--;
    if (dragCounter.current === 0) setDragging(false);
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    dragCounter.current = 0;
    setDragging(false);
    handleUpload(e.dataTransfer.files);
  }

  // --- Render ----------------------------------------------------------------

  return (
    <div
      className="relative mx-auto max-w-5xl px-4 py-6"
      onDragEnter={onDragEnter}
      onDragOver={(e) => e.preventDefault()}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      {/* Drag overlay */}
      {dragging && (
        <div className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center rounded-2xl border-2 border-dashed border-brand-400 bg-brand-50/80 dark:bg-brand-950/80">
          <div className="flex flex-col items-center gap-2 text-brand-600 dark:text-brand-300">
            <UploadCloud size={40} strokeWidth={1.5} />
            <span className="text-sm font-medium">Drop files to upload</span>
          </div>
        </div>
      )}

      {/* Toolbar */}
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <Breadcrumbs trail={trail} onNavigate={navigateToBreadcrumb} />

        <div className="flex items-center gap-2">
          <button
            onClick={() => {
              setFolderName('');
              setFolderModalOpen(true);
            }}
            className="btn btn-ghost text-sm"
          >
            <FolderPlus size={16} />
            <span className="hidden sm:inline">New folder</span>
          </button>
          <label
            className={`btn btn-primary text-sm ${uploading ? 'pointer-events-none opacity-60' : 'cursor-pointer'}`}
          >
            {uploading ? (
              <Loader2 size={16} className="animate-spin" />
            ) : (
              <Upload size={16} />
            )}
            <span className="hidden sm:inline">Upload</span>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden"
              onChange={(e) => handleUpload(e.target.files)}
              disabled={uploading}
            />
          </label>
        </div>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-ink-400">
          <Loader2 size={24} className="animate-spin" />
        </div>
      ) : error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {error}
        </div>
      ) : files.length === 0 ? (
        <EmptyState />
      ) : (
        <div className="overflow-hidden rounded-xl border border-ink-200/80 bg-white shadow-sm dark:border-ink-800 dark:bg-ink-900">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-ink-100 text-left text-xs font-medium uppercase tracking-wider text-ink-400 dark:border-ink-800 dark:text-ink-500">
                <th className="px-4 py-2.5">Name</th>
                <th className="hidden px-4 py-2.5 sm:table-cell">Size</th>
                <th className="hidden px-4 py-2.5 md:table-cell">Modified</th>
                <th className="w-28 px-4 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
              {files.map((node) => (
                <FileRow
                  key={node.id}
                  node={node}
                  onOpen={openFolder}
                  onRename={(n) => {
                    setRenameTarget(n);
                    setRenameName(n.name);
                  }}
                  onDelete={setDeleteTarget}
                  onDownload={downloadFile}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create folder modal */}
      <Modal
        open={folderModalOpen}
        onClose={() => setFolderModalOpen(false)}
        title="New folder"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submitCreateFolder();
          }}
        >
          <input
            autoFocus
            type="text"
            value={folderName}
            onChange={(e) => setFolderName(e.target.value)}
            placeholder="Folder name"
            className="block w-full rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-900 shadow-sm placeholder:text-ink-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20 dark:border-white/10 dark:bg-ink-900/60 dark:text-ink-50"
          />
          <div className="mt-4 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setFolderModalOpen(false)}
              className="btn btn-ghost text-sm"
            >
              Cancel
            </button>
            <button type="submit" className="btn btn-primary text-sm">
              Create
            </button>
          </div>
        </form>
      </Modal>

      {/* Rename modal */}
      <Modal
        open={renameTarget !== null}
        onClose={() => setRenameTarget(null)}
        title="Rename"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submitRename();
          }}
        >
          <input
            autoFocus
            type="text"
            value={renameName}
            onChange={(e) => setRenameName(e.target.value)}
            placeholder="New name"
            className="block w-full rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-900 shadow-sm placeholder:text-ink-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20 dark:border-white/10 dark:bg-ink-900/60 dark:text-ink-50"
          />
          <div className="mt-4 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setRenameTarget(null)}
              className="btn btn-ghost text-sm"
            >
              Cancel
            </button>
            <button type="submit" className="btn btn-primary text-sm">
              Rename
            </button>
          </div>
        </form>
      </Modal>

      {/* Delete confirm */}
      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
        title={`Delete ${deleteTarget?.isFolder ? 'folder' : 'file'}`}
        message={`Are you sure you want to delete "${deleteTarget?.name ?? ''}"?${deleteTarget?.isFolder ? ' All contents will be permanently removed.' : ''}`}
        confirmLabel="Delete"
        destructive
      />
    </div>
  );
}

// --- Sub-components ---------------------------------------------------------

function Breadcrumbs({
  trail,
  onNavigate,
}: {
  trail: BreadcrumbEntry[];
  onNavigate: (index: number) => void;
}) {
  return (
    <nav className="flex items-center gap-1 text-sm">
      <button
        onClick={() => onNavigate(-1)}
        className="flex items-center gap-1.5 rounded-lg px-2 py-1 text-ink-500 transition hover:bg-ink-100 hover:text-ink-900 dark:text-ink-400 dark:hover:bg-ink-800 dark:hover:text-ink-100"
      >
        <Home size={15} />
        <span>Files</span>
      </button>
      {trail.map((entry, i) => (
        <span key={entry.id} className="flex items-center gap-1">
          <ChevronRight size={14} className="text-ink-300 dark:text-ink-600" />
          <button
            onClick={() => onNavigate(i)}
            className={`rounded-lg px-2 py-1 transition hover:bg-ink-100 hover:text-ink-900 dark:hover:bg-ink-800 dark:hover:text-ink-100 ${
              i === trail.length - 1
                ? 'font-medium text-ink-900 dark:text-ink-100'
                : 'text-ink-500 dark:text-ink-400'
            }`}
          >
            {entry.name}
          </button>
        </span>
      ))}
    </nav>
  );
}

function FileRow({
  node,
  onOpen,
  onRename,
  onDelete,
  onDownload,
}: {
  node: FileNode;
  onOpen: (f: FileNode) => void;
  onRename: (f: FileNode) => void;
  onDelete: (f: FileNode) => void;
  onDownload: (f: FileNode) => void;
}) {
  return (
    <tr className="group transition hover:bg-ink-50/80 dark:hover:bg-ink-800/40">
      <td className="px-4 py-2.5">
        <div
          className={`flex items-center gap-3 ${node.isFolder ? 'cursor-pointer' : ''}`}
          onClick={() => node.isFolder && onOpen(node)}
        >
          {node.isFolder ? (
            <Folder
              size={18}
              className="shrink-0 text-brand-500"
              fill="currentColor"
              fillOpacity={0.15}
            />
          ) : (
            <File size={18} className="shrink-0 text-ink-400 dark:text-ink-500" />
          )}
          <span className="min-w-0 truncate text-ink-900 dark:text-ink-100">
            {node.name}
          </span>
        </div>
      </td>
      <td className="hidden px-4 py-2.5 text-ink-400 sm:table-cell dark:text-ink-500">
        {node.isFolder ? '—' : formatBytes(node.sizeBytes)}
      </td>
      <td className="hidden px-4 py-2.5 text-ink-400 md:table-cell dark:text-ink-500">
        {formatRelativeTime(node.updatedAt)}
      </td>
      <td className="px-4 py-2.5">
        <div className="flex items-center justify-end gap-0.5 opacity-0 transition group-hover:opacity-100">
          {!node.isFolder && (
            <IconButton title="Download" onClick={() => onDownload(node)}>
              <Download size={15} />
            </IconButton>
          )}
          <IconButton title="Rename" onClick={() => onRename(node)}>
            <Pencil size={15} />
          </IconButton>
          <IconButton
            title="Delete"
            onClick={() => onDelete(node)}
            className="text-red-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950"
          >
            <Trash2 size={15} />
          </IconButton>
        </div>
      </td>
    </tr>
  );
}

function IconButton({
  title,
  onClick,
  className = '',
  children,
}: {
  title: string;
  onClick: () => void;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <button
      title={title}
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      className={`rounded-md p-1.5 text-ink-400 transition hover:bg-ink-100 hover:text-ink-700 dark:hover:bg-ink-800 dark:hover:text-ink-200 ${className}`}
    >
      {children}
    </button>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-ink-100 dark:bg-ink-800">
        <UploadCloud size={32} strokeWidth={1.5} className="text-ink-400 dark:text-ink-500" />
      </div>
      <div>
        <p className="font-medium text-ink-700 dark:text-ink-300">No files yet</p>
        <p className="mt-1 text-sm text-ink-400 dark:text-ink-500">
          Upload files or drag and drop them here.
        </p>
      </div>
    </div>
  );
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1,
  );
  const value = bytes / Math.pow(1024, i);
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`;
}

function formatRelativeTime(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 7) return `${diffDay}d ago`;
  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
  });
}
