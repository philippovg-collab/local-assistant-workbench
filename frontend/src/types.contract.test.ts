import { describe, expect, it } from "vitest";
import type { MaterialMetadata } from "./types";
import { DEFAULT_MATERIAL_METADATA } from "./utils/materialMetadata";

describe("frontend API contracts", () => {
  it("keeps required material metadata fields present in local builders", () => {
    const metadata: MaterialMetadata = DEFAULT_MATERIAL_METADATA;

    expect(metadata.documentType).toBe("OTHER");
    expect(metadata.knowledgeDocumentClass).toBe("other");
    expect(metadata.sourceTrust).toBe("UNKNOWN");
  });
});
