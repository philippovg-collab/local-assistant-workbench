import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, apiClient } from "../api/client";
import type { MaterialUploadPolicy } from "../types";
import { useMaterials } from "./useMaterials";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      createTextMaterial: vi.fn(),
      deleteMaterial: vi.fn(),
      fetchMaterialUploadPolicy: vi.fn(),
      fetchMaterials: vi.fn(),
      uploadMaterial: vi.fn(),
    },
  };
});

function swallow(promise: Promise<unknown>) {
  void promise.catch(() => undefined);
}

function buildPolicy(overrides: Partial<MaterialUploadPolicy> = {}) {
  return {
    maxUploadBytes: 1024,
    acceptedExtensions: ["txt"],
    acceptedMimeHints: ["text/plain"],
    richDocumentSupport: true,
    pdf: {
      enabled: false,
      scannedPdfSupport: false,
      mode: "embedded_text_only" as const,
      ocrLanguages: ["kaz", "rus", "eng"],
      ocrMaxPages: 12,
    },
    ...overrides,
  };
}

function MaterialsHookHarness() {
  const materials = useMaterials();

  return (
    <section>
      <button
        type="button"
        onClick={() =>
          swallow(materials.createTextMaterial({
            title: "",
            content: "  ",
          }))
        }
      >
        create-empty-text
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            title: "",
            file: new File(["12345"], "too-big.txt", { type: "text/plain" }),
          }))
        }
      >
        upload-oversized
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            title: "",
            file: new File(["hello"], "unsupported.png", { type: "image/png" }),
          }))
        }
      >
        upload-unsupported
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            title: "",
            file: new File(["hello"], "scan.pdf", { type: "application/pdf" }),
          }))
        }
      >
        upload-pdf
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            title: "",
            file: new File(["hello"], "pricing.txt", { type: "text/plain" }),
          }))
        }
      >
        upload-supported
      </button>

      <output data-testid="action-error">{materials.actionError ?? ""}</output>
      <output data-testid="message">{materials.message ?? ""}</output>
      <output data-testid="policy-warning">{materials.policyWarning ?? ""}</output>
      <output data-testid="policy-mode">{materials.uploadPolicy?.pdf.mode ?? ""}</output>
      <output data-testid="policy-enabled">{String(materials.uploadPolicy?.pdf.enabled ?? false)}</output>
    </section>
  );
}

describe("useMaterials", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("blocks oversized uploads before sending a network request", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy({
      maxUploadBytes: 4,
    }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
      expect(apiClient.fetchMaterials).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-oversized"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("Файл превышает лимит");
    });

    expect(apiClient.uploadMaterial).not.toHaveBeenCalled();
  });

  it("blocks unsupported formats before sending a network request", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-unsupported"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain(".txt");
    });

    expect(apiClient.uploadMaterial).not.toHaveBeenCalled();
  });

  it("translates known extraction errors into a friendly upload message", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.uploadMaterial).mockRejectedValue(
      new ApiClientError("Unable to extract text from the uploaded file", {
        code: "material.extraction_failed",
        status: 400,
      }),
    );

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-supported"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("Не удалось извлечь текст из файла");
    });
  });

  it("translates known validation errors for text materials", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.createTextMaterial).mockRejectedValue(
      new ApiClientError("Material text is empty", {
        code: "material.empty_text",
        status: 400,
      }),
    );

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterials).toHaveBeenCalled();
    });

    await user.click(screen.getByText("create-empty-text"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("Добавь содержимое материала");
    });
  });

  it("renders requestId for unexpected backend upload failures", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.uploadMaterial).mockRejectedValue(
      new ApiClientError("Unexpected server error", {
        code: "internal.unexpected_error",
        requestId: "req-123",
        status: 500,
      }),
    );

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-supported"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("req-123");
    });
  });

  it("uses a fallback policy and still uploads supported files when policy prefetch fails", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockRejectedValue(new Error("boom"));
    vi.mocked(apiClient.uploadMaterial).mockResolvedValue({
      id: "mat-1",
      title: "pricing.txt",
      sourceType: "file",
      originalFileName: "pricing.txt",
      extractable: true,
      createdAt: "2026-04-16T00:00:00Z",
      contentLength: 5,
      preview: "hello",
    });

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(screen.getByTestId("policy-warning").textContent).toContain("Не удалось подтвердить capability backend");
    });

    expect(screen.getByTestId("policy-mode").textContent).toBe("embedded_text_only");
    expect(screen.getByTestId("policy-enabled").textContent).toBe("true");

    await user.click(screen.getByText("upload-supported"));

    await waitFor(() => {
      expect(apiClient.uploadMaterial).toHaveBeenCalled();
    });
  });

  it("normalizes legacy upload policy payloads without pdf metadata", async () => {
    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue({
      maxUploadBytes: 1024,
      acceptedExtensions: ["txt", "pdf"],
      acceptedMimeHints: ["text/plain", "application/pdf"],
      richDocumentSupport: true,
    } as unknown as MaterialUploadPolicy);

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(screen.getByTestId("policy-mode").textContent).toBe("embedded_text_only");
    });

    expect(screen.getByTestId("policy-enabled").textContent).toBe("true");
    expect(screen.getByTestId("policy-warning").textContent).toContain("устаревший upload policy");
  });

  it("does not block pdf uploads when policy reports embedded_text_only", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue([]);
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy({
      acceptedExtensions: ["txt", "pdf"],
      acceptedMimeHints: ["text/plain", "application/pdf"],
      pdf: {
        enabled: true,
        scannedPdfSupport: false,
        mode: "embedded_text_only",
        ocrReasonCode: "material.ocr_unavailable",
        ocrReasonMessage: "Tesseract OCR binary is unavailable at 'tesseract'.",
        ocrLanguages: ["kaz", "rus", "eng"],
        ocrMaxPages: 12,
      },
    }));
    vi.mocked(apiClient.uploadMaterial).mockResolvedValue({
      id: "mat-1",
      title: "scan.pdf",
      sourceType: "file",
      originalFileName: "scan.pdf",
      extractable: true,
      createdAt: "2026-04-16T00:00:00Z",
      contentLength: 5,
      preview: "hello",
    });

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-pdf"));

    await waitFor(() => {
      expect(apiClient.uploadMaterial).toHaveBeenCalled();
    });
  });
});
