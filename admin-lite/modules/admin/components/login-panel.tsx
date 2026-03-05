"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Bug, KeyRound, ShieldCheck, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Particles } from "@/components/ui/particles";
import { AdminLocaleSwitch } from "@/modules/admin/components/admin-locale-switch";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";

type LoginFlow = "auto" | "token";
type NoticeKind = "success" | "error";

const DEV_DIRECT_TOKEN_ENABLED =
  process.env.NODE_ENV !== "production" ||
  process.env.NEXT_PUBLIC_ADMIN_ALLOW_DIRECT_TOKEN === "true";

function toReadableError(
  raw: string,
  flow: LoginFlow,
  t: (zh: string, en: string) => string,
): string {
  if (!raw) {
    return t("登录失败，请稍后重试。", "Sign in failed. Please try again.");
  }
  if (raw.includes("admin_role_required")) {
    return flow === "auto"
      ? t(
          "当前账号没有管理权限。若这是首次初始化，请填写 bootstrap 邮箱后重试；否则请让管理员分配 MANAGER/ADMIN 角色。",
          "Your account has no admin role. For first-time setup, fill bootstrap email and retry; otherwise ask an admin to grant MANAGER/ADMIN role.",
        )
      : t(
          "当前账号没有管理权限。请让管理员分配 MANAGER/ADMIN 角色，或在首次部署时使用初始化流程。",
          "Your account has no admin role. Ask an admin to grant MANAGER/ADMIN role, or use bootstrap for first deployment.",
        );
  }
  if (raw.includes("bootstrap_already_done")) {
    return t(
      "平台已经初始化，不能重复 bootstrap。请使用已有管理员账号登录。",
      "Bootstrap has already been completed. Please sign in with an existing admin account.",
    );
  }
  if (raw.includes("bootstrap_email_mismatch")) {
    return t(
      "Bootstrap 邮箱与后端配置不一致，请联系负责人确认配置。",
      "Bootstrap email does not match backend configuration. Please verify configuration with the project owner.",
    );
  }
  if (raw.includes("Missing user token")) {
    return t("请先粘贴用户 token。", "Please paste a user token first.");
  }
  if (raw.includes("Invalid mode")) {
    return t("登录模式无效，请刷新页面后重试。", "Invalid sign-in mode. Please refresh and retry.");
  }
  if (raw.includes("Email is required")) {
    return t("首次初始化必须填写 bootstrap 邮箱。", "Bootstrap email is required for first-time setup.");
  }
  return raw;
}

export function LoginPanel() {
  const { t } = useAdminI18n();
  const router = useRouter();
  const [flow, setFlow] = useState<LoginFlow>("auto");
  const [adminToken, setAdminToken] = useState("");
  const [userToken, setUserToken] = useState("");
  const [bootstrapEmail, setBootstrapEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<{ kind: NoticeKind; text: string } | null>(null);

  function switchFlow(nextFlow: LoginFlow) {
    setFlow(nextFlow);
    setNotice(null);
  }

  async function parseErrorMessage(response: Response) {
    const payload = (await response.json().catch(() => null)) as { message?: string } | null;
    return payload?.message ?? t("请求失败。", "Request failed.");
  }

  async function saveToken(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setNotice(null);
    try {
      const response = await fetch("/api/admin/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: adminToken }),
      });
      if (!response.ok) {
        throw new Error(await parseErrorMessage(response));
      }
      setNotice({
        kind: "success",
        text: t("管理员会话已建立，正在进入控制台。", "Admin session created. Entering dashboard."),
      });
      router.replace("/dashboard");
    } catch (unknownError) {
      const fallback = t("保存管理员 token 失败。", "Failed to save admin token.");
      const raw = unknownError instanceof Error ? unknownError.message : fallback;
      setNotice({ kind: "error", text: toReadableError(raw, "token", t) });
    } finally {
      setLoading(false);
    }
  }

  async function exchangeToken(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setNotice(null);
    try {
      const response = await fetch("/api/admin/session/exchange", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          mode: "auto",
          userToken,
          email: bootstrapEmail.trim() || undefined,
        }),
      });
      if (!response.ok) {
        throw new Error(await parseErrorMessage(response));
      }
      const payload = (await response.json().catch(() => null)) as { source?: string } | null;
      const source = payload?.source;
      const text =
        source === "bootstrap"
          ? t("已完成首次初始化并进入控制台。", "Bootstrap completed. Entering dashboard.")
          : t("登录成功，正在进入控制台。", "Signed in successfully. Entering dashboard.");
      setNotice({ kind: "success", text });
      router.replace("/dashboard");
    } catch (unknownError) {
      const fallback = t("登录失败。", "Sign in failed.");
      const raw = unknownError instanceof Error ? unknownError.message : fallback;
      setNotice({ kind: "error", text: toReadableError(raw, "auto", t) });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="relative min-h-screen overflow-hidden bg-[radial-gradient(circle_at_top_left,#edf5ff_0%,#ffffff_45%,#f3f6fb_100%)]">
      <Particles className="absolute inset-0" color="#8b95a7" quantity={70} />
      <div className="absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.34),rgba(255,255,255,0))]" />
      <div className="relative mx-auto flex min-h-screen max-w-5xl items-center px-4 py-8 sm:px-6 lg:px-8">
        <section className="grid w-full overflow-hidden rounded-3xl border border-border/70 bg-card/90 shadow-2xl backdrop-blur md:grid-cols-[1.05fr_1fr]">
          <aside className="relative border-b border-border/60 p-6 sm:p-8 md:border-b-0 md:border-r">
            <div className="absolute right-6 top-6 md:hidden">
              <AdminLocaleSwitch />
            </div>
            <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
              <ShieldCheck className="size-3.5" />
              <span>{t("管理入口", "Control entry")}</span>
            </div>
            <h1 className="mt-4 text-3xl font-semibold tracking-tight text-foreground">
              WTF-Repo Lockroom
            </h1>
            <p className="mt-2 max-w-md text-sm text-muted-foreground">
              {t(
                "统一的管理登录入口。登录后即可进入标本上架与治理工作台。",
                "Unified admin sign-in portal for curation and governance workflows.",
              )}
            </p>

            <div className="mt-8 space-y-3">
              <div className="rounded-2xl border border-border/70 bg-background/75 p-4">
                <div className="flex items-center gap-2 text-sm font-medium">
                  <UserRound className="size-4 text-emerald-600" />
                  <span>{t("个人账号登录", "User token sign-in")}</span>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  {t(
                    "默认推荐。自动校验权限并进入管理台。",
                    "Recommended path. Role check and session exchange are automatic.",
                  )}
                </p>
              </div>
              {DEV_DIRECT_TOKEN_ENABLED ? (
                <div className="rounded-2xl border border-border/70 bg-background/75 p-4">
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <Bug className="size-4 text-sky-700" />
                    <span>{t("开发调试入口", "Debug token entry")}</span>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {t(
                      "仅用于本地调试或排查问题，正式环境不建议使用。",
                      "Only for local debugging or troubleshooting.",
                    )}
                  </p>
                </div>
              ) : null}
            </div>
          </aside>

          <div className="p-6 sm:p-8">
            <div className="mb-5 hidden justify-end md:flex">
              <AdminLocaleSwitch />
            </div>
            <h2 className="text-lg font-semibold">
              {t("管理员登录", "Admin sign-in")}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {t("填写信息后立即进入管理台。", "Complete the form and continue to dashboard.")}
            </p>

            <div className="mt-5 grid grid-cols-1 gap-2 sm:grid-cols-2">
              <Button
                type="button"
                variant={flow === "auto" ? "default" : "outline"}
                onClick={() => switchFlow("auto")}
                className="w-full justify-start"
              >
                <UserRound className="size-4" />
                {t("个人账号登录", "User token sign-in")}
              </Button>
              {DEV_DIRECT_TOKEN_ENABLED ? (
                <Button
                  type="button"
                  variant={flow === "token" ? "default" : "outline"}
                  onClick={() => switchFlow("token")}
                  className="w-full justify-start"
                >
                  <Bug className="size-4" />
                  {t("开发调试入口", "Debug token entry")}
                </Button>
              ) : null}
            </div>

            {flow === "token" ? (
              <form className="mt-5 space-y-4" onSubmit={saveToken}>
                <div className="rounded-xl border border-amber-300/70 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  {t(
                    "该入口仅建议用于本地调试。",
                    "This mode is intended for local debugging only.",
                  )}
                </div>
                <label className="space-y-2 text-sm">
                  <span>{t("管理员 access token", "Admin access token")}</span>
                  <Textarea
                    required
                    placeholder={t(
                      "粘贴 /api/v1/admin/platform/login 返回的 accessToken",
                      "Paste accessToken from /api/v1/admin/platform/login",
                    )}
                    value={adminToken}
                    onChange={(event) => setAdminToken(event.target.value)}
                    className="min-h-[132px]"
                  />
                </label>
                <Button
                  disabled={loading || adminToken.trim().length < 20}
                  type="submit"
                  className="w-full justify-center"
                >
                  <KeyRound className="size-4" />
                  {t("保存并进入管理台", "Save and enter dashboard")}
                  <ArrowRight className="size-4" />
                </Button>
              </form>
            ) : (
              <form className="mt-5 space-y-4" onSubmit={(event) => void exchangeToken(event)}>
                <label className="space-y-2 text-sm">
                  <span>{t("用户 token", "User token")}</span>
                  <Textarea
                    required
                    placeholder={t("粘贴主站登录后获取的 JWT", "Paste JWT from main site sign-in")}
                    value={userToken}
                    onChange={(event) => setUserToken(event.target.value)}
                    className="min-h-[132px]"
                  />
                </label>
                <label className="space-y-2 text-sm">
                  <span>{t("Bootstrap 邮箱（可选）", "Bootstrap email (optional)")}</span>
                  <Input
                    type="email"
                    placeholder={t(
                      "仅首次初始化需要，常规登录可留空",
                      "Only needed for first bootstrap. Leave empty for normal sign-in.",
                    )}
                    value={bootstrapEmail}
                    onChange={(event) => setBootstrapEmail(event.target.value)}
                  />
                </label>
                <Button
                  disabled={loading || userToken.trim().length < 20}
                  type="submit"
                  className="w-full justify-center"
                >
                  <KeyRound className="size-4" />
                  {t("自动检查并进入管理台", "Auto-check and enter dashboard")}
                  <ArrowRight className="size-4" />
                </Button>
              </form>
            )}

            {notice ? (
              <p
                className={[
                  "mt-4 rounded-xl px-3 py-2 text-sm",
                  notice.kind === "success"
                    ? "border border-emerald-300 bg-emerald-50 text-emerald-700"
                    : "border border-rose-300 bg-rose-50 text-rose-700",
                ].join(" ")}
              >
                {notice.text}
              </p>
            ) : null}
          </div>
        </section>
      </div>
    </div>
  );
}
