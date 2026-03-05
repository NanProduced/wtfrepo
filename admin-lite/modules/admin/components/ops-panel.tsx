"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import {
  banUser,
  createBroadcast,
  forceRecalc,
  forceSettle,
  getMatchQuality,
  grantBug,
  listBannedUsers,
  listBroadcasts,
  resetElo,
  unbanUser,
  updateHouseConfig,
} from "@/modules/admin/lib/request";
import type { BannedUser, BroadcastItem, MatchQualityReport } from "@/modules/admin/types";
import { formatTime } from "@/modules/admin/lib/format";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";
import { SectionCard } from "@/modules/admin/components/panel-common";

export function OpsPanel() {
  const { locale, t } = useAdminI18n();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [matchQuality, setMatchQuality] = useState<MatchQualityReport | null>(null);
  const [bannedUsers, setBannedUsers] = useState<BannedUser[]>([]);
  const [broadcasts, setBroadcasts] = useState<BroadcastItem[]>([]);

  const [arenaReason, setArenaReason] = useState("");
  const [grantUserId, setGrantUserId] = useState("");
  const [grantDelta, setGrantDelta] = useState("100");
  const [grantReason, setGrantReason] = useState("ADMIN_MANUAL");
  const [grantNote, setGrantNote] = useState("");

  const [forceSettleSpecimenId, setForceSettleSpecimenId] = useState("");
  const [forceSettleReason, setForceSettleReason] = useState("");
  const [houseSpecimenId, setHouseSpecimenId] = useState("");
  const [houseBudget, setHouseBudget] = useState("5000");
  const [weightUp, setWeightUp] = useState("0.4");
  const [weightFlat, setWeightFlat] = useState("0.3");
  const [weightDown, setWeightDown] = useState("0.3");

  const [banUserId, setBanUserId] = useState("");
  const [banReason, setBanReason] = useState("");
  const [banType, setBanType] = useState("TEMPORARY");
  const [banExpiresAt, setBanExpiresAt] = useState("");
  const [unbanUserId, setUnbanUserId] = useState("");

  const [broadcastTitle, setBroadcastTitle] = useState("");
  const [broadcastBody, setBroadcastBody] = useState("");
  const [broadcastTargetUrl, setBroadcastTargetUrl] = useState("");

  function clearTip() {
    setError(null);
    setMessage(null);
  }

  const loadReadonlyData = useCallback(async () => {
    try {
      const [quality, banned, broadcastList] = await Promise.all([
        getMatchQuality(),
        listBannedUsers(1, 8, true),
        listBroadcasts(1, 8),
      ]);
      setMatchQuality(quality);
      setBannedUsers(banned.items);
      setBroadcasts(broadcastList.items);
    } catch (unknownError) {
      setError(unknownError instanceof Error ? unknownError.message : t("加载数据失败。", "Failed to load data."));
    }
  }, [t]);

  useEffect(() => {
    void loadReadonlyData();
  }, [loadReadonlyData]);

  async function runTask(task: () => Promise<void>) {
    setLoading(true);
    clearTip();
    try {
      await task();
      await loadReadonlyData();
    } catch (unknownError) {
      setError(unknownError instanceof Error ? unknownError.message : t("执行失败。", "Operation failed."));
    } finally {
      setLoading(false);
    }
  }

  function submitArenaRecalc(event: FormEvent) {
    event.preventDefault();
    void runTask(async () => {
      await forceRecalc(arenaReason || undefined);
      setMessage(t("已触发 force-recalc。", "Force recalc triggered."));
    });
  }

  function submitArenaReset() {
    void runTask(async () => {
      await resetElo(arenaReason || undefined);
      setMessage(t("已触发 reset-elo。", "Reset elo triggered."));
    });
  }

  function submitGrantBug(event: FormEvent) {
    event.preventDefault();
    void runTask(async () => {
      await grantBug({
        userId: grantUserId,
        delta: Number(grantDelta),
        reason: grantReason,
        note: grantNote || undefined,
      });
      setMessage(t("已提交经济调节。", "Economy adjustment submitted."));
    });
  }

  function submitForceSettle(event: FormEvent) {
    event.preventDefault();
    void runTask(async () => {
      await forceSettle({
        specimenId: forceSettleSpecimenId,
        reason: forceSettleReason || undefined,
        confirm: true,
      });
      setMessage(t("已提交强制结算。", "Force settlement submitted."));
    });
  }

  function submitHouseConfig(event: FormEvent) {
    event.preventDefault();
    void runTask(async () => {
      await updateHouseConfig({
        specimenId: houseSpecimenId,
        houseBudget: Number(houseBudget),
        weightUp: Number(weightUp),
        weightFlat: Number(weightFlat),
        weightDown: Number(weightDown),
      });
      setMessage(t("已更新庄家配置。", "House configuration updated."));
    });
  }

  function submitBan(event: FormEvent) {
    event.preventDefault();
    void runTask(async () => {
      await banUser(banUserId, {
        banType,
        reason: banReason,
        expiresAt: banExpiresAt || undefined,
      });
      setMessage(t("已封禁用户。", "User banned."));
    });
  }

  function submitUnban(event: FormEvent) {
    event.preventDefault();
    void runTask(async () => {
      await unbanUser(unbanUserId);
      setMessage(t("已解封用户。", "User unbanned."));
    });
  }

  function submitBroadcast(event: FormEvent) {
    event.preventDefault();
    void runTask(async () => {
      await createBroadcast({
        title: broadcastTitle,
        body: broadcastBody || undefined,
        targetUrl: broadcastTargetUrl || undefined,
      });
      setMessage(t("已创建系统广播。", "Broadcast created."));
    });
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t("运营操作", "Operations")}</h1>
          <p className="text-sm text-muted-foreground">
            {t(
              "Arena 运维、经济调控、下注处置、用户封禁和广播能力入口。",
              "Entry for arena operations, economy controls, betting handling, bans, and broadcasts.",
            )}
          </p>
        </div>
        <Button variant="outline" onClick={() => void loadReadonlyData()} disabled={loading}>
          {t("刷新", "Refresh")}
        </Button>
      </header>

      {message ? <p className="text-sm text-emerald-700">{message}</p> : null}
      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      <SectionCard
        title={t("Arena 运维", "Arena operations")}
        description={t("对应 /api/v1/admin/arena/* 接口。", "Mapped to /api/v1/admin/arena/* APIs.")}
      >
        <div className="grid gap-3 md:grid-cols-2">
          <form className="space-y-2" onSubmit={submitArenaRecalc}>
            <Textarea
              value={arenaReason}
              onChange={(event) => setArenaReason(event.target.value)}
              placeholder={t("原因（可选）", "Reason (optional)")}
            />
            <div className="flex gap-2">
              <Button disabled={loading} type="submit">{t("重算配对", "Force recalc")}</Button>
              <Button
                disabled={loading}
                type="button"
                variant="outline"
                onClick={submitArenaReset}
              >
                {t("重置 elo", "Reset elo")}
              </Button>
            </div>
          </form>
          <div className="rounded-lg border border-border/80 p-3 text-sm">
            <p>{t("活跃标本", "Active specimens")}: {matchQuality?.activeSpecimenCount ?? "-"}</p>
            <p>{t("预期配对数", "Expected pairs")}: {matchQuality?.expectedPairCount ?? "-"}</p>
            <p>{t("可用配对数", "Available pairs")}: {matchQuality?.availablePairCount ?? "-"}</p>
            <p>{t("覆盖率", "Coverage ratio")}: {matchQuality?.pairCoverageRatio ?? "-"}</p>
          </div>
        </div>
      </SectionCard>

      <SectionCard
        title={t("经济调控", "Economy controls")}
        description={t("手动发放或回收用户 Bug 余额。", "Manually grant or reclaim user bug balance.")}
      >
        <form className="grid gap-2 md:grid-cols-2" onSubmit={submitGrantBug}>
          <Input
            required
            placeholder={t("用户 ID", "User ID")}
            value={grantUserId}
            onChange={(event) => setGrantUserId(event.target.value)}
          />
          <Input
            required
            type="number"
            placeholder={t("变更值", "Delta")}
            value={grantDelta}
            onChange={(event) => setGrantDelta(event.target.value)}
          />
          <Input
            required
            placeholder={t("原因", "Reason")}
            value={grantReason}
            onChange={(event) => setGrantReason(event.target.value)}
          />
          <Input
            placeholder={t("备注", "Note")}
            value={grantNote}
            onChange={(event) => setGrantNote(event.target.value)}
          />
          <div className="md:col-span-2">
            <Button disabled={loading} type="submit">{t("提交经济操作", "Submit economy action")}</Button>
          </div>
        </form>
      </SectionCard>

      <SectionCard
        title={t("下注治理", "Betting governance")}
        description={t("强制结算和庄家资金配置。", "Force settlement and house fund configuration.")}
      >
        <div className="grid gap-3 md:grid-cols-2">
          <form className="space-y-2" onSubmit={submitForceSettle}>
            <Input
              required
              placeholder={t("标本 ID", "Specimen ID")}
              value={forceSettleSpecimenId}
              onChange={(event) => setForceSettleSpecimenId(event.target.value)}
            />
            <Textarea
              placeholder={t("原因", "Reason")}
              value={forceSettleReason}
              onChange={(event) => setForceSettleReason(event.target.value)}
            />
            <Button disabled={loading} type="submit">{t("强制结算", "Force settle")}</Button>
          </form>
          <form className="grid gap-2" onSubmit={submitHouseConfig}>
            <Input
              required
              placeholder={t("标本 ID", "Specimen ID")}
              value={houseSpecimenId}
              onChange={(event) => setHouseSpecimenId(event.target.value)}
            />
            <Input
              required
              type="number"
              placeholder={t("庄家预算", "House budget")}
              value={houseBudget}
              onChange={(event) => setHouseBudget(event.target.value)}
            />
            <div className="grid grid-cols-3 gap-2">
              <Input required value={weightUp} onChange={(event) => setWeightUp(event.target.value)} />
              <Input required value={weightFlat} onChange={(event) => setWeightFlat(event.target.value)} />
              <Input required value={weightDown} onChange={(event) => setWeightDown(event.target.value)} />
            </div>
            <Button disabled={loading} type="submit">{t("更新庄家配置", "Update house config")}</Button>
          </form>
        </div>
      </SectionCard>

      <SectionCard
        title={t("用户封禁", "User bans")}
        description={t("快速执行封禁与解封，并查看已封禁用户。", "Quickly ban or unban users and inspect current bans.")}
      >
        <div className="grid gap-3 md:grid-cols-2">
          <form className="space-y-2" onSubmit={submitBan}>
            <Input
              required
              placeholder={t("用户 ID", "User ID")}
              value={banUserId}
              onChange={(event) => setBanUserId(event.target.value)}
            />
            <Input
              required
              placeholder={t("封禁原因", "Ban reason")}
              value={banReason}
              onChange={(event) => setBanReason(event.target.value)}
            />
            <Input
              required
              placeholder={t("封禁类型：TEMPORARY / PERMANENT", "Ban type: TEMPORARY / PERMANENT")}
              value={banType}
              onChange={(event) => setBanType(event.target.value)}
            />
            <Input
              type="datetime-local"
              value={banExpiresAt}
              onChange={(event) => setBanExpiresAt(event.target.value)}
            />
            <Button disabled={loading} type="submit">{t("封禁用户", "Ban user")}</Button>
          </form>
          <form className="space-y-2" onSubmit={submitUnban}>
            <Input
              required
              placeholder={t("待解封用户 ID", "User ID to unban")}
              value={unbanUserId}
              onChange={(event) => setUnbanUserId(event.target.value)}
            />
            <Button disabled={loading} type="submit" variant="outline">{t("解除封禁", "Unban user")}</Button>
            <div className="rounded-lg border border-border/80 p-2 text-xs">
              {bannedUsers.slice(0, 5).map((item) => (
                <p key={item.id} className="mb-1 last:mb-0">
                  <Badge variant={item.active ? "warning" : "neutral"} className="mr-2">
                    {item.active ? t("生效中", "Active") : t("已失效", "Inactive")}
                  </Badge>
                  {item.userId} ({item.username})
                </p>
              ))}
              {bannedUsers.length === 0 ? t("暂无封禁记录。", "No ban records.") : null}
            </div>
          </form>
        </div>
      </SectionCard>

      <SectionCard
        title={t("系统广播", "System broadcasts")}
        description={t("创建广播并查看最近发送记录。", "Create broadcasts and inspect recent deliveries.")}
      >
        <div className="grid gap-3 md:grid-cols-2">
          <form className="space-y-2" onSubmit={submitBroadcast}>
            <Input
              required
              placeholder={t("标题", "Title")}
              value={broadcastTitle}
              onChange={(event) => setBroadcastTitle(event.target.value)}
            />
            <Textarea
              placeholder={t("正文", "Body")}
              value={broadcastBody}
              onChange={(event) => setBroadcastBody(event.target.value)}
            />
            <Input
              placeholder={t("跳转链接", "Target URL")}
              value={broadcastTargetUrl}
              onChange={(event) => setBroadcastTargetUrl(event.target.value)}
            />
            <Button disabled={loading} type="submit">{t("创建广播", "Create broadcast")}</Button>
          </form>
          <div className="rounded-lg border border-border/80 p-3 text-sm">
            {broadcasts.slice(0, 6).map((item) => (
              <p key={item.broadcastUid} className="mb-2 last:mb-0">
                <Badge className="mr-2">{item.status}</Badge>
                {item.title} · {formatTime(item.createdAt, locale)}
              </p>
            ))}
            {broadcasts.length === 0 ? t("暂无广播记录。", "No broadcast records.") : null}
          </div>
        </div>
      </SectionCard>
    </div>
  );
}
