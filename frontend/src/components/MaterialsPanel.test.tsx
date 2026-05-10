import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MaterialsPanel } from "./MaterialsPanel";
import { buildMaterialSummary } from "../testBuilders";
import { buildRagReadinessPresentation, deriveRagReadiness } from "../utils/readiness";
import type { HealthResponse, MaterialDetail, ReferenceProject, ReferenceWorkspace } from "../types";
import type { MaterialMetadataFormState, MaterialMetadataValidation } from "../utils/materialMetadata";

const materialEditorMock = vi.hoisted(() => ({
  detail: null as MaterialDetail | null,
  loadError: null as string | null,
  isLoadingDetail: false,
}));

vi.mock("@/hooks/useMaterialEditorDialog", async () => {
  const React = await vi.importActual<typeof import("react")>("react");
  const metadata = await vi.importActual<typeof import("@/utils/materialMetadata")>("@/utils/materialMetadata");
  return {
    useMaterialEditorDialog: (material: { id: string } | null, activeWorkspaceKey?: string | null) => {
      const detail = material ? materialEditorMock.detail : null;
      const [title, setTitle] = React.useState("");
      const [content, setContent] = React.useState("");
      const [formMetadata, setMetadata] = React.useState(metadata.emptyMaterialMetadataFormState());
      const [metadataValidation, setMetadataValidation] =
        React.useState<MaterialMetadataValidation | null>(null);
      const [submitError, setSubmitError] = React.useState<string | null>(null);
      const normalizedActiveWorkspaceKey = (activeWorkspaceKey ?? "").trim();

      React.useEffect(() => {
        if (!detail) {
          setTitle("");
          setContent("");
          setMetadata(metadata.emptyMaterialMetadataFormState());
          setMetadataValidation(null);
          setSubmitError(null);
          return;
        }
        setTitle(detail.title);
        setContent(detail.content);
        setMetadata({
          ...metadata.toMaterialMetadataFormState(detail.metadata),
          workspaceKey: normalizedActiveWorkspaceKey,
          projectKey: "",
        });
        setMetadataValidation(null);
        setSubmitError(null);
      }, [detail?.id, normalizedActiveWorkspaceKey]);

      return {
        detail,
        title,
        setTitle,
        content,
        setContent,
        metadata: formMetadata,
        setMetadata,
        metadataValidation,
        setMetadataValidation,
        loadError: material ? materialEditorMock.loadError : null,
        submitError,
        setSubmitError,
        isLoadingDetail: material ? materialEditorMock.isLoadingDetail : false,
        withActiveWorkspace: (current: MaterialMetadataFormState) => ({
          ...current,
          workspaceKey: normalizedActiveWorkspaceKey,
          projectKey: "",
        }),
      };
    },
  };
});

const referenceWorkspaces: ReferenceWorkspace[] = [
  {
    key: "general",
    nameRu: "Общая",
    active: true,
    sortOrder: 0,
    isDefault: true,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "north-upgrade",
    nameRu: "Северная модернизация",
    active: true,
    sortOrder: 1,
    isDefault: false,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
];

const referenceProjects: ReferenceProject[] = [
  {
    key: "north-line",
    workspaceKey: "north-upgrade",
    nameRu: "Северная линия",
    active: true,
    sortOrder: 0,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
];

const renderPanel = (
  overrides: Partial<Parameters<typeof MaterialsPanel>[0]> = {},
) => {
  const materials = overrides.materials ?? [];
  const health: HealthResponse = {
    application: "Local Assistant Workbench",
    status: "UP",
    timestamp: "2026-04-16T10:00:00Z",
    directStatus: "UP",
    ragStatus: materials.some((material) => material.versionState !== "SUPERSEDED") ? "UP" : "DOWN",
    llmStatus: "UP",
    embeddingStatus: "UP",
    knowledgeStatus: materials.length === 0
      ? "EMPTY"
      : materials.every((material) => material.versionState === "SUPERSEDED")
        ? "HISTORICAL_ONLY"
        : "READY",
    materialCount: materials.length,
    activeMaterialCount: materials.filter((material) => material.versionState !== "SUPERSEDED").length,
    historicalMaterialCount: materials.filter((material) => material.versionState === "SUPERSEDED").length,
    readyMaterialCount: materials.filter(
      (material) =>
        material.versionState !== "SUPERSEDED"
        && (material.status === "READY" || material.status === "PARTIAL_READY"),
    ).length,
    indexingPendingCount: 0,
    indexingInProgressCount: 0,
  };

  return render(
    <MaterialsPanel
      actionError={null}
      deletingMaterialId={null}
      editingMaterialId={null}
      error={null}
	      isLoading={false}
	      isLoadingMore={false}
      lineageError={null}
      loadingLineageMaterialId={null}
      hasMoreMaterials={false}
      isReferenceDataLoading={false}
      materials={materials}
      materialTotal={materials.length}
      metadataV1Enabled={false}
      message={null}
      onClearLineage={vi.fn()}
      onCreateText={vi.fn()}
      onDelete={vi.fn()}
      onEditMaterial={vi.fn()}
      onLoadLineage={vi.fn()}
      onLoadMore={vi.fn()}
      onReindex={vi.fn()}
      onUpload={vi.fn()}
      onUploadVersion={vi.fn()}
      policyWarning={null}
      ragPresentation={
        overrides.ragPresentation ?? buildRagReadinessPresentation({
          health,
          ragReadiness: deriveRagReadiness(health),
          isLoadingMaterials: false,
          materialsError: null,
          selectedModel: "qwen2.5:7b",
        })
      }
      referenceDataError={null}
      referenceProjects={referenceProjects}
      referenceWorkspaces={referenceWorkspaces}
      reindexingMaterialId={null}
      selectedLineage={null}
      uploadPolicy={null}
      versionUploadingMaterialId={null}
      {...overrides}
    />,
  );
};

const chooseSelectOption = async (
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
  index = 0,
) => {
  await waitFor(() => {
    expect((screen.getAllByLabelText(label)[index] as HTMLButtonElement).disabled).toBe(false);
  });
  await user.click(screen.getAllByLabelText(label)[index]);
  await user.click(await screen.findByRole("option", { name: option }));
};

describe("MaterialsPanel", () => {
  afterEach(() => {
    materialEditorMock.detail = null;
    materialEditorMock.loadError = null;
    materialEditorMock.isLoadingDetail = false;
    cleanup();
    vi.clearAllMocks();
  });

  it("includes pdf in the upload accept list and mentions OCR fallback", () => {
    renderPanel({
      uploadPolicy: {
        maxUploadBytes: 8_388_608,
        acceptedExtensions: ["txt", "pdf"],
        acceptedMimeHints: ["text/plain", "application/pdf"],
        richDocumentSupport: true,
        pdf: {
          enabled: true,
          scannedPdfSupport: true,
          mode: "embedded_text_and_ocr",
          ocrLanguages: ["kaz", "rus", "eng"],
          ocrMaxPages: 12,
        },
      },
    });

    const fileInput = screen.getByLabelText("Файлы") as HTMLInputElement;
    const uploadButton = screen.getByRole("button", { name: "Загрузить файл" });
    expect(fileInput.accept).toContain(".pdf");
    expect(fileInput.multiple).toBe(true);
    expect(uploadButton).toBeTruthy();
    expect(screen.getByText(/scanned PDF поддерживается через OCR/i)).toBeTruthy();
  });

  it("submits selected files as a batch and leaves batch titles to filenames", async () => {
    const user = userEvent.setup();
    const onUpload = vi.fn().mockResolvedValue(undefined);
    renderPanel({ onUpload });

    const titleInput = document.querySelector("#upload-material-title") as HTMLInputElement;
    const fileInput = screen.getByLabelText("Файлы") as HTMLInputElement;
    const firstFile = new File(["first"], "first.txt", { type: "text/plain" });
    const secondFile = new File(["second"], "second.txt", { type: "text/plain" });

    await user.type(titleInput, "Shared title");
    await user.upload(fileInput, [firstFile, secondFile]);

    expect(titleInput.disabled).toBe(true);
    expect(titleInput.value).toBe("");

    const uploadButton = screen.getByRole("button", { name: "Загрузить файлы" }) as HTMLButtonElement;
    expect(uploadButton.disabled).toBe(false);
    fireEvent.submit(fileInput.closest("form") as HTMLFormElement);

    await waitFor(() => {
      expect(onUpload).toHaveBeenCalledWith({
        items: [
          { file: firstFile },
          { file: secondFile },
        ],
      });
    });
  });

  it("renders partial PDF support warning with the OCR reason", () => {
    renderPanel({
      uploadPolicy: {
        maxUploadBytes: 8_388_608,
        acceptedExtensions: ["txt", "pdf"],
        acceptedMimeHints: ["text/plain", "application/pdf"],
        richDocumentSupport: true,
        pdf: {
          enabled: true,
          scannedPdfSupport: false,
          mode: "embedded_text_only",
          ocrReasonCode: "material.ocr_unavailable",
          ocrReasonMessage: "Tesseract OCR binary is unavailable at 'tesseract'.",
          ocrLanguages: ["kaz", "rus", "eng"],
          ocrMaxPages: 12,
        },
      },
    });

    expect(screen.getByText(/PDF со встроенным текстом всё ещё поддерживаются/i)).toBeTruthy();
    expect(screen.getByText(/Tesseract OCR сейчас недоступен на сервере/i)).toBeTruthy();
    expect(screen.getByText(/Scanned PDF сейчас не поддерживаются: Tesseract OCR сейчас недоступен/i)).toBeTruthy();
  });

  it("renders a policy warning without hiding the upload form", () => {
    renderPanel({
      policyWarning: "Не удалось подтвердить capability backend; возможны ограничения при загрузке PDF.",
      uploadPolicy: null,
    });

    expect(screen.getByText(/Не удалось подтвердить capability backend/i)).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "Загрузить файл" }).length).toBeGreaterThan(0);
  });

  it("uses OCR languages from the backend policy in warnings", () => {
    renderPanel({
      uploadPolicy: {
        maxUploadBytes: 8_388_608,
        acceptedExtensions: ["txt", "pdf"],
        acceptedMimeHints: ["text/plain", "application/pdf"],
        richDocumentSupport: true,
        pdf: {
          enabled: true,
          scannedPdfSupport: false,
          mode: "embedded_text_only",
          ocrReasonCode: "material.ocr_language_data_missing",
          ocrLanguages: ["deu", "eng"],
          ocrMaxPages: 12,
        },
      },
    });

    expect(screen.getByText(/не хватает языковых данных OCR для deu и eng/i)).toBeTruthy();
  });

  it("gracefully handles a legacy policy payload without pdf metadata", () => {
    renderPanel({
      uploadPolicy: {
        maxUploadBytes: 8_388_608,
        acceptedExtensions: ["txt", "pdf"],
        acceptedMimeHints: ["text/plain", "application/pdf"],
        richDocumentSupport: true,
      } as unknown as Parameters<typeof MaterialsPanel>[0]["uploadPolicy"],
    });

    expect(screen.getByText(/устаревший upload policy/i)).toBeTruthy();
    expect(screen.getAllByText(/PDF со встроенным текстом всё ещё поддерживаются/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Tesseract OCR сейчас недоступен на сервере/i).length).toBeGreaterThan(0);
  });

  it("warns when the catalog contains only historical versions", () => {
    const materials = [
      buildMaterialSummary({
        id: "historical-1",
        title: "Archived tariff note",
        sourceType: "file",
        originalFileName: "tariff.pdf",
        status: "READY" as const,
        versionState: "SUPERSEDED" as const,
        createdAt: "2026-04-16T10:00:00Z",
        contentLength: 128,
        preview: "Старая версия тарифа.",
      }),
    ];

    renderPanel({ materials });

    expect(screen.getByText(/в каталоге сейчас только исторические версии/i)).toBeTruthy();
    expect(screen.getByText("0 active / 1 total")).toBeTruthy();
    expect(screen.getByText(/исключены из retrieval и readiness/i)).toBeTruthy();
  });

  it("renders reference names instead of machine keys in material metadata cards", () => {
    renderPanel({
      materials: [
        buildMaterialSummary({
          id: "metadata-card",
          title: "Grid policy",
          metadata: {
            workspaceKey: "general",
            projectKey: "north-line",
            documentType: "POLICY",
            documentStatus: "ACTIVE",
            provenance: {
              fieldOrigins: {
                workspaceKey: "MANUAL",
                projectKey: "MANUAL",
                documentType: "MANUAL",
                documentStatus: "MANUAL",
              },
              fieldConfidence: {},
            },
          },
        }),
      ],
    });

    expect(screen.getByText("Grid policy")).toBeTruthy();
    expect(screen.getAllByText("Общая").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Северная линия").length).toBeGreaterThan(0);
    expect(screen.queryByText((_, element) => element?.textContent?.includes("Рабочая область: general") ?? false))
      .toBeNull();
    expect(screen.queryByText((_, element) => element?.textContent?.includes("Проект: north-line") ?? false))
      .toBeNull();
  });

  it("renders reference names in lineage metadata", () => {
    renderPanel({
      selectedLineage: {
        requestedMaterialId: "lineage-version",
        activeMaterialId: "lineage-version",
        versions: [
          {
            ...buildMaterialSummary({
              id: "lineage-version",
              title: "Lineage grid policy",
              metadata: {
                workspaceKey: "general",
                projectKey: "north-line",
                documentType: "POLICY",
                documentStatus: "ACTIVE",
                provenance: {
                  fieldOrigins: {
                    workspaceKey: "MANUAL",
                    projectKey: "MANUAL",
                    documentType: "MANUAL",
                    documentStatus: "MANUAL",
                  },
                  fieldConfidence: {},
                },
              },
            }),
            supersededByMaterialId: null,
            supersedeReason: null,
          },
        ],
      },
    });

    expect(screen.getByText("История версий")).toBeTruthy();
    expect(screen.getAllByText("Общая").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Северная линия").length).toBeGreaterThan(0);
    expect(screen.queryByText((_, element) => element?.textContent?.includes("Рабочая область: general") ?? false))
      .toBeNull();
    expect(screen.queryByText((_, element) => element?.textContent?.includes("Проект: north-line") ?? false))
      .toBeNull();
  });

  it("renders lineage in a dialog instead of an inline catalog block", () => {
    renderPanel({
      selectedLineage: {
        requestedMaterialId: "lineage-version",
        activeMaterialId: "lineage-version",
        versions: [
          {
            ...buildMaterialSummary({
              id: "lineage-version",
              title: "Lineage grid policy",
            }),
            supersededByMaterialId: null,
            supersedeReason: null,
          },
        ],
      },
    });

    const dialog = screen.getByRole("dialog", { name: "История версий материала" });
    expect(within(dialog).getByText("История версий")).toBeTruthy();
    expect(within(dialog).getByText("Lineage grid policy")).toBeTruthy();
    expect(within(dialog).queryByRole("button", { name: "Закрыть историю" })).toBeNull();
    expect(within(dialog).getByRole("button", { name: "Закрыть" })).toBeTruthy();
  });

  it("calls lineage loading from the History button", async () => {
    const user = userEvent.setup();
    const onLoadLineage = vi.fn().mockResolvedValue(undefined);
    renderPanel({
      onLoadLineage,
      materials: [
        buildMaterialSummary({
          id: "active-ready",
          title: "Ready tariff",
          sourceType: "file",
          originalFileName: "ready.pdf",
          status: "READY",
          versionState: "ACTIVE",
        }),
      ],
    });

    await user.click(screen.getByRole("button", { name: "История" }));

    expect(onLoadLineage).toHaveBeenCalledWith("active-ready");
  });

  it("filters the catalog to problematic materials and exposes reindex actions only there", async () => {
    const user = userEvent.setup();
    renderPanel({
      materials: [
        {
          ...buildMaterialSummary({
            id: "active-ready",
            title: "Ready tariff",
            sourceType: "file",
            originalFileName: "ready.pdf",
            status: "READY",
            versionState: "ACTIVE",
            createdAt: "2026-04-16T10:00:00Z",
            contentLength: 128,
            preview: "Готовый материал.",
          }),
        },
        {
          ...buildMaterialSummary({
            id: "failed-active",
            title: "Broken tariff",
            sourceType: "file",
            originalFileName: "broken.pdf",
            status: "FAILED",
            versionState: "ACTIVE",
            createdAt: "2026-04-16T10:00:00Z",
            contentLength: 128,
            preview: "Проблемный материал.",
          }),
        },
      ],
    });

    await user.click(screen.getByRole("button", { name: "Проблемные" }));

    expect(screen.queryByText("Ready tariff")).toBeNull();
    expect(screen.getByText("Broken tariff")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Повторить индекс" })).toBeTruthy();
  });

  it("shows controlled version upload only for active material versions", async () => {
    const user = userEvent.setup();
    renderPanel({
      materials: [
        buildMaterialSummary({
          id: "active-ready",
          title: "Ready tariff",
          sourceType: "file",
          originalFileName: "ready.pdf",
          status: "READY",
          versionState: "ACTIVE",
        }),
        buildMaterialSummary({
          id: "historical-1",
          title: "Old tariff",
          sourceType: "file",
          originalFileName: "old.pdf",
          status: "READY",
          versionState: "SUPERSEDED",
        }),
      ],
    });

    await user.click(screen.getByRole("button", { name: "Все версии" }));

    expect(screen.getByText("Ready tariff")).toBeTruthy();
    expect(screen.getByText("Old tariff")).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "Загрузить новую версию" })).toHaveLength(1);
  });

  it("shows edit actions only for active material versions", async () => {
    const user = userEvent.setup();
    renderPanel({
      materials: [
        buildMaterialSummary({
          id: "active-ready",
          title: "Ready tariff",
          sourceType: "file",
          originalFileName: "ready.pdf",
          status: "READY",
          versionState: "ACTIVE",
        }),
        buildMaterialSummary({
          id: "historical-1",
          title: "Old tariff",
          sourceType: "file",
          originalFileName: "old.pdf",
          status: "READY",
          versionState: "SUPERSEDED",
        }),
      ],
    });

    await user.click(screen.getByRole("button", { name: "Все версии" }));

    expect(screen.getByText("Ready tariff")).toBeTruthy();
    expect(screen.getByText("Old tariff")).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "Редактировать" })).toHaveLength(1);
    const actions = screen.getByTestId("material-actions-active-ready");
    expect(actions.className).toContain("flex-wrap");
    expect(actions.className).toContain("min-w-0");
    expect(actions.className).not.toContain("overflow-x-auto");
  });

  it("opens material edit dialog, loads detail, and submits a new revision", async () => {
    const user = userEvent.setup();
    const onEditMaterial = vi.fn().mockResolvedValue(undefined);
    materialEditorMock.detail = {
      id: "active-ready",
      title: "Ready tariff",
      sourceType: "file",
      originalFileName: "ready.pdf",
      mediaType: "application/pdf",
      content: "Исходный извлечённый текст.",
      status: "READY",
      versionState: "ACTIVE",
      createdAt: "2026-04-16T10:00:00Z",
      metadata: buildMaterialSummary().metadata,
      chunks: [],
    };
    renderPanel({
      onEditMaterial,
      materials: [
        buildMaterialSummary({
          id: "active-ready",
          title: "Ready tariff",
          sourceType: "file",
          originalFileName: "ready.pdf",
          status: "READY",
          versionState: "ACTIVE",
        }),
      ],
    });

    await user.click(screen.getByRole("button", { name: "Редактировать" }));
    const dialog = await screen.findByRole("dialog", { name: "Редактировать материал" });
    await waitFor(() => {
      expect((within(dialog).getByLabelText("Содержимое") as HTMLTextAreaElement).value)
        .toBe("Исходный извлечённый текст.");
    });

    const titleInput = within(dialog).getByLabelText("Название") as HTMLInputElement;
    const contentInput = within(dialog).getByLabelText("Содержимое") as HTMLTextAreaElement;
    await user.clear(titleInput);
    await user.type(titleInput, "Ready tariff edited");
    await user.clear(contentInput);
    await user.type(contentInput, "Новая редакция текста.");
    await user.click(within(dialog).getByRole("button", { name: "Сохранить редакцию" }));

    await waitFor(() => {
      expect(onEditMaterial).toHaveBeenCalledWith("active-ready", {
        title: "Ready tariff edited",
        content: "Новая редакция текста.",
      });
    });
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: "Редактировать материал" })).toBeNull();
    });
  });

  it("keeps the material edit dialog open when saving fails", async () => {
    const user = userEvent.setup();
    const onEditMaterial = vi.fn().mockRejectedValue(new Error("boom"));
    materialEditorMock.detail = {
      id: "active-ready",
      title: "Ready tariff",
      sourceType: "text",
      originalFileName: null,
      content: "Исходный текст.",
      status: "READY",
      versionState: "ACTIVE",
      createdAt: "2026-04-16T10:00:00Z",
      metadata: buildMaterialSummary().metadata,
      chunks: [],
    };
    renderPanel({
      onEditMaterial,
      materials: [
        buildMaterialSummary({
          id: "active-ready",
          title: "Ready tariff",
          status: "READY",
          versionState: "ACTIVE",
        }),
      ],
    });

    await user.click(screen.getByRole("button", { name: "Редактировать" }));
    const dialog = await screen.findByRole("dialog", { name: "Редактировать материал" });
    await waitFor(() => {
      expect((within(dialog).getByLabelText("Содержимое") as HTMLTextAreaElement).value).toBe("Исходный текст.");
    });
    await user.click(within(dialog).getByRole("button", { name: "Сохранить редакцию" }));

    await waitFor(() => {
      expect(within(dialog).getByText("Редакция не сохранена")).toBeTruthy();
    });
    expect(screen.getByRole("dialog", { name: "Редактировать материал" })).toBeTruthy();
  });

  it("submits controlled version upload from the active material card", async () => {
    const user = userEvent.setup();
    const onUploadVersion = vi.fn().mockResolvedValue(undefined);
    const file = new File(["version two"], "version-two.txt", { type: "text/plain" });
    renderPanel({
      onUploadVersion,
      materials: [
        buildMaterialSummary({
          id: "active-ready",
          title: "Ready tariff",
          sourceType: "file",
          originalFileName: "ready.pdf",
          status: "READY",
          versionState: "ACTIVE",
        }),
      ],
    });

    await user.click(screen.getByRole("button", { name: "Загрузить новую версию" }));
    await user.type(screen.getByPlaceholderText("Ready tariff"), "Ready tariff override");
    const fileInput = screen.getByLabelText("Файл новой версии") as HTMLInputElement;
    await user.upload(fileInput, file);
    await waitFor(() => {
      expect((screen.getByRole("button", { name: "Отправить новую версию" }) as HTMLButtonElement).disabled).toBe(false);
    });
    fireEvent.submit(fileInput.closest("form") as HTMLFormElement);

    await waitFor(() => {
      expect(onUploadVersion).toHaveBeenCalledWith("active-ready", {
        file,
        title: "Ready tariff override",
      });
    });
  });

  it("renders backend catalog total separately from the loaded page", () => {
    const onLoadMore = vi.fn();
    renderPanel({
      materials: [
        buildMaterialSummary({
          id: "page-1",
          title: "Loaded material",
        }),
      ],
      materialTotal: 101,
      hasMoreMaterials: true,
      onLoadMore,
    });

    expect(screen.getByText("Показано 1 из 101")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Показать ещё" })).toBeTruthy();
  });

  it("renders lineage details with the stored supersede reason", () => {
    renderPanel({
      selectedLineage: {
        requestedMaterialId: "historical-1",
        activeMaterialId: "active-2",
        versions: [
          buildMaterialSummary({
            id: "active-2",
            title: "Current tariff",
            sourceType: "file",
            originalFileName: "tariff-v2.pdf",
            status: "READY",
            versionState: "ACTIVE",
            createdAt: "2026-04-16T10:00:00Z",
            updatedAt: "2026-04-16T10:10:00Z",
            indexingAttempts: 1,
            contentLength: 200,
            preview: "Актуальная версия.",
          }),
          {
            ...buildMaterialSummary({
            id: "historical-1",
            title: "Old tariff",
            sourceType: "file",
            originalFileName: "tariff-v1.pdf",
            status: "READY",
            versionState: "SUPERSEDED",
            createdAt: "2026-04-15T10:00:00Z",
            updatedAt: "2026-04-16T10:00:00Z",
            indexingAttempts: 1,
            contentLength: 190,
            preview: "Старая версия.",
            }),
            supersedeReason: "material.superseded_by_new_active_version",
          },
        ],
      },
    });

    expect(screen.getByText("История версий")).toBeTruthy();
    expect(screen.getByText(/была вытеснена более новой активной загрузкой/i)).toBeTruthy();
    expect(screen.getByText(/Активная версия: active-2/i)).toBeTruthy();
  });

  it("requires document type and submits canonical metadata when metadata rollout is enabled", async () => {
    const user = userEvent.setup();
    const onCreateText = vi.fn().mockResolvedValue(undefined);

    renderPanel({ metadataV1Enabled: true, onCreateText });

    await user.type(screen.getByLabelText("Содержимое"), "Новый материал");
    await waitFor(() => {
      expect((screen.getByRole("button", { name: "Сохранить текст" }) as HTMLButtonElement).disabled).toBe(false);
    });
    await user.click(screen.getByRole("button", { name: "Сохранить текст" }));

    await waitFor(() => {
      expect(screen.getAllByText("Выберите тип документа.").length).toBeGreaterThan(1);
    });
    expect(onCreateText).not.toHaveBeenCalled();

    await chooseSelectOption(user, "Тип документа", "Политика");
    await user.click(screen.getByRole("button", { name: "Сохранить текст" }));

    await waitFor(() => {
      expect(onCreateText).toHaveBeenCalled();
    });
    const payload = onCreateText.mock.calls[0][0];
    expect(payload.title).toBe("");
    expect(payload.content).toBe("Новый материал");
    expect(payload.metadata).toEqual({
      workspaceKey: "general",
      documentType: "POLICY",
      documentStatus: "ACTIVE",
      languageCode: null,
    });
  });

  it("allows text submit without metadata when metadata rollout is disabled", async () => {
    const user = userEvent.setup();
    const onCreateText = vi.fn();

    renderPanel({ metadataV1Enabled: false, onCreateText });

    await user.type(screen.getByLabelText("Содержимое"), "Новый материал");
    await user.click(screen.getByRole("button", { name: "Сохранить текст" }));

    expect(onCreateText).toHaveBeenCalledWith({
      title: "",
      content: "Новый материал",
    });
    expect(screen.queryByText("Выберите тип документа.")).toBeNull();
  });

  it("supports per-file title override while sharing canonical metadata in batch upload", async () => {
    const user = userEvent.setup();
    const onUpload = vi.fn().mockResolvedValue(undefined);
    renderPanel({ metadataV1Enabled: true, onUpload });

    await chooseSelectOption(user, "Тип документа", "Договор", 1);

    const fileInput = screen.getByLabelText("Файлы") as HTMLInputElement;
    const firstFile = new File(["first"], "first.txt", { type: "text/plain" });
    const secondFile = new File(["second"], "second.txt", { type: "text/plain" });

    await user.upload(fileInput, [firstFile, secondFile]);
    await user.click(screen.getAllByRole("button", { name: "Настроить название" })[0]);
    await user.type(
      screen.getByPlaceholderText("Оставь пустым, чтобы backend использовал имя файла"),
      "Первый override",
    );

    fireEvent.submit(fileInput.closest("form") as HTMLFormElement);

    await waitFor(() => {
      expect(onUpload).toHaveBeenCalledWith({
        items: [
          expect.objectContaining({
            file: firstFile,
            title: "Первый override",
            metadata: {
              workspaceKey: "general",
              documentType: "CONTRACT",
              documentStatus: "ACTIVE",
              languageCode: null,
            },
          }),
          expect.objectContaining({
            file: secondFile,
            metadata: {
              workspaceKey: "general",
              documentType: "CONTRACT",
              documentStatus: "ACTIVE",
              languageCode: null,
            },
          }),
        ],
      });
    });
  });

  it("renders effective metadata with provenance badges", () => {
    renderPanel({
      materials: [
        buildMaterialSummary({
          id: "material-provenance",
          title: "Provenance sample",
	          metadata: {
	            documentType: "CONTRACT",
	            documentStatus: "ACTIVE",
            workspaceKey: "general",
            projectKey: "north-upgrade",
            languageCode: "RU",
            manualTags: ["energy", "grid"],
            autoTags: ["auto-grid"],
            effectiveTags: ["energy", "grid", "auto-grid"],
	            knowledgeDocumentClass: "contracts",
	            documentDate: "2026-04-15",
            documentNumber: "KZ-2026-0415-ENERGY",
            author: "Dana Sarsen",
            department: "Grid operations",
            versionLabel: "v2",
            language: "ru",
            tags: ["energy", "grid", "auto-grid"],
            sourceTrust: "UNKNOWN",
            project: "North Upgrade",
            counterparty: null,
            businessStatus: null,
            periodStart: null,
            periodEnd: null,
            provenance: {
              fieldOrigins: {
                documentType: "MANUAL",
                workspaceKey: "MANUAL",
                projectKey: "MANUAL",
                languageCode: "INFERRED",
                manualTags: "MANUAL",
                autoTags: "INFERRED",
                effectiveTags: "MANUAL",
                documentDate: "INFERRED",
                documentNumber: "INFERRED",
                author: "MANUAL",
                department: "INFERRED",
                versionLabel: "INFERRED",
                language: "INFERRED",
                tags: "INFERRED",
                sourceTrust: "DEFAULT",
                project: "MANUAL",
              },
              fieldConfidence: {
                documentDate: 0.75,
                documentNumber: 0.75,
                department: 0.9,
              },
            },
          },
        }),
      ],
    });

    expect(
      screen.getAllByText((_, element) => element?.textContent?.includes("Тип документа: Договор") ?? false).length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText((_, element) => element?.textContent?.includes("Номер документа: KZ-2026-0415-ENERGY") ?? false).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText((_, element) => element?.textContent?.includes("Рабочая область: Общая") ?? false).length)
      .toBeGreaterThan(0);
    expect(screen.getAllByText((_, element) => element?.textContent?.includes("Статус документа: Действует") ?? false).length)
      .toBeGreaterThan(0);
    expect(screen.getAllByText((_, element) => element?.textContent?.includes("Ручные теги: energy, grid") ?? false).length)
      .toBeGreaterThan(0);
    expect(screen.getAllByText((_, element) => element?.textContent?.includes("Авто-теги: auto-grid") ?? false).length)
      .toBeGreaterThan(0);
    expect(screen.getAllByText((_, element) => element?.textContent?.includes("Теги: energy, grid, auto-grid") ?? false).length)
      .toBeGreaterThan(0);
    expect(screen.getAllByText("вручную").length).toBeGreaterThan(0);
    expect(screen.getAllByText("авто").length).toBeGreaterThan(0);
    expect(screen.getAllByText("по умолчанию").length).toBeGreaterThan(0);
    expect(screen.queryByText(/Класс знаний:/i)).toBeNull();
    expect(screen.queryByText(/Автор:/i)).toBeNull();
    expect(screen.queryByText(/Доверие:/i)).toBeNull();
  });
});
