import { describe, expect, test } from "bun:test";

import { dockerRepository, parseReleaseTag, shortSha } from "./release";

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

describe("dockerRepository", () => {
  test("amd64 uses the language repository", () => {
    expect(dockerRepository("go")).toBe("hello-world-client-go");
    expect(dockerRepository("rust", "amd64")).toBe("hello-world-client-rust");
    expect(dockerRepository("java", "amd64")).toBe("hello-world-client-java");
  });

  test("arm64 uses a separate -arm repository", () => {
    expect(dockerRepository("go", "arm64")).toBe("hello-world-client-go-arm");
    expect(dockerRepository("rust", "arm64")).toBe(
      "hello-world-client-rust-arm",
    );
    expect(dockerRepository("java", "arm64")).toBe(
      "hello-world-client-java-arm",
    );
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
