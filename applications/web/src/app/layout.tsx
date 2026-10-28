import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TrackerApp Monitor",
  description: "Real-time monitoring dashboard for the TrackerApp Positioning API",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
