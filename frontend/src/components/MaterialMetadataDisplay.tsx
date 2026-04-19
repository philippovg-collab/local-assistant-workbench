import { Badge } from "@/components/ui/badge";
import type { MaterialMetadata } from "@/types";
import {
  buildMaterialMetadataEntries,
  provenanceLabel,
} from "@/utils/materialMetadata";

type MaterialMetadataDisplayProps = {
  metadata?: MaterialMetadata | null;
};

const badgeVariantForOrigin = (origin: "MANUAL" | "INFERRED" | "DEFAULT") => {
  switch (origin) {
    case "MANUAL":
      return "outline";
    case "INFERRED":
      return "warning";
    default:
      return "secondary";
  }
};

export function MaterialMetadataDisplay({ metadata }: MaterialMetadataDisplayProps) {
  const entries = buildMaterialMetadataEntries(metadata);

  if (entries.length === 0) {
    return null;
  }

  return (
    <div className="space-y-2">
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
        Metadata
      </p>
      <div className="flex flex-wrap gap-2">
        {entries.map((entry) => (
          <div
            className="inline-flex items-center gap-2 rounded-2xl border border-border bg-background px-3 py-2 text-sm text-foreground"
            key={entry.key}
          >
            <span>
              <span className="text-muted-foreground">{entry.label}:</span> {entry.value}
            </span>
            <Badge variant={badgeVariantForOrigin(entry.origin)}>
              {provenanceLabel(entry.origin)}
            </Badge>
          </div>
        ))}
      </div>
    </div>
  );
}
