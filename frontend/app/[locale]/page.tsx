import { ArenaLayout } from "@/modules/arena/components/arena-layout";
import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Arena | WTF-Repo",
  description: "The Asylum of Repositories. Vote for the most absurd, ingenious, or insane GitHub repositories in the world.",
  openGraph: {
    title: "WTF-Repo Arena",
    description: "Diagnose and vote for the weirdest code on GitHub.",
    type: "website",
  },
};

export default function Home() {
  return <ArenaLayout />;
}
