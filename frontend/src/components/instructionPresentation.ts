import type {
  CreateInstructionRequest,
  InstructionCategory,
  InstructionScopeLevel,
  InstructionSummary,
} from "@/types";

export type InstructionFormState = {
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel: InstructionScopeLevel;
  scopeTargetId: string;
  active: boolean;
};

export const initialInstructionForm: InstructionFormState = {
  title: "",
  category: "system",
  content: "",
  scopeLevel: "chat_scenario",
  scopeTargetId: "",
  active: true,
};

export const instructionCategoryOrder: InstructionCategory[] = ["system", "user", "context", "safety"];

export const scopeHelperText: Record<InstructionScopeLevel, string> = {
  assistant_system: "Глобальная роль ассистента. Подставляется автоматически во все запросы.",
  workspace_project: "Правила конкретного workspace или проекта. Подставляются автоматически по target.",
  chat_scenario: "Переиспользуемые сценарные инструкции, которые пользователь выбирает в конкретном чате.",
  request_temporary: "Справочная запись для одноразовой инструкции. Обычно не сохраняется, но можно зафиксировать шаблон.",
};

export const instructionDiffFieldLabels: Record<string, string> = {
  title: "Название",
  category: "Категория",
  content: "Текст",
  scopeLevel: "Уровень",
  scopeTargetId: "Scope target",
  active: "Активность",
};

export const categoryTooltipLines = [
  "Системная: системные правила, роль и базовое поведение модели.",
  "Безопасность: ограничения и запреты, которые добавляются в системную часть промпта.",
  "Контекстная: правила работы с контекстом и материалами, попадают перед контекстом или запросом.",
  "Пользовательская: сценарные пожелания к ответу, попадают перед пользовательским запросом.",
];

export const scopeTooltipLines = [
  "Системная роль ассистента: применяется автоматически во всех запросах.",
  "Рабочая область / проект: применяется автоматически при совпадении Scope target с ключом рабочей области запроса.",
  "Сценарий / чат: доступна для ручного выбора в RAG/Direct чате.",
  "Временная инструкция: одноразовое правило для текущего запроса.",
];

export const buildInstructionPayload = (
  form: InstructionFormState,
  normalizedActiveRagProjectKey: string,
): CreateInstructionRequest => ({
  title: form.title,
  category: form.category,
  content: form.content,
  scopeLevel: form.scopeLevel,
  scopeTargetId: form.scopeLevel === "workspace_project" && normalizedActiveRagProjectKey
    ? normalizedActiveRagProjectKey
    : form.scopeTargetId.trim() || null,
  active: form.active,
});

export const groupInstructions = (
  instructions: InstructionSummary[],
  activeRagProjectKey: string,
  activeRagProjectName: string,
) => [
  {
    id: "project",
    label: "Инструкции этого RAG-проекта",
    description: activeRagProjectKey
      ? `Автоматически применяются, когда активен проект ${activeRagProjectName || activeRagProjectKey}.`
      : "Автоматически применяются при совпадении workspaceKey запроса.",
    items: instructions.filter((instruction) =>
      (instruction.scopeLevel ?? "chat_scenario") === "workspace_project"
      && instruction.scopeTargetId === activeRagProjectKey),
  },
  {
    id: "scenario",
    label: "Сценарии",
    description: scopeHelperText.chat_scenario,
    items: instructions.filter((instruction) => (instruction.scopeLevel ?? "chat_scenario") === "chat_scenario"),
  },
  {
    id: "global",
    label: "Глобальные",
    description: "Системные и одноразовые шаблоны, не привязанные к конкретному RAG-проекту.",
    items: instructions.filter((instruction) =>
      ["assistant_system", "request_temporary"].includes(instruction.scopeLevel ?? "chat_scenario")),
  },
];
