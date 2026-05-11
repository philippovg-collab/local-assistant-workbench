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
    <nav aria-label="Навигация по рабочей области" className={cn("space-y-1.5", className)}>
      {visibleTabs.map((tab) => {
        const isActive = activeTab === tab.id;
        const Icon = tab.icon;

        return (
          <button
            aria-controls={`panel-${tab.id}`}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "flex w-full items-center gap-2.5 rounded-[18px] px-3 py-2.5 text-left transition",
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
                "flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border",
                isActive
                  ? "border-white/12 bg-white/12"
                  : "border-white/8 bg-transparent",
              )}
            >
              <Icon className="h-4 w-4" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-semibold leading-5">{tab.label}</span>
              <span className="line-clamp-2 text-[11px] leading-4 text-sidebar-foreground/60">
                {tab.description}
              </span>
            </span>
          </button>
        );
      })}
    </nav>
  );
}
