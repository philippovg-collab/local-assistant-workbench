import { describe, expect, it } from "vitest";
import {
  emptyRetrievalFilters,
  extractRetrievalQueryHints,
  mergeHintFilters,
  type RetrievalFilterKey,
} from "./retrievalHints";

describe("retrievalHints", () => {
  it("extracts document dates and free-text project hints without treating project as a key", () => {
    const hints = extractRetrievalQueryHints(
      "Покажи по проекту North Upgrade договор № KZ-2026-0415-ENERGY с 2026-04-01 по 2026-04-30",
    );

    expect(hints).toMatchObject({
      documentNumber: "KZ-2026-0415-ENERGY",
      documentDateFrom: "2026-04-01",
      documentDateTo: "2026-04-30",
      project: "North Upgrade",
      documentTypes: ["CONTRACT"],
      projectKeys: [],
      periodStartFrom: null,
      periodStartTo: null,
    });
  });

  it("extracts language, status, counterparty and department facets", () => {
    const hints = extractRetrievalQueryHints(
      "Find report for project Alpha in english, counterparty GridBuild LLP, status draft, department Grid operations",
    );

    expect(hints).toMatchObject({
      language: "en",
      languageCodes: ["EN"],
      documentTypes: ["REPORT"],
      documentStatuses: ["DRAFT"],
      project: "Alpha",
      counterparty: "GridBuild LLP",
      businessStatus: "draft",
      department: "Grid operations",
    });
  });

  it("keeps manual project keys from being combined with free-text project hints", () => {
    const filters = mergeHintFilters(
      { ...emptyRetrievalFilters(), projectKeys: ["manual-project"] },
      { project: "North Upgrade" },
      new Set<RetrievalFilterKey>(),
    );

    expect(filters.projectKeys).toEqual(["manual-project"]);
    expect(filters.project).toBeNull();
  });

  it("keeps manual language codes from being combined with legacy language hints", () => {
    const filters = mergeHintFilters(
      { ...emptyRetrievalFilters(), languageCodes: ["EN"] },
      { language: "ru", languageCodes: ["RU"] },
      new Set<RetrievalFilterKey>(),
    );

    expect(filters.languageCodes).toEqual(["EN"]);
    expect(filters.language).toBeNull();
  });

  it("dismisses document date hints as a pair", () => {
    const filters = mergeHintFilters(
      emptyRetrievalFilters(),
      {
        documentDateFrom: "2026-04-01",
        documentDateTo: "2026-04-30",
      },
      new Set<RetrievalFilterKey>(["documentDateFrom"]),
    );

    expect(filters.documentDateFrom).toBeNull();
    expect(filters.documentDateTo).toBeNull();
  });

  it("merges and dismisses version label hints", () => {
    const merged = mergeHintFilters(
      emptyRetrievalFilters(),
      { versionLabel: "v2" },
      new Set<RetrievalFilterKey>(),
    );
    const dismissed = mergeHintFilters(
      emptyRetrievalFilters(),
      { versionLabel: "v2" },
      new Set<RetrievalFilterKey>(["versionLabel"]),
    );

    expect(merged.versionLabel).toBe("v2");
    expect(dismissed.versionLabel).toBeNull();
  });

  it("normalizes reference-time filters", () => {
    const filters = mergeHintFilters(
      {
        ...emptyRetrievalFilters(),
        effectiveDate: "19.04.2026",
        uploadedAfterInclusive: "2026-04-19T00:00:00+05:00",
        uploadedBeforeExclusive: "invalid",
        versionSelectionMode: "VERSION_STATE",
        versionState: "SUPERSEDED",
      },
      {},
      new Set<RetrievalFilterKey>(),
    );

    expect(filters.effectiveDate).toBe("2026-04-19");
    expect(filters.uploadedAfterInclusive).toBe("2026-04-18T19:00:00.000Z");
    expect(filters.uploadedBeforeExclusive).toBeNull();
    expect(filters.versionSelectionMode).toBe("VERSION_STATE");
    expect(filters.versionState).toBe("SUPERSEDED");
  });
});
