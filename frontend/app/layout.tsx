import type { Metadata } from "next";
import { Inter, Manrope, JetBrains_Mono } from "next/font/google";
import { Providers } from "./providers";
import "./globals.css";

// Inter for dense data, Manrope for headings.
const body = Inter({ subsets: ["latin"], variable: "--font-body" });
const heading = Manrope({ subsets: ["latin"], variable: "--font-heading" });
const code = JetBrains_Mono({ subsets: ["latin"], variable: "--font-code" });

export const metadata: Metadata = {
  title: { default: "PayOps360 — AI Payment Operations", template: "%s · PayOps360" },
  description: "AI-native command center for payment operations: real-time monitoring, anomaly detection, incident intelligence and an ops copilot.",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`dark ${body.variable} ${heading.variable} ${code.variable}`} suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
