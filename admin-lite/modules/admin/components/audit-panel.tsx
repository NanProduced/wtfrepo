"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { listAuditLogs } from "@/modules/admin/lib/request";
import { formatTime, shortText } from "@/modules/admin/lib/format";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";
import type { AuditLogItem } from "@/modules/admin/types";
import { SectionCard } from "@/modules/admin/components/panel-common";

export function AuditPanel() {
  const { locale, t } = useAdminI18n();
  const [items, setItems] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    operatorId: "",
    action: "",
    targetType: "",
    targetId: "",
  });

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await listAuditLogs(1, 40, {
        operatorId: filters.operatorId,
        action: filters.action,
        targetType: filters.targetType,
        targetId: filters.targetId,
      });
      setItems(result.items);
    } catch (unknownError) {
      setError(
        unknownError instanceof Error
          ? unknownError.message
          : t("加载审计日志失败。", "Failed to load audit logs."),
      );
    } finally {
      setLoading(false);
    }
  }, [filters, t]);

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  function handleFilterSubmit(event: FormEvent) {
    event.preventDefault();
    void loadLogs();
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t("审计日志", "Audit logs")}</h1>
          <p className="text-sm text-muted-foreground">
            {t(
              "检查运营动作、目标资源和请求轨迹，支持按关键字段过滤。",
              "Inspect admin actions, targets, and request traces with field-based filters.",
            )}
          </p>
        </div>
        <Button variant="outline" onClick={() => void loadLogs()} disabled={loading}>
          {t("刷新", "Refresh")}
        </Button>
      </header>

      <SectionCard title={t("过滤条件", "Filters")}>
        <form className="grid gap-2 md:grid-cols-4" onSubmit={handleFilterSubmit}>
          <Input
            placeholder="operatorId"
            value={filters.operatorId}
            onChange={(event) =>
              setFilters((current) => ({ ...current, operatorId: event.target.value }))
            }
          />
          <Input
            placeholder="action"
            value={filters.action}
            onChange={(event) =>
              setFilters((current) => ({ ...current, action: event.target.value }))
            }
          />
          <Input
            placeholder="targetType"
            value={filters.targetType}
            onChange={(event) =>
              setFilters((current) => ({ ...current, targetType: event.target.value }))
            }
          />
          <Input
            placeholder="targetId"
            value={filters.targetId}
            onChange={(event) =>
              setFilters((current) => ({ ...current, targetId: event.target.value }))
            }
          />
          <div className="md:col-span-4">
            <Button type="submit">{t("应用过滤", "Apply filters")}</Button>
          </div>
        </form>
      </SectionCard>

      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      <SectionCard title={t("日志明细", "Audit items")}>
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-muted-foreground">
              <tr>
                <th className="px-2 py-2 font-medium">{t("时间", "Time")}</th>
                <th className="px-2 py-2 font-medium">{t("操作人", "Operator")}</th>
                <th className="px-2 py-2 font-medium">{t("动作", "Action")}</th>
                <th className="px-2 py-2 font-medium">{t("目标", "Target")}</th>
                <th className="px-2 py-2 font-medium">{t("请求", "Request")}</th>
                <th className="px-2 py-2 font-medium">{t("元数据", "Metadata")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-t border-border/80 align-top">
                  <td className="px-2 py-2">{formatTime(item.createdAt, locale)}</td>
                  <td className="px-2 py-2">{item.operatorId}</td>
                  <td className="px-2 py-2">{item.action}</td>
                  <td className="px-2 py-2">
                    {item.targetType}:{item.targetId}
                  </td>
                  <td className="px-2 py-2">{item.requestId ?? "-"}</td>
                  <td className="px-2 py-2">
                    <code className="text-xs">{shortText(JSON.stringify(item.metadata), 120)}</code>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && !loading ? (
            <p className="px-2 py-4 text-sm text-muted-foreground">{t("暂无日志。", "No audit logs.")}</p>
          ) : null}
        </div>
      </SectionCard>
    </div>
  );
}
