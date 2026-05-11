import { LogOut, Menu } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import type { HealthResponse, RagProjectSummary } from "@/types";
import { cn } from "@/lib/utils";
import type { RagReadinessPresentation } from "@/utils/readiness";
import type {
  WorkspaceTab,
  WorkspaceTabConfig,
} from "./workbenchConfig";
import { OverviewHero } from "./OverviewTab";
import { WorkbenchNavigation } from "./WorkbenchNavigation";

type WorkbenchMobileHeaderProps = {
  activeTab: WorkspaceTab;
  activeWorkspace: WorkspaceTabConfig;
  activeRagProject: RagProjectSummary | null;
  activeRagProjectKey: string;
  activeRagProjectName: string;
  backendStatus: string;
  health: HealthResponse | null;
  hiddenTabs?: WorkspaceTab[];
  isLoggingOut: boolean;
  mobileNavOpen: boolean;
  ragPresentation: RagReadinessPresentation;
  onLogout: () => Promise<void>;
  onMobileNavOpenChange: (open: boolean) => void;
  onSelectTab: (tab: WorkspaceTab) => void;
};

export function WorkbenchMobileHeader({
  activeTab,
  activeWorkspace,
  activeRagProject,
  activeRagProjectKey,
  activeRagProjectName,
  backendStatus,
  health,
  hiddenTabs = [],
  isLoggingOut,
  mobileNavOpen,
  ragPresentation,
  onLogout,
  onMobileNavOpenChange,
  onSelectTab,
}: WorkbenchMobileHeaderProps) {
  return (
    <header
      className={cn(
        "surface-panel mb-6 rounded-[34px] px-5 py-5 sm:px-6 lg:px-7",
        activeTab !== "overview" && "lg:hidden",
      )}
    >
      <div
        className={cn(
          "flex items-center justify-between gap-3 lg:hidden",
          activeTab === "overview" && "mb-5",
        )}
      >
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-primary">
            Рабочая область
          </p>
          <strong className="text-lg font-semibold tracking-[-0.03em] text-foreground">
            {activeWorkspace.label}
          </strong>
        </div>

        <div className="flex items-center gap-2">
          <Button
            disabled={isLoggingOut}
            size="icon"
            type="button"
            variant="secondary"
            onClick={() => {
              void onLogout();
            }}
          >
            <LogOut className="h-5 w-5" />
            <span className="sr-only">Выйти</span>
          </Button>
          <Sheet open={mobileNavOpen} onOpenChange={onMobileNavOpenChange}>
            <SheetTrigger asChild>
              <Button size="icon" variant="secondary">
                <Menu className="h-5 w-5" />
                <span className="sr-only">Открыть навигацию</span>
              </Button>
            </SheetTrigger>
            <SheetContent className="flex flex-col" side="left">
              <SheetHeader className="mb-6 shrink-0">
                <SheetTitle>KEGOC RAG</SheetTitle>
                <SheetDescription>
                  Навигация по основным разделам фронтенда.
                </SheetDescription>
              </SheetHeader>
              <WorkbenchNavigation
                activeTab={activeTab}
                className="min-h-0 flex-1 overflow-y-auto pr-1"
                hiddenTabs={hiddenTabs}
                onSelectTab={onSelectTab}
              />
            </SheetContent>
          </Sheet>
        </div>
      </div>

      {activeTab === "overview" && (
        <OverviewHero
          activeRagProject={activeRagProject}
          activeRagProjectKey={activeRagProjectKey}
          activeRagProjectName={activeRagProjectName}
          activeWorkspace={activeWorkspace}
          backendStatus={backendStatus}
          health={health}
          ragPresentation={ragPresentation}
        />
      )}
    </header>
  );
}
