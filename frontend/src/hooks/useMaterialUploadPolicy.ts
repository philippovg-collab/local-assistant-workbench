import { useEffect, useState } from "react";
import { apiClient } from "@/api/client";
import {
  MATERIAL_POLICY_WARNING_MESSAGE,
} from "@/utils/materialPresentation";
import {
  buildFallbackMaterialUploadPolicy,
  normalizeMaterialUploadPolicy,
} from "@/utils/materialUploadPolicy";
import type { MaterialUploadPolicy } from "@/types";

export const useMaterialUploadPolicy = () => {
  const [uploadPolicy, setUploadPolicy] = useState<MaterialUploadPolicy | null>(null);
  const [policyWarning, setPolicyWarning] = useState<string | null>(null);

  const applyUploadPolicy = (payload: unknown) => {
    const normalized = normalizeMaterialUploadPolicy(payload);

    if (!normalized.policy) {
      const fallbackPolicy = buildFallbackMaterialUploadPolicy();
      setUploadPolicy(fallbackPolicy);
      setPolicyWarning(MATERIAL_POLICY_WARNING_MESSAGE);
      return fallbackPolicy;
    }

    setUploadPolicy(normalized.policy);
    setPolicyWarning(normalized.warning);
    return normalized.policy;
  };

  const loadUploadPolicy = async (signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchMaterialUploadPolicy(signal);
      return applyUploadPolicy(payload);
    } catch (policyError) {
      if (signal?.aborted) {
        return null;
      }

      const fallbackPolicy = buildFallbackMaterialUploadPolicy();
      setUploadPolicy(fallbackPolicy);
      setPolicyWarning(MATERIAL_POLICY_WARNING_MESSAGE);
      return fallbackPolicy;
    }
  };

  const ensureUploadPolicy = async () => {
    if (uploadPolicy) {
      return uploadPolicy;
    }

    try {
      const payload = await apiClient.fetchMaterialUploadPolicy();
      return applyUploadPolicy(payload);
    } catch (error) {
      const fallbackPolicy = buildFallbackMaterialUploadPolicy();
      setUploadPolicy(fallbackPolicy);
      setPolicyWarning(MATERIAL_POLICY_WARNING_MESSAGE);
      return fallbackPolicy;
    }
  };

  useEffect(() => {
    const controller = new AbortController();
    void loadUploadPolicy(controller.signal);
    return () => controller.abort();
  }, []);

  return {
    uploadPolicy,
    policyWarning,
    loadUploadPolicy,
    ensureUploadPolicy,
  };
};
