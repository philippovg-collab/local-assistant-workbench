import {
  Database,
  Brain,
  ClipboardCheck,
  Files,
  LayoutDashboard,
  MessageCircleCode,
  NotebookPen,
  Radar,
  Settings,
  type LucideIcon,
} from "lucide-react";
import kegocLogoAsset from "@/assets/logo-kegoc.png";

export const kegocLogo = kegocLogoAsset;
export const ACTIVE_RAG_PROJECT_STORAGE_KEY = "kegoc.activeRagProjectKey";

export type WorkspaceTab = "overview" | "materials" | "instructions" | "references" | "memory" | "rag" | "direct" | "eval" | "settings";

export type WorkspaceTabConfig = {
  id: WorkspaceTab;
  label: string;
  description: string;
  icon: LucideIcon;
};

export const workspaceTabs: WorkspaceTabConfig[] = [
  {
    id: "overview",
    label: "Дашборд",
    description: "Общий health, readiness и production signals",
    icon: LayoutDashboard,
  },
  {
    id: "materials",
    label: "Материалы",
    description: "Управление knowledge base и lineage",
    icon: Files,
  },
  {
    id: "instructions",
    label: "Инструкции",
    description: "Instruction stack по уровням",
    icon: NotebookPen,
  },
  {
    id: "rag",
    label: "RAG Studio",
    description: "Контекстные ответы по локальным материалам",
    icon: Radar,
  },
  {
    id: "direct",
    label: "Direct Studio",
    description: "Прямые запросы к модели без retrieval",
    icon: MessageCircleCode,
  },
  {
    id: "references",
    label: "Справочники",
    description: "Пресеты корпуса, рабочие области и проекты",
    icon: Database,
  },
  {
    id: "memory",
    label: "Память",
    description: "Review queue и pinned continuity",
    icon: Brain,
  },
  {
    id: "eval",
    label: "Eval",
    description: "Регрессии, golden sets и сравнение запусков",
    icon: ClipboardCheck,
  },
  {
    id: "settings",
    label: "Настройки",
    description: "LLM подключения и runtime provider",
    icon: Settings,
  },
];

export const getFallbackRagProjectName = (projectKey: string) =>
  projectKey === "general" ? "Общая" : projectKey;
