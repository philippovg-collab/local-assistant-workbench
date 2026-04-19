import type { LucideIcon } from "lucide-react";
import { AlertCircle, Info, TriangleAlert } from "lucide-react";
import { cn } from "@/lib/utils";

type EmptyStateTone = "default" | "warning" | "danger";

type EmptyStateProps = {
  title: string;
  description: string;
  icon?: LucideIcon;
  tone?: EmptyStateTone;
  className?: string;
};

const toneClasses: Record<EmptyStateTone, string> = {
  default: "surface-subtle text-foreground",
  warning: "border-warning/20 bg-warning/8 text-foreground",
  danger: "border-destructive/20 bg-destructive/8 text-foreground",
};

const defaultIcons: Record<EmptyStateTone, LucideIcon> = {
  default: Info,
  warning: TriangleAlert,
  danger: AlertCircle,
};

export function EmptyState({
  title,
  description,
  icon,
  tone = "default",
  className,
}: EmptyStateProps) {
  const Icon = icon ?? defaultIcons[tone];

  return (
    <div
      className={cn(
        "flex flex-col items-start gap-3 rounded-[24px] border px-5 py-5 shadow-soft",
        toneClasses[tone],
        className,
      )}
    >
      <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-field-border bg-field text-primary">
        <Icon className="h-5 w-5" />
      </div>
      <div className="space-y-1">
        <strong className="block text-sm font-semibold">{title}</strong>
        <p className="text-sm leading-6 text-muted-foreground">{description}</p>
      </div>
    </div>
  );
}
