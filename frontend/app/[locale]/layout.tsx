import RootLayout from "@/shared/components/layout/app-shell";
import { AppProviders } from "@/shared/providers/app-providers";
import { notFound } from 'next/navigation';
import { getMessages } from 'next-intl/server';
import { Metadata } from "next";
import "../globals.css";

export const metadata: Metadata = {
  title: {
    default: "WTF-Repo | The Asylum of Repositories",
    template: "%s | WTF-Repo"
  },
  description: "A platform for diagnosing, voting, and archive weird, funny, and insane GitHub repositories.",
  keywords: ["github", "weird repos", "funny code", "repository arena", "coding asylum"],
  authors: [{ name: "WTF-Repo Team" }],
  viewport: "width=device-width, initial-scale=1",
};

// This is the App Router root layout
export default async function Layout({
  children,
  params
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;

  // Validate that the incoming `locale` parameter is valid
  if (!['en', 'zh'].includes(locale)) notFound();

  // Providing all messages to the client
  // side is the easiest way to get started
  const messages = await getMessages();

  return (
    <html lang={locale} className="dark" style={{colorScheme: 'dark'}}>
      <body className="antialiased min-h-screen bg-background text-foreground">
        <AppProviders messages={messages} locale={locale}>
          <RootLayout>{children}</RootLayout>
        </AppProviders>
      </body>
    </html>
  );
}
