export interface FileNode {
  id: string;
  parentId: string | null;
  name: string;
  isFolder: boolean;
  sizeBytes: number;
  contentType: string | null;
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
}
