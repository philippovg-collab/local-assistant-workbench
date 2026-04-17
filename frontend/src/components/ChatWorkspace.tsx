import type { ReactNode } from "react";

type ChatWorkspaceProps = {
  eyebrow: string;
  title: string;
  badge: string;
  description: string;
  children: ReactNode;
};

export function ChatWorkspace({
  eyebrow,
  title,
  badge,
  description,
  children,
}: ChatWorkspaceProps) {
  return (
    <article className="panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
        <span className="badge">{badge}</span>
      </div>

      <p className="section-copy">{description}</p>
      {children}
    </article>
  );
}
