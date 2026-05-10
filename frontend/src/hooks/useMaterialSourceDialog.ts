import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "@/api/client";
import { parseSourceTarget, type SourceTarget } from "@/components/ragChatPresentation";
import type { MaterialDetail } from "@/types";

export const useMaterialSourceDialog = (activeRagProjectKey?: string | null) => {
  const [openedMaterial, setOpenedMaterial] = useState<MaterialDetail | null>(null);
  const [isLoadingMaterial, setIsLoadingMaterial] = useState(false);
  const [materialError, setMaterialError] = useState<string | null>(null);
  const [openedSourceTarget, setOpenedSourceTarget] = useState<SourceTarget | null>(null);
  const highlightedChunkRef = useRef<HTMLDivElement | null>(null);
  const normalizedActiveRagProjectKey = useMemo(() => (activeRagProjectKey ?? "").trim(), [activeRagProjectKey]);

  useEffect(() => {
    if (!openedMaterial || !openedSourceTarget || !highlightedChunkRef.current) {
      return;
    }

    highlightedChunkRef.current.scrollIntoView({
      behavior: "smooth",
      block: "center",
    });
  }, [openedMaterial, openedSourceTarget]);

  const openSource = useCallback(async (materialId: string, openSourceUrl?: string | null) => {
    const target = parseSourceTarget(materialId, openSourceUrl);
    setIsLoadingMaterial(true);
    setMaterialError(null);
    setOpenedSourceTarget(target);

    try {
      const detail = normalizedActiveRagProjectKey
        ? await apiClient.fetchMaterial(target.materialId, { workspaceKey: normalizedActiveRagProjectKey })
        : await apiClient.fetchMaterial(target.materialId);
      setOpenedMaterial(detail);
    } catch {
      setMaterialError("Не удалось загрузить источник. Проверь доступность backend и попробуй ещё раз.");
    } finally {
      setIsLoadingMaterial(false);
    }
  }, [normalizedActiveRagProjectKey]);

  const closeSourceDialog = useCallback(() => {
    setOpenedMaterial(null);
    setOpenedSourceTarget(null);
    setMaterialError(null);
  }, []);

  return {
    highlightedChunkRef,
    openedMaterial,
    isLoadingMaterial,
    materialError,
    openedSourceTarget,
    openSource,
    closeSourceDialog,
  };
};

export type MaterialSourceDialogState = ReturnType<typeof useMaterialSourceDialog>;
