import { describe, expect, it } from "vitest";
import contractText from "../../backend/src/main/resources/api-contract/frontend-api-contract.json?raw";
import { API_CONTRACT_ENUM_VALUES, API_CONTRACT_TYPE_NAMES } from "./generated/api-types";
import type { CompatibilityMetadataInput, MaterialMetadata, MaterialMetadataInput } from "./types";
import { DEFAULT_MATERIAL_METADATA } from "./utils/materialMetadata";

const canonicalEditableFields = [
  "documentType",
  "workspaceKey",
  "documentStatus",
  "projectKey",
  "documentNumber",
  "languageCode",
  "manualTags",
  "periodStart",
  "periodEnd",
] as const satisfies readonly (keyof MaterialMetadataInput)[];

const compatibilityOnlyFields = [
  "knowledgeDocumentClass",
  "documentDate",
  "author",
  "department",
  "versionLabel",
  "language",
  "tags",
  "sourceTrust",
  "project",
  "counterparty",
  "businessStatus",
] as const satisfies readonly (keyof CompatibilityMetadataInput)[];

const displayedSnapshotFields = [
  ...canonicalEditableFields,
  ...compatibilityOnlyFields,
  "autoTags",
  "effectiveTags",
  "provenance",
] as const satisfies readonly (keyof MaterialMetadata)[];

type Equal<A, B> = (<T>() => T extends A ? 1 : 2) extends
  (<T>() => T extends B ? 1 : 2) ? true : false;
type Expect<T extends true> = T;
type CanonicalEditableField = typeof canonicalEditableFields[number];
type CompatibilityOnlyField = typeof compatibilityOnlyFields[number];
type DisplayedSnapshotField = typeof displayedSnapshotFields[number];

type _CanonicalInputContract = Expect<Equal<keyof MaterialMetadataInput, CanonicalEditableField>>;
type _CompatibilityInputContract = Expect<Equal<keyof CompatibilityMetadataInput, CompatibilityOnlyField>>;
type _CompatibilityFieldsAreNotCanonical = Expect<
  Equal<Extract<CompatibilityOnlyField, keyof MaterialMetadataInput>, never>
>;
type _SnapshotContainsContractFields = Expect<
  Equal<Exclude<CanonicalEditableField | CompatibilityOnlyField, DisplayedSnapshotField>, never>
>;

describe("frontend API contracts", () => {
  it("keeps generated frontend contract types in sync with the backend artifact", () => {
    const contract = JSON.parse(contractText) as {
      types: Record<string, { kind: string; values?: string[] }>;
    };
    const backendTypeNames = Object.keys(contract.types).sort();
    const backendEnumValues = Object.fromEntries(
      Object.entries(contract.types)
        .filter(([, descriptor]) => descriptor.kind === "enum")
        .map(([name, descriptor]) => [name, descriptor.values ?? []]),
    );

    expect([...API_CONTRACT_TYPE_NAMES]).toEqual(backendTypeNames);
    expect(API_CONTRACT_ENUM_VALUES).toEqual(backendEnumValues);
    expect(API_CONTRACT_ENUM_VALUES.DocumentBlockType).toEqual([
      "TITLE",
      "NARRATIVE",
      "TABLE",
      "LIST",
      "QA",
      "APPENDIX",
      "SLIDE",
      "CAPTION",
    ]);
  });

  it("keeps LLM provider enum values and contract names generated from the backend artifact", () => {
    expect(API_CONTRACT_ENUM_VALUES.LlmProviderPurpose).toEqual([
      "CHAT",
      "EMBEDDING",
      "CHAT_AND_EMBEDDING",
    ]);
    expect(API_CONTRACT_ENUM_VALUES.LlmProviderStatus).toEqual([
      "UNKNOWN",
      "UP",
      "DOWN",
      "DEGRADED",
    ]);
    expect(API_CONTRACT_ENUM_VALUES.LlmProviderType).toEqual(["OPENAI_COMPATIBLE"]);
    expect([...API_CONTRACT_TYPE_NAMES]).toEqual(expect.arrayContaining([
      "LlmProviderActivateRequest",
      "LlmProviderConfigResponse",
      "LlmProviderInput",
      "LlmProviderModelInfo",
      "LlmProviderProbeResult",
      "LlmProviderPurpose",
      "LlmProviderStatus",
      "LlmProviderType",
    ]));
  });

  it("keeps canonical editable metadata fields present in local builders", () => {
    const metadata: MaterialMetadata = DEFAULT_MATERIAL_METADATA;

    expect(metadata.documentType).toBe("OTHER");
    expect(metadata.documentStatus).toBe("ACTIVE");
    expect(metadata.manualTags).toEqual([]);
  });

  it("documents compatibility-only metadata separately from ordinary UI input", () => {
    expect(canonicalEditableFields).toEqual([
      "documentType",
      "workspaceKey",
      "documentStatus",
      "projectKey",
      "documentNumber",
      "languageCode",
      "manualTags",
      "periodStart",
      "periodEnd",
    ]);
    expect(compatibilityOnlyFields).toEqual([
      "knowledgeDocumentClass",
      "documentDate",
      "author",
      "department",
      "versionLabel",
      "language",
      "tags",
      "sourceTrust",
      "project",
      "counterparty",
      "businessStatus",
    ]);
  });

  it("keeps displayed snapshot fields available for stored and inferred metadata", () => {
    const metadata: MaterialMetadata = DEFAULT_MATERIAL_METADATA;

    for (const field of displayedSnapshotFields) {
      expect(field in metadata).toBe(true);
    }
  });
});
