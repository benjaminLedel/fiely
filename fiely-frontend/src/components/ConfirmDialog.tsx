import Modal from './Modal';
import { AlertTriangle } from 'lucide-react';

interface Props {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel?: string;
  destructive?: boolean;
}

export default function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirm',
  destructive = false,
}: Props) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="flex gap-3">
        {destructive && (
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
            <AlertTriangle size={20} className="text-red-600 dark:text-red-400" />
          </div>
        )}
        <p className="text-sm text-ink-600 dark:text-ink-400">{message}</p>
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <button onClick={onClose} className="btn btn-ghost text-sm">
          Cancel
        </button>
        <button
          onClick={() => {
            onConfirm();
            onClose();
          }}
          className={`btn text-sm text-white ${
            destructive
              ? 'bg-red-600 shadow-none hover:bg-red-500'
              : 'btn-primary'
          }`}
        >
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
