import type { ReactNode } from "react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Card, CardHeader } from "@/components/ui/card";
import type { BadgeProps } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

type PageHeaderPanelProps = {
  eyebrow: string;
  title: string;
  description?: string;
  badge?: string;
  badgeVariant?: BadgeProps["variant"];
  actions?: ReactNode;
  className?: string;
};

export function PageHeaderPanel({
  eyebrow,
  title,
  description,
  badge,
  badgeVariant = "secondary",
  actions,
  className,
}: PageHeaderPanelProps) {
  return (
    <Card className={cn("overflow-hidden", className)}>
      <CardHeader>
        <SectionIntro
          actions={actions}
          badge={badge}
          badgeVariant={badgeVariant}
          description={description}
          eyebrow={eyebrow}
          title={title}
        />
      </CardHeader>
    </Card>
  );
}
