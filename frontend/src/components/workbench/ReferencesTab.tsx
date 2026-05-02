import { ReferenceDataPanel } from "@/components/ReferenceDataPanel";
import type { useKnowledgeFacets } from "@/hooks/useKnowledgeFacets";
import type { useKnowledgePresets } from "@/hooks/useKnowledgePresets";
import type { useRagProjects } from "@/hooks/useRagProjects";
import type { useReferenceData } from "@/hooks/useReferenceData";

type ReferencesTabProps = {
  activeRagProjectKey: string;
  knowledgeFacets: ReturnType<typeof useKnowledgeFacets>;
  knowledgePresets: ReturnType<typeof useKnowledgePresets>;
  ragProjects: ReturnType<typeof useRagProjects>;
  referenceData: ReturnType<typeof useReferenceData>;
  showRagProjectSwitcher: boolean;
  onActiveRagProjectChange: (projectKey: string) => void;
};

export function ReferencesTab({
  activeRagProjectKey,
  knowledgeFacets,
  knowledgePresets,
  ragProjects,
  referenceData,
  showRagProjectSwitcher,
  onActiveRagProjectChange,
}: ReferencesTabProps) {
  return (
    <ReferenceDataPanel
      activeRagProjectKey={activeRagProjectKey}
      knowledgeFacets={{
        actionError: knowledgeFacets.actionError,
        error: knowledgeFacets.error,
        isLoading: knowledgeFacets.isLoading,
        message: knowledgeFacets.message,
        onCreatePreset: knowledgeFacets.createPreset,
        onDeletePreset: knowledgeFacets.deletePreset,
        onLoadPreset: (facetId) => knowledgeFacets.loadPreset(facetId),
        onLoadRevisionDiff: (facetId, fromRevision, toRevision) =>
          knowledgeFacets.loadRevisionDiff(facetId, fromRevision, toRevision),
        onLoadRevisions: (facetId) => knowledgeFacets.loadRevisions(facetId),
        onRestoreRevision: (facetId, revision) => knowledgeFacets.restoreRevision(facetId, revision),
        onUpdatePreset: knowledgeFacets.updatePreset,
        presets: knowledgeFacets.presets,
        revisionDiff: knowledgeFacets.revisionDiff,
        revisions: knowledgeFacets.revisions,
        selectedPreset: knowledgeFacets.selectedPreset,
      }}
      knowledgePresets={{
        actionError: knowledgePresets.actionError,
        error: knowledgePresets.error,
        isLoading: knowledgePresets.isLoading,
        message: knowledgePresets.message,
        onCreatePreset: knowledgePresets.createPreset,
        onDeletePreset: knowledgePresets.deletePreset,
        onLoadPreset: (presetId) => knowledgePresets.loadPreset(presetId),
        onLoadRevisionDiff: (presetId, fromRevision, toRevision) =>
          knowledgePresets.loadRevisionDiff(presetId, fromRevision, toRevision),
        onLoadRevisions: (presetId) => knowledgePresets.loadRevisions(presetId),
        onRestoreRevision: (presetId, revision) => knowledgePresets.restoreRevision(presetId, revision),
        onUpdatePreset: knowledgePresets.updatePreset,
        presets: knowledgePresets.presets,
        revisionDiff: knowledgePresets.revisionDiff,
        revisions: knowledgePresets.revisions,
        selectedPreset: knowledgePresets.selectedPreset,
      }}
      ragProjects={ragProjects}
      referenceData={referenceData}
      showRagProjectSwitcher={showRagProjectSwitcher}
      onActiveRagProjectChange={onActiveRagProjectChange}
    />
  );
}
