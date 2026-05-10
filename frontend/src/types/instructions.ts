import type {
  InstructionCategory,
  InstructionScopeLevel,
} from "../generated/api-types";

export type InstructionSummary = {
  id: string;
  title: string;
  category: InstructionCategory;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision?: number;
  active?: boolean;
  preview: string;
  createdAt: string;
  updatedAt?: string;
};

export type InstructionDetail = {
  id: string;
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision?: number;
  active?: boolean;
  createdAt: string;
  updatedAt?: string;
};

export type AppliedInstruction = {
  id: string;
  title: string;
  category: InstructionCategory;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision?: number;
};

export type InstructionTraceEntry = {
  instructionId?: string | null;
  title: string;
  category: InstructionCategory;
  scopeLevel: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision: number;
  active: boolean;
  temporary: boolean;
  contentPreview: string;
};

export type InstructionRevisionDetail = {
  instructionId: string;
  revision: number;
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel: InstructionScopeLevel;
  scopeTargetId?: string | null;
  active: boolean;
  restoredFromRevision?: number | null;
  createdAt: string;
  updatedAt?: string;
};

export type RevisionDiffEntry = {
  field: string;
  fromValue?: string | null;
  toValue?: string | null;
};

export type InstructionRevisionDiff = {
  instructionId: string;
  fromRevision: number;
  toRevision: number;
  changes: RevisionDiffEntry[];
};

export type CreateInstructionRequest = {
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  active?: boolean;
};
