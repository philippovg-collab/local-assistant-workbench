import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, apiClient } from "../api/client";
import type { MaterialMetadataInput, MaterialUploadPolicy } from "../types";
import { buildMaterialListResponse, buildMaterialSummary } from "../testBuilders";
import { useMaterials } from "./useMaterials";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      createTextMaterial: vi.fn(),
      deleteMaterial: vi.fn(),
      fetchMaterialLineage: vi.fn(),
      fetchMaterialUploadPolicy: vi.fn(),
      fetchMaterials: vi.fn(),
      updateMaterial: vi.fn(),
      uploadMaterial: vi.fn(),
      uploadMaterialVersion: vi.fn(),
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

const baseMetadata: MaterialMetadataInput = {
  workspaceKey: "general",
  documentType: "OTHER",
  documentStatus: "ACTIVE",
};

const policyMetadata: MaterialMetadataInput = {
  workspaceKey: "general",
  documentType: "POLICY",
  documentStatus: "ACTIVE",
  manualTags: ["policy", "grid"],
};

function MaterialsHookHarness({ enabled = true }: { enabled?: boolean }) {
  const materials = useMaterials({ enabled });

  return (
    <section>
      <button
        type="button"
        onClick={() =>
          swallow(materials.createTextMaterial({
            title: "",
            content: "  ",
            metadata: {
              ...baseMetadata,
            },
          }))
        }
      >
        create-empty-text
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            items: [{
              file: new File(["12345"], "too-big.txt", { type: "text/plain" }),
              metadata: {
                ...baseMetadata,
              },
            }],
          }))
        }
      >
        upload-oversized
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            items: [{
              file: new File(["hello"], "unsupported.png", { type: "image/png" }),
              metadata: {
                ...baseMetadata,
              },
            }],
          }))
        }
      >
        upload-unsupported
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            items: [{
              file: new File(["hello"], "scan.pdf", { type: "application/pdf" }),
              metadata: {
                ...baseMetadata,
              },
            }],
          }))
        }
      >
        upload-pdf
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            items: [{
              file: new File(["hello"], "pricing.txt", { type: "text/plain" }),
              metadata: {
                ...baseMetadata,
              },
            }],
          }))
        }
      >
        upload-supported
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            items: [{
              title: "Custom material",
              file: new File(["hello"], "single-title.txt", { type: "text/plain" }),
              metadata: {
                ...baseMetadata,
              },
            }],
          }))
        }
      >
        upload-single-title
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            items: [
              {
                file: new File(["alpha"], "first.txt", { type: "text/plain" }),
                metadata: {
                  ...policyMetadata,
                },
              },
              {
                file: new File(["beta"], "second.txt", { type: "text/plain" }),
                metadata: {
                  ...policyMetadata,
                },
              },
            ],
          }))
        }
      >
        upload-two-supported
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterial({
            items: [
              {
                file: new File(["alpha"], "first-ok.txt", { type: "text/plain" }),
                metadata: {
                  ...baseMetadata,
                },
              },
              {
                file: new File(["beta"], "second-fails.txt", { type: "text/plain" }),
                metadata: {
                  ...baseMetadata,
                },
              },
            ],
          }))
        }
      >
        upload-partial-failure
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.createTextMaterial({
            title: "Metadata note",
            content: "Материал с metadata.",
            metadata: {
              ...policyMetadata,
            },
          }))
        }
      >
        create-with-metadata
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.uploadMaterialVersion("material-1", {
            file: new File(["version"], "version.txt", { type: "text/plain" }),
            title: "Version title",
          }))
        }
      >
        upload-version
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(materials.editMaterial("material-1", {
            title: "Edited material",
            content: "Edited content",
            metadata: {
              ...policyMetadata,
            },
          }))
        }
      >
        edit-material
      </button>

      <output data-testid="action-error">{materials.actionError ?? ""}</output>
      <output data-testid="message">{materials.message ?? ""}</output>
      <output data-testid="policy-warning">{materials.policyWarning ?? ""}</output>
      <output data-testid="policy-mode">{materials.uploadPolicy?.pdf.mode ?? ""}</output>
      <output data-testid="policy-enabled">{String(materials.uploadPolicy?.pdf.enabled ?? false)}</output>
      <output data-testid="material-count">{materials.materials.length}</output>
      <output data-testid="is-loading">{String(materials.isLoading)}</output>
    </section>
  );
}

describe("useMaterials", () => {
  beforeEach(() => {
    vi.useRealTimers();
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "visible",
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not prefetch while disabled and keeps loaded catalog across disabled toggles", async () => {
    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse([
      buildMaterialSummary({
        id: "material-1",
        title: "Pricing note",
      }),
    ]));
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());

    const { rerender } = render(<MaterialsHookHarness enabled={false} />);

    expect(screen.getByTestId("is-loading").textContent).toBe("false");
    expect(apiClient.fetchMaterialUploadPolicy).not.toHaveBeenCalled();
    expect(apiClient.fetchMaterials).not.toHaveBeenCalled();

    rerender(<MaterialsHookHarness enabled />);

    await waitFor(() => {
      expect(screen.getByTestId("material-count").textContent).toBe("1");
    });
    expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalledTimes(1);
    expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(1);

    rerender(<MaterialsHookHarness enabled={false} />);
    expect(screen.getByTestId("material-count").textContent).toBe("1");

    rerender(<MaterialsHookHarness enabled />);
    expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalledTimes(1);
    expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(1);
  });

  it("blocks oversized uploads before sending a network request", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
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
      expect(screen.getByTestId("action-error").textContent).toContain("too-big.txt");
    });

    expect(apiClient.uploadMaterial).not.toHaveBeenCalled();
  });

  it("blocks unsupported formats before sending a network request", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-unsupported"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain(".txt");
      expect(screen.getByTestId("action-error").textContent).toContain("unsupported.png");
    });

    expect(apiClient.uploadMaterial).not.toHaveBeenCalled();
  });

  it("translates known extraction errors into a friendly upload message", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
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

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
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

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
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

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockRejectedValue(new Error("boom"));
    vi.mocked(apiClient.uploadMaterial).mockResolvedValue(buildMaterialSummary({
      id: "mat-1",
      title: "pricing.txt",
      sourceType: "file",
      originalFileName: "pricing.txt",
      status: "PENDING",
      createdAt: "2026-04-16T00:00:00Z",
      contentLength: 5,
      preview: "hello",
    }));

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

  it("uploads a controlled material version and reloads the catalog", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.uploadMaterialVersion).mockResolvedValue(buildMaterialSummary({
      id: "mat-v2",
      title: "Version title",
      sourceType: "file",
      originalFileName: "version.txt",
      status: "PENDING",
      createdAt: "2026-04-21T00:00:00Z",
      contentLength: 7,
      preview: "version",
    }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterials).toHaveBeenCalled();
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-version"));

    await waitFor(() => {
      expect(apiClient.uploadMaterialVersion).toHaveBeenCalledWith(
        "material-1",
        expect.objectContaining({ title: "Version title" }),
        null,
      );
      expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(2);
    });
    expect(screen.getByTestId("message").textContent).toContain("Файл новой версии");
  });

  it("edits a material as a new version and reloads the catalog", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.updateMaterial).mockResolvedValue(buildMaterialSummary({
      id: "mat-edited",
      title: "Edited material",
      sourceType: "text",
      originalFileName: null,
      status: "PENDING",
      createdAt: "2026-04-21T00:00:00Z",
      contentLength: 14,
      preview: "Edited content",
    }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterials).toHaveBeenCalled();
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("edit-material"));

    await waitFor(() => {
      expect(apiClient.updateMaterial).toHaveBeenCalledWith("material-1", {
        title: "Edited material",
        content: "Edited content",
        metadata: {
          ...policyMetadata,
        },
      }, null);
      expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(2);
    });
    expect(screen.getByTestId("message").textContent).toContain("Редакция сохранена как новая версия");
  });

  it("shows a stale backend message when material edit API is missing", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.updateMaterial).mockRejectedValue(
      new ApiClientError("Method not supported", {
        code: "request.method_not_supported",
        status: 405,
      }),
    );

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterials).toHaveBeenCalled();
    });

    await user.click(screen.getByText("edit-material"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("Backend не поддерживает этот API");
      expect(screen.getByTestId("action-error").textContent).toContain("перезапусти сервер");
    });
  });

  it("normalizes legacy upload policy payloads without pdf metadata", async () => {
    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
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

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
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
    vi.mocked(apiClient.uploadMaterial).mockResolvedValue(buildMaterialSummary({
      id: "mat-1",
      title: "scan.pdf",
      sourceType: "file",
      originalFileName: "scan.pdf",
      status: "READY",
      createdAt: "2026-04-16T00:00:00Z",
      contentLength: 5,
      preview: "hello",
    }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-pdf"));

    await waitFor(() => {
      expect(apiClient.uploadMaterial).toHaveBeenCalled();
    });
  });

  it("uses the entered title for single-file uploads", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.uploadMaterial).mockResolvedValue(buildMaterialSummary({
      id: "mat-single-title",
      title: "Custom material",
      sourceType: "file",
      originalFileName: "single-title.txt",
      status: "PENDING",
      createdAt: "2026-04-16T00:00:00Z",
      contentLength: 5,
      preview: "hello",
    }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-single-title"));

    await waitFor(() => {
      expect(apiClient.uploadMaterial).toHaveBeenCalled();
    });

    const uploadInput = vi.mocked(apiClient.uploadMaterial).mock.calls[0][0];
    expect(uploadInput.title).toBe("Custom material");
    expect(uploadInput.file.name).toBe("single-title.txt");
  });

  it("uploads multiple supported files sequentially with filename titles and shared metadata", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.uploadMaterial)
      .mockResolvedValueOnce(buildMaterialSummary({
        id: "mat-first",
        title: "first.txt",
        sourceType: "file",
        originalFileName: "first.txt",
        status: "PENDING",
        createdAt: "2026-04-16T00:00:00Z",
        contentLength: 5,
        preview: "alpha",
      }))
      .mockResolvedValueOnce(buildMaterialSummary({
        id: "mat-second",
        title: "second.txt",
        sourceType: "file",
        originalFileName: "second.txt",
        status: "PENDING",
        createdAt: "2026-04-16T00:00:00Z",
        contentLength: 4,
        preview: "beta",
      }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-two-supported"));

    await waitFor(() => {
      expect(apiClient.uploadMaterial).toHaveBeenCalledTimes(2);
    });

    const [firstUpload, secondUpload] = vi.mocked(apiClient.uploadMaterial).mock.calls.map(([input]) => input);
    expect(firstUpload.title).toBe("");
    expect(firstUpload.file.name).toBe("first.txt");
    expect(firstUpload.metadata).toEqual(policyMetadata);
    expect(secondUpload.title).toBe("");
    expect(secondUpload.file.name).toBe("second.txt");
    expect(secondUpload.metadata).toEqual(firstUpload.metadata);
    expect(screen.getByTestId("message").textContent).toContain("2 файла приняты");
  });

  it("stops a multi-file upload at the first failed file and refreshes accepted materials", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.uploadMaterial)
      .mockResolvedValueOnce(buildMaterialSummary({
        id: "mat-first-ok",
        title: "first-ok.txt",
        sourceType: "file",
        originalFileName: "first-ok.txt",
        status: "PENDING",
        createdAt: "2026-04-16T00:00:00Z",
        contentLength: 5,
        preview: "alpha",
      }))
      .mockRejectedValueOnce(new ApiClientError("Unable to extract text from the uploaded file", {
        code: "material.extraction_failed",
        status: 400,
      }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterialUploadPolicy).toHaveBeenCalled();
    });

    await user.click(screen.getByText("upload-partial-failure"));

    await waitFor(() => {
      expect(apiClient.uploadMaterial).toHaveBeenCalledTimes(2);
      expect(screen.getByTestId("action-error").textContent).toContain("second-fails.txt");
    });

    expect(screen.getByTestId("action-error").textContent).toContain("Не удалось извлечь текст из файла");
    expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(2);
  });

  it("polls materials while active indexing exists and stops after the queue becomes ready", async () => {
    vi.useFakeTimers();
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.fetchMaterials)
      .mockResolvedValueOnce(buildMaterialListResponse([
        buildMaterialSummary({
          id: "mat-1",
          title: "pricing.txt",
          sourceType: "file",
          originalFileName: "pricing.txt",
          status: "PENDING",
          versionState: "ACTIVE",
          createdAt: "2026-04-16T00:00:00Z",
          contentLength: 5,
          preview: "hello",
        }),
      ]))
      .mockResolvedValueOnce(buildMaterialListResponse([
        buildMaterialSummary({
          id: "mat-1",
          title: "pricing.txt",
          sourceType: "file",
          originalFileName: "pricing.txt",
          status: "READY",
          versionState: "ACTIVE",
          createdAt: "2026-04-16T00:00:00Z",
          contentLength: 5,
          preview: "hello",
        }),
      ]));

    await act(async () => {
      render(<MaterialsHookHarness />);
      await Promise.resolve();
    });

    expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(5_000);
      await Promise.resolve();
    });

    expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(2);

    await act(async () => {
      vi.advanceTimersByTime(5_000);
      await Promise.resolve();
    });

    expect(apiClient.fetchMaterials).toHaveBeenCalledTimes(2);
  });

  it("forwards metadata to the API client for text materials", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchMaterials).mockResolvedValue(buildMaterialListResponse());
    vi.mocked(apiClient.fetchMaterialUploadPolicy).mockResolvedValue(buildPolicy());
    vi.mocked(apiClient.createTextMaterial).mockResolvedValue(buildMaterialSummary({
      id: "mat-2",
      title: "Metadata note",
      sourceType: "text",
      originalFileName: null,
      status: "READY",
      createdAt: "2026-04-16T00:00:00Z",
      contentLength: 20,
      preview: "Материал с metadata.",
    }));

    render(<MaterialsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchMaterials).toHaveBeenCalled();
    });

    await user.click(screen.getByText("create-with-metadata"));

    await waitFor(() => {
      expect(apiClient.createTextMaterial).toHaveBeenCalledWith({
        title: "Metadata note",
        content: "Материал с metadata.",
        metadata: {
          ...policyMetadata,
        },
      });
    });
  });
});
