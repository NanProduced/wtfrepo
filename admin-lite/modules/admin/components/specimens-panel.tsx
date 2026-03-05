"use client";

import { FormEvent, useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  deactivateSpecimen,
  importSpecimen,
  reviewSpecimen,
  submitSpecimen,
  updateSpecimenTags,
  type SubmitSpecimenInput,
} from "@/modules/admin/lib/request";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";
import type { ImportSpecimenResult } from "@/modules/admin/types";
import { SectionCard } from "@/modules/admin/components/panel-common";

type WorkflowStep = "import" | "curate" | "submit" | "review";
type FieldErrorKey =
  | "specimenId"
  | "languages"
  | "tags"
  | "excerptText"
  | "excerptTranslationMeta"
  | "officialCommentary"
  | "owner";
type FieldErrors = Partial<Record<FieldErrorKey, string>>;

const STEP_ORDER: Record<WorkflowStep, number> = {
  import: 1,
  curate: 2,
  submit: 3,
  review: 4,
};

const DRAFT_STORAGE_KEY = "wtf-admin-lite-specimen-curation-draft-v1";
const GITHUB_REPO_URL_REGEX = /^https?:\/\/github\.com\/([^/]+)\/([^/#?]+)(?:[/?#].*)?$/i;
const FIELD_ERROR_ORDER: FieldErrorKey[] = [
  "specimenId",
  "languages",
  "tags",
  "excerptText",
  "excerptTranslationMeta",
  "officialCommentary",
  "owner",
];

interface CurationDraft {
  updatedAt: string;
  githubUrl: string;
  specimenId: string;
  languagesText: string;
  tagsText: string;
  excerptType: string;
  excerptCandidateId: string;
  excerptPriority: string;
  excerptText: string;
  excerptTranslatedTextZh: string;
  excerptTranslationMeta: string;
  snapshotEnabled: boolean;
  oneLinerZh: string;
  oneLinerEn: string;
  arenaReasonZh: string;
  arenaReasonEn: string;
  ownerLogin: string;
  ownerUserId: string;
  ownerAvatarUrl: string;
  ownerHtmlUrl: string;
  ownerContributions: string;
  maintainersText: string;
  contributorsText: string;
  codeTitle: string;
  codeCandidateId: string;
  codeLanguage: string;
  codeSnippet: string;
  codeExplainText: string;
  codePriority: string;
  note: string;
}

interface IdentityLine {
  githubLogin: string;
  githubUserId: string;
  githubAvatarUrl?: string;
  githubHtmlUrl?: string;
  contributions?: number;
}

function parseTags(raw: string) {
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [dimensionKey, tagKey] = line.split(":").map((item) => item.trim());
      return { dimensionKey, tagKey };
    })
    .filter((item) => item.dimensionKey && item.tagKey);
}

function parseLanguages(raw: string) {
  const items: Array<{ name: string; percentage?: number }> = [];

  for (const rawLine of raw.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) {
      continue;
    }

    const [name, percentText] = line.split(":").map((item) => item.trim());
    if (!name) {
      continue;
    }

    const percentage = percentText ? Number(percentText) : Number.NaN;
    if (Number.isFinite(percentage)) {
      items.push({ name, percentage });
      continue;
    }
    items.push({ name });
  }

  return items;
}

function parseIdentityLines(raw: string): IdentityLine[] {
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [githubLogin, githubUserId, githubAvatarUrl, githubHtmlUrl, contributionsText] = line
        .split(",")
        .map((item) => item.trim());
      const contributions = contributionsText ? Number(contributionsText) : Number.NaN;
      return {
        githubLogin,
        githubUserId,
        githubAvatarUrl: githubAvatarUrl || undefined,
        githubHtmlUrl: githubHtmlUrl || undefined,
        contributions: Number.isFinite(contributions) ? contributions : undefined,
      };
    })
    .filter((item) => item.githubLogin && item.githubUserId);
}

function parseTranslationMeta(
  raw: string,
  t: (zh: string, en: string) => string,
): Record<string, unknown> | undefined {
  const text = raw.trim();
  if (!text) {
    return undefined;
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error(
      t(
        "translationMeta 必须是合法 JSON 对象，例如 {\"source\":\"manual\",\"quality\":\"draft\"}。",
        "translationMeta must be valid JSON object, e.g. {\"source\":\"manual\",\"quality\":\"draft\"}.",
      ),
    );
  }

  if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error(
      t(
        "translationMeta 必须是 JSON 对象（key-value），不能是数组或其他类型。",
        "translationMeta must be a JSON object (key-value), not an array or other types.",
      ),
    );
  }

  return parsed as Record<string, unknown>;
}

function hasFieldErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}

function firstFieldErrorKey(errors: FieldErrors): FieldErrorKey | null {
  return FIELD_ERROR_ORDER.find((key) => Boolean(errors[key])) ?? null;
}

function firstFieldError(errors: FieldErrors): string | null {
  const firstKey = firstFieldErrorKey(errors);
  return firstKey ? (errors[firstKey] ?? null) : null;
}

function parseGithubRepoUrl(rawUrl: string): { owner: string; repo: string } | null {
  const trimmed = rawUrl.trim();
  if (!trimmed) {
    return null;
  }
  const matched = trimmed.match(GITHUB_REPO_URL_REGEX);
  if (!matched) {
    return null;
  }
  const owner = matched[1];
  const repo = matched[2].replace(/\.git$/i, "");
  if (!owner || !repo) {
    return null;
  }
  return { owner, repo };
}

type MarkdownViewMode = "edit" | "preview" | "split";

function escapeHtml(raw: string): string {
  return raw
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function safeHref(rawHref: string): string {
  return /^https?:\/\//i.test(rawHref) ? rawHref : "#";
}

function renderInlineMarkdown(raw: string): string {
  let text = escapeHtml(raw);
  text = text.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label: string, href: string) => {
    return `<a class="text-blue-700 underline" href="${safeHref(href)}" target="_blank" rel="noreferrer">${label}</a>`;
  });
  text = text.replace(/`([^`]+)`/g, '<code class="rounded bg-muted px-1 py-0.5 text-xs">$1</code>');
  text = text.replace(/~~([^~]+)~~/g, "<del>$1</del>");
  text = text.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  text = text.replace(/\*([^*]+)\*/g, "<em>$1</em>");
  return text;
}

function renderMarkdownPreview(markdown: string): string {
  const lines = markdown.replaceAll("\r\n", "\n").split("\n");
  const html: string[] = [];
  let listMode: "ul" | "ol" | null = null;
  let inCodeBlock = false;
  let codeLanguage = "";
  let codeLines: string[] = [];
  let idx = 0;

  function closeList() {
    if (!listMode) {
      return;
    }
    html.push(`</${listMode}>`);
    listMode = null;
  }

  function flushCodeBlock() {
    const escaped = escapeHtml(codeLines.join("\n"));
    const language = codeLanguage ? `<span class="text-[11px] text-muted-foreground">${escapeHtml(codeLanguage)}</span>` : "";
    html.push(
      `<div class="my-3 rounded-md border border-border/70 bg-muted/20"><div class="flex items-center justify-between border-b border-border/50 px-3 py-1.5">${language}</div><pre class="overflow-x-auto p-3 text-xs leading-5"><code>${escaped}</code></pre></div>`,
    );
    codeLines = [];
    codeLanguage = "";
  }

  function isTableLine(rawLine: string): boolean {
    const text = rawLine.trim();
    return text.startsWith("|") && text.endsWith("|") && text.includes("|");
  }

  function isTableDivider(rawLine: string): boolean {
    const text = rawLine.trim();
    if (!isTableLine(text)) {
      return false;
    }
    const cells = text
      .slice(1, -1)
      .split("|")
      .map((cell) => cell.trim());
    return cells.length > 0 && cells.every((cell) => /^:?-{2,}:?$/.test(cell));
  }

  function parseTableRow(rawLine: string): string[] {
    return rawLine
      .trim()
      .slice(1, -1)
      .split("|")
      .map((cell) => renderInlineMarkdown(cell.trim()));
  }

  while (idx < lines.length) {
    const line = lines[idx];
    const fenceMatch = line.match(/^```(.*)$/);
    if (fenceMatch) {
      closeList();
      if (inCodeBlock) {
        flushCodeBlock();
        inCodeBlock = false;
      } else {
        inCodeBlock = true;
        codeLanguage = fenceMatch[1].trim();
      }
      idx += 1;
      continue;
    }

    if (inCodeBlock) {
      codeLines.push(line);
      idx += 1;
      continue;
    }

    const trimmed = line.trim();
    if (!trimmed) {
      closeList();
      idx += 1;
      continue;
    }

    if (/^---+$/.test(trimmed) || /^\*\*\*+$/.test(trimmed)) {
      closeList();
      html.push('<hr class="my-3 border-border/70" />');
      idx += 1;
      continue;
    }

    if (idx + 1 < lines.length && isTableLine(line) && isTableDivider(lines[idx + 1])) {
      closeList();
      const headerCells = parseTableRow(line);
      idx += 2;
      const bodyRows: string[][] = [];
      while (idx < lines.length && isTableLine(lines[idx])) {
        bodyRows.push(parseTableRow(lines[idx]));
        idx += 1;
      }
      html.push('<div class="my-3 overflow-x-auto rounded-md border border-border/70">');
      html.push('<table class="min-w-full border-collapse text-sm">');
      html.push('<thead class="bg-muted/20"><tr>');
      for (const cell of headerCells) {
        html.push(`<th class="border-b border-border/70 px-3 py-2 text-left font-medium">${cell}</th>`);
      }
      html.push("</tr></thead>");
      html.push("<tbody>");
      for (const row of bodyRows) {
        html.push("<tr>");
        for (const cell of row) {
          html.push(`<td class="border-t border-border/60 px-3 py-2 align-top">${cell}</td>`);
        }
        html.push("</tr>");
      }
      html.push("</tbody></table></div>");
      continue;
    }

    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      closeList();
      const level = headingMatch[1].length;
      const text = renderInlineMarkdown(headingMatch[2]);
      const headingClass =
        level <= 2 ? "mt-3 text-base font-semibold" : level <= 4 ? "mt-2 text-sm font-semibold" : "mt-2 text-sm font-medium";
      html.push(`<h${level} class="${headingClass}">${text}</h${level}>`);
      idx += 1;
      continue;
    }

    const quoteMatch = trimmed.match(/^>\s?(.*)$/);
    if (quoteMatch) {
      closeList();
      html.push(
        `<blockquote class="my-2 border-l-2 border-border px-3 py-1 text-sm text-muted-foreground">${renderInlineMarkdown(quoteMatch[1])}</blockquote>`,
      );
      idx += 1;
      continue;
    }

    const taskMatch = trimmed.match(/^[-*]\s+\[( |x|X)\]\s+(.+)$/);
    if (taskMatch) {
      if (listMode !== "ul") {
        closeList();
        listMode = "ul";
        html.push('<ul class="my-2 list-none space-y-1 pl-2 text-sm">');
      }
      const checked = taskMatch[1].toLowerCase() === "x";
      const icon = checked ? "☑" : "☐";
      html.push(`<li class="flex items-start gap-2"><span class="mt-0.5 text-xs">${icon}</span><span>${renderInlineMarkdown(taskMatch[2])}</span></li>`);
      idx += 1;
      continue;
    }

    const unorderedMatch = trimmed.match(/^[-*]\s+(.+)$/);
    if (unorderedMatch) {
      if (listMode !== "ul") {
        closeList();
        listMode = "ul";
        html.push('<ul class="my-2 list-disc space-y-1 pl-6 text-sm">');
      }
      html.push(`<li>${renderInlineMarkdown(unorderedMatch[1])}</li>`);
      idx += 1;
      continue;
    }

    const orderedMatch = trimmed.match(/^\d+\.\s+(.+)$/);
    if (orderedMatch) {
      if (listMode !== "ol") {
        closeList();
        listMode = "ol";
        html.push('<ol class="my-2 list-decimal space-y-1 pl-6 text-sm">');
      }
      html.push(`<li>${renderInlineMarkdown(orderedMatch[1])}</li>`);
      idx += 1;
      continue;
    }

    closeList();
    html.push(`<p class="my-2 whitespace-pre-wrap text-sm leading-6">${renderInlineMarkdown(trimmed)}</p>`);
    idx += 1;
  }

  if (inCodeBlock) {
    flushCodeBlock();
  }
  closeList();

  return html.join("");
}

function insertAroundSelection(
  text: string,
  setText: (next: string) => void,
  textarea: HTMLTextAreaElement | null,
  prefix: string,
  suffix: string,
  fallback: string,
) {
  if (!textarea) {
    setText(`${text}${prefix}${fallback}${suffix}`);
    return;
  }

  const start = textarea.selectionStart ?? text.length;
  const end = textarea.selectionEnd ?? text.length;
  const selected = text.slice(start, end) || fallback;
  const nextText = `${text.slice(0, start)}${prefix}${selected}${suffix}${text.slice(end)}`;
  setText(nextText);

  const cursorStart = start + prefix.length;
  const cursorEnd = cursorStart + selected.length;
  requestAnimationFrame(() => {
    textarea.focus();
    textarea.setSelectionRange(cursorStart, cursorEnd);
  });
}

function insertLinePrefix(
  text: string,
  setText: (next: string) => void,
  textarea: HTMLTextAreaElement | null,
  prefix: string,
  fallback: string,
) {
  if (!textarea) {
    setText(`${text}\n${prefix}${fallback}`);
    return;
  }

  const start = textarea.selectionStart ?? text.length;
  const end = textarea.selectionEnd ?? text.length;
  const selected = text.slice(start, end);

  if (!selected) {
    const nextText = `${text.slice(0, start)}${prefix}${fallback}${text.slice(end)}`;
    setText(nextText);
    const cursor = start + prefix.length;
    requestAnimationFrame(() => {
      textarea.focus();
      textarea.setSelectionRange(cursor, cursor + fallback.length);
    });
    return;
  }

  const updatedSelection = selected
    .split("\n")
    .map((line) => `${prefix}${line}`)
    .join("\n");
  const nextText = `${text.slice(0, start)}${updatedSelection}${text.slice(end)}`;
  setText(nextText);
  requestAnimationFrame(() => {
    textarea.focus();
    textarea.setSelectionRange(start, start + updatedSelection.length);
  });
}

interface MarkdownEditorProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  helperText?: string;
  required?: boolean;
  t: (zh: string, en: string) => string;
}

function MarkdownEditor({
  label,
  value,
  onChange,
  placeholder,
  helperText,
  required = false,
  t,
}: MarkdownEditorProps) {
  const [mode, setMode] = useState<MarkdownViewMode>("split");
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const previewHtml = useMemo(() => renderMarkdownPreview(value), [value]);
  const lineCount = value ? value.split(/\r?\n/).length : 0;

  function appendTemplate(template: string) {
    const next = value.trim() ? `${value.replace(/\s+$/, "")}\n\n${template}` : template;
    onChange(next);
    requestAnimationFrame(() => {
      textareaRef.current?.focus();
      textareaRef.current?.setSelectionRange(next.length, next.length);
    });
  }

  function applyBold() {
    insertAroundSelection(value, onChange, textareaRef.current, "**", "**", t("加粗文字", "bold text"));
  }

  function applyItalic() {
    insertAroundSelection(value, onChange, textareaRef.current, "*", "*", t("斜体文字", "italic text"));
  }

  function applyStrike() {
    insertAroundSelection(value, onChange, textareaRef.current, "~~", "~~", t("删除线文字", "strikethrough text"));
  }

  function applyHeading() {
    insertLinePrefix(value, onChange, textareaRef.current, "## ", t("小标题", "Heading"));
  }

  function applyList() {
    insertLinePrefix(value, onChange, textareaRef.current, "- ", t("列表项", "list item"));
  }

  function applyTaskList() {
    insertLinePrefix(value, onChange, textareaRef.current, "- [ ] ", t("待办项", "todo item"));
  }

  function applyQuote() {
    insertLinePrefix(value, onChange, textareaRef.current, "> ", t("引用内容", "quoted text"));
  }

  function applyCodeBlock() {
    insertAroundSelection(value, onChange, textareaRef.current, "```text\n", "\n```", t("代码片段", "code snippet"));
  }

  function applyDivider() {
    insertAroundSelection(value, onChange, textareaRef.current, "\n---\n", "", "");
  }

  function applyLink() {
    insertAroundSelection(value, onChange, textareaRef.current, "[", "](https://example.com)", t("链接文本", "link text"));
  }

  function applyTableTemplate() {
    appendTemplate(`| Field | Value |\n| --- | --- |\n| Summary | ${t("填写摘要", "write summary")} |\n| Signal | ${t("填写关键信号", "write key signal")} |`);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    const withCommand = event.ctrlKey || event.metaKey;
    if (!withCommand) {
      return;
    }
    if (event.key.toLowerCase() === "b") {
      event.preventDefault();
      applyBold();
      return;
    }
    if (event.key.toLowerCase() === "i") {
      event.preventDefault();
      applyItalic();
      return;
    }
    if (event.key.toLowerCase() === "k" && event.shiftKey) {
      event.preventDefault();
      applyLink();
    }
  }

  return (
    <div className="space-y-2 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span>{label}</span>
        <div className="inline-flex items-center gap-1 rounded-md border border-border bg-background p-1">
          <Button type="button" size="sm" variant={mode === "edit" ? "default" : "ghost"} onClick={() => setMode("edit")}>
            {t("编辑", "Edit")}
          </Button>
          <Button
            type="button"
            size="sm"
            variant={mode === "preview" ? "default" : "ghost"}
            onClick={() => setMode("preview")}
          >
            {t("预览", "Preview")}
          </Button>
          <Button
            type="button"
            size="sm"
            variant={mode === "split" ? "default" : "ghost"}
            onClick={() => setMode("split")}
          >
            {t("分栏", "Split")}
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 rounded-md border border-border/70 bg-background p-2">
        <Button type="button" size="sm" variant="outline" onClick={applyHeading}>
          H2
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyBold}>
          Bold
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyItalic}>
          Italic
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyStrike}>
          Strike
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyLink}>
          Link
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyList}>
          List
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyTaskList}>
          Task
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyQuote}>
          Quote
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyCodeBlock}>
          Code
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyTableTemplate}>
          Table
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={applyDivider}>
          Divider
        </Button>
        <Button
          type="button"
          size="sm"
          variant="outline"
          onClick={() =>
            appendTemplate(
              `## ${t("摘要", "Summary")}\n- ${t("这个仓库做什么", "What this repository does")}\n- ${t("适合什么用户", "Who should use it")}\n\n## ${t("关键亮点", "Key signals")}\n- ${t("实现特点", "Implementation trait")}`,
            )
          }
        >
          Template
        </Button>
      </div>

      <div className={mode === "split" ? "grid gap-3 md:grid-cols-2" : "space-y-3"}>
        {(mode === "edit" || mode === "split") ? (
          <textarea
            ref={textareaRef}
            required={required}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            className="min-h-56 w-full rounded-md border border-border bg-background px-3 py-2 text-sm shadow-xs transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          />
        ) : null}

        {(mode === "preview" || mode === "split") ? (
          <div className="min-h-56 rounded-md border border-border bg-muted/10 p-3">
            {value.trim() ? (
              <div className="space-y-2" dangerouslySetInnerHTML={{ __html: previewHtml }} />
            ) : (
              <p className="text-xs text-muted-foreground">
                {t("暂无内容可预览。", "No markdown content to preview yet.")}
              </p>
            )}
          </div>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>
          {t("字符", "Chars")}: {value.length} · {t("行数", "Lines")}: {lineCount}
        </span>
        <span>
          {t("预览为轻量渲染，最终展示以主站渲染结果为准。", "Preview is lightweight. Final rendering follows main-site renderer.")}
        </span>
        <span>{t("快捷键：Ctrl/Cmd+B 粗体，Ctrl/Cmd+I 斜体，Ctrl/Cmd+Shift+K 链接。", "Shortcuts: Ctrl/Cmd+B bold, Ctrl/Cmd+I italic, Ctrl/Cmd+Shift+K link.")}</span>
      </div>
      {helperText ? <p className="text-xs text-muted-foreground">{helperText}</p> : null}
    </div>
  );
}

export function SpecimensPanel() {
  const { t } = useAdminI18n();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [step, setStep] = useState<WorkflowStep>("import");
  const [curationValidated, setCurationValidated] = useState(false);
  const [importResult, setImportResult] = useState<ImportSpecimenResult | null>(null);

  const [githubUrl, setGithubUrl] = useState("");

  const [specimenId, setSpecimenId] = useState("");
  const [languagesText, setLanguagesText] = useState("");
  const [tagsText, setTagsText] = useState("");
  const [excerptType, setExcerptType] = useState("SUMMARY");
  const [excerptCandidateId, setExcerptCandidateId] = useState("");
  const [excerptPriority, setExcerptPriority] = useState("1");
  const [excerptText, setExcerptText] = useState("");
  const [excerptTranslatedTextZh, setExcerptTranslatedTextZh] = useState("");
  const [excerptTranslationMeta, setExcerptTranslationMeta] = useState("");
  const [snapshotEnabled, setSnapshotEnabled] = useState(false);
  const [oneLinerZh, setOneLinerZh] = useState("");
  const [oneLinerEn, setOneLinerEn] = useState("");
  const [arenaReasonZh, setArenaReasonZh] = useState("");
  const [arenaReasonEn, setArenaReasonEn] = useState("");
  const [ownerLogin, setOwnerLogin] = useState("");
  const [ownerUserId, setOwnerUserId] = useState("");
  const [ownerAvatarUrl, setOwnerAvatarUrl] = useState("");
  const [ownerHtmlUrl, setOwnerHtmlUrl] = useState("");
  const [ownerContributions, setOwnerContributions] = useState("");
  const [maintainersText, setMaintainersText] = useState("");
  const [contributorsText, setContributorsText] = useState("");
  const [codeTitle, setCodeTitle] = useState("");
  const [codeCandidateId, setCodeCandidateId] = useState("");
  const [codeLanguage, setCodeLanguage] = useState("");
  const [codeSnippet, setCodeSnippet] = useState("");
  const [codeExplainText, setCodeExplainText] = useState("");
  const [codePriority, setCodePriority] = useState("1");
  const [note, setNote] = useState("");

  const [reviewSpecimenId, setReviewSpecimenId] = useState("");
  const [reviewAction, setReviewAction] = useState<"APPROVE" | "REJECT">("APPROVE");
  const [reviewReason, setReviewReason] = useState("");
  const [deactivateSpecimenId, setDeactivateSpecimenId] = useState("");
  const [deactivateReason, setDeactivateReason] = useState("");
  const [tagSpecimenId, setTagSpecimenId] = useState("");
  const [tagUpdateText, setTagUpdateText] = useState("");
  const [draftUpdatedAt, setDraftUpdatedAt] = useState<string | null>(null);

  const fieldAnchorsRef = useRef<Partial<Record<FieldErrorKey, HTMLDivElement | null>>>({});
  const parsedGithubUrl = useMemo(() => parseGithubRepoUrl(githubUrl), [githubUrl]);
  const canImport = Boolean(parsedGithubUrl);

  const fieldLabels = useMemo<Record<FieldErrorKey, string>>(
    () => ({
      specimenId: t("标本 ID", "Specimen ID"),
      languages: t("语言列表", "Languages"),
      tags: t("标签", "Tags"),
      excerptText: t("README 摘要", "README excerpt"),
      excerptTranslationMeta: t("翻译元数据", "Translation meta"),
      officialCommentary: t("官方文案", "Official commentary"),
      owner: t("仓库 owner", "Repository owner"),
    }),
    [t],
  );

  const steps = useMemo(
    () => [
      { key: "import", label: t("导入", "Import") },
      { key: "curate", label: t("整理", "Curate") },
      { key: "submit", label: t("提交", "Submit") },
      { key: "review", label: t("审核", "Review") },
    ],
    [t],
  );

  const maxUnlockedStep =
    step === "review"
      ? STEP_ORDER.review
      : curationValidated
        ? STEP_ORDER.submit
        : STEP_ORDER.curate;
  const curationSummary = useMemo(() => {
    let translationMetaValid = true;
    try {
      parseTranslationMeta(excerptTranslationMeta, t);
    } catch {
      translationMetaValid = false;
    }
    return {
      languages: parseLanguages(languagesText).length,
      tags: parseTags(tagsText).length,
      hasTranslatedReadmeZh: excerptTranslatedTextZh.trim().length > 0,
      translationMetaValid,
      hasCodeHighlight: codeLanguage.trim().length > 0 && codeSnippet.trim().length > 0,
    };
  }, [codeLanguage, codeSnippet, excerptTranslatedTextZh, excerptTranslationMeta, languagesText, tagsText, t]);

  const curationHasContent = useMemo(
    () =>
      [
        githubUrl,
        specimenId,
        languagesText,
        tagsText,
        excerptText,
        excerptTranslatedTextZh,
        oneLinerZh,
        oneLinerEn,
        arenaReasonZh,
        arenaReasonEn,
        ownerLogin,
        ownerUserId,
        codeSnippet,
        note,
      ].some((value) => value.trim().length > 0),
    [
      arenaReasonEn,
      arenaReasonZh,
      codeSnippet,
      excerptText,
      excerptTranslatedTextZh,
      githubUrl,
      languagesText,
      note,
      oneLinerEn,
      oneLinerZh,
      ownerLogin,
      ownerUserId,
      specimenId,
      tagsText,
    ],
  );

  function bindFieldAnchor(key: FieldErrorKey) {
    return (node: HTMLDivElement | null) => {
      fieldAnchorsRef.current[key] = node;
    };
  }

  function scrollToField(key: FieldErrorKey) {
    const target = fieldAnchorsRef.current[key];
    if (!target) {
      return;
    }
    target.scrollIntoView({ behavior: "smooth", block: "center" });
    const focusable = target.querySelector("input, textarea, button");
    if (focusable instanceof HTMLElement) {
      focusable.focus();
    }
  }

  function clearDraftStorage() {
    localStorage.removeItem(DRAFT_STORAGE_KEY);
    setDraftUpdatedAt(null);
  }

  function replaceLanguageByCandidates(limit = 8) {
    if (!importResult?.languageCandidates.length) {
      return;
    }
    const lines = importResult.languageCandidates
      .slice(0, limit)
      .map((item) => `${item.name}:${item.percentage.toFixed(1)}`)
      .join("\n");
    setLanguagesText(lines);
    clearFieldError("languages");
    clearCurationValidation();
  }

  function appendLanguageByCandidates(limit = 8) {
    if (!importResult?.languageCandidates.length) {
      return;
    }
    const existing = parseLanguages(languagesText).map((item) => item.name.toLowerCase());
    const additions = importResult.languageCandidates
      .slice(0, limit)
      .filter((item) => !existing.includes(item.name.toLowerCase()))
      .map((item) => `${item.name}:${item.percentage.toFixed(1)}`);
    if (additions.length === 0) {
      return;
    }
    const prefix = languagesText.trim() ? `${languagesText.replace(/\s+$/, "")}\n` : "";
    setLanguagesText(`${prefix}${additions.join("\n")}`);
    clearFieldError("languages");
    clearCurationValidation();
  }

  function fillOwnerFromCandidates() {
    const owner = importResult?.repoIdentityCandidates.owner;
    if (!owner) {
      return;
    }
    setOwnerLogin(owner.githubLogin);
    setOwnerUserId(owner.githubUserId);
    setOwnerAvatarUrl(owner.githubAvatarUrl ?? "");
    setOwnerHtmlUrl(owner.githubHtmlUrl ?? "");
    setOwnerContributions(owner.contributions == null ? "" : String(owner.contributions));
    clearFieldError("owner");
    clearCurationValidation();
  }

  function fillContributorsFromCandidates() {
    const contributors = importResult?.repoIdentityCandidates.contributors;
    if (!contributors?.length) {
      return;
    }
    const lines = contributors
      .map((item) =>
        [
          item.githubLogin,
          item.githubUserId,
          item.githubAvatarUrl ?? "",
          item.githubHtmlUrl ?? "",
          item.contributions == null ? "" : String(item.contributions),
        ].join(","),
      )
      .join("\n");
    setContributorsText(lines);
    clearCurationValidation();
  }

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const rawDraft = localStorage.getItem(DRAFT_STORAGE_KEY);
    if (!rawDraft) {
      return;
    }
    try {
      const draft = JSON.parse(rawDraft) as CurationDraft;
      if (!draft || typeof draft !== "object") {
        return;
      }
      setGithubUrl(draft.githubUrl ?? "");
      setSpecimenId(draft.specimenId ?? "");
      setLanguagesText(draft.languagesText ?? "");
      setTagsText(draft.tagsText ?? "");
      setExcerptType(draft.excerptType ?? "SUMMARY");
      setExcerptCandidateId(draft.excerptCandidateId ?? "");
      setExcerptPriority(draft.excerptPriority ?? "1");
      setExcerptText(draft.excerptText ?? "");
      setExcerptTranslatedTextZh(draft.excerptTranslatedTextZh ?? "");
      setExcerptTranslationMeta(draft.excerptTranslationMeta ?? "");
      setSnapshotEnabled(Boolean(draft.snapshotEnabled));
      setOneLinerZh(draft.oneLinerZh ?? "");
      setOneLinerEn(draft.oneLinerEn ?? "");
      setArenaReasonZh(draft.arenaReasonZh ?? "");
      setArenaReasonEn(draft.arenaReasonEn ?? "");
      setOwnerLogin(draft.ownerLogin ?? "");
      setOwnerUserId(draft.ownerUserId ?? "");
      setOwnerAvatarUrl(draft.ownerAvatarUrl ?? "");
      setOwnerHtmlUrl(draft.ownerHtmlUrl ?? "");
      setOwnerContributions(draft.ownerContributions ?? "");
      setMaintainersText(draft.maintainersText ?? "");
      setContributorsText(draft.contributorsText ?? "");
      setCodeTitle(draft.codeTitle ?? "");
      setCodeCandidateId(draft.codeCandidateId ?? "");
      setCodeLanguage(draft.codeLanguage ?? "");
      setCodeSnippet(draft.codeSnippet ?? "");
      setCodeExplainText(draft.codeExplainText ?? "");
      setCodePriority(draft.codePriority ?? "1");
      setNote(draft.note ?? "");
      setDraftUpdatedAt(draft.updatedAt ?? null);
      if ((draft.specimenId ?? "").trim()) {
        setStep("curate");
      }
      setMessage(t("已恢复本地草稿，可继续整理。", "Restored local draft. Continue curation."));
    } catch {
      localStorage.removeItem(DRAFT_STORAGE_KEY);
    }
  }, [t]);

  useEffect(() => {
    if (typeof window === "undefined" || !curationHasContent) {
      return;
    }
    const draft: CurationDraft = {
      updatedAt: new Date().toISOString(),
      githubUrl,
      specimenId,
      languagesText,
      tagsText,
      excerptType,
      excerptCandidateId,
      excerptPriority,
      excerptText,
      excerptTranslatedTextZh,
      excerptTranslationMeta,
      snapshotEnabled,
      oneLinerZh,
      oneLinerEn,
      arenaReasonZh,
      arenaReasonEn,
      ownerLogin,
      ownerUserId,
      ownerAvatarUrl,
      ownerHtmlUrl,
      ownerContributions,
      maintainersText,
      contributorsText,
      codeTitle,
      codeCandidateId,
      codeLanguage,
      codeSnippet,
      codeExplainText,
      codePriority,
      note,
    };
    localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft));
    setDraftUpdatedAt(draft.updatedAt);
  }, [
    arenaReasonEn,
    arenaReasonZh,
    codeCandidateId,
    codeExplainText,
    codeLanguage,
    codePriority,
    codeSnippet,
    codeTitle,
    contributorsText,
    curationHasContent,
    excerptCandidateId,
    excerptPriority,
    excerptText,
    excerptTranslatedTextZh,
    excerptTranslationMeta,
    excerptType,
    githubUrl,
    languagesText,
    maintainersText,
    note,
    oneLinerEn,
    oneLinerZh,
    ownerAvatarUrl,
    ownerContributions,
    ownerHtmlUrl,
    ownerLogin,
    ownerUserId,
    snapshotEnabled,
    specimenId,
    tagsText,
  ]);

  function clearTips() {
    setMessage(null);
    setError(null);
  }

  function clearFieldError(key: FieldErrorKey) {
    setFieldErrors((current) => {
      if (!current[key]) {
        return current;
      }
      const next = { ...current };
      delete next[key];
      return next;
    });
  }

  function clearCurationValidation() {
    setCurationValidated(false);
  }

  async function withFeedback(task: () => Promise<void>) {
    setLoading(true);
    clearTips();
    try {
      await task();
    } catch (unknownError) {
      setError(unknownError instanceof Error ? unknownError.message : t("操作失败。", "Operation failed."));
    } finally {
      setLoading(false);
    }
  }

  function fillCurationDefaults(result: ImportSpecimenResult) {
    setFieldErrors({});
    setCurationValidated(false);
    setSpecimenId(result.specimenId);
    setReviewSpecimenId(result.specimenId);
    setDeactivateSpecimenId(result.specimenId);
    setTagSpecimenId(result.specimenId);

    const languageLines =
      result.languageCandidates.length > 0
        ? result.languageCandidates
            .slice(0, 5)
            .map((item) => `${item.name}:${item.percentage.toFixed(1)}`)
            .join("\n")
        : result.fetchedMeta.languages.map((item) => `${item.name}:${item.percentage ?? 0}`).join("\n");
    setLanguagesText(languageLines);

    const firstReadme = result.readmeCandidates[0];
    if (firstReadme) {
      setExcerptCandidateId(firstReadme.candidateId);
      setExcerptText(firstReadme.text);
    }
    setExcerptTranslatedTextZh("");
    setExcerptTranslationMeta("");

    const owner = result.repoIdentityCandidates.owner;
    if (owner) {
      setOwnerLogin(owner.githubLogin);
      setOwnerUserId(owner.githubUserId);
      setOwnerAvatarUrl(owner.githubAvatarUrl ?? "");
      setOwnerHtmlUrl(owner.githubHtmlUrl ?? "");
      setOwnerContributions(owner.contributions == null ? "" : String(owner.contributions));
    }

    const contributorLines = result.repoIdentityCandidates.contributors
      .map((item) =>
        [
          item.githubLogin,
          item.githubUserId,
          item.githubAvatarUrl ?? "",
          item.githubHtmlUrl ?? "",
          item.contributions == null ? "" : String(item.contributions),
        ].join(","),
      )
      .join("\n");
    setContributorsText(contributorLines);

    const firstCode = result.codeCandidates[0];
    if (firstCode) {
      setCodeCandidateId(firstCode.candidateId);
      setCodeLanguage(firstCode.codeLanguage ?? "");
      setCodeSnippet(firstCode.text);
      setCodeTitle(firstCode.heading ?? t("代码亮点", "Code highlight"));
      setCodeExplainText(t("来自候选代码片段，可手动改写说明。", "Imported from candidate snippet. You can rewrite this explanation."));
    }

    const repoName = result.fetchedMeta.repoName || result.specimenId;
    setOneLinerZh(`${repoName} 值得收录。`);
    setOneLinerEn(`${repoName} deserves to be archived.`);
    setArenaReasonZh(`${repoName} 适合进入 Arena 对决。`);
    setArenaReasonEn(`${repoName} is suitable for Arena matchups.`);
  }

  async function submitImport(event: FormEvent) {
    event.preventDefault();
    if (!canImport) {
      setError(
        t(
          "请输入有效 GitHub 仓库地址，例如 https://github.com/org/repo。",
          "Please enter a valid GitHub repository URL like https://github.com/org/repo.",
        ),
      );
      return;
    }
    await withFeedback(async () => {
      const result = await importSpecimen(githubUrl);
      setImportResult(result);
      fillCurationDefaults(result);
      setStep("curate");
      setMessage(
        t("导入成功，已进入整理步骤：", "Import succeeded. Proceed to curate step: ") +
          `${result.specimenId}`,
      );
    });
  }

  function validateAndBuildSubmitPayload(): {
    payload: SubmitSpecimenInput | null;
    errors: FieldErrors;
  } {
    const nextErrors: FieldErrors = {};

    const languages = parseLanguages(languagesText);
    if (languages.length === 0) {
      nextErrors.languages = t(
        "至少填写一条语言，格式 name:percentage。",
        "At least one language is required in name:percentage format.",
      );
    }

    const tags = parseTags(tagsText);
    if (tags.length === 0) {
      nextErrors.tags = t(
        "至少填写一个标签，格式 dimension:tag。",
        "At least one tag is required in dimension:tag format.",
      );
    }

    let translationMeta: Record<string, unknown> | undefined;
    try {
      translationMeta = parseTranslationMeta(excerptTranslationMeta, t);
    } catch (translationError) {
      nextErrors.excerptTranslationMeta =
        translationError instanceof Error
          ? translationError.message
          : t("translationMeta 校验失败。", "Failed to validate translationMeta.");
    }

    if (!excerptText.trim()) {
      nextErrors.excerptText = t("README 摘要不能为空。", "README excerpt is required.");
    }

    if (!oneLinerZh.trim() || !oneLinerEn.trim() || !arenaReasonZh.trim() || !arenaReasonEn.trim()) {
      nextErrors.officialCommentary = t(
        "官方文案需同时提供中文和英文的一句话点评与 Arena 理由。",
        "Official commentary requires zh/en one-liner and Arena reason.",
      );
    }

    if (!ownerLogin.trim() || !ownerUserId.trim()) {
      nextErrors.owner = t(
        "Owner 的 githubLogin 与 githubUserId 不能为空。",
        "Owner githubLogin and githubUserId are required.",
      );
    }

    if (!specimenId.trim()) {
      nextErrors.specimenId = t("缺少 specimenId，请先导入标本。", "Missing specimenId. Import a specimen first.");
    }

    if (hasFieldErrors(nextErrors)) {
      return { payload: null, errors: nextErrors };
    }

    const maintainers = parseIdentityLines(maintainersText);
    const contributors = parseIdentityLines(contributorsText);
    const codeHighlights =
      codeSnippet.trim() && codeLanguage.trim()
        ? [
            {
              title: codeTitle.trim() || t("代码亮点", "Code highlight"),
              candidateId: codeCandidateId.trim() || undefined,
              codeLanguage: codeLanguage.trim(),
              snippet: codeSnippet.trim(),
              explainText: codeExplainText.trim() || t("待补充说明。", "Pending explanation."),
              priority: Number.isFinite(Number(codePriority)) ? Number(codePriority) : 1,
            },
          ]
        : [];

    const payload: SubmitSpecimenInput = {
      languages,
      tags,
      readmeCuration: {
        excerpts: [
          {
            excerptType: excerptType.trim() || "SUMMARY",
            candidateId: excerptCandidateId.trim() || undefined,
            text: excerptText.trim(),
            translatedTextZh: excerptTranslatedTextZh.trim() || undefined,
            translationMeta,
            priority: Number.isFinite(Number(excerptPriority)) ? Number(excerptPriority) : 1,
          },
        ],
      },
      readmeSnapshotDecision: {
        enabled: snapshotEnabled,
      },
      officialCommentary: {
        oneLinerZh: oneLinerZh.trim(),
        oneLinerEn: oneLinerEn.trim(),
        arenaReasonZh: arenaReasonZh.trim(),
        arenaReasonEn: arenaReasonEn.trim(),
      },
      codeHighlights,
      repoIdentity: {
        owner: {
          githubLogin: ownerLogin.trim(),
          githubUserId: ownerUserId.trim(),
          githubAvatarUrl: ownerAvatarUrl.trim() || undefined,
          githubHtmlUrl: ownerHtmlUrl.trim() || undefined,
          contributions: Number.isFinite(Number(ownerContributions))
            ? Number(ownerContributions)
            : undefined,
        },
        maintainers,
        contributors,
      },
      note: note.trim() || undefined,
    };

    return { payload, errors: {} };
  }

  async function goToSubmitStep(event: FormEvent) {
    event.preventDefault();
    await withFeedback(async () => {
      const { payload, errors } = validateAndBuildSubmitPayload();
      setFieldErrors(errors);
      if (!payload) {
        const firstErrorKey = firstFieldErrorKey(errors);
        if (firstErrorKey) {
          scrollToField(firstErrorKey);
        }
        throw new Error(firstFieldError(errors) ?? t("请先修复表单错误。", "Please fix form errors first."));
      }
      setCurationValidated(true);
      setStep("submit");
      setMessage(t("整理信息已通过校验，可以提交上架。", "Curation validated. Ready to submit."));
    });
  }

  async function submitCuration(event: FormEvent) {
    event.preventDefault();
    await withFeedback(async () => {
      const { payload, errors } = validateAndBuildSubmitPayload();
      setFieldErrors(errors);
      if (!payload) {
        setStep("curate");
        const firstErrorKey = firstFieldErrorKey(errors);
        if (firstErrorKey) {
          scrollToField(firstErrorKey);
        }
        throw new Error(firstFieldError(errors) ?? t("请先修复表单错误。", "Please fix form errors first."));
      }
      const result = await submitSpecimen(specimenId, payload);
      setReviewSpecimenId(result.specimenId);
      setDeactivateSpecimenId(result.specimenId);
      setTagSpecimenId(result.specimenId);
      setCurationValidated(true);
      setStep("review");
      clearDraftStorage();
      setMessage(
        t("提交成功，已进入审核步骤：", "Submit succeeded. Proceed to review step: ") +
          `${result.specimenId} -> ${result.status}`,
      );
    });
  }

  async function submitReview(event: FormEvent) {
    event.preventDefault();
    await withFeedback(async () => {
      const result = await reviewSpecimen(
        reviewSpecimenId,
        reviewAction,
        reviewAction === "REJECT" ? reviewReason : undefined,
      );
      setMessage(`${t("审核完成", "Review completed")}: ${result.specimenId} -> ${result.status}`);
    });
  }

  async function submitDeactivate(event: FormEvent) {
    event.preventDefault();
    await withFeedback(async () => {
      const result = await deactivateSpecimen(deactivateSpecimenId, deactivateReason);
      setMessage(`${t("已下线", "Deactivated")}: ${result.specimenId} -> ${result.status}`);
    });
  }

  async function submitTags(event: FormEvent) {
    event.preventDefault();
    await withFeedback(async () => {
      const tags = parseTags(tagUpdateText);
      if (tags.length === 0) {
        throw new Error(t("请至少输入一个标签，格式为 dimension:tag。", "At least one tag is required in dimension:tag format."));
      }
      const result = await updateSpecimenTags(tagSpecimenId, tags);
      setMessage(`${t("标签更新完成", "Tags updated")}: ${result.specimenId} -> ${result.status}`);
    });
  }

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-xl font-semibold">{t("标本上架", "Specimen onboarding")}</h1>
        <p className="text-sm text-muted-foreground">
          {t(
            "按流程执行导入、整理、提交与审核。高级操作（下线/标签修订）放在最后处理。",
            "Run import, curation, submit, and review in order. Advanced operations (deactivate/tag update) are listed at the end.",
          )}
        </p>
        {draftUpdatedAt ? (
          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span>
              {t("本地草稿已保存于", "Local draft saved at")}{" "}
              {new Date(draftUpdatedAt).toLocaleString()}
            </span>
            <Button type="button" size="sm" variant="outline" onClick={clearDraftStorage}>
              {t("清除草稿", "Clear draft")}
            </Button>
          </div>
        ) : null}
      </header>

      <div className="flex flex-wrap gap-2">
        {steps.map((item) => {
          const nextStep = item.key as WorkflowStep;
          const active = nextStep === step;
          const locked = STEP_ORDER[nextStep] > maxUnlockedStep && !active;
          return (
            <button
              key={item.key}
              type="button"
              disabled={locked}
              aria-disabled={locked}
              className={[
                "rounded-md border px-3 py-1 text-sm transition-colors",
                active
                  ? "border-foreground bg-foreground text-background"
                  : locked
                    ? "cursor-not-allowed border-border/70 bg-muted/30 text-muted-foreground/70"
                    : "border-border bg-background text-muted-foreground hover:bg-secondary",
              ].join(" ")}
              onClick={() => {
                if (!locked) {
                  setStep(nextStep);
                }
              }}
            >
              {item.label}
            </button>
          );
        })}
      </div>

      {message ? (
        <p className="text-sm text-emerald-700" role="status" aria-live="polite">
          {message}
        </p>
      ) : null}
      {error ? (
        <p className="text-sm text-rose-700" role="alert" aria-live="assertive">
          {error}
        </p>
      ) : null}

      <SectionCard
        title={t("步骤 1：导入仓库", "Step 1: Import repository")}
        description={t(
          "输入 GitHub 仓库地址，生成 DRAFT 标本并拉取候选数据。",
          "Input GitHub repository URL to create a draft specimen and fetch curation candidates.",
        )}
      >
        <form className="flex flex-col gap-3 md:flex-row" onSubmit={submitImport}>
          <Input
            required
            value={githubUrl}
            onChange={(event) => setGithubUrl(event.target.value)}
            placeholder="https://github.com/org/repo"
          />
          <Button disabled={loading || !canImport} type="submit">
            {t("执行导入", "Run import")}
          </Button>
        </form>
        <div className="mt-2 text-xs">
          {githubUrl.trim().length === 0 ? (
            <p className="text-muted-foreground">
              {t(
                "建议输入标准地址格式：https://github.com/{owner}/{repo}",
                "Use standard format: https://github.com/{owner}/{repo}",
              )}
            </p>
          ) : parsedGithubUrl ? (
            <p className="text-emerald-700">
              {t("将导入仓库", "Will import")} {parsedGithubUrl.owner}/{parsedGithubUrl.repo}
            </p>
          ) : (
            <p className="text-rose-700">
              {t(
                "仓库地址格式无效，请使用 https://github.com/{owner}/{repo}",
                "Invalid repository URL. Expected https://github.com/{owner}/{repo}",
              )}
            </p>
          )}
        </div>
        {importResult ? (
          <div className="mt-3 space-y-2 rounded-md border border-border/70 bg-muted/10 p-3 text-xs text-muted-foreground">
            <p>
              {t("当前导入结果", "Current import result")}: {importResult.specimenId} ({importResult.status})
            </p>
            <p>
              {t("仓库", "Repository")}: {importResult.fetchedMeta.owner}/{importResult.fetchedMeta.repoName} ·{" "}
              {t("README 抓取", "README fetched")}:{" "}
              {importResult.fetchedMeta.readmeFetched ? t("成功", "Yes") : t("失败", "No")}
            </p>
            <p>
              {t("候选数量", "Candidate counts")}: README {importResult.readmeCandidates.length} · Code{" "}
              {importResult.codeCandidates.length} · {t("语言候选", "Language candidates")}{" "}
              {importResult.languageCandidates.length}
            </p>
            {!importResult.fetchedMeta.readmeFetched ? (
              <p className="text-rose-700">
                {t(
                  "README 未抓取成功，请在整理阶段手工补全摘要并标注来源。",
                  "README fetch failed. Please manually curate excerpt and annotate source in curation.",
                )}
              </p>
            ) : null}
          </div>
        ) : null}
      </SectionCard>

      {step !== "import" ? (
        <SectionCard
          title={t("步骤 2：整理内容", "Step 2: Curate content")}
          description={t(
            "填充 README 摘要、官方文案、语言标签和仓库身份信息。",
            "Fill README excerpt, official commentary, language tags, and repository identity.",
          )}
        >
          <form className="space-y-4" onSubmit={goToSubmitStep}>
            {hasFieldErrors(fieldErrors) ? (
              <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm">
                <p className="font-medium text-rose-700">
                  {t("请先修复以下字段问题：", "Please resolve these field issues first:")}
                </p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {FIELD_ERROR_ORDER.filter((key) => fieldErrors[key]).map((key) => (
                    <button
                      key={key}
                      type="button"
                      className="rounded-md border border-rose-300 bg-white px-2 py-1 text-xs text-rose-700 transition-colors hover:bg-rose-100"
                      onClick={() => scrollToField(key)}
                    >
                      {fieldLabels[key]}
                    </button>
                  ))}
                </div>
              </div>
            ) : null}

            {importResult ? (
              <div className="rounded-lg border border-border/80 bg-background p-3 text-xs">
                <p className="font-medium">{t("候选快速动作", "Candidate quick actions")}</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  <Button type="button" size="sm" variant="outline" onClick={() => replaceLanguageByCandidates(8)}>
                    {t("语言替换为 Top 8", "Replace languages with Top 8")}
                  </Button>
                  <Button type="button" size="sm" variant="outline" onClick={() => appendLanguageByCandidates(8)}>
                    {t("追加未收录语言", "Append missing languages")}
                  </Button>
                  <Button type="button" size="sm" variant="outline" onClick={fillOwnerFromCandidates}>
                    {t("回填 Owner", "Fill owner")}
                  </Button>
                  <Button type="button" size="sm" variant="outline" onClick={fillContributorsFromCandidates}>
                    {t("回填 Contributors", "Fill contributors")}
                  </Button>
                </div>
              </div>
            ) : null}

            <div className="rounded-lg border border-border/80 bg-muted/10 p-4">
              <p className="text-sm font-medium">{t("基础信息", "Basic information")}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {t(
                  "先确认 specimenId、语言与标签，这些会影响后续聚合与检索。",
                  "Confirm specimenId, languages, and tags first. They affect indexing and downstream aggregation.",
                )}
              </p>
              <div className="mt-3 space-y-3">
                <div ref={bindFieldAnchor("specimenId")}>
                  <Input
                    required
                    value={specimenId}
                    onChange={(event) => {
                      setSpecimenId(event.target.value);
                      clearFieldError("specimenId");
                      clearCurationValidation();
                    }}
                    placeholder="specimenId"
                  />
                </div>
                {fieldErrors.specimenId ? <FieldError text={fieldErrors.specimenId} /> : null}
                <div className="grid gap-3 md:grid-cols-2">
                  <div ref={bindFieldAnchor("languages")}>
                    <label className="space-y-2 text-sm">
                      <span>{t("语言列表（每行 name:percentage）", "Languages (one per line: name:percentage)")}</span>
                      <Textarea
                        required
                        value={languagesText}
                        onChange={(event) => {
                          setLanguagesText(event.target.value);
                          clearFieldError("languages");
                          clearCurationValidation();
                        }}
                        placeholder={"TypeScript:72.5\nPython:20.0"}
                      />
                    </label>
                  </div>
                  <div ref={bindFieldAnchor("tags")}>
                    <label className="space-y-2 text-sm">
                      <span>{t("标签（每行 dimension:tag）", "Tags (one per line: dimension:tag)")}</span>
                      <Textarea
                        required
                        value={tagsText}
                        onChange={(event) => {
                          setTagsText(event.target.value);
                          clearFieldError("tags");
                          clearCurationValidation();
                        }}
                        placeholder={"species:tooling\ndiagnosis:funny"}
                      />
                    </label>
                  </div>
                </div>
                {fieldErrors.languages ? <FieldError text={fieldErrors.languages} /> : null}
                {fieldErrors.tags ? <FieldError text={fieldErrors.tags} /> : null}
              </div>
            </div>

            <div className="rounded-lg border border-border/80 bg-muted/10 p-4">
              <p className="text-sm font-medium">{t("README 整理", "README curation")}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {t(
                  "维护原文摘要与中文译文。译文为平台维护内容，不等于 GitHub 原仓库提供。",
                  "Maintain original excerpt and Chinese translation. Translation is platform-maintained, not a native GitHub version.",
                )}
              </p>
              <div className="mt-3 space-y-3">
                <div className="grid gap-3 md:grid-cols-4">
                  <Input
                    value={excerptType}
                    onChange={(event) => {
                      setExcerptType(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("摘要类型", "Excerpt type")}
                  />
                  <Input
                    value={excerptCandidateId}
                    onChange={(event) => {
                      setExcerptCandidateId(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("候选 ID（可选）", "Candidate ID (optional)")}
                  />
                  <Input
                    value={excerptPriority}
                    onChange={(event) => {
                      setExcerptPriority(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("优先级", "Priority")}
                  />
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      variant={snapshotEnabled ? "default" : "outline"}
                      onClick={() => {
                        setSnapshotEnabled(true);
                        clearCurationValidation();
                      }}
                    >
                      {t("保存快照", "Snapshot on")}
                    </Button>
                    <Button
                      type="button"
                      variant={!snapshotEnabled ? "default" : "outline"}
                      onClick={() => {
                        setSnapshotEnabled(false);
                        clearCurationValidation();
                      }}
                    >
                      {t("不保存", "Snapshot off")}
                    </Button>
                  </div>
                </div>

                {importResult?.readmeCandidates.length ? (
                  <div className="space-y-2 rounded-md border border-border/70 bg-background p-3">
                    <p className="text-xs font-medium text-muted-foreground">
                      {t(
                        "README 候选（可一键回填，先选再编辑）",
                        "README candidates (one-click fill, then edit)",
                      )}
                    </p>
                    <div className="space-y-2">
                      {importResult.readmeCandidates.slice(0, 4).map((candidate) => (
                        <div key={candidate.candidateId} className="rounded-md border border-border/60 bg-muted/10 p-2">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <div className="text-xs text-muted-foreground">
                              <span className="font-medium text-foreground">
                                {candidate.heading || t("无标题候选", "Untitled candidate")}
                              </span>{" "}
                              · {candidate.candidateType} · {candidate.candidateId}
                              {candidate.score != null ? ` · score ${candidate.score.toFixed(2)}` : ""}
                            </div>
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                setExcerptCandidateId(candidate.candidateId);
                                setExcerptType(candidate.candidateType || excerptType);
                                setExcerptText(candidate.text);
                                clearFieldError("excerptText");
                                clearCurationValidation();
                              }}
                            >
                              {t("使用该候选", "Use candidate")}
                            </Button>
                          </div>
                          <p className="mt-2 max-h-24 overflow-auto whitespace-pre-wrap text-xs text-muted-foreground">
                            {candidate.text}
                          </p>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : null}

                <div ref={bindFieldAnchor("excerptText")}>
                  <MarkdownEditor
                    required
                    label={t("README 摘要正文", "README excerpt text")}
                    value={excerptText}
                    onChange={(nextValue) => {
                      setExcerptText(nextValue);
                      clearFieldError("excerptText");
                      clearCurationValidation();
                    }}
                    placeholder={t("填写面向用户的摘要内容", "Enter user-facing excerpt text")}
                    helperText={t(
                      "建议保留结构（标题/要点/链接），避免只贴纯文本。",
                      "Keep structural markdown (titles, bullets, links) instead of plain text.",
                    )}
                    t={t}
                  />
                </div>
                {fieldErrors.excerptText ? <FieldError text={fieldErrors.excerptText} /> : null}

                <div className="grid gap-3 md:grid-cols-2">
                  <MarkdownEditor
                    label={t("README 中文译文（可选）", "README translated text zh (optional)")}
                    value={excerptTranslatedTextZh}
                    onChange={(nextValue) => {
                      setExcerptTranslatedTextZh(nextValue);
                      clearCurationValidation();
                    }}
                    placeholder={t("填写平台维护的中文译文", "Enter platform-maintained Chinese translation")}
                    helperText={t(
                      "建议先保留术语英文原词，再在括号补充中文解释。",
                      "Keep original technical terms and add Chinese explanations in parentheses when needed.",
                    )}
                    t={t}
                  />
                  <div ref={bindFieldAnchor("excerptTranslationMeta")}>
                    <label className="space-y-2 text-sm">
                      <span>{t("翻译元数据 JSON（可选）", "Translation meta JSON (optional)")}</span>
                      <Textarea
                        value={excerptTranslationMeta}
                        onChange={(event) => {
                          setExcerptTranslationMeta(event.target.value);
                          clearFieldError("excerptTranslationMeta");
                          clearCurationValidation();
                        }}
                        placeholder={'{"source":"manual","quality":"draft"}'}
                      />
                    </label>
                    <div className="mt-2 flex flex-wrap gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          const preset = {
                            source: "manual",
                            method: "human-curated",
                            quality: "draft",
                          };
                          setExcerptTranslationMeta(JSON.stringify(preset, null, 2));
                          clearFieldError("excerptTranslationMeta");
                          clearCurationValidation();
                        }}
                      >
                        {t("插入默认 meta", "Insert default meta")}
                      </Button>
                    </div>
                  </div>
                </div>
                {fieldErrors.excerptTranslationMeta ? (
                  <FieldError text={fieldErrors.excerptTranslationMeta} />
                ) : null}
              </div>
            </div>

            <div ref={bindFieldAnchor("officialCommentary")} className="rounded-lg border border-border/80 bg-muted/10 p-4">
              <p className="text-sm font-medium">{t("官方文案", "Official commentary")}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {t(
                  "中英文都要完整，避免主站切换语言时出现缺失文案。",
                  "Both zh and en are required to avoid missing copy on language switch in the main site.",
                )}
              </p>
              <div className="mt-3 grid gap-3 md:grid-cols-2">
                <Input
                  required
                  value={oneLinerZh}
                  onChange={(event) => {
                    setOneLinerZh(event.target.value);
                    clearFieldError("officialCommentary");
                    clearCurationValidation();
                  }}
                  placeholder={t("一句话点评（中文）", "One-liner (zh)")}
                />
                <Input
                  required
                  value={oneLinerEn}
                  onChange={(event) => {
                    setOneLinerEn(event.target.value);
                    clearFieldError("officialCommentary");
                    clearCurationValidation();
                  }}
                  placeholder={t("一句话点评（英文）", "One-liner (en)")}
                />
                <Input
                  required
                  value={arenaReasonZh}
                  onChange={(event) => {
                    setArenaReasonZh(event.target.value);
                    clearFieldError("officialCommentary");
                    clearCurationValidation();
                  }}
                  placeholder={t("Arena 理由（中文）", "Arena reason (zh)")}
                />
                <Input
                  required
                  value={arenaReasonEn}
                  onChange={(event) => {
                    setArenaReasonEn(event.target.value);
                    clearFieldError("officialCommentary");
                    clearCurationValidation();
                  }}
                  placeholder={t("Arena 理由（英文）", "Arena reason (en)")}
                />
              </div>
              {fieldErrors.officialCommentary ? (
                <div className="mt-3">
                  <FieldError text={fieldErrors.officialCommentary} />
                </div>
              ) : null}
            </div>

            <div ref={bindFieldAnchor("owner")} className="rounded-lg border border-border/80 bg-muted/10 p-4">
              <p className="text-sm font-medium">{t("仓库身份", "Repository identity")}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {t(
                  "Owner 必填，maintainers/contributors 可按需补充。",
                  "Owner is required. Maintainers and contributors are optional.",
                )}
              </p>
              <div className="mt-3 space-y-3">
                <div className="grid gap-3 md:grid-cols-2">
                  <Input
                    required
                    value={ownerLogin}
                    onChange={(event) => {
                      setOwnerLogin(event.target.value);
                      clearFieldError("owner");
                      clearCurationValidation();
                    }}
                    placeholder="owner githubLogin"
                  />
                  <Input
                    required
                    value={ownerUserId}
                    onChange={(event) => {
                      setOwnerUserId(event.target.value);
                      clearFieldError("owner");
                      clearCurationValidation();
                    }}
                    placeholder="owner githubUserId"
                  />
                  <Input
                    value={ownerAvatarUrl}
                    onChange={(event) => {
                      setOwnerAvatarUrl(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder="owner avatar URL"
                  />
                  <Input
                    value={ownerHtmlUrl}
                    onChange={(event) => {
                      setOwnerHtmlUrl(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder="owner profile URL"
                  />
                  <Input
                    value={ownerContributions}
                    onChange={(event) => {
                      setOwnerContributions(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("owner 贡献次数（可选）", "owner contributions (optional)")}
                  />
                </div>
                {fieldErrors.owner ? <FieldError text={fieldErrors.owner} /> : null}

                <div className="grid gap-3 md:grid-cols-2">
                  <label className="space-y-2 text-sm">
                    <span>{t("Maintainers（每行 login,userId,avatarUrl,htmlUrl,contributions）", "Maintainers (line format: login,userId,avatarUrl,htmlUrl,contributions)")}</span>
                    <Textarea
                      value={maintainersText}
                      onChange={(event) => {
                        setMaintainersText(event.target.value);
                        clearCurationValidation();
                      }}
                      placeholder={t("可留空", "Optional")}
                    />
                  </label>
                  <label className="space-y-2 text-sm">
                    <span>{t("Contributors（每行 login,userId,avatarUrl,htmlUrl,contributions）", "Contributors (line format: login,userId,avatarUrl,htmlUrl,contributions)")}</span>
                    <Textarea
                      value={contributorsText}
                      onChange={(event) => {
                        setContributorsText(event.target.value);
                        clearCurationValidation();
                      }}
                      placeholder={t("可留空", "Optional")}
                    />
                  </label>
                </div>
              </div>
            </div>

            <div className="rounded-lg border border-border/80 bg-muted/10 p-4">
              <p className="text-sm font-medium">{t("代码高亮与备注", "Code highlights and note")}</p>
              <div className="mt-3 space-y-3">
                {importResult?.codeCandidates.length ? (
                  <div className="space-y-2 rounded-md border border-border/70 bg-background p-3">
                    <p className="text-xs font-medium text-muted-foreground">
                      {t("代码候选（可一键回填）", "Code candidates (one-click fill)")}
                    </p>
                    <div className="space-y-2">
                      {importResult.codeCandidates.slice(0, 3).map((candidate) => (
                        <div key={candidate.candidateId} className="rounded-md border border-border/60 bg-muted/10 p-2">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <div className="text-xs text-muted-foreground">
                              <span className="font-medium text-foreground">
                                {candidate.heading || t("代码片段候选", "Code candidate")}
                              </span>{" "}
                              · {candidate.candidateId}
                              {candidate.codeLanguage ? ` · ${candidate.codeLanguage}` : ""}
                            </div>
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                setCodeCandidateId(candidate.candidateId);
                                setCodeLanguage(candidate.codeLanguage || "");
                                setCodeSnippet(candidate.text);
                                setCodeTitle(candidate.heading || t("代码亮点", "Code highlight"));
                                setCodeExplainText(
                                  t(
                                    "来自导入候选，可按业务语境改写说明。",
                                    "Imported from candidate. Rewrite explanation for business context.",
                                  ),
                                );
                                clearCurationValidation();
                              }}
                            >
                              {t("使用该候选", "Use candidate")}
                            </Button>
                          </div>
                          <p className="mt-2 max-h-24 overflow-auto whitespace-pre-wrap text-xs text-muted-foreground">
                            {candidate.text}
                          </p>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : null}

                <div className="grid gap-3 md:grid-cols-2">
                  <Input
                    value={codeTitle}
                    onChange={(event) => {
                      setCodeTitle(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("代码高亮标题（可选）", "Code highlight title (optional)")}
                  />
                  <Input
                    value={codeCandidateId}
                    onChange={(event) => {
                      setCodeCandidateId(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("代码候选 ID（可选）", "Code candidate ID (optional)")}
                  />
                  <Input
                    value={codeLanguage}
                    onChange={(event) => {
                      setCodeLanguage(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("代码语言（填写后表示启用代码高亮）", "Code language (fill to enable code highlight)")}
                  />
                  <Input
                    value={codePriority}
                    onChange={(event) => {
                      setCodePriority(event.target.value);
                      clearCurationValidation();
                    }}
                    placeholder={t("代码优先级", "Code priority")}
                  />
                </div>

                <Textarea
                  value={codeSnippet}
                  onChange={(event) => {
                    setCodeSnippet(event.target.value);
                    clearCurationValidation();
                  }}
                  placeholder={t("代码片段（可选）", "Code snippet (optional)")}
                />
                <Textarea
                  value={codeExplainText}
                  onChange={(event) => {
                    setCodeExplainText(event.target.value);
                    clearCurationValidation();
                  }}
                  placeholder={t("代码说明（可选）", "Code explanation (optional)")}
                />
                <Textarea
                  value={note}
                  onChange={(event) => {
                    setNote(event.target.value);
                    clearCurationValidation();
                  }}
                  placeholder={t("内部备注（可选，不对外展示）", "Internal note (optional, not public)")}
                />
              </div>
            </div>

            <Button disabled={loading} type="submit">
              {t("校验并进入提交步骤", "Validate and continue to submit")}
            </Button>
          </form>
        </SectionCard>
      ) : null}

      {(step === "submit" || step === "review") ? (
        <SectionCard
          title={t("步骤 3：提交上架", "Step 3: Submit for review")}
          description={t(
            "调用 /api/v1/admin/specimens/{id}/submit，将状态推进到 PENDING。",
            "Call /api/v1/admin/specimens/{id}/submit and move status to Pending.",
          )}
        >
          <div className="mb-3 rounded-lg border border-border/80 bg-muted/10 p-3 text-sm">
            <p className="font-medium">{t("提交前检查", "Pre-submit checks")}</p>
            <div className="mt-2 grid gap-2 md:grid-cols-2">
              <p className="text-muted-foreground">
                {t("语言条目", "Language entries")}: {curationSummary.languages}
              </p>
              <p className="text-muted-foreground">
                {t("标签条目", "Tag entries")}: {curationSummary.tags}
              </p>
              <p className="text-muted-foreground">
                {t("中文译文", "Chinese translation")}:{" "}
                {curationSummary.hasTranslatedReadmeZh ? t("已填写", "Provided") : t("未填写", "Not provided")}
              </p>
              <p className="text-muted-foreground">
                {t("翻译元数据 JSON", "Translation meta JSON")}:{" "}
                {curationSummary.translationMetaValid ? t("有效", "Valid") : t("无效", "Invalid")}
              </p>
              <p className="text-muted-foreground">
                {t("代码高亮", "Code highlight")}:{" "}
                {curationSummary.hasCodeHighlight ? t("已启用", "Enabled") : t("未启用", "Disabled")}
              </p>
            </div>
          </div>
          <form className="space-y-3" onSubmit={submitCuration}>
            <Input
              required
              value={specimenId}
              onChange={(event) => setSpecimenId(event.target.value)}
              placeholder="specimenId"
            />
            <Button disabled={loading} type="submit">
              {t("提交上架申请", "Submit onboarding request")}
            </Button>
          </form>
        </SectionCard>
      ) : null}

      {step === "review" ? (
        <SectionCard
          title={t("步骤 4：审核发布", "Step 4: Review and publish")}
          description={t(
            "执行 approve/reject；reject 时必须填写原因。",
            "Run approve or reject. Reason is required for reject.",
          )}
        >
          <form className="space-y-3" onSubmit={submitReview}>
            <Input
              required
              value={reviewSpecimenId}
              onChange={(event) => setReviewSpecimenId(event.target.value)}
              placeholder="specimenId"
            />
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant={reviewAction === "APPROVE" ? "default" : "outline"}
                onClick={() => setReviewAction("APPROVE")}
              >
                {t("通过", "Approve")}
              </Button>
              <Button
                type="button"
                variant={reviewAction === "REJECT" ? "default" : "outline"}
                onClick={() => setReviewAction("REJECT")}
              >
                {t("拒绝", "Reject")}
              </Button>
            </div>
            {reviewAction === "REJECT" ? (
              <Textarea
                value={reviewReason}
                onChange={(event) => setReviewReason(event.target.value)}
                placeholder={t("拒绝原因", "Reject reason")}
                required
              />
            ) : null}
            <Button disabled={loading || !reviewSpecimenId.trim()} type="submit">
              {t("提交审核结果", "Submit review")}
            </Button>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title={t("高级操作", "Advanced operations")}
        description={t(
          "用于已上线或已收录标本的维护：下线与标签修订。",
          "Maintenance tools for listed specimens: deactivate and tag update.",
        )}
      >
        <div className="grid gap-4 md:grid-cols-2">
          <form className="space-y-3" onSubmit={submitDeactivate}>
            <Input
              required
              value={deactivateSpecimenId}
              onChange={(event) => setDeactivateSpecimenId(event.target.value)}
              placeholder="specimenId"
            />
            <Textarea
              value={deactivateReason}
              onChange={(event) => setDeactivateReason(event.target.value)}
              placeholder={t("下线原因（可选）", "Deactivation reason (optional)")}
            />
            <Button disabled={loading || !deactivateSpecimenId.trim()} type="submit">
              {t("执行下线", "Deactivate specimen")}
            </Button>
          </form>

          <form className="space-y-3" onSubmit={submitTags}>
            <Input
              required
              value={tagSpecimenId}
              onChange={(event) => setTagSpecimenId(event.target.value)}
              placeholder="specimenId"
            />
            <Textarea
              required
              value={tagUpdateText}
              onChange={(event) => setTagUpdateText(event.target.value)}
              placeholder={"stack:typescript\nlicense:mit"}
            />
            <Button disabled={loading || !tagSpecimenId.trim()} type="submit">
              {t("提交标签更新", "Update tags")}
            </Button>
          </form>
        </div>
      </SectionCard>
    </div>
  );
}

function FieldError({ text }: { text: string }) {
  return (
    <p className="text-xs text-rose-700" role="alert" aria-live="assertive">
      {text}
    </p>
  );
}
