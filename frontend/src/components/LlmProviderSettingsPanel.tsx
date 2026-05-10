import type { ReactNode } from "react";
import {
  CheckCircle2,
  KeyRound,
  ListChecks,
  Pencil,
  PlugZap,
  Plus,
  RefreshCw,
  RotateCcw,
  ServerCog,
  Trash2,
  XCircle,
} from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge, type BadgeProps } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  LlmProviderModelInfo,
  LlmProviderPurpose,
  LlmProviderStatus,
} from "@/types";
import { useLlmProviders } from "@/hooks/useLlmProviders";
import { formatDate } from "@/utils/format";

const statusVariant = (status?: LlmProviderStatus | null): BadgeProps["variant"] => {
  if (status === "UP") {
    return "success";
  }
  if (status === "DOWN") {
    return "destructive";
  }
  if (status === "DEGRADED") {
    return "warning";
  }
  return "secondary";
};

const purposeLabel = (purpose?: LlmProviderPurpose | null) => {
  if (purpose === "CHAT") {
    return "Chat";
  }
  if (purpose === "EMBEDDING") {
    return "Embeddings";
  }
  if (purpose === "CHAT_AND_EMBEDDING") {
    return "Chat + Embeddings";
  }
  return "Не задано";
};

const supportsChatActivation = (purpose?: LlmProviderPurpose | null) =>
  purpose === "CHAT" || purpose === "CHAT_AND_EMBEDDING";

const supportsEmbeddingActivation = (purpose?: LlmProviderPurpose | null) =>
  purpose === "EMBEDDING" || purpose === "CHAT_AND_EMBEDDING";

const modelNames = (models?: LlmProviderModelInfo[] | null) =>
  models
    ?.map((model) => model.name?.trim())
    .filter((name): name is string => Boolean(name)) ?? [];

export function LlmProviderSettingsPanel() {
  const {
    activeChatProvider,
    activeEmbeddingProvider,
    activateFallback,
    activateProvider,
    deleteProvider,
    dialogOpen,
    editingProvider,
    error,
    fetchProviderModels,
    form,
    providers,
    isLoading,
    isMutating,
    loadProviders,
    message,
    modelResults,
    openCreateDialog,
    openEditDialog,
    probeProvider,
    probeResults,
    saveProvider,
    setDialogOpen,
    updateForm,
  } = useLlmProviders();

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="default">LLM подключения</Badge>
            <Badge variant={activeChatProvider ? "success" : "secondary"}>
              Chat: {activeChatProvider?.name ?? "env fallback"}
            </Badge>
            <Badge variant={activeEmbeddingProvider ? "success" : "secondary"}>
              Embeddings: {activeEmbeddingProvider?.name ?? "env fallback"}
            </Badge>
          </div>
          <h2 className="text-2xl font-semibold text-foreground">
            Корпоративные OpenAI-compatible endpoints
          </h2>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button disabled={isMutating} onClick={() => void loadProviders()} type="button" variant="secondary">
            <RefreshCw />
            Обновить
          </Button>
          <Button onClick={openCreateDialog} type="button">
            <Plus />
            Добавить
          </Button>
        </div>
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Ошибка</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}
      {message ? (
        <Alert variant="success">
          <AlertTitle>Готово</AlertTitle>
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      ) : null}

      <div className="overflow-hidden rounded-[28px] border border-border bg-card shadow-soft">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1120px] text-left text-sm">
            <thead className="border-b border-border bg-surface-subtle text-xs uppercase tracking-[0.14em] text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Название</th>
                <th className="px-4 py-3">Назначение</th>
                <th className="px-4 py-3">Base URL</th>
                <th className="px-4 py-3">Модели</th>
                <th className="px-4 py-3">Статус</th>
                <th className="px-4 py-3">Активно</th>
                <th className="px-4 py-3">Проверка</th>
                <th className="px-4 py-3 text-right">Действия</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {providers.map((provider) => {
                const probe = provider.id ? probeResults[provider.id] : null;
                const models = provider.id ? modelResults[provider.id] : null;
                const remoteModelNames = modelNames(models);
                const status = probe?.status ?? provider.status ?? "UNKNOWN";
                const lastProbeAt = probe?.checkedAt ?? provider.lastProbeAt;
                const latencyMs = probe?.latencyMs ?? null;
                const statusError = probe?.errorMessage ?? provider.lastErrorMessage;
                const canActivateChat = supportsChatActivation(provider.purpose);
                const canActivateEmbedding = supportsEmbeddingActivation(provider.purpose);
                const isActive = provider.activeChat || provider.activeEmbedding;
                return (
                  <tr key={provider.id ?? provider.name} className="align-top">
                    <td className="px-4 py-4">
                      <div className="space-y-2">
                        <div className="font-semibold text-foreground">{provider.name}</div>
                        <div className="flex flex-wrap gap-2">
                          <Badge variant="outline">{provider.providerType}</Badge>
                          {provider.hasApiKey ? (
                            <Badge variant="secondary">
                              <KeyRound className="h-3 w-3" />
                              key
                            </Badge>
                          ) : null}
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-4">{purposeLabel(provider.purpose)}</td>
                    <td className="max-w-[220px] break-all px-4 py-4 text-muted-foreground">{provider.baseUrl}</td>
                    <td className="px-4 py-4">
                      <div className="space-y-1 text-muted-foreground">
                        <div>{provider.defaultModel || "chat model не задана"}</div>
                        <div>{provider.embeddingModel || "embedding model не задана"}</div>
                        {models ? (
                          <div className="space-y-2">
                            <Badge variant="secondary">{models.length} remote</Badge>
                            {remoteModelNames.length > 0 ? (
                              <ul className="max-w-[260px] space-y-1 text-xs normal-case tracking-normal text-foreground">
                                {remoteModelNames.slice(0, 6).map((name, index) => (
                                  <li className="break-all" key={`${name}-${index}`}>
                                    {name}
                                  </li>
                                ))}
                                {remoteModelNames.length > 6 ? (
                                  <li className="text-muted-foreground">+{remoteModelNames.length - 6} еще</li>
                                ) : null}
                              </ul>
                            ) : null}
                          </div>
                        ) : null}
                      </div>
                    </td>
                    <td className="px-4 py-4">
                      <Badge variant={statusVariant(status)}>{status}</Badge>
                      {statusError ? (
                        <p className="mt-2 max-w-[240px] text-xs leading-5 text-muted-foreground">
                          {statusError}
                        </p>
                      ) : null}
                    </td>
                    <td className="px-4 py-4">
                      <div className="flex flex-col gap-2">
                        {provider.activeChat ? <Badge variant="success">Chat active</Badge> : null}
                        {provider.activeEmbedding ? <Badge variant="success">Embedding active</Badge> : null}
                        {!provider.activeChat && !provider.activeEmbedding ? <Badge variant="secondary">inactive</Badge> : null}
                      </div>
                    </td>
                    <td className="px-4 py-4 text-muted-foreground">
                      <div>{lastProbeAt ? formatDate(lastProbeAt) : "Пока нет"}</div>
                      {latencyMs != null ? <div>{latencyMs} ms</div> : null}
                    </td>
                    <td className="px-4 py-4">
                      <div className="flex flex-wrap justify-end gap-2">
                        <Button disabled={isMutating} onClick={() => openEditDialog(provider)} size="sm" type="button" variant="secondary">
                          <Pencil />
                          Edit
                        </Button>
                        <Button disabled={isMutating} onClick={() => void probeProvider(provider)} size="sm" type="button" variant="secondary">
                          <PlugZap />
                          Probe
                        </Button>
                        <Button disabled={isMutating} onClick={() => void fetchProviderModels(provider)} size="sm" type="button" variant="secondary">
                          <ListChecks />
                          Models
                        </Button>
                        <Button
                          disabled={isMutating || !canActivateChat}
                          onClick={() => void activateProvider(provider, "CHAT")}
                          size="sm"
                          title={canActivateChat ? "Активировать provider для Chat" : "Provider не поддерживает Chat activation"}
                          type="button"
                          variant="outline"
                        >
                          <CheckCircle2 />
                          {canActivateChat ? "Chat" : "Chat недоступен"}
                        </Button>
                        <Button
                          disabled={isMutating || !canActivateEmbedding}
                          onClick={() => void activateProvider(provider, "EMBEDDING")}
                          size="sm"
                          title={canActivateEmbedding ? "Активировать provider для Embeddings" : "Provider не поддерживает Embeddings activation"}
                          type="button"
                          variant="outline"
                        >
                          <ServerCog />
                          {canActivateEmbedding ? "Embeddings" : "Embeddings недоступны"}
                        </Button>
                        <Button
                          disabled={isMutating || isActive}
                          onClick={() => void deleteProvider(provider)}
                          size="sm"
                          title={isActive ? "Активное подключение нельзя удалить" : `Удалить ${provider.name ?? "подключение"}`}
                          type="button"
                          variant="destructive"
                        >
                          <Trash2 />
                          Delete
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
              {!isLoading && providers.length === 0 ? (
                <tr>
                  <td className="px-4 py-10 text-center text-muted-foreground" colSpan={8}>
                    Подключения не созданы. Используется env fallback.
                  </td>
                </tr>
              ) : null}
              {isLoading ? (
                <tr>
                  <td className="px-4 py-10 text-center text-muted-foreground" colSpan={8}>
                    Загружаем подключения...
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button disabled={isMutating} onClick={() => void activateFallback("CHAT")} type="button" variant="secondary">
          <RotateCcw />
          Chat env fallback
        </Button>
        <Button disabled={isMutating} onClick={() => void activateFallback("EMBEDDING")} type="button" variant="secondary">
          <RotateCcw />
          Embeddings env fallback
        </Button>
      </div>

      <Dialog onOpenChange={setDialogOpen} open={dialogOpen}>
        <DialogContent className="max-h-[92vh] w-[calc(100vw-2rem)] max-w-5xl overflow-y-auto p-4 sm:p-6">
          <DialogHeader>
            <DialogTitle>{editingProvider ? "Редактировать подключение" : "Новое LLM подключение"}</DialogTitle>
            <DialogDescription>
              API key не возвращается после сохранения; пустое поле при редактировании оставит текущий ключ.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 md:grid-cols-2">
            <Field htmlFor="llm-provider-name" label="Название подключения">
              <Input id="llm-provider-name" value={form.name} onChange={(event) => updateForm("name", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-purpose" label="Назначение">
              <Select value={form.purpose} onValueChange={(value) => updateForm("purpose", value as LlmProviderPurpose)}>
                <SelectTrigger id="llm-provider-purpose">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CHAT">Chat</SelectItem>
                  <SelectItem value="EMBEDDING">Embeddings</SelectItem>
                  <SelectItem value="CHAT_AND_EMBEDDING">Chat + Embeddings</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field className="md:col-span-2" htmlFor="llm-provider-base-url" label="Base URL">
              <Input id="llm-provider-base-url" placeholder="http://10.10.20.15:8000" value={form.baseUrl} onChange={(event) => updateForm("baseUrl", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-api-key" label="API key">
              <Input
                id="llm-provider-api-key"
                autoComplete="new-password"
                disabled={Boolean(editingProvider && form.clearApiKey)}
                type="password"
                value={form.apiKey}
                onChange={(event) => updateForm("apiKey", event.target.value)}
              />
            </Field>
            <Field htmlFor="llm-provider-auth-scheme" label="Auth scheme">
              <Input id="llm-provider-auth-scheme" value={form.authScheme} onChange={(event) => updateForm("authScheme", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-auth-header" label="Auth header name">
              <Input id="llm-provider-auth-header" value={form.authHeaderName} onChange={(event) => updateForm("authHeaderName", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-type" label="Provider type">
              <Select value={form.providerType} onValueChange={() => updateForm("providerType", "OPENAI_COMPATIBLE")}>
                <SelectTrigger id="llm-provider-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="OPENAI_COMPATIBLE">OPENAI_COMPATIBLE</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field htmlFor="llm-provider-models-path" label="Models path">
              <Input id="llm-provider-models-path" value={form.modelsPath} onChange={(event) => updateForm("modelsPath", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-chat-path" label="Chat completions path">
              <Input id="llm-provider-chat-path" value={form.chatCompletionsPath} onChange={(event) => updateForm("chatCompletionsPath", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-embeddings-path" label="Embeddings path">
              <Input id="llm-provider-embeddings-path" value={form.embeddingsPath} onChange={(event) => updateForm("embeddingsPath", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-chat-model" label="Default chat model">
              <Input id="llm-provider-chat-model" value={form.defaultModel} onChange={(event) => updateForm("defaultModel", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-embedding-model" label="Embedding model">
              <Input id="llm-provider-embedding-model" value={form.embeddingModel} onChange={(event) => updateForm("embeddingModel", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-temperature" label="Temperature">
              <Input id="llm-provider-temperature" min="0" max="2" step="0.1" type="number" value={form.temperature} onChange={(event) => updateForm("temperature", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-timeout" label="Timeout seconds">
              <Input id="llm-provider-timeout" min="1" max="3600" type="number" value={form.timeoutSeconds} onChange={(event) => updateForm("timeoutSeconds", event.target.value)} />
            </Field>
            <Field htmlFor="llm-provider-dimension" label="Expected embedding dimension">
              <Input id="llm-provider-dimension" min="1" type="number" value={form.expectedEmbeddingDimension} onChange={(event) => updateForm("expectedEmbeddingDimension", event.target.value)} />
            </Field>
          </div>

          <div className="grid gap-3 rounded-[24px] border border-border bg-surface-subtle p-4 sm:grid-cols-3">
            {!editingProvider ? (
              <>
                <CheckboxRow checked={form.activeChat} label="Сделать активным для Chat" onCheckedChange={(checked) => updateForm("activeChat", checked)} />
                <CheckboxRow checked={form.activeEmbedding} label="Сделать активным для Embeddings" onCheckedChange={(checked) => updateForm("activeEmbedding", checked)} />
              </>
            ) : (
              <CheckboxRow
                checked={form.clearApiKey}
                label="Очистить сохраненный API key"
                onCheckedChange={(checked) => {
                  updateForm("clearApiKey", checked);
                  if (checked) {
                    updateForm("apiKey", "");
                  }
                }}
              />
            )}
          </div>

          <DialogFooter className="sticky bottom-0 -mx-4 -mb-4 border-t border-border bg-popover/95 px-4 py-4 backdrop-blur sm:-mx-6 sm:-mb-6 sm:px-6">
            <Button onClick={() => setDialogOpen(false)} type="button" variant="secondary">
              <XCircle />
              Отмена
            </Button>
            <Button disabled={isMutating} onClick={() => void saveProvider()} type="button">
              <CheckCircle2 />
              Сохранить
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Field({
  children,
  className = "",
  htmlFor,
  label,
}: {
  children: ReactNode;
  className?: string;
  htmlFor: string;
  label: string;
}) {
  return (
    <div className={`space-y-2 ${className}`}>
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}

function CheckboxRow({
  checked,
  label,
  onCheckedChange,
}: {
  checked: boolean;
  label: string;
  onCheckedChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex items-center gap-3 text-sm font-medium text-foreground">
      <Checkbox checked={checked} onCheckedChange={(value) => onCheckedChange(value === true)} />
      <span>{label}</span>
    </label>
  );
}
