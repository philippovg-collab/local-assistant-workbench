import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MaterialsPanel } from "./MaterialsPanel";
import { buildRagReadinessPresentation, deriveRagReadiness } from "../utils/readiness";

const renderPanel = (
  overrides: Partial<Parameters<typeof MaterialsPanel>[0]> = {},
) => {
  const materials = overrides.materials ?? [];

  return render(
    <MaterialsPanel
      actionError={null}
      deletingMaterialId={null}
      error={null}
      isLoading={false}
      lineageError={null}
      loadingLineageMaterialId={null}
      materials={materials}
      message={null}
      onClearLineage={vi.fn()}
      onCreateText={vi.fn()}
      onDelete={vi.fn()}
      onLoadLineage={vi.fn()}
      onReindex={vi.fn()}
      onUpload={vi.fn()}
      policyWarning={null}
      ragPresentation={
        overrides.ragPresentation ?? buildRagReadinessPresentation({
          health: null,
          ragReadiness: deriveRagReadiness(null, materials),
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
        maxUploadBytes: 2_000_000,
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

    const fileInput = screen.getByLabelText("Файл") as HTMLInputElement;
    const uploadButton = screen.getByRole("button", { name: "Загрузить файл" });
    expect(fileInput.accept).toContain(".pdf");
    expect(uploadButton).toBeTruthy();
    expect(screen.getByText(/scanned PDF поддерживается через OCR/i)).toBeTruthy();
  });

  it("renders partial PDF support warning with the OCR reason", () => {
    renderPanel({
      uploadPolicy: {
        maxUploadBytes: 2_000_000,
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
        maxUploadBytes: 2_000_000,
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
        maxUploadBytes: 2_000_000,
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
      {
        id: "historical-1",
        title: "Archived tariff note",
        sourceType: "file",
        originalFileName: "tariff.pdf",
        status: "READY" as const,
        versionState: "SUPERSEDED" as const,
        createdAt: "2026-04-16T10:00:00Z",
        contentLength: 128,
        preview: "Старая версия тарифа.",
      },
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
          id: "active-ready",
          title: "Ready tariff",
          sourceType: "file",
          originalFileName: "ready.pdf",
          status: "READY",
          versionState: "ACTIVE",
          createdAt: "2026-04-16T10:00:00Z",
          contentLength: 128,
          preview: "Готовый материал.",
        },
        {
          id: "failed-active",
          title: "Broken tariff",
          sourceType: "file",
          originalFileName: "broken.pdf",
          status: "FAILED",
          versionState: "ACTIVE",
          createdAt: "2026-04-16T10:00:00Z",
          contentLength: 128,
          preview: "Проблемный материал.",
        },
      ],
    });

    await user.click(screen.getByRole("button", { name: "Проблемные" }));

    expect(screen.queryByText("Ready tariff")).toBeNull();
    expect(screen.getByText("Broken tariff")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Повторить индекс" })).toBeTruthy();
  });

  it("renders lineage details with the stored supersede reason", () => {
    renderPanel({
      selectedLineage: {
        requestedMaterialId: "historical-1",
        activeMaterialId: "active-2",
        versions: [
          {
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
          },
          {
            id: "historical-1",
            title: "Old tariff",
            sourceType: "file",
            originalFileName: "tariff-v1.pdf",
            status: "READY",
            versionState: "SUPERSEDED",
            createdAt: "2026-04-15T10:00:00Z",
            updatedAt: "2026-04-16T10:00:00Z",
            indexingAttempts: 1,
            supersedeReason: "material.superseded_by_new_active_version",
            contentLength: 190,
            preview: "Старая версия.",
          },
        ],
      },
    });

    expect(screen.getByText("История версий")).toBeTruthy();
    expect(screen.getByText(/была вытеснена более новой активной загрузкой/i)).toBeTruthy();
    expect(screen.getByText(/Активная версия: active-2/i)).toBeTruthy();
  });
});
