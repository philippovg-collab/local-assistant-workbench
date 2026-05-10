export type ReferenceWorkspace = {
  key: string;
  nameRu: string;
  description?: string | null;
  active: boolean;
  sortOrder: number;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ReferenceProject = {
  key: string;
  workspaceKey: string;
  nameRu: string;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type ReferenceWorkspaceInput = {
  key?: string;
  nameRu: string;
  description?: string | null;
  active?: boolean;
  sortOrder?: number;
  isDefault?: boolean;
};

export type RagProjectSummary = {
  key: string;
  name: string;
  description?: string | null;
  active: boolean;
  isDefault: boolean;
  sortOrder: number;
  materialCount: number;
  readyMaterialCount: number;
  updatedAt: string;
};

export type RagProjectInput = {
  key?: string;
  name: string;
  description?: string | null;
  active?: boolean;
  sortOrder?: number;
  isDefault?: boolean;
};

export type ReferenceProjectInput = {
  key?: string;
  workspaceKey: string;
  nameRu: string;
  active?: boolean;
  sortOrder?: number;
};
