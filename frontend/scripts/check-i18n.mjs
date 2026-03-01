#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.resolve(scriptDir, "..");
const localeFiles = ["en", "zh"];

const TEXT_SCAN_DIRS = ["app", "modules", "shared"];
const TEXT_SCAN_EXCLUDE = [
  /^shared\/components\/ui\//,
  /^modules\/arena\/components\/specimen-card\.tsx$/,
  /^shared\/components\/layout\/header\.tsx$/,
];

const ALLOWED_TEXT_PATTERNS = [
  /^VS$/,
  /^README\.MD$/,
  /^W$/,
];

function readJson(filePath) {
  const raw = fs.readFileSync(filePath, "utf8");
  return JSON.parse(raw);
}

function flattenKeys(value, prefix = "", keys = new Set()) {
  if (Array.isArray(value)) {
    keys.add(prefix);
    return keys;
  }

  if (!value || typeof value !== "object") {
    if (prefix) {
      keys.add(prefix);
    }
    return keys;
  }

  for (const key of Object.keys(value)) {
    const nextPrefix = prefix ? `${prefix}.${key}` : key;
    flattenKeys(value[key], nextPrefix, keys);
  }

  return keys;
}

function collectTsxFiles(relativeDir) {
  const absoluteDir = path.join(frontendRoot, relativeDir);
  if (!fs.existsSync(absoluteDir)) {
    return [];
  }

  const files = [];
  const stack = [absoluteDir];

  while (stack.length > 0) {
    const current = stack.pop();
    if (!current) {
      continue;
    }

    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
        continue;
      }

      if (entry.isFile() && fullPath.endsWith(".tsx")) {
        files.push(fullPath);
      }
    }
  }

  return files;
}

function normalizeRelativePath(filePath) {
  return path.relative(frontendRoot, filePath).replace(/\\/g, "/");
}

function toLineNumber(content, index) {
  return content.slice(0, index).split(/\r?\n/).length;
}

function shouldSkipText(text) {
  if (!text) {
    return true;
  }

  if (!/[A-Za-z\u4e00-\u9fff]/.test(text)) {
    return true;
  }

  if (/[?:()]/.test(text) || text.includes('t("') || text.includes("t('")) {
    return true;
  }

  return ALLOWED_TEXT_PATTERNS.some((pattern) => pattern.test(text));
}

function scanHardcodedTexts() {
  const findings = [];
  const jsxTextPattern = />\s*([^<>{\r\n]+?)\s*</g;
  const toastLiteralPattern = /toast\.(?:success|error|info|warning)\(\s*(["'`])([^"'`]*[A-Za-z\u4e00-\u9fff][^"'`]*)\1/g;
  const labelLiteralPattern = /\blabel\s*:\s*(["'`])([^"'`]*[A-Za-z\u4e00-\u9fff][^"'`]*)\1/g;

  for (const dir of TEXT_SCAN_DIRS) {
    for (const filePath of collectTsxFiles(dir)) {
      const relativePath = normalizeRelativePath(filePath);
      if (TEXT_SCAN_EXCLUDE.some((pattern) => pattern.test(relativePath))) {
        continue;
      }

      const content = fs.readFileSync(filePath, "utf8");

      for (const match of content.matchAll(jsxTextPattern)) {
        const rawText = match[1] ?? "";
        const text = rawText.replace(/\s+/g, " ").trim();
        if (shouldSkipText(text)) {
          continue;
        }

        findings.push({
          file: relativePath,
          line: toLineNumber(content, match.index ?? 0),
          type: "jsx-text",
          value: text,
        });
      }

      for (const match of content.matchAll(toastLiteralPattern)) {
        findings.push({
          file: relativePath,
          line: toLineNumber(content, match.index ?? 0),
          type: "toast-literal",
          value: match[2],
        });
      }

      for (const match of content.matchAll(labelLiteralPattern)) {
        findings.push({
          file: relativePath,
          line: toLineNumber(content, match.index ?? 0),
          type: "label-literal",
          value: match[2],
        });
      }
    }
  }

  return findings;
}

function compareMessageKeys() {
  const localeMap = {};

  for (const locale of localeFiles) {
    const filePath = path.join(frontendRoot, "messages", `${locale}.json`);
    localeMap[locale] = flattenKeys(readJson(filePath));
  }

  const baseLocale = localeFiles[0];
  const baseKeys = localeMap[baseLocale];
  const errors = [];

  for (const locale of localeFiles.slice(1)) {
    const localeKeys = localeMap[locale];

    const missingInLocale = [...baseKeys].filter((key) => !localeKeys.has(key));
    const extraInLocale = [...localeKeys].filter((key) => !baseKeys.has(key));

    if (missingInLocale.length > 0) {
      errors.push(`[${locale}] missing keys (${missingInLocale.length}):`);
      for (const key of missingInLocale.slice(0, 40)) {
        errors.push(`  - ${key}`);
      }
    }

    if (extraInLocale.length > 0) {
      errors.push(`[${locale}] extra keys (${extraInLocale.length}):`);
      for (const key of extraInLocale.slice(0, 40)) {
        errors.push(`  - ${key}`);
      }
    }
  }

  return errors;
}

function main() {
  const keyErrors = compareMessageKeys();
  const hardcodedFindings = scanHardcodedTexts();

  if (keyErrors.length > 0 || hardcodedFindings.length > 0) {
    console.error("[i18n-check] failed.");

    if (keyErrors.length > 0) {
      console.error("\nMessage key mismatch:");
      for (const line of keyErrors) {
        console.error(line);
      }
    }

    if (hardcodedFindings.length > 0) {
      console.error("\nPotential hardcoded texts in TSX:");
      for (const finding of hardcodedFindings.slice(0, 120)) {
        console.error(`- ${finding.file}:${finding.line} [${finding.type}] ${finding.value}`);
      }
      if (hardcodedFindings.length > 120) {
        console.error(`... and ${hardcodedFindings.length - 120} more`);
      }
    }

    process.exit(1);
  }

  console.log("[i18n-check] passed.");
}

main();
