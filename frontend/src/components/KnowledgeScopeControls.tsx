import type { ChatFormProps } from "@/components/ChatForm";
import type { KnowledgePresetSummary, KnowledgeScope } from "@/types";

export type KnowledgeScopeControlConfig = NonNullable<ChatFormProps["knowledgeControls"]>;

export const buildKnowledgeScopeControls = ({
  knowledgeScope,
  onKnowledgeScopeChange,
  visibleKnowledgePresets,
  visibleKnowledgeFacets,
  workspaceKey,
  workspaceName,
  activeScopeSummary,
}: {
  knowledgeScope: KnowledgeScope;
  onKnowledgeScopeChange: (nextScope: KnowledgeScope) => void;
  visibleKnowledgePresets: KnowledgePresetSummary[];
  visibleKnowledgeFacets: KnowledgePresetSummary[];
  workspaceKey: string;
  workspaceName: string;
  activeScopeSummary: string;
}): KnowledgeScopeControlConfig => ({
  presets: visibleKnowledgePresets,
  facets: visibleKnowledgeFacets,
  selectedPresetIds: knowledgeScope.presetIds,
  onTogglePreset: (presetId: string) =>
    onKnowledgeScopeChange({
      ...knowledgeScope,
      presetIds: knowledgeScope.presetIds.includes(presetId)
        ? knowledgeScope.presetIds.filter((id) => id !== presetId)
        : [...knowledgeScope.presetIds, presetId],
    }),
  selectedFacetIds: knowledgeScope.facetIds ?? [],
  onToggleFacet: (facetId: string) =>
    onKnowledgeScopeChange({
      ...knowledgeScope,
      facetIds: (knowledgeScope.facetIds ?? []).includes(facetId)
        ? (knowledgeScope.facetIds ?? []).filter((id) => id !== facetId)
        : [...(knowledgeScope.facetIds ?? []), facetId],
    }),
  workspaceKey,
  workspaceName,
  activeScopeSummary,
});
