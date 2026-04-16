import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MaterialsPanel } from "./MaterialsPanel";

describe("MaterialsPanel", () => {
  afterEach(() => {
    cleanup();
  });

  it("includes pdf in the upload accept list and mentions OCR fallback", () => {
    render(
      <MaterialsPanel
        actionError={null}
        deletingMaterialId={null}
        error={null}
        isLoading={false}
        materials={[]}
        message={null}
        onCreateText={vi.fn()}
        onDelete={vi.fn()}
        onUpload={vi.fn()}
        policyWarning={null}
        uploadPolicy={{
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
        }}
      />,
    );

    const fileInput = screen.getByLabelText("Файл") as HTMLInputElement;
    const uploadButton = screen.getByRole("button", { name: "Загрузить файл" });
    expect(fileInput.accept).toContain(".pdf");
    expect(uploadButton).toBeTruthy();
    expect(screen.getByText(/scanned PDF поддерживается через OCR/i)).toBeTruthy();
  });

  it("renders partial PDF support warning with the OCR reason", () => {
    render(
      <MaterialsPanel
        actionError={null}
        deletingMaterialId={null}
        error={null}
        isLoading={false}
        materials={[]}
        message={null}
        onCreateText={vi.fn()}
        onDelete={vi.fn()}
        onUpload={vi.fn()}
        policyWarning={null}
        uploadPolicy={{
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
        }}
      />,
    );

    expect(screen.getByText(/PDF со встроенным текстом всё ещё поддерживаются/i)).toBeTruthy();
    expect(screen.getByText(/Tesseract OCR сейчас недоступен на сервере/i)).toBeTruthy();
    expect(screen.getByText(/Scanned PDF сейчас не поддерживаются: Tesseract OCR сейчас недоступен/i)).toBeTruthy();
  });

  it("renders a policy warning without hiding the upload form", () => {
    render(
      <MaterialsPanel
        actionError={null}
        deletingMaterialId={null}
        error={null}
        isLoading={false}
        materials={[]}
        message={null}
        onCreateText={vi.fn()}
        onDelete={vi.fn()}
        onUpload={vi.fn()}
        policyWarning="Не удалось подтвердить capability backend; возможны ограничения при загрузке PDF."
        uploadPolicy={null}
      />,
    );

    expect(screen.getByText(/Не удалось подтвердить capability backend/i)).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "Загрузить файл" }).length).toBeGreaterThan(0);
  });

  it("uses OCR languages from the backend policy in warnings", () => {
    render(
      <MaterialsPanel
        actionError={null}
        deletingMaterialId={null}
        error={null}
        isLoading={false}
        materials={[]}
        message={null}
        onCreateText={vi.fn()}
        onDelete={vi.fn()}
        onUpload={vi.fn()}
        policyWarning={null}
        uploadPolicy={{
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
        }}
      />,
    );

    expect(screen.getByText(/не хватает языковых данных OCR для deu и eng/i)).toBeTruthy();
  });

  it("gracefully handles a legacy policy payload without pdf metadata", () => {
    render(
      <MaterialsPanel
        actionError={null}
        deletingMaterialId={null}
        error={null}
        isLoading={false}
        materials={[]}
        message={null}
        onCreateText={vi.fn()}
        onDelete={vi.fn()}
        onUpload={vi.fn()}
        policyWarning={null}
        uploadPolicy={{
          maxUploadBytes: 2_000_000,
          acceptedExtensions: ["txt", "pdf"],
          acceptedMimeHints: ["text/plain", "application/pdf"],
          richDocumentSupport: true,
        } as unknown as Parameters<typeof MaterialsPanel>[0]["uploadPolicy"]}
      />,
    );

    expect(screen.getByText(/устаревший upload policy/i)).toBeTruthy();
    expect(screen.getAllByText(/PDF со встроенным текстом всё ещё поддерживаются/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Tesseract OCR сейчас недоступен на сервере/i).length).toBeGreaterThan(0);
  });
});
