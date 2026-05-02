import { useEffect, useRef, useState } from "react";
import { apiClient } from "@/api/client";
import type { MaterialListResponse, MaterialSummary, MaterialUploadPolicy } from "@/types";
import { hasActiveIndexing } from "@/utils/readiness";
import {
  MATERIAL_PAGE_LIMIT,
  MATERIAL_POLL_INTERVAL_MS,
  translateMaterialError,
} from "@/utils/materialPresentation";

const isPageVisible = () => typeof document === "undefined" || document.visibilityState !== "hidden";

const normalizeMaterialListResponse = (payload: MaterialListResponse | MaterialSummary[]): MaterialListResponse => {
  if (Array.isArray(payload)) {
    return {
      items: payload,
      total: payload.length,
      offset: 0,
      limit: payload.length,
      hasMore: false,
    };
  }

  return {
    items: payload.items ?? [],
    total: payload.total ?? payload.items?.length ?? 0,
    offset: payload.offset ?? 0,
    limit: payload.limit ?? MATERIAL_PAGE_LIMIT,
    hasMore: payload.hasMore ?? false,
  };
};

const mergeMaterialPages = (current: MaterialSummary[], next: MaterialSummary[]) => {
  const byId = new Map(current.map((material) => [material.id, material]));
  next.forEach((material) => byId.set(material.id, material));
  return Array.from(byId.values());
};

type UseMaterialCatalogOptions = {
  enabled?: boolean;
};

export const useMaterialCatalog = (
  uploadPolicy: MaterialUploadPolicy | null,
  workspaceKey?: string | null,
  options: UseMaterialCatalogOptions = {},
) => {
  const { enabled = true } = options;
  const [materials, setMaterials] = useState<MaterialSummary[]>([]);
  const [materialTotal, setMaterialTotal] = useState(0);
  const [materialPageLimit, setMaterialPageLimit] = useState(MATERIAL_PAGE_LIMIT);
  const [hasMoreMaterials, setHasMoreMaterials] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(enabled);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const loadedParamsRef = useRef<string | null>(null);
  const paramsKey = workspaceKey?.trim() ?? "";

  const loadMaterials = async (
    signal?: AbortSignal,
    options: { offset?: number; append?: boolean; force?: boolean } = {},
  ) => {
    if (!enabled && !options.force) {
      setIsLoading(false);
      setIsLoadingMore(false);
      return null;
    }

    const offset = options.offset ?? 0;
    const append = options.append ?? false;
    if (append) {
      setIsLoadingMore(true);
    } else {
      setIsLoading(true);
    }

    try {
      const payload = normalizeMaterialListResponse(
        await apiClient.fetchMaterials({ offset, limit: MATERIAL_PAGE_LIMIT, workspaceKey }, signal),
      );
      setMaterials((current) => (append ? mergeMaterialPages(current, payload.items) : payload.items));
      setMaterialTotal(payload.total);
      setMaterialPageLimit(payload.limit);
      setHasMoreMaterials(payload.hasMore);
      setError(null);
      if (!append) {
        loadedParamsRef.current = paramsKey;
      }
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateMaterialError(loadError, "Не удалось загрузить материалы", uploadPolicy);
      setError(nextError);
      return null;
    } finally {
      setIsLoading(false);
      setIsLoadingMore(false);
    }
  };

  useEffect(() => {
    if (!enabled) {
      setIsLoading(false);
      return;
    }
    if (loadedParamsRef.current === paramsKey) {
      return;
    }

    const controller = new AbortController();
    void loadMaterials(controller.signal);
    return () => controller.abort();
  }, [enabled, paramsKey, workspaceKey]);

  useEffect(() => {
    if (!enabled || !hasActiveIndexing(materials)) {
      return;
    }

    let controller: AbortController | null = null;
    const tick = () => {
      if (!isPageVisible()) {
        return;
      }

      controller?.abort();
      controller = new AbortController();
      void loadMaterials(controller.signal);
    };

    const intervalId = window.setInterval(tick, MATERIAL_POLL_INTERVAL_MS);
    const handleVisibilityChange = () => {
      if (isPageVisible()) {
        tick();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      controller?.abort();
      window.clearInterval(intervalId);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [enabled, materials, uploadPolicy, workspaceKey]);

  const loadMoreMaterials = async () => {
    if (!enabled || isLoadingMore || !hasMoreMaterials) {
      return null;
    }

    return loadMaterials(undefined, { offset: materials.length, append: true });
  };

  return {
    materials,
    materialTotal,
    materialPageLimit,
    hasMoreMaterials,
    error,
    isLoading,
    isLoadingMore,
    loadMaterials,
    loadMoreMaterials,
  };
};
