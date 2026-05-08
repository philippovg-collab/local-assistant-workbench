import type { RefObject } from "react";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Separator } from "@/components/ui/separator";
import type { MaterialDetail } from "@/types";
import type { SourceTarget } from "@/components/ragChatPresentation";

type RagSourceDialogProps = {
  openedMaterial: MaterialDetail | null;
  isLoadingMaterial: boolean;
  materialError: string | null;
  openedSourceTarget: SourceTarget | null;
  highlightedChunkRef: RefObject<HTMLDivElement>;
  onClose: () => void;
};

export function RagSourceDialog({
  openedMaterial,
  isLoadingMaterial,
  materialError,
  openedSourceTarget,
  highlightedChunkRef,
  onClose,
}: RagSourceDialogProps) {
  return (
    <Dialog
      open={openedMaterial !== null || isLoadingMaterial || materialError !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>{openedMaterial?.title ?? "Источник"}</DialogTitle>
          <DialogDescription>
            {isLoadingMaterial
              ? "Загружаем материал и его чанки."
              : materialError ?? "Показываем активную версию материала и сразу прокручиваем к выбранному chunk."}
          </DialogDescription>
        </DialogHeader>

        {openedMaterial ? (
          <div className="space-y-4">
            <div className="rounded-[22px] border border-field-border bg-field px-4 py-4 text-sm leading-6 text-foreground">
              <p>Файл: {openedMaterial.originalFileName ?? openedMaterial.sourceType}</p>
              <p>Статус: {openedMaterial.status}</p>
              <p>Chunks: {openedMaterial.chunks.length}</p>
              <p>
                Jump target: {openedSourceTarget?.chunkIndex ?? "n/a"}
                {openedSourceTarget?.page ? ` · page ${openedSourceTarget.page}` : ""}
              </p>
            </div>

            <article className="max-h-[52vh] space-y-3 overflow-y-auto rounded-[22px] border border-field-border bg-field px-4 py-4">
              <p className="text-sm leading-7 text-foreground">{openedMaterial.content}</p>
              <Separator />
              <div className="space-y-3">
                {openedMaterial.chunks.map((chunk) => {
                  const isHighlighted = (openedSourceTarget?.chunkId && chunk.chunkId === openedSourceTarget.chunkId)
                    || (openedSourceTarget?.chunkIndex !== null
                      && openedSourceTarget?.chunkIndex !== undefined
                      && chunk.chunkIndex === openedSourceTarget.chunkIndex);
                  return (
                    <div
                      className={isHighlighted
                        ? "rounded-[18px] border border-primary bg-primary/10 px-4 py-3 shadow-[0_0_0_1px_rgba(0,0,0,0.02)]"
                        : "rounded-[18px] border border-border bg-background px-4 py-3"}
                      key={chunk.chunkId}
                      ref={isHighlighted ? highlightedChunkRef : null}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">
                          chunk {chunk.chunkIndex}
                          {chunk.page ? ` · page ${chunk.page}` : ""}
                          {chunk.extractor ? ` · ${chunk.extractor}` : ""}
                        </p>
                        {isHighlighted ? <Badge variant="default">retrieval target</Badge> : null}
                      </div>
                      <p className="mt-2 text-sm leading-6 text-foreground">{chunk.text}</p>
                    </div>
                  );
                })}
              </div>
            </article>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
