import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Badge } from "./badge";

describe("Badge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders the inverted variant for dark surfaces", () => {
    render(<Badge variant="inverted">RAG readiness</Badge>);

    const badge = screen.getByText("RAG readiness");
    expect(badge).toBeTruthy();
    expect(badge.className).toContain("bg-white/10");
    expect(badge.className).toContain("text-sidebar-foreground");
  });
});
