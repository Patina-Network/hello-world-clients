import { describe, expect, test } from "bun:test";

import { parseReleaseTag, shortSha } from "./release";

describe("parseReleaseTag", () => {
  test("vX.Y.Z releases every client", () => {
    expect(parseReleaseTag("v1.2.3")).toEqual({
      languages: ["go", "rust", "java"],
      version: "v1.2.3",
    });
  });

  test("<language>-vX.Y.Z releases one client", () => {
    expect(parseReleaseTag("rust-v2.0.1")).toEqual({
      languages: ["rust"],
      version: "v2.0.1",
    });
  });

  test.each([
    "latest",
    "v1",
    "go-v01.0.0",
    "go-v1.0.0;echo pwned",
    "js-v1.0.0",
  ])("rejects %p", (tag) => {
    expect(() => parseReleaseTag(tag)).toThrow();
  });
});

describe("shortSha", () => {
  test("returns the first 7 characters", () => {
    expect(shortSha("1234567" + "a".repeat(33))).toBe("1234567");
  });

  test("rejects anything but a full SHA", () => {
    expect(() => shortSha("1234567")).toThrow();
  });
});
