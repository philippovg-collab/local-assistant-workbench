import { SectionIntro } from "@/components/app/SectionIntro";
import { MemoryReviewPanel } from "@/components/MemoryReviewPanel";

type MemoryTabProps = {
  activeRagProjectKey: string;
  activeRagProjectName: string;
  enabled?: boolean;
};

export function MemoryTab({ activeRagProjectKey, activeRagProjectName, enabled = true }: MemoryTabProps) {
  return (
    <div className="space-y-5">
      <SectionIntro
        badge="Review mode"
        badgeVariant="secondary"
        description="Pending candidates, approved continuity notes, pinning and deletion stay separate from KB materials."
        eyebrow="Long-term Memory"
        title="Память на ревью"
      />
      <MemoryReviewPanel enabled={enabled} workspaceKey={activeRagProjectKey} workspaceName={activeRagProjectName} />
    </div>
  );
}
