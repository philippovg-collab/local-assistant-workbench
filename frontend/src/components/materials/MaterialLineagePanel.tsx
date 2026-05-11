import { SectionIntro } from "@/components/app/SectionIntro";
import { MaterialMetadataDisplay } from "@/components/MaterialMetadataDisplay";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import type { MaterialLineageResponse, ReferenceProject, ReferenceWorkspace } from "@/types";
import {
  formatLineageMeta,
  lineageVersions,
  materialStatusLabel,
  materialStatusVariant,
  translateSupersedeReason,
  versionStateLabel,
} from "@/utils/materialPresentation";

type MaterialLineagePanelProps = {
  lineageError: string | null;
  referenceProjects?: ReferenceProject[];
  referenceWorkspaces?: ReferenceWorkspace[];
  selectedLineage: MaterialLineageResponse | null;
};

export function MaterialLineagePanel({
  lineageError,
  referenceProjects = [],
  referenceWorkspaces = [],
  selectedLineage,
}: MaterialLineagePanelProps) {
  return (
    <>
      {lineageError ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось загрузить lineage</AlertTitle>
          <AlertDescription>{lineageError}</AlertDescription>
        </Alert>
      ) : null}

      {selectedLineage ? (
        <div className="space-y-4">
          <SectionIntro
            description={`Активная версия: ${selectedLineage.activeMaterialId ?? "сейчас отсутствует"}`}
            eyebrow="Lineage"
            title="История версий"
          />

          {selectedLineage.lineageOverride ? (
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="default">override: {selectedLineage.lineageOverride.lineageKey}</Badge>
              <Badge variant="secondary">{selectedLineage.lineageOverride.active ? "active" : "inactive"}</Badge>
              {selectedLineage.lineageOverride.reason ? (
                <Badge variant="secondary">{selectedLineage.lineageOverride.reason}</Badge>
              ) : null}
            </div>
          ) : null}

          <div className="space-y-3">
            {lineageVersions(selectedLineage).map((version) => (
              <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={version.id}>
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                        {version.title}
                      </h3>
                      <Badge variant={materialStatusVariant[version.status]}>
                        {materialStatusLabel[version.status]}
                      </Badge>
                      <Badge variant="secondary">
                        {version.id === selectedLineage.requestedMaterialId
                          ? "Запрошена"
                          : versionStateLabel(version)}
                      </Badge>
                    </div>
                    <p className="text-sm leading-6 text-muted-foreground">{formatLineageMeta(version)}</p>
                  </div>
                </div>

                <Separator />
                <p className="text-sm leading-7 text-foreground">{version.preview}</p>

                <MaterialMetadataDisplay
                  metadata={version.metadata}
                  references={{ projects: referenceProjects, workspaces: referenceWorkspaces }}
                />

                {version.versionState === "SUPERSEDED" ? (
                  <Alert variant="warning">
                    <AlertTitle>Почему версия ушла в историю</AlertTitle>
                    <AlertDescription>{translateSupersedeReason(version.supersedeReason)}</AlertDescription>
                  </Alert>
                ) : null}

                {version.statusReasonMessage ? (
                  <Alert variant="default">
                    <AlertTitle>Причина статуса</AlertTitle>
                    <AlertDescription>{version.statusReasonMessage}</AlertDescription>
                  </Alert>
                ) : null}
              </article>
            ))}
          </div>
        </div>
      ) : null}
    </>
  );
}
