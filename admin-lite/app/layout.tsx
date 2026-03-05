import type { Metadata } from "next";
import { cookies } from "next/headers";
import { Fira_Code, Fira_Sans } from "next/font/google";
import "./globals.css";
import { AdminI18nProvider } from "@/modules/admin/i18n/admin-i18n-provider";
import {
  ADMIN_LOCALE_COOKIE,
  normalizeAdminLocale,
  toLocaleTag,
} from "@/modules/admin/i18n/locale";

const firaCode = Fira_Code({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-fira-code",
  display: "swap",
});

const firaSans = Fira_Sans({
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700"],
  variable: "--font-fira-sans",
  display: "swap",
});

export const metadata: Metadata = {
  title: "WTF Repo Lockroom",
  description: "Admin Lite platform for WTF Repo operations.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const locale = normalizeAdminLocale(cookieStore.get(ADMIN_LOCALE_COOKIE)?.value);

  return (
    <html lang={toLocaleTag(locale)}>
      <body className={`${firaCode.variable} ${firaSans.variable} antialiased`}>
        <AdminI18nProvider initialLocale={locale}>{children}</AdminI18nProvider>
      </body>
    </html>
  );
}
