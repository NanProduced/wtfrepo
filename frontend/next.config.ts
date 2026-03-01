import type { NextConfig } from "next";
import path from "node:path";
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin();

const nextConfig: NextConfig = {
  turbopack: {
    // Keep Turbopack resolution rooted at the frontend app directory,
    // even when commands are launched from the repo root.
    root: path.resolve(__dirname),
  },
};

export default withNextIntl(nextConfig);
