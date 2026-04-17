import { useState, type FormEvent } from "react";
import type {
  CreateInstructionRequest,
  InstructionCategory,
  InstructionDetail,
  InstructionSummary,
} from "../types";
import { formatDate } from "../utils/format";

type InstructionLibraryPanelProps = {
  instructions: InstructionSummary[];
  selectedInstruction: InstructionDetail | null;
  detailError: string | null;
  isLoadingDetail: boolean;
  isLoading: boolean;
  error: string | null;
  message: string | null;
  actionError: string | null;
  deletingInstructionId: string | null;
  onCreateInstruction: (input: CreateInstructionRequest) => Promise<unknown>;
  onUpdateInstruction: (instructionId: string, input: CreateInstructionRequest) => Promise<unknown>;
  onDeleteInstruction: (instructionId: string) => Promise<unknown>;
  onLoadInstruction: (instructionId: string) => Promise<InstructionDetail | null>;
};

type InstructionFormState = {
  title: string;
  category: InstructionCategory;
  content: string;
};

const initialInstructionForm: InstructionFormState = {
  title: "",
  category: "system",
  content: "",
};

const categoryLabels: Record<string, string> = {
  system: "System",
  user: "User",
  context: "Context",
  safety: "Safety",
};

export function InstructionLibraryPanel({
  instructions,
  selectedInstruction,
  detailError,
  isLoadingDetail,
  isLoading,
  error,
  message,
  actionError,
  deletingInstructionId,
  onCreateInstruction,
  onUpdateInstruction,
  onDeleteInstruction,
  onLoadInstruction,
}: InstructionLibraryPanelProps) {
  const [form, setForm] = useState<InstructionFormState>(initialInstructionForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [editingInstructionId, setEditingInstructionId] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);
    try {
      if (editingInstructionId) {
        await onUpdateInstruction(editingInstructionId, form);
      } else {
        await onCreateInstruction(form);
      }
      setForm(initialInstructionForm);
      setEditingInstructionId(null);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (instructionId: string) => {
    const instruction = instructions.find((item) => item.id === instructionId);
    const confirmed = window.confirm(`Удалить инструкцию "${instruction?.title ?? instructionId}"?`);
    if (!confirmed) {
      return;
    }

    await onDeleteInstruction(instructionId);
    if (editingInstructionId === instructionId) {
      setEditingInstructionId(null);
      setForm(initialInstructionForm);
    }
  };

  const handleEdit = async (instructionId: string) => {
    const detail = selectedInstruction?.id === instructionId
      ? selectedInstruction
      : await onLoadInstruction(instructionId);

    if (!detail) {
      return;
    }

    setEditingInstructionId(instructionId);
    setForm({
      title: detail.title,
      category: detail.category,
      content: detail.content,
    });
  };

  const cancelEditing = () => {
    setEditingInstructionId(null);
    setForm(initialInstructionForm);
  };

  const formModeLabel = editingInstructionId ? "Редактирование" : "Создание";

  return (
    <section className="panel-stack">
      <article className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Prompt Snippets</p>
            <h2>Библиотека инструкций</h2>
          </div>
          <span className="badge">/api/instructions</span>
        </div>

        <p className="section-copy">
          Эти snippets больше не декоративные. Их можно явно выбрать в direct и RAG режимах, и backend включит их в prompt policy в заданном порядке.
        </p>

        <form className="form-card" onSubmit={handleSubmit}>
          <div className="panel-header compact">
            <h3>{editingInstructionId ? "Редактировать инструкцию" : "Добавить инструкцию"}</h3>
            <span className="badge subtle">{formModeLabel}</span>
          </div>

          <label className="field">
            <span>Название</span>
            <input
              placeholder="Например: Базовая роль ассистента"
              required
              value={form.title}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            />
          </label>

          <label className="field">
            <span>Тип инструкции</span>
            <select
              value={form.category}
              onChange={(event) =>
                setForm((current) => ({ ...current, category: event.target.value as InstructionCategory }))
              }
            >
              <option value="system">System</option>
              <option value="user">User</option>
              <option value="context">Context</option>
              <option value="safety">Safety</option>
            </select>
          </label>

          <label className="field">
            <span>Текст инструкции</span>
            <textarea
              placeholder="Опиши роль, ограничение или контекст."
              required
              rows={7}
              value={form.content}
              onChange={(event) => setForm((current) => ({ ...current, content: event.target.value }))}
            />
          </label>

          <div className="form-actions">
            <button className="primary-button" disabled={isSubmitting} type="submit">
              {isSubmitting
                ? editingInstructionId ? "Сохраняем..." : "Создаём..."
                : editingInstructionId ? "Сохранить изменения" : "Сохранить инструкцию"}
            </button>
            {editingInstructionId ? (
              <button className="secondary-button" type="button" onClick={cancelEditing}>
                Отменить редактирование
              </button>
            ) : null}
          </div>
        </form>

        {message ? <p className="inline-success">{message}</p> : null}
        {actionError ? <p className="inline-error">{actionError}</p> : null}
      </article>

      <aside className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Instruction Library</p>
            <h2>Сохранённые инструкции</h2>
          </div>
          <span className="badge">{instructions.length} saved</span>
        </div>

        {isLoading ? (
          <div className="empty-state">
            <strong>Загружаем библиотеку</strong>
            <span>Читаем инструкции из локального диска backend.</span>
          </div>
        ) : error ? (
          <div className="empty-state warning-state">
            <strong>Не удалось загрузить инструкции</strong>
            <span>{error}</span>
          </div>
        ) : instructions.length === 0 ? (
          <div className="empty-state">
            <strong>Пока пусто</strong>
            <span>Добавь первую инструкцию, чтобы использовать её в prompt policy.</span>
          </div>
        ) : (
          <div className="stack-list">
            {instructions.map((instruction) => (
              <article className="item-card" key={instruction.id}>
                <div className="item-row">
                  <div>
                    <h3>{instruction.title}</h3>
                    <p className="item-meta">
                      {categoryLabels[instruction.category] ?? instruction.category} · обновлена{" "}
                      {formatDate(instruction.updatedAt ?? instruction.createdAt)}
                    </p>
                  </div>
                  <div className="item-actions">
                    <button
                      className="secondary-button"
                      type="button"
                      onClick={() => void onLoadInstruction(instruction.id)}
                    >
                      {selectedInstruction?.id === instruction.id ? "Обновить текст" : "Открыть текст"}
                    </button>
                    <button
                      className="secondary-button"
                      type="button"
                      onClick={() => void handleEdit(instruction.id)}
                    >
                      Редактировать
                    </button>
                    <button
                      className="danger-button"
                      disabled={deletingInstructionId === instruction.id}
                      type="button"
                      onClick={() => void handleDelete(instruction.id)}
                    >
                      {deletingInstructionId === instruction.id ? "Удаляем..." : "Удалить"}
                    </button>
                  </div>
                </div>
                <p>{instruction.preview}</p>
              </article>
            ))}
          </div>
        )}

        {isLoadingDetail ? (
          <div className="empty-state compact-empty">
            <strong>Загружаем текст инструкции</strong>
            <span>Запрашиваем detail payload отдельно от list API.</span>
          </div>
        ) : detailError ? (
          <div className="empty-state warning-state">
            <strong>Не удалось открыть инструкцию</strong>
            <span>{detailError}</span>
          </div>
        ) : selectedInstruction ? (
          <article className="item-card">
            <div className="item-row">
              <div>
                <h3>{selectedInstruction.title}</h3>
                <p className="item-meta">
                  {categoryLabels[selectedInstruction.category] ?? selectedInstruction.category} · обновлена{" "}
                  {formatDate(selectedInstruction.updatedAt ?? selectedInstruction.createdAt)}
                </p>
              </div>
              <span className="badge subtle">detail</span>
            </div>
            <p>{selectedInstruction.content}</p>
          </article>
        ) : null}
      </aside>
    </section>
  );
}
