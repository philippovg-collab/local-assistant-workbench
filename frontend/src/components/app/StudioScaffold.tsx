import type { ReactNode } from "react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { SectionIntro } from "@/components/app/SectionIntro";
import { cn } from "@/lib/utils";

type StudioScaffoldProps = {
  eyebrow: string;
  title: string;
  description: string;
  badge: string;
  controls: ReactNode;
  results: ReactNode;
  layout?: "split" | "stacked";
};

export function StudioScaffold({
  eyebrow,
  title,
  description,
  badge,
  controls,
  results,
  layout = "split",
}: StudioScaffoldProps) {
  return (
    <div
      className={cn(
        "grid gap-6",
        layout === "split" && "xl:grid-cols-[minmax(0,1.05fr)_minmax(340px,0.95fr)]",
      )}
    >
      <Card className="overflow-hidden">
        <CardHeader>
          <SectionIntro
            badge={badge}
            badgeVariant="default"
            description={description}
            eyebrow={eyebrow}
            title={title}
          />
        </CardHeader>
        <CardContent>{controls}</CardContent>
      </Card>

      <Card className="overflow-hidden">
        <CardContent className="mt-0 h-full">{results}</CardContent>
      </Card>
    </div>
  );
}
