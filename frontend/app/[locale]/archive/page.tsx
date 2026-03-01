import { ArchiveList } from "@/modules/specimen/components/archive-list";
import { Metadata } from "next";
import { getTranslations } from "next-intl/server";

interface ArchivePageProps {
  params: Promise<{ locale: string }>;
}

export async function generateMetadata({ params }: ArchivePageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "archive.page.meta" });

  return {
    title: t("title"),
    description: t("description"),
  };
}

export default async function ArchivePage({ params }: ArchivePageProps) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "archive.page" });

  return (
    <div className="container mx-auto px-4 pt-24 pb-32 min-h-screen">
      <div className="max-w-7xl mx-auto space-y-12">
        {/* Page Hero/Header */}
        <header className="space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary/10 border border-primary/20">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-primary"></span>
            </span>
            <span className="font-mono text-[10px] text-primary font-bold tracking-widest">
              {t("badge")}
            </span>
          </div>

          <h1 className="text-4xl md:text-6xl font-bold tracking-tighter text-zinc-100 font-mono">
            {t("title_prefix")} <span className="text-primary">{t("title_highlight")}</span>
          </h1>
          <p className="text-zinc-500 max-w-2xl font-mono text-sm leading-relaxed opacity-80">
            {t("description")}
          </p>
        </header>

        {/* List Content */}
        <ArchiveList />
      </div>

      {/* Background Ambience */}
      <div className="fixed inset-0 pointer-events-none -z-10 overflow-hidden">
        <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-primary/5 blur-[120px] rounded-full" />
        <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-violet-500/5 blur-[100px] rounded-full" />
      </div>
    </div>
  );
}
