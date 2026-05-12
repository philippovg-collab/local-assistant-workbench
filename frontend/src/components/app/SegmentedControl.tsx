import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type SegmentedControlItem<TValue extends string> = {
  value: TValue;
  label: string;
  icon?: LucideIcon;
};

type SegmentedControlProps<TValue extends string> = {
  items: SegmentedControlItem<TValue>[];
  value: TValue;
  onValueChange: (value: TValue) => void;
  ariaLabel: string;
  trailing?: ReactNode;
  className?: string;
};

export function SegmentedControl<TValue extends string>({
  items,
  value,
  onValueChange,
  ariaLabel,
  trailing,
  className,
}: SegmentedControlProps<TValue>) {
  return (
    <div
      aria-label={ariaLabel}
      className={cn("surface-subtle flex flex-wrap items-center gap-2 rounded-[24px] p-2", className)}
      role="toolbar"
    >
      {items.map((item) => {
        const Icon = item.icon;
        const active = item.value === value;

        return (
          <Button
            aria-pressed={active}
            key={item.value}
            size="sm"
            type="button"
            variant={active ? "default" : "ghost"}
            onClick={() => onValueChange(item.value)}
          >
            {Icon ? <Icon className="h-4 w-4" /> : null}
            {item.label}
          </Button>
        );
      })}
      {trailing ? <div className="ml-auto flex flex-wrap items-center gap-2">{trailing}</div> : null}
    </div>
  );
}
