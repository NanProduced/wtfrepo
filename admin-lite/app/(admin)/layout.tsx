import type { ReactNode } from "react";
import { requireAdminToken } from "@/lib/admin-session";
import { AdminShell } from "@/modules/admin/components/admin-shell";

export default async function AdminLayout({
  children,
}: {
  children: ReactNode;
}) {
  await requireAdminToken();
  return <AdminShell>{children}</AdminShell>;
}
