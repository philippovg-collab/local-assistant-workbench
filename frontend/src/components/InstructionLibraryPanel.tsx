import { useState, type FormEvent } from "react";
import type { Instruction } from "../types";
import { formatDate } from "../utils/format";

type InstructionLibraryPanelProps = {
  instructions: Instruction[];
  isLoading: boolean;
  error: string | null;
  message: string | null;
  actionError: string | null;
  deletingInstructionId: string | null;
  onCreateInstruction: (input: { title: string; category: string; content: string }) => Promise<unknown>;
  onDeleteInstruction: (instructionId: string) => Promise<unknown>;
};

type InstructionFormState = {
  title: string;
  category: string;
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
  isLoading,
  error,
  message,
  actionError,
  deletingInstructionId,
  onCreateInstruction,
  onDeleteInstruction,
}: InstructionLibraryPanelProps) {
  const [form, setForm] = useState<InstructionFormState>(initialInstructionForm);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);
    try {
      await onCreateInstruction(form);
      setForm(initialInstructionForm);
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
  };

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
              onChange={(event) => setForm((current) => ({ ...current, category: event.target.value }))}
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

          <button className="primary-button" disabled={isSubmitting} type="submit">
            {isSubmitting ? "Сохраняем..." : "Сохранить инструкцию"}
          </button>
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
                      {categoryLabels[instruction.category] ?? instruction.category} · {formatDate(instruction.createdAt)}
                    </p>
                  </div>
                  <button
                    className="danger-button"
                    disabled={deletingInstructionId === instruction.id}
                    type="button"
                    onClick={() => void handleDelete(instruction.id)}
                  >
                    {deletingInstructionId === instruction.id ? "Удаляем..." : "Удалить"}
                  </button>
                </div>
                <p>{instruction.content}</p>
              </article>
            ))}
          </div>
        )}
      </aside>
    </section>
  );
}
