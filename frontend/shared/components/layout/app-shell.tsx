import { Header } from "@/shared/components/layout/header";
import { Ticker } from "@/shared/components/layout/ticker";
import { NarratorDock } from "@/shared/components/layout/narrator-dock";
import { NarratorStreamBridge } from "@/shared/components/layout/narrator-stream-bridge";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col font-sans selection:bg-primary selection:text-primary-foreground relative overflow-hidden">
      <Header />

      {/* 1. Global Noise Texture (Grain) */}
      <div className="fixed inset-0 pointer-events-none z-[1] opacity-[0.03] mix-blend-overlay">
        <svg xmlns='http://www.w3.org/2000/svg' width='100%' height='100%'>
          <filter id='noiseFilter'>
            <feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch' />
          </filter>
          <rect width='100%' height='100%' filter='url(#noiseFilter)' />
        </svg>
      </div>

      {/* 2. Scanline Grid Overlay */}
      <div className="fixed inset-0 pointer-events-none z-0 opacity-[0.03] bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:24px_24px]" />

      {/* 3. Ambient Vignette */}
      <div className="fixed inset-0 pointer-events-none z-0 bg-radial-gradient from-transparent to-background opacity-80" />

      {/* 4. Scanning Beam Animation */}
      <div className="fixed inset-0 pointer-events-none z-0 opacity-[0.02] bg-gradient-to-b from-transparent via-primary/10 to-transparent animate-scan" />

      <main className="flex-1 flex flex-col relative z-10 pt-16 pb-8">
        {children}
      </main>

      <NarratorDock />
      <NarratorStreamBridge />
      <Ticker />
    </div>
  );
}
