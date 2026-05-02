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
});
