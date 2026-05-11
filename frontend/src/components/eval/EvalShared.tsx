import type { ReactNode } from "react";
import { Label } from "@/components/ui/label";

export function MetricCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-[20px] border border-field-border bg-field px-4 py-3">
      <p className="text-xs uppercase text-muted-foreground">{label}</p>
      <strong className="mt-1 block text-lg font-semibold text-foreground">{value}</strong>
    </div>
  );
}

export function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <Label className="space-y-2">
      <span className="block">{label}</span>
      {children}
    </Label>
  );
}

export function JsonBlock({ value }: { value: unknown }) {
  return (
    <pre className="max-h-80 overflow-auto rounded-[18px] border border-border bg-background px-4 py-3 text-xs leading-5 text-foreground">
      {JSON.stringify(value ?? {}, null, 2)}
    </pre>
  );
}
