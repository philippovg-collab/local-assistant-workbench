import { cn } from "@/lib/utils";
import {
  workspaceTabs,
  type WorkspaceTab,
} from "./workbenchConfig";

type WorkbenchNavigationProps = {
  activeTab: WorkspaceTab;
  className?: string;
  hiddenTabs?: WorkspaceTab[];
  onSelectTab: (tab: WorkspaceTab) => void;
};

export function WorkbenchNavigation({
  activeTab,
  className,
  hiddenTabs = [],
  onSelectTab,
}: WorkbenchNavigationProps) {
  const visibleTabs = workspaceTabs.filter((tab) => !hiddenTabs.includes(tab.id));

  return (
    <nav aria-label="Workspace navigation" className={cn("space-y-2", className)}>
      {visibleTabs.map((tab) => {
        const isActive = activeTab === tab.id;
        const Icon = tab.icon;

        return (
          <button
            aria-controls={`panel-${tab.id}`}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "flex w-full items-center gap-3 rounded-[24px] px-4 py-4 text-left transition",
              isActive
                ? "bg-sidebar-accent text-sidebar-accent-foreground shadow-soft"
                : "text-sidebar-foreground/78 hover:bg-white/8 hover:text-sidebar-foreground",
            )}
            id={`nav-${tab.id}`}
            key={tab.id}
            type="button"
            onClick={() => onSelectTab(tab.id)}
          >
            <span
              className={cn(
                "flex h-11 w-11 items-center justify-center rounded-2xl border",
                isActive
                  ? "border-white/12 bg-white/12"
                  : "border-white/8 bg-transparent",
              )}
            >
              <Icon className="h-5 w-5" />
            </span>
            <span className="min-w-0">
              <span className="block text-sm font-semibold">{tab.label}</span>
              <span className="block text-xs leading-5 text-sidebar-foreground/60">
                {tab.description}
              </span>
            </span>
          </button>
        );
      })}
    </nav>
  );
}
