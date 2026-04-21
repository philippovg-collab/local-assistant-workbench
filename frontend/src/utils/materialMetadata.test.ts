import { describe, expect, it } from "vitest";
import {
  buildMaterialMetadataEntries,
  emptyMaterialMetadataFormState,
  toMaterialMetadataInput,
  validateMaterialMetadata,
} from "./materialMetadata";

describe("materialMetadata utils", () => {
  it("requires canonical metadata fields", () => {
    const validation = validateMaterialMetadata(emptyMaterialMetadataFormState());

    expect(validation.isValid).toBe(false);
    expect(validation.fieldErrors.workspaceKey).toBe("Выберите рабочую область.");
    expect(validation.fieldErrors.documentType).toBe("Выберите тип документа.");
  });

  it("omits periodEnd when the document is open ended", () => {
    const input = toMaterialMetadataInput({
      ...emptyMaterialMetadataFormState(),
      workspaceKey: "general",
      documentType: "POLICY",
      documentStatus: "ACTIVE",
      periodStart: "2026-04-01",
      periodEnd: "2026-12-31",
      openEnded: true,
    });

    expect(input).toEqual({
      workspaceKey: "general",
      documentType: "POLICY",
      documentStatus: "ACTIVE",
      languageCode: null,
      periodStart: "2026-04-01",
    });
  });

  it("deduplicates manual tags and keeps only canonical payload fields", () => {
    const input = toMaterialMetadataInput({
      ...emptyMaterialMetadataFormState(),
      workspaceKey: "general",
      documentType: "CONTRACT",
      documentStatus: "DRAFT",
      projectKey: "north-line",
      documentNumber: "KZ-2026-0415-ENERGY",
      languageCode: "RU",
      periodStart: "2026-04-01",
      periodEnd: "2026-12-31",
      openEnded: false,
      manualTags: "grid, policy, grid",
    });

    expect(input).toEqual({
      workspaceKey: "general",
      documentType: "CONTRACT",
      documentStatus: "DRAFT",
      projectKey: "north-line",
      documentNumber: "KZ-2026-0415-ENERGY",
      languageCode: "RU",
      periodStart: "2026-04-01",
      periodEnd: "2026-12-31",
      manualTags: ["grid", "policy"],
    });
    expect("sourceTrust" in input).toBe(false);
    expect("author" in input).toBe(false);
  });

  it("validates period boundaries when the end date is enabled", () => {
    const validation = validateMaterialMetadata({
      ...emptyMaterialMetadataFormState(),
      workspaceKey: "general",
      documentType: "POLICY",
      documentStatus: "ACTIVE",
      periodStart: "2026-12-31",
      periodEnd: "2026-04-01",
      openEnded: false,
    });

    expect(validation.isValid).toBe(false);
    expect(validation.messages).toContain("Дата начала периода не может быть позже даты окончания.");
  });

  it("renders manual, auto, and effective tags as separate display entries", () => {
    const entries = buildMaterialMetadataEntries({
      documentType: "POLICY",
      documentStatus: "ACTIVE",
      manualTags: ["grid", "policy"],
      autoTags: ["energy"],
      effectiveTags: ["grid", "policy", "energy"],
      provenance: {
        fieldOrigins: {
          documentType: "MANUAL",
          documentStatus: "MANUAL",
          manualTags: "MANUAL",
          autoTags: "INFERRED",
          effectiveTags: "MANUAL",
        },
        fieldConfidence: {},
      },
    });

    expect(entries).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: "manualTags", label: "Ручные теги", value: "grid, policy", origin: "MANUAL" }),
      expect.objectContaining({ key: "autoTags", label: "Авто-теги", value: "energy", origin: "INFERRED" }),
      expect.objectContaining({ key: "effectiveTags", label: "Теги", value: "grid, policy, energy", origin: "MANUAL" }),
    ]));
  });

  it("renders workspace and project Russian names when reference data is available", () => {
    const entries = buildMaterialMetadataEntries(
      {
        workspaceKey: "general",
        projectKey: "north-line",
        documentType: "POLICY",
        documentStatus: "ACTIVE",
      },
      {
        workspaces: [{ key: "general", nameRu: "Общая" }],
        projects: [{ key: "north-line", nameRu: "Северная линия" }],
      },
    );

    expect(entries).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: "workspaceKey", value: "Общая" }),
      expect.objectContaining({ key: "projectKey", value: "Северная линия" }),
    ]));
  });

  it("falls back to workspace and project keys when reference data is missing", () => {
    const entries = buildMaterialMetadataEntries({
      workspaceKey: "unknown-workspace",
      projectKey: "unknown-project",
      documentType: "POLICY",
      documentStatus: "ACTIVE",
    });

    expect(entries).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: "workspaceKey", value: "unknown-workspace" }),
      expect.objectContaining({ key: "projectKey", value: "unknown-project" }),
    ]));
  });

  it("uses legacy tags as manual display fallback when split fields are absent", () => {
    const entries = buildMaterialMetadataEntries({
      documentType: "REPORT",
      documentStatus: "ACTIVE",
      tags: ["legacy", "tag"],
      provenance: {
        fieldOrigins: {
          documentType: "MANUAL",
          documentStatus: "MANUAL",
          tags: "INFERRED",
        },
        fieldConfidence: {},
      },
    });

    expect(entries).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: "manualTags", label: "Ручные теги", value: "legacy, tag", origin: "INFERRED" }),
    ]));
  });
});
