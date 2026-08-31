import "./globals.css";
import { ReactNode } from "react";

export const metadata = {
  title: "AI Incident Investigator",
  description: "Incident command dashboard",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header style={{ padding: "1rem 2rem", borderBottom: "1px solid #333" }}>
          <strong>AI Incident Investigator</strong>
        </header>
        <main style={{ padding: "2rem" }}>{children}</main>
      </body>
    </html>
  );
}
