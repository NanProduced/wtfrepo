import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  turbopack: {
    // Keep Turbopack resolution rooted at the admin-lite app directory.
    root: path.resolve(__dirname),
  },
};

export default nextConfig;
