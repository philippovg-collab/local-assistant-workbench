import { useState } from "react";
import { apiClient } from "@/api/client";
import type {
  MaterialListResponse,
  MaterialLineageResponse,
  MaterialLineageOverrideInput,
  MaterialMetadataInput,
  MaterialSummary,
  MaterialUploadPolicy,
  MaterialVersionUploadInput,
  UpdateMaterialInput,
} from "@/types";
import {
  buildIngestionMessage,
  buildUploadIngestionMessage,
  translateMaterialError,
  validateUploadInput,
  withFileLabel,
  type UploadMaterialInput,
} from "@/utils/materialPresentation";

type UseMaterialMutationsInput = {
  workspaceKey?: string | null;
  uploadPolicy: MaterialUploadPolicy | null;
  ensureUploadPolicy: () => Promise<MaterialUploadPolicy>;
  loadMaterials: () => Promise<MaterialListResponse | null>;
  clearLineageIfContains: (materialId: string) => void;
  refreshLineageIfContains: (materialId: string) => Promise<MaterialLineageResponse | null>;
};

export const useMaterialMutations = ({
  workspaceKey,
  uploadPolicy,
  ensureUploadPolicy,
  loadMaterials,
  clearLineageIfContains,
  refreshLineageIfContains,
}: UseMaterialMutationsInput) => {
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deletingMaterialId, setDeletingMaterialId] = useState<string | null>(null);
  const [editingMaterialId, setEditingMaterialId] = useState<string | null>(null);
  const [reindexingMaterialId, setReindexingMaterialId] = useState<string | null>(null);
  const [versionUploadingMaterialId, setVersionUploadingMaterialId] = useState<string | null>(null);

  const withWorkspaceMetadata = (metadata?: MaterialMetadataInput) => {
    const normalizedWorkspaceKey = workspaceKey?.trim();
    if (!metadata && !normalizedWorkspaceKey) {
      return undefined;
    }
    return {
      ...(metadata ?? {}),
      ...(normalizedWorkspaceKey ? { workspaceKey: normalizedWorkspaceKey, projectKey: undefined } : {}),
    };
  };

  const createTextMaterial = async (input: {
    title: string;
    content: string;
    metadata?: MaterialMetadataInput;
    lineageOverride?: MaterialLineageOverrideInput;
  }) => {
    setActionError(null);
    setMessage(null);

    try {
      const created = await apiClient.createTextMaterial({
        ...input,
        metadata: withWorkspaceMetadata(input.metadata),
      });
      await loadMaterials();
      setMessage(buildIngestionMessage(created, "Текстовый материал"));
    } catch (submissionError) {
      setActionError(translateMaterialError(submissionError, "Не удалось сохранить материал", uploadPolicy));
      throw submissionError;
    }
  };

  const uploadMaterial = async (input: UploadMaterialInput) => {
    setActionError(null);
    setMessage(null);
    let activePolicy = uploadPolicy;
    let handledError = false;

    try {
      const policy = await ensureUploadPolicy();
      activePolicy = policy;

      const validationError = validateUploadInput(input, policy);
      if (validationError) {
        setActionError(validationError);
        handledError = true;
        throw new Error(validationError);
      }

      const createdMaterials: MaterialSummary[] = [];

      for (const item of input.items) {
        try {
          const created = await apiClient.uploadMaterial({
            title: item.title ?? "",
            file: item.file,
            metadata: withWorkspaceMetadata(item.metadata),
            lineageOverride: item.lineageOverride,
          });
          createdMaterials.push(created);
        } catch (fileError) {
          if (createdMaterials.length > 0) {
            await loadMaterials();
          }

          setActionError(withFileLabel(
            item.file,
            translateMaterialError(fileError, "Не удалось загрузить файл", activePolicy),
          ));
          handledError = true;
          throw fileError;
        }
      }

      await loadMaterials();
      setMessage(buildUploadIngestionMessage(createdMaterials));
    } catch (submissionError) {
      if (!handledError) {
        setActionError(translateMaterialError(submissionError, "Не удалось загрузить файл", activePolicy));
      }
      throw submissionError;
    }
  };

  const deleteMaterial = async (materialId: string) => {
    setDeletingMaterialId(materialId);
    setActionError(null);
    setMessage(null);

    try {
      await apiClient.deleteMaterial(materialId, workspaceKey);
      await loadMaterials();
      clearLineageIfContains(materialId);
      setMessage("Материал удалён.");
    } catch (deleteError) {
      setActionError(translateMaterialError(deleteError, "Не удалось удалить материал", uploadPolicy));
      throw deleteError;
    } finally {
      setDeletingMaterialId(null);
    }
  };

  const uploadMaterialVersion = async (materialId: string, input: MaterialVersionUploadInput) => {
    setVersionUploadingMaterialId(materialId);
    setActionError(null);
    setMessage(null);
    let activePolicy = uploadPolicy;
    let handledError = false;

    try {
      const policy = await ensureUploadPolicy();
      activePolicy = policy;

      const validationError = validateUploadInput({ items: [input] }, policy);
      if (validationError) {
        setActionError(validationError);
        handledError = true;
        throw new Error(validationError);
      }

      const created = await apiClient.uploadMaterialVersion(
        materialId,
        {
          ...input,
          metadata: withWorkspaceMetadata(input.metadata),
        },
        workspaceKey,
      );
      await loadMaterials();
      await refreshLineageIfContains(materialId);
      setMessage(buildIngestionMessage(created, "Файл новой версии"));
      return created;
    } catch (uploadError) {
      if (!handledError) {
        setActionError(withFileLabel(
          input.file,
          translateMaterialError(uploadError, "Не удалось загрузить новую версию", activePolicy),
        ));
      }
      throw uploadError;
    } finally {
      setVersionUploadingMaterialId((current) => (current === materialId ? null : current));
    }
  };

  const editMaterial = async (materialId: string, input: UpdateMaterialInput) => {
    setEditingMaterialId(materialId);
    setActionError(null);
    setMessage(null);

    try {
      const updated = await apiClient.updateMaterial(
        materialId,
        {
          ...input,
          metadata: withWorkspaceMetadata(input.metadata),
        },
        workspaceKey,
      );
      await loadMaterials();
      await refreshLineageIfContains(materialId);
      setMessage("Редакция сохранена как новая версия.");
      return updated;
    } catch (editError) {
      setActionError(translateMaterialError(editError, "Не удалось сохранить редакцию материала", uploadPolicy));
      throw editError;
    } finally {
      setEditingMaterialId((current) => (current === materialId ? null : current));
    }
  };

  const reindexMaterial = async (materialId: string) => {
    setReindexingMaterialId(materialId);
    setActionError(null);
    setMessage(null);

    try {
      const updated = await apiClient.reindexMaterial(materialId, workspaceKey);
      await loadMaterials();
      await refreshLineageIfContains(materialId);
      setMessage(
        updated.status === "PENDING"
          ? "Материал повторно поставлен в очередь индексации."
          : "Материал обновил состояние после запроса на повторную индексацию.",
      );
      return updated;
    } catch (reindexError) {
      setActionError(translateMaterialError(reindexError, "Не удалось повторно запустить индексацию", uploadPolicy));
      throw reindexError;
    } finally {
      setReindexingMaterialId(null);
    }
  };

  return {
    message,
    actionError,
    deletingMaterialId,
    editingMaterialId,
    reindexingMaterialId,
    versionUploadingMaterialId,
    createTextMaterial,
    uploadMaterial,
    uploadMaterialVersion,
    editMaterial,
    deleteMaterial,
    reindexMaterial,
  };
};
