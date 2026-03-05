import { redirect } from "next/navigation";
import { getAdminToken } from "@/lib/admin-session";

export default async function HomePage() {
  const token = await getAdminToken();
  if (token) {
    redirect("/dashboard");
  }
  redirect("/login");
}
