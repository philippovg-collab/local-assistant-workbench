import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { useEvalRuns } from "@/hooks/useEvalRuns";
import type { CreateE2EEvalRunRequest, CreateRetrievalEvalRunRequest, EvalDatasetSummary } from "@/types";
import { evalJudgeModeValues } from "@/types";
import { Field } from "./EvalShared";
import { NONE_VALUE, shortId } from "./evalPresentation";

type StartEvalRunDialogProps = {
  datasets: EvalDatasetSummary[];
  isOpen: boolean;
  isSaving: boolean;
  onClose: () => void;
  onResolveConfig: ReturnType<typeof useEvalRuns>["resolveExecutionConfig"];
  onStartE2E: ReturnType<typeof useEvalRuns>["startE2ERun"];
  onStartRetrieval: ReturnType<typeof useEvalRuns>["startRetrievalRun"];
  resolvedConfigHash: string | null;
  selectedDatasetId: string | null;
  snapshots: ReturnType<typeof useEvalRuns>["snapshots"];
};

export function StartEvalRunDialog({
  datasets,
  isOpen,
  isSaving,
  onClose,
  onResolveConfig,
  onStartE2E,
  onStartRetrieval,
  resolvedConfigHash,
  selectedDatasetId,
  snapshots,
}: StartEvalRunDialogProps) {
  const [runType, setRunType] = useState<"RETRIEVAL" | "E2E">("RETRIEVAL");
  const [datasetId, setDatasetId] = useState(selectedDatasetId ?? "");
  const [datasetVersion, setDatasetVersion] = useState("");
  const [snapshotId, setSnapshotId] = useState("");
  const [executionConfigHash, setExecutionConfigHash] = useState("");
  const [referenceInstant, setReferenceInstant] = useState("");
  const [limit, setLimit] = useState("50");
  const [model, setModel] = useState("");
  const [judgeMode, setJudgeMode] = useState<string>(evalJudgeModeValues[0]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const defaultDatasetId = selectedDatasetId ?? datasets[0]?.id ?? "";
    setDatasetId(defaultDatasetId);
    setDatasetVersion(datasets.find((dataset) => dataset.id === defaultDatasetId)?.version ?? "");
    setSnapshotId(snapshots[0]?.id ?? "");
    setExecutionConfigHash(resolvedConfigHash ?? "");
  }, [datasets, isOpen, resolvedConfigHash, selectedDatasetId, snapshots]);

  useEffect(() => {
    if (resolvedConfigHash) {
      setExecutionConfigHash(resolvedConfigHash);
    }
  }, [resolvedConfigHash]);

  const resolve = async () => {
    const result = await onResolveConfig({
      corpusSnapshotId: snapshotId || null,
      datasetId: datasetId || null,
      datasetVersion: datasetVersion || null,
      referenceInstant: referenceInstant || null,
    });
    if (result?.configHash) {
      setExecutionConfigHash(result.configHash);
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const numericLimit = Number(limit);
    const base = {
      corpusSnapshotId: snapshotId || null,
      datasetId: datasetId || null,
      datasetVersion: datasetVersion || null,
      executionConfigHash: executionConfigHash || null,
      limit: Number.isFinite(numericLimit) ? numericLimit : null,
      referenceInstant: referenceInstant || null,
      tags: [],
    };
    const result = runType === "RETRIEVAL"
      ? await onStartRetrieval(base as CreateRetrievalEvalRunRequest)
      : await onStartE2E({
          ...base,
          judgeMode: judgeMode as CreateE2EEvalRunRequest["judgeMode"],
          model: model.trim() || null,
        } as CreateE2EEvalRunRequest);
    if (result) {
      onClose();
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open ? onClose() : undefined}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Запустить оценку</DialogTitle>
          <DialogDescription>Backend выполняет retrieval или E2E workflow и рассчитывает вердикты.</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={(event) => void submit(event)}>
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Тип запуска">
              <Select value={runType} onValueChange={(value) => setRunType(value as "RETRIEVAL" | "E2E")}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="RETRIEVAL">Retrieval</SelectItem>
                  <SelectItem value="E2E">E2E</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Набор">
              <Select value={datasetId || NONE_VALUE} onValueChange={(value) => setDatasetId(value === NONE_VALUE ? "" : value)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE_VALUE}>Набор не выбран</SelectItem>
                  {datasets.map((dataset) => (
                    <SelectItem key={dataset.id} value={dataset.id ?? NONE_VALUE}>{dataset.name ?? dataset.datasetKey}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Версия набора">
              <Input value={datasetVersion} onChange={(event) => setDatasetVersion(event.target.value)} />
            </Field>
            <Field label="Снимок корпуса">
              <Select value={snapshotId || NONE_VALUE} onValueChange={(value) => setSnapshotId(value === NONE_VALUE ? "" : value)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE_VALUE}>Снимок не выбран</SelectItem>
                  {snapshots.map((snapshot) => (
                    <SelectItem key={snapshot.id} value={snapshot.id ?? NONE_VALUE}>
                      {snapshot.snapshotKey ?? shortId(snapshot.id)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Хеш config">
              <Input value={executionConfigHash} onChange={(event) => setExecutionConfigHash(event.target.value)} />
            </Field>
            <Field label="Контрольный момент">
              <Input placeholder="2026-05-11T00:00:00Z" value={referenceInstant} onChange={(event) => setReferenceInstant(event.target.value)} />
            </Field>
            <Field label="Лимит">
              <Input min={1} type="number" value={limit} onChange={(event) => setLimit(event.target.value)} />
            </Field>
            {runType === "E2E" ? (
              <>
                <Field label="Модель">
                  <Input value={model} onChange={(event) => setModel(event.target.value)} />
                </Field>
                <Field label="Режим судьи">
                  <Select value={judgeMode} onValueChange={setJudgeMode}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {evalJudgeModeValues.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </Field>
              </>
            ) : null}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => void resolve()}>Рассчитать config</Button>
            <Button type="button" variant="outline" onClick={onClose}>Отмена</Button>
            <Button disabled={isSaving} type="submit">{isSaving ? "Запускаем..." : "Запустить"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
