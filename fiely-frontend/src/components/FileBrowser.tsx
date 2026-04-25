import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiFetch } from '../api';
import type { FileNode } from '../types';
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

  const fileInputRef = useRef<HTMLInputElement>(null);

  const fetchFiles = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = folderId
        ? `/api/files?parentId=${folderId}`
        : '/api/files';
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

  // When deep-linking to a folder, resolve the breadcrumb trail.
  useEffect(() => {
    if (!folderId) {
      setTrail([]);
      return;
    }
    // If we already have this folder in trail, slice to it.
    const idx = trail.findIndex((b) => b.id === folderId);
    if (idx >= 0) {
      setTrail((prev) => prev.slice(0, idx + 1));
      return;
    }
    // Resolve from API (deep link).
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
          const msg = body?.error ?? `Upload failed (${res.status})`;
          alert(`Failed to upload ${file.name}: ${msg}`);
        }
      }
      await fetchFiles();
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function createFolder() {
    const name = window.prompt('Folder name:');
    if (!name?.trim()) return;
    const res = await apiFetch('/api/files/folder', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name.trim(), parentId: folderId ?? null }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      alert(body?.error ?? 'Failed to create folder');
      return;
    }
    await fetchFiles();
  }

  async function renameNode(node: FileNode) {
    const newName = window.prompt('New name:', node.name);
    if (!newName?.trim() || newName.trim() === node.name) return;
    const res = await apiFetch(`/api/files/${node.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newName.trim() }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      alert(body?.error ?? 'Rename failed');
      return;
    }
    await fetchFiles();
  }

  async function deleteNode(node: FileNode) {
    const label = node.isFolder ? 'folder' : 'file';
    if (!window.confirm(`Delete ${label} "${node.name}"?`)) return;
    const res = await apiFetch(`/api/files/${node.id}`, { method: 'DELETE' });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      alert(body?.error ?? 'Delete failed');
      return;
    }
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

  // --- Render ----------------------------------------------------------------

  return (
    <div className="mx-auto max-w-5xl px-4 py-6">
      {/* Toolbar */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Breadcrumbs trail={trail} onNavigate={navigateToBreadcrumb} />

        <div className="flex items-center gap-2">
          <button onClick={createFolder} className="btn btn-ghost text-sm">
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
        <div className="flex items-center justify-center py-20 text-ink-400">
          <Loader2 size={24} className="animate-spin" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {error}
        </div>
      ) : files.length === 0 ? (
        <EmptyState />
      ) : (
        <ul className="divide-y divide-ink-100 rounded-xl border border-ink-200 bg-white dark:divide-ink-800 dark:border-ink-800 dark:bg-ink-900">
          {files.map((node) => (
            <FileRow
              key={node.id}
              node={node}
              onOpen={openFolder}
              onRename={renameNode}
              onDelete={deleteNode}
              onDownload={downloadFile}
            />
          ))}
        </ul>
      )}
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
    <nav className="flex items-center gap-1 text-sm text-ink-600 dark:text-ink-400">
      <button
        onClick={() => onNavigate(-1)}
        className="flex items-center gap-1 rounded px-1.5 py-1 transition hover:bg-ink-100 hover:text-ink-900 dark:hover:bg-ink-800 dark:hover:text-ink-100"
      >
        <Home size={15} />
        <span>Files</span>
      </button>
      {trail.map((entry, i) => (
        <span key={entry.id} className="flex items-center gap-1">
          <ChevronRight size={14} className="text-ink-300 dark:text-ink-600" />
          <button
            onClick={() => onNavigate(i)}
            className={`rounded px-1.5 py-1 transition hover:bg-ink-100 hover:text-ink-900 dark:hover:bg-ink-800 dark:hover:text-ink-100 ${
              i === trail.length - 1
                ? 'font-medium text-ink-900 dark:text-ink-100'
                : ''
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
  const handleClick = () => {
    if (node.isFolder) onOpen(node);
  };

  return (
    <li className="group flex items-center gap-3 px-4 py-2.5 transition hover:bg-ink-50 dark:hover:bg-ink-800/50">
      <div
        className={`flex flex-1 items-center gap-3 ${
          node.isFolder ? 'cursor-pointer' : ''
        }`}
        onClick={handleClick}
      >
        {node.isFolder ? (
          <Folder
            size={20}
            className="shrink-0 text-brand-500"
            fill="currentColor"
            fillOpacity={0.15}
          />
        ) : (
          <File size={20} className="shrink-0 text-ink-400 dark:text-ink-500" />
        )}
        <span className="min-w-0 truncate text-sm text-ink-900 dark:text-ink-100">
          {node.name}
        </span>
      </div>

      {!node.isFolder && (
        <span className="hidden shrink-0 text-xs text-ink-400 sm:block">
          {formatBytes(node.sizeBytes)}
        </span>
      )}

      <div className="flex shrink-0 items-center gap-1 opacity-0 transition group-hover:opacity-100">
        {!node.isFolder && (
          <IconButton
            title="Download"
            onClick={() => onDownload(node)}
          >
            <Download size={15} />
          </IconButton>
        )}
        <IconButton title="Rename" onClick={() => onRename(node)}>
          <Pencil size={15} />
        </IconButton>
        <IconButton
          title="Delete"
          onClick={() => onDelete(node)}
          className="text-red-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950"
        >
          <Trash2 size={15} />
        </IconButton>
      </div>
    </li>
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
    <div className="flex flex-col items-center justify-center gap-3 py-20 text-center text-ink-400 dark:text-ink-500">
      <Folder size={48} strokeWidth={1} />
      <p className="text-sm">
        No files yet. Upload something or create a folder.
      </p>
    </div>
  );
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, i);
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`;
}
