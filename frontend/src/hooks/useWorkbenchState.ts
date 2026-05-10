import { useState } from "react";
import {
  ACTIVE_RAG_PROJECT_STORAGE_KEY,
  type WorkspaceTab,
} from "@/components/workbench/workbenchConfig";

export const useWorkbenchState = () => {
  const [activeTab, setActiveTab] = useState<WorkspaceTab>("overview");
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [ragInstructionIds, setRagInstructionIds] = useState<string[]>([]);
  const [directInstructionIds, setDirectInstructionIds] = useState<string[]>([]);
  const [activeRagProjectKey, setActiveRagProjectKey] = useState(() => {
    if (typeof window === "undefined") {
      return "general";
    }
    return window.localStorage.getItem(ACTIVE_RAG_PROJECT_STORAGE_KEY) || "general";
  });

  return {
    activeTab,
    setActiveTab,
    mobileNavOpen,
    setMobileNavOpen,
    ragInstructionIds,
    setRagInstructionIds,
    directInstructionIds,
    setDirectInstructionIds,
    activeRagProjectKey,
    setActiveRagProjectKey,
  };
};
