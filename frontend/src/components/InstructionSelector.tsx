import type { InstructionSummary } from "../types";

type InstructionSelectorProps = {
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  label: string;
  emptyStateMessage: string;
};

export function InstructionSelector({
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  label,
  emptyStateMessage,
}: InstructionSelectorProps) {
  const selectedById = new Map(
    selectedInstructionIds.map((instructionId, index) => [instructionId, index + 1]),
  );
  const orderedSelectedInstructions = selectedInstructionIds
    .map((instructionId) => instructions.find((instruction) => instruction.id === instructionId))
    .filter((instruction): instruction is InstructionSummary => instruction !== undefined);

  return (
    <fieldset className="field instruction-selector">
      <legend>{label}</legend>

      {instructions.length === 0 ? (
        <div className="empty-state compact-empty">
          <strong>Инструкции пока недоступны</strong>
          <span>{emptyStateMessage}</span>
        </div>
      ) : (
        <>
          <div className="selection-list">
            {instructions.map((instruction) => {
              const selectedOrder = selectedById.get(instruction.id);

              return (
                <label
                  className={`selection-card ${selectedOrder ? "selected" : ""}`}
                  key={instruction.id}
                >
                  <div className="selection-card-header">
                    <input
                      aria-label={`Выбрать инструкцию ${instruction.title}`}
                      checked={selectedOrder !== undefined}
                      type="checkbox"
                      onChange={() => onToggleInstruction(instruction.id)}
                    />

                    <div className="selection-card-heading">
                      <div className="item-row">
                        <strong>{instruction.title}</strong>
                        {selectedOrder ? <span className="badge subtle">#{selectedOrder}</span> : null}
                      </div>
                      <p className="item-meta">{instruction.category}</p>
                    </div>
                  </div>

                  <p className="selection-card-copy">{instruction.preview}</p>
                </label>
              );
            })}
          </div>

          <div className="instruction-order-summary">
            {orderedSelectedInstructions.length === 0 ? (
              <p className="helper">
                Если ничего не выбрать, backend выполнит запрос только с базовым system prompt.
              </p>
            ) : (
              <>
                <p className="helper">Backend применит выбранные инструкции в этом порядке:</p>
                <ol className="instruction-order-list">
                  {orderedSelectedInstructions.map((instruction) => (
                    <li className="instruction-order-item" key={instruction.id}>
                      <strong>{instruction.title}</strong>
                      <span>{instruction.category}</span>
                    </li>
                  ))}
                </ol>
              </>
            )}
          </div>
        </>
      )}
    </fieldset>
  );
}
