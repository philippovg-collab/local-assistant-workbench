import type { AppliedInstruction } from "../types";

type AppliedInstructionListProps = {
  appliedInstructions: AppliedInstruction[];
};

export function AppliedInstructionList({ appliedInstructions }: AppliedInstructionListProps) {
  return (
    <article className="answer-card">
      <div className="item-row">
        <h3>Применённые инструкции</h3>
        <span className="badge subtle">{appliedInstructions.length} item(s)</span>
      </div>

      {appliedInstructions.length === 0 ? (
        <div className="empty-state compact-empty">
          <strong>Дополнительные инструкции не выбраны</strong>
          <span>Запрос был выполнен только с базовой prompt policy для выбранного режима.</span>
        </div>
      ) : (
        <ol className="instruction-order-list">
          {appliedInstructions.map((instruction) => (
            <li className="instruction-order-item" key={instruction.id}>
              <strong>{instruction.title}</strong>
              <span>{instruction.category}</span>
            </li>
          ))}
        </ol>
      )}
    </article>
  );
}
