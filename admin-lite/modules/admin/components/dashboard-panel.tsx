"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { acknowledgeAlert, getProfile, listAlerts, listSafetyTickets } from "@/modules/admin/lib/request";
import { formatTime, shortText } from "@/modules/admin/lib/format";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";
import type { AdminAlert, AdminProfile, SafetyTicket } from "@/modules/admin/types";
import { SectionCard } from "@/modules/admin/components/panel-common";

export function DashboardPanel() {
  const { locale, t } = useAdminI18n();
  const [profile, setProfile] = useState<AdminProfile | null>(null);
  const [tickets, setTickets] = useState<SafetyTicket[]>([]);
  const [alerts, setAlerts] = useState<AdminAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [profileData, ticketsData, alertsData] = await Promise.all([
        getProfile(),
        listSafetyTickets(1, 6),
        listAlerts(1, 6),
      ]);
      setProfile(profileData ?? null);
      setTickets(ticketsData.items);
      setAlerts(alertsData.items);
    } catch (unknownError) {
      setError(unknownError instanceof Error ? unknownError.message : t("加载失败。", "Failed to load."));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openTickets = useMemo(
    () => tickets.filter((item) => item.status === "OPEN" || item.status === "IN_REVIEW").length,
    [tickets],
  );

  const unackedAlerts = useMemo(
    () => alerts.filter((item) => !item.acknowledged).length,
    [alerts],
  );

  async function handleAcknowledge(alertId: string) {
    try {
      await acknowledgeAlert(alertId);
      await loadData();
    } catch (unknownError) {
      setError(
        unknownError instanceof Error
          ? unknownError.message
          : t("确认告警失败。", "Failed to acknowledge alert."),
      );
    }
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t("平台概览", "Overview")}</h1>
          <p className="text-sm text-muted-foreground">
            {t(
              "查看当前管理员身份、风险工单与告警状态。",
              "Check current admin identity, safety tickets, and alert status.",
            )}
          </p>
        </div>
        <Button variant="outline" onClick={() => void loadData()} disabled={loading}>
          {t("刷新数据", "Refresh")}
        </Button>
      </header>

      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      <div className="grid gap-3 md:grid-cols-3">
        <Metric
          title={t("当前管理员", "Current admin")}
          value={profile?.username ?? (loading ? t("加载中", "Loading") : t("未识别", "Unknown"))}
        />
        <Metric title={t("待处理工单", "Open tickets")} value={String(openTickets)} />
        <Metric title={t("未确认告警", "Unacknowledged alerts")} value={String(unackedAlerts)} />
      </div>

      <SectionCard
        title={t("工单队列", "Safety ticket queue")}
        description={t(
          "最近的安全工单，建议优先处理 OPEN 与 IN_REVIEW。",
          "Recent safety tickets. Prioritize Open and In review items.",
        )}
      >
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-muted-foreground">
              <tr>
                <th className="px-2 py-2 font-medium">{t("工单", "Ticket")}</th>
                <th className="px-2 py-2 font-medium">{t("目标", "Target")}</th>
                <th className="px-2 py-2 font-medium">{t("状态", "Status")}</th>
                <th className="px-2 py-2 font-medium">{t("更新时间", "Updated at")}</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((item) => (
                <tr key={item.id} className="border-t border-border/80">
                  <td className="px-2 py-2">{shortText(item.reason, 46)}</td>
                  <td className="px-2 py-2">{item.targetType}:{item.targetId}</td>
                  <td className="px-2 py-2">
                    <Badge variant={item.status === "OPEN" ? "warning" : "neutral"}>
                      {item.status ?? "-"}
                    </Badge>
                  </td>
                  <td className="px-2 py-2">{formatTime(item.updatedAt, locale)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {tickets.length === 0 && !loading ? (
            <p className="px-2 py-4 text-sm text-muted-foreground">{t("暂无工单。", "No tickets.")}</p>
          ) : null}
        </div>
      </SectionCard>

      <SectionCard
        title={t("告警列表", "Alerts")}
        description={t(
          "可在这里确认已处理告警，避免重复提醒。",
          "Acknowledge handled alerts here to avoid repeated reminders.",
        )}
      >
        <div className="space-y-2">
          {alerts.map((item) => (
            <div
              key={item.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border/80 p-3"
            >
              <div className="min-w-0">
                <p className="font-medium">{item.alertType ?? "-"}</p>
                <p className="text-sm text-muted-foreground">{shortText(item.message, 90)}</p>
                <p className="text-xs text-muted-foreground">{formatTime(item.createdAt, locale)}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={item.acknowledged ? "success" : "warning"}>
                  {item.acknowledged ? t("已确认", "Acknowledged") : t("待确认", "Pending")}
                </Badge>
                {!item.acknowledged ? (
                  <Button size="sm" onClick={() => void handleAcknowledge(item.id)}>
                    {t("确认", "Acknowledge")}
                  </Button>
                ) : null}
              </div>
            </div>
          ))}
          {alerts.length === 0 && !loading ? (
            <p className="text-sm text-muted-foreground">{t("暂无告警。", "No alerts.")}</p>
          ) : null}
        </div>
      </SectionCard>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-background p-4">
      <p className="text-sm text-muted-foreground">{title}</p>
      <p className="mt-1 text-2xl font-semibold">{value}</p>
    </div>
  );
}
