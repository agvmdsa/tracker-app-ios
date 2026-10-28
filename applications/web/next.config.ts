import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Produces a minimal self-contained server (.next/standalone) for the Docker image.
  output: "standalone",
};

export default nextConfig;
