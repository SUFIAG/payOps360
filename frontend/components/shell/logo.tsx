import { cn } from "@/lib/utils";

/** PayOps360 mark: an ink seal with a jade 360° ledger ring closed by a brass settlement point. */
export function Logo({ className, withText = true, tone = "auto" }: { className?: string; withText?: boolean; tone?: "auto" | "light" }) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <svg viewBox="0 0 40 40" className="h-9 w-9 shrink-0" aria-hidden>
        <rect width="40" height="40" rx="10" fill="#141518" />
        <rect x="0.5" y="0.5" width="39" height="39" rx="9.5" fill="none" stroke="#ffffff" strokeOpacity="0.1" />
        <circle cx="20" cy="20" r="10.5" fill="none" stroke="#ffffff" strokeOpacity="0.12" strokeWidth="2.6" />
        <path d="M20 9.5a10.5 10.5 0 1 1 -9.9 7" fill="none" stroke="#3fbf92" strokeWidth="2.6" strokeLinecap="round" />
        <circle cx="10.6" cy="15.6" r="2.4" fill="#d6b574" />
        <path d="M16.2 20.4l2.7 2.7 5.2-5.4" fill="none" stroke="#eeece7" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {withText && (
        <div className="leading-tight">
          <div className={cn("font-display text-[15px] font-semibold tracking-tight", tone === "light" && "text-[#eeece7]")}>
            PayOps<span className={tone === "light" ? "text-[#d6b574]" : "text-brass-400"}>360</span>
          </div>
          <div className={cn("text-[10px] uppercase tracking-[0.2em]", tone === "light" ? "text-[#a5a29a]" : "text-muted")}>Payment Operations</div>
        </div>
      )}
    </div>
  );
}
