import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { JsonView } from "./JsonView";

describe("JsonView", () => {
  it("shows nested objects and lists as key/value text", () => {
    render(
      <JsonView
        value={{
          hypothesis: "Bad deployment",
          confidence: 0.8,
          evidence: [{ source: "runbooks/checkout.md" }],
          empty: null,
        }}
      />,
    );

    expect(screen.getByText("hypothesis")).toBeInTheDocument();
    expect(screen.getByText("Bad deployment")).toBeInTheDocument();
    expect(screen.getByText("0.8")).toBeInTheDocument();
    expect(screen.getByText("runbooks/checkout.md")).toBeInTheDocument();
    expect(screen.getByText("null")).toBeInTheDocument();
  });

  it("never interprets markup or creates links", () => {
    const { container } = render(
      <JsonView
        value={{ "<b>key</b>": '<a href="https://evil.example">x</a><script>alert(1)</script>' }}
      />,
    );

    expect(screen.getByText("<b>key</b>")).toBeInTheDocument();
    expect(container.querySelector("a, b, script")).toBeNull();
  });
});
