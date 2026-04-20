import { useState } from "react";
import { apiClient } from "@/api/client";
import type { MaterialLineageResponse, MaterialUploadPolicy } from "@/types";
import { translateMaterialError } from "@/utils/materialPresentation";

export const useMaterialLineage = (uploadPolicy: MaterialUploadPolicy | null) => {
  const [selectedLineage, setSelectedLineage] = useState<MaterialLineageResponse | null>(null);
  const [lineageError, setLineageError] = useState<string | null>(null);
  const [loadingLineageMaterialId, setLoadingLineageMaterialId] = useState<string | null>(null);

  const loadLineage = async (materialId: string, signal?: AbortSignal) => {
    setLoadingLineageMaterialId(materialId);
    setLineageError(null);

    try {
      const lineage = await apiClient.fetchMaterialLineage(materialId, signal);
      setSelectedLineage(lineage);
      return lineage;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateMaterialError(loadError, "Не удалось загрузить историю версий материала", uploadPolicy);
      setLineageError(nextError);
      return null;
    } finally {
      setLoadingLineageMaterialId((current) => (current === materialId ? null : current));
    }
  };

  const clearLineage = () => {
    setSelectedLineage(null);
    setLineageError(null);
    setLoadingLineageMaterialId(null);
  };

  const clearLineageIfContains = (materialId: string) => {
    if (!selectedLineage?.versions.some((version) => version.id === materialId)) {
      return;
    }

    clearLineage();
  };

  const refreshLineageIfContains = async (materialId: string) => {
    if (!selectedLineage?.versions.some((version) => version.id === materialId)) {
      return null;
    }

    return loadLineage(materialId);
  };

  return {
    selectedLineage,
    lineageError,
    loadingLineageMaterialId,
    loadLineage,
    clearLineage,
    clearLineageIfContains,
    refreshLineageIfContains,
  };
};
