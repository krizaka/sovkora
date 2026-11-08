import { ConsoleNav } from "@/console/components/ConsoleNav";
import { ConsoleSubnav } from "@/console/components/ConsoleSubnav";

/** The console's shell: its bar above every section (the sign-in page has none). */
export default function ConsoleLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <>
      <ConsoleNav />
      <ConsoleSubnav />
      {children}
    </>
  );
}
