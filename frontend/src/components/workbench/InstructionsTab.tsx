import { InstructionLibraryPanel } from "@/components/InstructionLibraryPanel";
import type { useInstructions } from "@/hooks/useInstructions";

type InstructionsTabProps = {
  activeRagProjectKey: string;
  activeRagProjectName: string;
  instructions: ReturnType<typeof useInstructions>;
};

export function InstructionsTab({
  activeRagProjectKey,
  activeRagProjectName,
  instructions,
}: InstructionsTabProps) {
  return (
    <InstructionLibraryPanel
      actionError={instructions.actionError}
      activeRagProjectKey={activeRagProjectKey}
      activeRagProjectName={activeRagProjectName}
      deletingInstructionId={instructions.deletingInstructionId}
      detailError={instructions.detailError}
      error={instructions.error}
      instructions={instructions.instructions}
      isLoading={instructions.isLoading}
      isLoadingDetail={instructions.isLoadingDetail}
      message={instructions.message}
      revisionDiff={instructions.revisionDiff}
      revisions={instructions.revisions}
      selectedInstruction={instructions.selectedInstruction}
      onCreateInstruction={instructions.createInstruction}
      onDeleteInstruction={instructions.deleteInstruction}
      onLoadInstruction={(instructionId) => instructions.loadInstruction(instructionId)}
      onLoadInstructionDiff={(instructionId, fromRevision, toRevision) =>
        instructions.loadInstructionDiff(instructionId, fromRevision, toRevision)}
      onLoadInstructionRevisions={(instructionId) => instructions.loadInstructionRevisions(instructionId)}
      onRestoreInstructionRevision={(instructionId, revision) =>
        instructions.restoreInstructionRevision(instructionId, revision)}
      onUpdateInstruction={instructions.updateInstruction}
    />
  );
}
