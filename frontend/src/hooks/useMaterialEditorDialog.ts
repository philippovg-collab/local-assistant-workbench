import { useEffect, useMemo, useState } from "react";
import { apiClient } from "@/api/client";
import type { MaterialDetail, MaterialSummary } from "@/types";
import {
  emptyMaterialMetadataFormState,
  toMaterialMetadataFormState,
  type MaterialMetadataFormState,
  type MaterialMetadataValidation,
} from "@/utils/materialMetadata";
import { translateMaterialError } from "@/utils/materialPresentation";

export const useMaterialEditorDialog = (
  material: MaterialSummary | null,
  activeWorkspaceKey?: string | null,
) => {
  const [detail, setDetail] = useState<MaterialDetail | null>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [metadata, setMetadata] = useState<MaterialMetadataFormState>(emptyMaterialMetadataFormState);
  const [metadataValidation, setMetadataValidation] = useState<MaterialMetadataValidation | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const normalizedActiveWorkspaceKey = useMemo(() => (activeWorkspaceKey ?? "").trim(), [activeWorkspaceKey]);

  const withActiveWorkspace = (current: MaterialMetadataFormState) => ({
    ...current,
    workspaceKey: normalizedActiveWorkspaceKey,
    projectKey: "",
  });

  useEffect(() => {
    if (!material) {
      setDetail(null);
      setTitle("");
      setContent("");
      setMetadata(emptyMaterialMetadataFormState());
      setMetadataValidation(null);
      setLoadError(null);
      setSubmitError(null);
      setIsLoadingDetail(false);
      return;
    }

    const controller = new AbortController();
    setIsLoadingDetail(true);
    setLoadError(null);
    setSubmitError(null);

    apiClient.fetchMaterial(material.id, { workspaceKey: normalizedActiveWorkspaceKey }, controller.signal)
      .then((loadedDetail) => {
        setDetail(loadedDetail);
        setTitle(loadedDetail.title);
        setContent(loadedDetail.content);
        setMetadata(withActiveWorkspace(toMaterialMetadataFormState(loadedDetail.metadata)));
        setMetadataValidation(null);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setLoadError(translateMaterialError(error, "Не удалось открыть материал для редактирования", null));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoadingDetail(false);
        }
      });

    return () => controller.abort();
  }, [material, normalizedActiveWorkspaceKey]);

  return {
    detail,
    title,
    setTitle,
    content,
    setContent,
    metadata,
    setMetadata,
    metadataValidation,
    setMetadataValidation,
    loadError,
    submitError,
    setSubmitError,
    isLoadingDetail,
    withActiveWorkspace,
  };
};

export type MaterialEditorDialogState = ReturnType<typeof useMaterialEditorDialog>;
