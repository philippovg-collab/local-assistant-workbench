import { useMemo, useState, type FormEvent } from "react";
import { Check, Edit3, Pin, PinOff, Plus, RefreshCw, Trash2, X } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useMemoryEntries } from "@/hooks/useMemoryEntries";
import type { MemoryEntryResponse, MemoryEntryStatus, MemoryEntryType } from "@/types";
import { formatDate } from "@/utils/format";

const statusOptions: Array<{ value: MemoryEntryStatus | "all"; label: string }> = [
  { value: "pending_review", label: "На review" },
  { value: "approved", label: "Approved" },
  { value: "rejected", label: "Rejected" },
  { value: "deleted", label: "Deleted" },
  { value: "all", label: "Все" },
];

const typeOptions: Array<{ value: MemoryEntryType | "all"; label: string }> = [
  { value: "all", label: "Все типы" },
  { value: "user_preference", label: "Preference" },
  { value: "user_alias", label: "Alias" },
  { value: "workspace_note", label: "Workspace" },
  { value: "project_note", label: "Project" },
  { value: "pinned_user_fact", label: "Pinned fact" },
];

type MemoryReviewPanelProps = { enabled?: boolean; workspaceKey?: string | null; workspaceName?: string | null };

export function MemoryReviewPanel({ enabled = true, workspaceKey = null, workspaceName = null }: MemoryReviewPanelProps) {
  const memory = useMemoryEntries({ enabled, workspaceKey });
  const [draftType, setDraftType] = useState<MemoryEntryType>("user_preference");
  const [draftContent, setDraftContent] = useState("");
  const [draftProjectKey, setDraftProjectKey] = useState("");
  const pendingCount = useMemo(
    () => memory.entries.filter((entry) => entry.status === "pending_review").length,
    [memory.entries],
  );

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!enabled || !draftContent.trim()) {
      return;
    }
    const created = await memory.createEntry({
      entryType: draftType,
      contentText: draftContent,
      workspaceKey: workspaceKey?.trim() || null,
      projectKey: draftProjectKey.trim() || null,
      provenance: { source: "manual-ui" },
    });
    if (created) {
      setDraftContent("");
      setDraftProjectKey("");
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="default">Memory Review</Badge>
          <Badge variant={pendingCount > 0 ? "warning" : "secondary"}>{pendingCount} pending</Badge>
          {workspaceKey && <Badge variant="outline">{workspaceName || workspaceKey}</Badge>}
        </div>
        <Button disabled={!enabled || memory.isLoading} type="button" variant="outline" onClick={() => void memory.loadEntries()}>
          <RefreshCw className={memory.isLoading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          Обновить
        </Button>
      </div>

      {memory.error && (
        <Alert variant="destructive">
          <AlertDescription>{memory.error}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-3 lg:grid-cols-[180px_180px_minmax(180px,1fr)]">
        <div className="space-y-2">
          <Label>Статус</Label>
          <Select value={memory.statusFilter} onValueChange={(value) => memory.setStatusFilter(value as MemoryEntryStatus | "all")}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {statusOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>Тип</Label>
          <Select value={memory.typeFilter} onValueChange={(value) => memory.setTypeFilter(value as MemoryEntryType | "all")}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {typeOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="memory-project-filter">Project key</Label>
          <Input
            id="memory-project-filter"
            value={memory.projectKeyFilter}
            onChange={(event) => memory.setProjectKeyFilter(event.target.value)}
          />
        </div>
      </div>

      <form className="rounded-[22px] border border-border bg-card/75 p-4" onSubmit={handleCreate}>
        <div className="grid gap-3 lg:grid-cols-[180px_180px_minmax(0,1fr)_auto] lg:items-end">
          <div className="space-y-2">
            <Label>Новая запись</Label>
            <Select value={draftType} onValueChange={(value) => setDraftType(value as MemoryEntryType)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {typeOptions.filter((option) => option.value !== "all").map((option) => (
                  <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="memory-draft-project">Project key</Label>
            <Input
              id="memory-draft-project"
              value={draftProjectKey}
              onChange={(event) => setDraftProjectKey(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="memory-draft-content">Content</Label>
            <Textarea
              id="memory-draft-content"
              rows={2}
              value={draftContent}
              onChange={(event) => setDraftContent(event.target.value)}
            />
          </div>
          <Button disabled={!enabled || memory.isMutating || !draftContent.trim()} type="submit">
            <Plus className="h-4 w-4" />
            Добавить
          </Button>
        </div>
      </form>

      <div className="grid gap-3">
        {memory.entries.length === 0 ? (
          <div className="rounded-[18px] border border-dashed border-border px-4 py-8 text-center text-sm font-medium text-muted-foreground">
            Очередь пуста
          </div>
        ) : (
          memory.entries.map((entry) => (
            <MemoryEntryRow
              entry={entry}
              isMutating={memory.isMutating}
              key={entry.id}
              onApprove={memory.approveEntry}
              onDelete={memory.deleteEntry}
              onPin={memory.pinEntry}
              onReject={memory.rejectEntry}
              onUnpin={memory.unpinEntry}
              onUpdate={memory.updateEntry}
            />
          ))
        )}
      </div>
    </div>
  );
}

type MemoryEntryRowProps = {
  entry: MemoryEntryResponse;
  isMutating: boolean;
  onApprove: (entryId: string) => Promise<unknown>;
  onReject: (entryId: string) => Promise<unknown>;
  onPin: (entryId: string) => Promise<unknown>;
  onUnpin: (entryId: string) => Promise<unknown>;
  onDelete: (entryId: string) => Promise<unknown>;
  onUpdate: (entryId: string, input: { contentText: string }) => Promise<unknown>;
};

function MemoryEntryRow({
  entry,
  isMutating,
  onApprove,
  onDelete,
  onPin,
  onReject,
  onUnpin,
  onUpdate,
}: MemoryEntryRowProps) {
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(entry.contentText ?? "");
  const isPending = entry.status === "pending_review";
  const isApproved = entry.status === "approved";
  const isDeleted = entry.status === "deleted";
  const canEdit = isPending || isApproved;

  const confirmDelete = () => {
    if (typeof window !== "undefined" && !window.confirm("Удалить память и скрыть содержимое?")) {
      return;
    }
    void onDelete(entry.id);
  };

  const saveEdit = async () => {
    const updated = await onUpdate(entry.id, { contentText: content });
    if (updated) {
      setEditing(false);
    }
  };

  return (
    <article className="rounded-[18px] border border-border bg-card/80 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={isPending ? "warning" : isApproved ? "success" : isDeleted ? "secondary" : "outline"}>
              {entry.status}
            </Badge>
            <Badge variant="outline">{entry.entryType}</Badge>
            {entry.pinned && <Badge variant="default">pinned</Badge>}
            {entry.confidence != null && <Badge variant="secondary">{Math.round(entry.confidence * 100)}%</Badge>}
          </div>
          <p className="text-xs text-muted-foreground">
            {entry.updatedAt ? formatDate(entry.updatedAt) : "—"}
            {entry.sourceRunId ? ` · run ${entry.sourceTurnNo ?? "—"}` : ""}
          </p>
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          {isPending && (
            <>
              <Button disabled={isMutating} size="sm" type="button" onClick={() => void onApprove(entry.id)}>
                <Check className="h-4 w-4" />
                Approve
              </Button>
              <Button disabled={isMutating} size="sm" type="button" variant="outline" onClick={() => void onReject(entry.id)}>
                <X className="h-4 w-4" />
                Reject
              </Button>
            </>
          )}
          {isApproved && (
            <Button
              disabled={isMutating}
              size="sm"
              type="button"
              variant="outline"
              onClick={() => void (entry.pinned ? onUnpin(entry.id) : onPin(entry.id))}
            >
              {entry.pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
              {entry.pinned ? "Unpin" : "Pin"}
            </Button>
          )}
          {canEdit && (
            <Button disabled={isMutating} size="sm" type="button" variant="outline" onClick={() => setEditing((value) => !value)}>
              <Edit3 className="h-4 w-4" />
              Edit
            </Button>
          )}
          {!isDeleted && (
            <Button disabled={isMutating} size="sm" type="button" variant="destructive" onClick={confirmDelete}>
              <Trash2 className="h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      {editing ? (
        <div className="mt-3 space-y-2">
          <Textarea rows={3} value={content} onChange={(event) => setContent(event.target.value)} />
          <Button disabled={isMutating || !content.trim()} size="sm" type="button" onClick={() => void saveEdit()}>
            Save
          </Button>
        </div>
      ) : (
        <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-foreground">
          {entry.contentText ?? "Содержимое удалено"}
        </p>
      )}

      {entry.sourceTextPreview && (
        <div className="mt-3 rounded-[14px] border border-border bg-background/70 px-3 py-2 text-xs leading-5 text-muted-foreground">
          {entry.sourceTextPreview}
        </div>
      )}
    </article>
  );
}
