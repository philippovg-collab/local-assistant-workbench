import type { ReactNode } from "react";
import { Badge, type BadgeProps } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

type SectionIntroProps = {
  eyebrow: string;
  title: string;
  description?: string;
  badge?: string;
  badgeVariant?: BadgeProps["variant"];
  actions?: ReactNode;
  className?: string;
};

export function SectionIntro({
  eyebrow,
  title,
  description,
  badge,
  badgeVariant = "secondary",
  actions,
  className,
}: SectionIntroProps) {
  return (
    <div className={cn("flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between", className)}>
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-primary">{eyebrow}</p>
        <div className="space-y-2">
          <h2 className="text-2xl font-semibold tracking-[-0.04em] text-foreground">{title}</h2>
          {description ? (
            <p className="max-w-3xl text-sm leading-6 text-muted-foreground">{description}</p>
          ) : null}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 self-start sm:justify-end">
        {badge ? <Badge variant={badgeVariant}>{badge}</Badge> : null}
        {actions}
      </div>
    </div>
  );
}
