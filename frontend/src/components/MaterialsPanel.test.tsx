import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MaterialsPanel } from "./MaterialsPanel";
import { buildMaterialSummary } from "../testBuilders";
import { buildRagReadinessPresentation, deriveRagReadiness } from "../utils/readiness";
import type { HealthResponse } from "../types";

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
      error={null}
	      isLoading={false}
	      isLoadingMore={false}
	      lineageError={null}
	      loadingLineageMaterialId={null}
	      hasMoreMaterials={false}
	      materials={materials}
	      materialTotal={materials.length}
	      metadataV1Enabled={false}
	      message={null}
	      onClearLineage={vi.fn()}
	      onCreateText={vi.fn()}
	      onDelete={vi.fn()}
	      onLoadLineage={vi.fn()}
	      onLoadMore={vi.fn()}
	      onReindex={vi.fn()}
      onUpload={vi.fn()}
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
      reindexingMaterialId={null}
      selectedLineage={null}
      uploadPolicy={null}
      {...overrides}
    />,
  );
};

describe("MaterialsPanel", () => {
  afterEach(() => {
    cleanup();
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

  it("allows text submit with empty metadata so backend can auto-fill fields", async () => {
    const user = userEvent.setup();
    const onCreateText = vi.fn().mockResolvedValue(undefined);

    renderPanel({ metadataV1Enabled: true, onCreateText });

    await user.type(screen.getByLabelText("Содержимое"), "Новый материал");
    await user.click(screen.getByRole("button", { name: "Сохранить текст" }));

    await waitFor(() => {
      expect(onCreateText).toHaveBeenCalled();
    });
    const payload = onCreateText.mock.calls[0][0];
    expect(payload.title).toBe("");
    expect(payload.content).toBe("Новый материал");
    expect(payload.metadata).toBeTruthy();
    expect(Object.values(payload.metadata).some((value) => value === "")).toBe(false);
    expect(screen.queryByText(/выбери тип документа/i)).toBeNull();
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
    expect(screen.queryByText(/выбери тип документа/i)).toBeNull();
  });

  it("supports per-file title and metadata overrides in batch upload", async () => {
    const user = userEvent.setup();
    const onUpload = vi.fn().mockResolvedValue(undefined);
    renderPanel({ metadataV1Enabled: true, onUpload });

    const fileInput = screen.getByLabelText("Файлы") as HTMLInputElement;
    const firstFile = new File(["first"], "first.txt", { type: "text/plain" });
    const secondFile = new File(["second"], "second.txt", { type: "text/plain" });

    await user.upload(fileInput, [firstFile, secondFile]);
    await user.click(screen.getAllByRole("button", { name: "Настроить атрибуты" })[0]);
    await user.type(
      screen.getByPlaceholderText("Оставь пустым, чтобы backend использовал имя файла"),
      "Первый override",
    );
    await user.type(
      screen.getByPlaceholderText("Оставь пустым, чтобы использовать общие теги"),
      "contract, premium",
    );

    fireEvent.submit(fileInput.closest("form") as HTMLFormElement);

    await waitFor(() => {
      expect(onUpload).toHaveBeenCalledWith({
        items: [
          expect.objectContaining({
            file: firstFile,
            title: "Первый override",
            metadata: expect.objectContaining({
              tags: ["contract", "premium"],
            }),
          }),
          expect.objectContaining({
            file: secondFile,
            metadata: expect.any(Object),
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
	            knowledgeDocumentClass: "contracts",
	            documentDate: "2026-04-15",
            documentNumber: "KZ-2026-0415-ENERGY",
            author: "Dana Sarsen",
            department: "Grid operations",
            versionLabel: "v2",
            language: "ru",
            tags: ["energy", "grid"],
            sourceTrust: "UNKNOWN",
            project: "North Upgrade",
            counterparty: null,
            businessStatus: null,
            periodStart: null,
            periodEnd: null,
            provenance: {
              fieldOrigins: {
                documentType: "MANUAL",
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
      screen.getAllByText((_, element) => element?.textContent?.includes("Тип: Contract") ?? false).length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText((_, element) => element?.textContent?.includes("Номер: KZ-2026-0415-ENERGY") ?? false).length,
    ).toBeGreaterThan(0);
    expect(screen.getByText(/KZ-2026-0415-ENERGY/i)).toBeTruthy();
    expect(screen.getAllByText("manual").length).toBeGreaterThan(0);
    expect(screen.getAllByText("auto").length).toBeGreaterThan(0);
    expect(screen.getAllByText("default").length).toBeGreaterThan(0);
    expect(screen.queryByText(/Корпус:/i)).toBeNull();
    expect(screen.queryByText(/Workspace:/i)).toBeNull();
  });
});
