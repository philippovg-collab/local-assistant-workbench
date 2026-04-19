import type { ReactNode } from "react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { SectionIntro } from "@/components/app/SectionIntro";

type StudioScaffoldProps = {
  eyebrow: string;
  title: string;
  description: string;
  badge: string;
  controls: ReactNode;
  results: ReactNode;
};

export function StudioScaffold({
  eyebrow,
  title,
  description,
  badge,
  controls,
  results,
}: StudioScaffoldProps) {
  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.05fr)_minmax(340px,0.95fr)]">
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
