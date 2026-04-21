import { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MaterialMetadataFormSection } from "./MaterialMetadataFormSection";
import type { MaterialMetadataFormState } from "@/utils/materialMetadata";
import { emptyMaterialMetadataFormState, toMaterialMetadataInput } from "@/utils/materialMetadata";
import type { ReferenceProject, ReferenceWorkspace } from "@/types";

const workspaces: ReferenceWorkspace[] = [
  {
    key: "general",
    nameRu: "Общая",
    active: true,
    sortOrder: 0,
    isDefault: true,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "north-upgrade",
    nameRu: "Северная модернизация",
    active: true,
    sortOrder: 1,
    isDefault: false,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
];

const projects: ReferenceProject[] = [
  {
    key: "north-line",
    workspaceKey: "north-upgrade",
    nameRu: "Северная линия",
    active: true,
    sortOrder: 0,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "general-faq",
    workspaceKey: "general",
    nameRu: "Общие FAQ",
    active: true,
    sortOrder: 1,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
];

function FormHarness({ initialState }: { initialState?: Partial<MaterialMetadataFormState> }) {
  const [state, setState] = useState<MaterialMetadataFormState>({
    ...emptyMaterialMetadataFormState(),
    ...initialState,
  });

  return (
    <section>
      <MaterialMetadataFormSection
        idPrefix="test-metadata"
        projects={projects}
        state={state}
        workspaces={workspaces}
        onChange={setState}
      />
      <output data-testid="state">{JSON.stringify(state)}</output>
      <output data-testid="payload">{JSON.stringify(toMaterialMetadataInput(state))}</output>
    </section>
  );
}

describe("MaterialMetadataFormSection", () => {
  afterEach(() => {
    cleanup();
  });

  it("selects the default workspace automatically", async () => {
    render(<FormHarness />);

    await waitFor(() => {
      expect(screen.getByTestId("state").textContent).toContain('"workspaceKey":"general"');
    });
  });

  it("filters projects by selected workspace", async () => {
    const user = userEvent.setup();
    render(<FormHarness initialState={{ workspaceKey: "north-upgrade" }} />);

    await user.click(screen.getByText("Дополнительно"));
    await user.click(screen.getByLabelText("Проект"));

    expect(await screen.findByRole("option", { name: "Северная линия" })).toBeTruthy();
    expect(screen.queryByRole("option", { name: "Общие FAQ" })).toBeNull();
  });

  it("keeps project disabled when no workspace is available", async () => {
    function EmptyWorkspaceHarness() {
      const [state, setState] = useState(emptyMaterialMetadataFormState());
      return (
        <MaterialMetadataFormSection
          idPrefix="empty-metadata"
          projects={[]}
          state={state}
          workspaces={[]}
          onChange={setState}
        />
      );
    }

    const user = userEvent.setup();
    render(<EmptyWorkspaceHarness />);

    await user.click(screen.getByText("Дополнительно"));
    expect((screen.getByLabelText("Проект") as HTMLButtonElement).disabled).toBe(true);
  });

  it("maps automatic language to null in the payload", async () => {
    render(<FormHarness initialState={{ workspaceKey: "general", documentType: "POLICY" }} />);

    await waitFor(() => {
      expect(screen.getByTestId("payload").textContent).toContain('"languageCode":null');
    });
  });
});
