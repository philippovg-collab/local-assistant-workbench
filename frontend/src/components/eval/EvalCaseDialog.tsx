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
import { Textarea } from "@/components/ui/textarea";
import type { CreateEvalCaseRequest, EvalCase } from "@/types";
import { evalCaseSeverityValues, evalCaseTypeValues, evalExpectedModeValues } from "@/types";
import { Field } from "./EvalShared";
import { expectedModeLabels, NONE_VALUE, parseJson, severityLabels, splitLines } from "./evalPresentation";

type EvalCaseDialogProps = {
  caseToEdit: EvalCase | null;
  datasetId: string | null;
  isOpen: boolean;
  isSaving: boolean;
  onClose: () => void;
  onCreateCase: (datasetId: string, input: CreateEvalCaseRequest) => Promise<EvalCase | null>;
  onUpdateCase: (caseId: string, input: CreateEvalCaseRequest) => Promise<EvalCase | null>;
};

export function EvalCaseDialog({
  caseToEdit,
  datasetId,
  isOpen,
  isSaving,
  onClose,
  onCreateCase,
  onUpdateCase,
}: EvalCaseDialogProps) {
  const [caseKey, setCaseKey] = useState("");
  const [caseType, setCaseType] = useState<string>(evalCaseTypeValues[0]);
  const [expectedMode, setExpectedMode] = useState<string>("ANSWER");
  const [severity, setSeverity] = useState<string>("MEDIUM");
  const [question, setQuestion] = useState("");
  const [tags, setTags] = useState("");
  const [goldFacts, setGoldFacts] = useState("");
  const [acceptedAnswers, setAcceptedAnswers] = useState("");
  const [forbiddenDocumentRefs, setForbiddenDocumentRefs] = useState("");
  const [knowledgeScope, setKnowledgeScope] = useState("{}");
  const [retrievalFilters, setRetrievalFilters] = useState("{}");
  const [goldEvidenceLocators, setGoldEvidenceLocators] = useState("[]");
  const [requiredDocGroups, setRequiredDocGroups] = useState("[]");
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    setCaseKey(caseToEdit?.caseKey ?? "");
    setCaseType(caseToEdit?.caseType ?? evalCaseTypeValues[0]);
    setExpectedMode(caseToEdit?.expectedMode ?? "ANSWER");
    setSeverity(caseToEdit?.severity ?? "MEDIUM");
    setQuestion(caseToEdit?.question ?? "");
    setTags((caseToEdit?.tags ?? []).join(", "));
    setGoldFacts((caseToEdit?.goldFacts ?? []).join("\n"));
    setAcceptedAnswers((caseToEdit?.acceptedAnswers ?? []).join("\n"));
    setForbiddenDocumentRefs((caseToEdit?.forbiddenDocumentRefs ?? []).join("\n"));
    setKnowledgeScope(JSON.stringify(caseToEdit?.knowledgeScope ?? {}, null, 2));
    setRetrievalFilters(JSON.stringify(caseToEdit?.retrievalFilters ?? {}, null, 2));
    setGoldEvidenceLocators(JSON.stringify(caseToEdit?.goldEvidenceLocators ?? [], null, 2));
    setRequiredDocGroups(JSON.stringify(caseToEdit?.requiredDocGroups ?? [], null, 2));
    setLocalError(null);
  }, [caseToEdit, isOpen]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!datasetId && !caseToEdit?.id) {
      setLocalError("Dataset is required.");
      return;
    }

    try {
      const payload: CreateEvalCaseRequest = {
        acceptedAnswers: splitLines(acceptedAnswers),
        caseKey: caseKey.trim(),
        caseType: caseType as CreateEvalCaseRequest["caseType"],
        expectedMode: expectedMode === NONE_VALUE ? null : expectedMode as CreateEvalCaseRequest["expectedMode"],
        forbiddenDocumentRefs: splitLines(forbiddenDocumentRefs),
        goldEvidenceLocators: parseJson(goldEvidenceLocators, []),
        goldFacts: splitLines(goldFacts),
        knowledgeScope: parseJson(knowledgeScope, {}),
        question: question.trim(),
        requiredDocGroups: parseJson(requiredDocGroups, []),
        retrievalFilters: parseJson(retrievalFilters, {}),
        severity: severity as CreateEvalCaseRequest["severity"],
        tags: splitLines(tags),
      };
      const result = caseToEdit?.id
        ? await onUpdateCase(caseToEdit.id, payload)
        : await onCreateCase(datasetId ?? "", payload);
      if (result) {
        onClose();
      }
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : "Invalid case JSON fields.");
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open ? onClose() : undefined}>
      <DialogContent className="max-h-[92vh] max-w-4xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{caseToEdit ? "Edit eval case" : "Create eval case"}</DialogTitle>
          <DialogDescription>Case fields follow the backend dataset lifecycle contract.</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={(event) => void submit(event)}>
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Case key">
              <Input required value={caseKey} onChange={(event) => setCaseKey(event.target.value)} />
            </Field>
            <Field label="Question">
              <Input required value={question} onChange={(event) => setQuestion(event.target.value)} />
            </Field>
            <Field label="Type">
              <Select value={caseType} onValueChange={setCaseType}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {evalCaseTypeValues.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Expected mode">
              <Select value={expectedMode} onValueChange={setExpectedMode}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {evalExpectedModeValues.map((value) => (
                    <SelectItem key={value} value={value}>{expectedModeLabels[value]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Severity">
              <Select value={severity} onValueChange={setSeverity}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {evalCaseSeverityValues.map((value) => <SelectItem key={value} value={value}>{severityLabels[value]}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Tags">
              <Input value={tags} onChange={(event) => setTags(event.target.value)} />
            </Field>
          </div>

          <div className="grid gap-3 md:grid-cols-3">
            <Field label="Gold facts">
              <Textarea value={goldFacts} onChange={(event) => setGoldFacts(event.target.value)} />
            </Field>
            <Field label="Accepted answers">
              <Textarea value={acceptedAnswers} onChange={(event) => setAcceptedAnswers(event.target.value)} />
            </Field>
            <Field label="Forbidden doc refs">
              <Textarea value={forbiddenDocumentRefs} onChange={(event) => setForbiddenDocumentRefs(event.target.value)} />
            </Field>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Knowledge scope JSON">
              <Textarea value={knowledgeScope} onChange={(event) => setKnowledgeScope(event.target.value)} />
            </Field>
            <Field label="Retrieval filters JSON">
              <Textarea value={retrievalFilters} onChange={(event) => setRetrievalFilters(event.target.value)} />
            </Field>
            <Field label="Gold evidence locators JSON">
              <Textarea value={goldEvidenceLocators} onChange={(event) => setGoldEvidenceLocators(event.target.value)} />
            </Field>
            <Field label="Required doc groups JSON">
              <Textarea value={requiredDocGroups} onChange={(event) => setRequiredDocGroups(event.target.value)} />
            </Field>
          </div>

          {localError ? <p className="text-sm leading-6 text-destructive">{localError}</p> : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button disabled={isSaving} type="submit">{isSaving ? "Saving..." : "Save"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
