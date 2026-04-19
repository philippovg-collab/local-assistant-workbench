import * as React from "react";
import { cn } from "@/lib/utils";

const Textarea = React.forwardRef<
  HTMLTextAreaElement,
  React.ComponentProps<"textarea">
>(({ className, ...props }, ref) => {
  return (
    <textarea
      className={cn(
        "flex min-h-[120px] w-full rounded-[22px] border border-field-border bg-field px-4 py-3 text-sm text-foreground shadow-[inset_0_1px_0_rgba(255,255,255,0.32)] transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-field-ring placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      ref={ref}
      {...props}
    />
  );
});
Textarea.displayName = "Textarea";

export { Textarea };
