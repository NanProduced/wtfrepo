"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ShieldCheck, LayoutDashboard, FlaskConical, MessageSquareWarning, FileText, Wrench, LogOut } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { AdminLocaleSwitch } from "@/modules/admin/components/admin-locale-switch";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";
import { clearAdminSession } from "@/modules/admin/lib/request";

export function AdminShell({ children }: { children: ReactNode }) {
  const { t } = useAdminI18n();
  const pathname = usePathname();
  const router = useRouter();
  const navItems = [
    { href: "/dashboard", label: t("平台概览", "Overview"), icon: LayoutDashboard },
    { href: "/specimens", label: t("标本上架", "Specimens"), icon: FlaskConical },
    { href: "/comments", label: t("评论治理", "Comment moderation"), icon: MessageSquareWarning },
    { href: "/audit", label: t("审计日志", "Audit logs"), icon: FileText },
    { href: "/ops", label: t("运营操作", "Operations"), icon: Wrench },
  ];

  async function handleLogout() {
    await clearAdminSession();
    router.replace("/login");
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,#f4f4f5_0%,#ffffff_45%)]">
      <div className="mx-auto grid min-h-screen max-w-[1440px] grid-cols-1 gap-4 p-4 lg:grid-cols-[240px_1fr]">
        <aside className="rounded-2xl border border-border/70 bg-card/80 p-4 shadow-sm backdrop-blur">
          <div className="mb-4 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <ShieldCheck className="size-5 text-emerald-600" />
              <div>
                <p className="text-sm text-muted-foreground">WTF Repo</p>
                <h1 className="font-semibold">Lockroom</h1>
              </div>
            </div>
            <AdminLocaleSwitch />
          </div>
          <p className="mb-6 text-xs text-muted-foreground">
            {t("管理员工作台", "Admin workspace")}
          </p>
          <nav className="space-y-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors",
                    active
                      ? "bg-foreground text-background"
                      : "text-foreground/80 hover:bg-secondary",
                  )}
                >
                  <Icon className="size-4" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>
          <div className="mt-8">
            <Button
              onClick={handleLogout}
              variant="outline"
              className="w-full justify-start"
            >
              <LogOut className="size-4" />
              {t("退出管理平台", "Sign out")}
            </Button>
          </div>
        </aside>
        <main className="rounded-2xl border border-border/70 bg-card p-4 shadow-sm md:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
