import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import type { AuthSession } from "@/types";
import { kegocLogo, type WorkspaceTab } from "./workbenchConfig";
import { WorkbenchNavigation } from "./WorkbenchNavigation";

type WorkbenchSidebarProps = {
  activeTab: WorkspaceTab;
  hiddenTabs?: WorkspaceTab[];
  session: AuthSession;
  isLoggingOut: boolean;
  onLogout: () => Promise<void>;
  onSelectTab: (tab: WorkspaceTab) => void;
};

export function WorkbenchSidebar({
  activeTab,
  hiddenTabs = [],
  session,
  isLoggingOut,
  onLogout,
  onSelectTab,
}: WorkbenchSidebarProps) {
  return (
    <aside className="surface-sidebar sticky top-4 hidden h-[calc(100vh-2rem)] min-h-0 w-[300px] shrink-0 rounded-[34px] px-5 py-5 lg:flex lg:flex-col">
      <div className="shrink-0 space-y-5">
        <div className="flex items-center gap-3">
          <div className="flex h-14 w-14 items-center justify-center rounded-[20px] bg-white/10">
            <img alt="Логотип KEGOC" className="h-10 w-10 object-contain" src={kegocLogo} />
          </div>
          <div className="space-y-1">
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-sidebar-foreground/60">
              ПАНЕЛЬ УПРАВЛЕНИЯ
            </p>
            <h1 className="text-xl font-semibold tracking-[-0.04em]">KEGOC RAG</h1>
          </div>
        </div>
      </div>

      <Separator className="my-4 shrink-0 bg-white/10" />
      <WorkbenchNavigation
        activeTab={activeTab}
        className="min-h-0 flex-1 overflow-y-auto pr-1"
        hiddenTabs={hiddenTabs}
        onSelectTab={onSelectTab}
      />
      <Separator className="my-4 shrink-0 bg-white/10" />

      <div className="mb-1 shrink-0 rounded-[22px] border border-white/8 bg-white/6 px-4 py-3">
        <p className="truncate text-sm font-semibold text-sidebar-foreground">
          {session.username ?? "admin"}
        </p>
        <Button
          className="mt-3 w-full justify-center"
          disabled={isLoggingOut}
          size="sm"
          type="button"
          variant="secondary"
          onClick={() => {
            void onLogout();
          }}
        >
          <LogOut className="h-4 w-4" />
          Выйти
        </Button>
      </div>
    </aside>
  );
}
