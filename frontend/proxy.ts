import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/** Pages reachable without a session (sign-in and onboarding flows). */
const PUBLIC_PATHS = ["/login", "/forgot-password", "/reset-password", "/accept-invite", "/signup", "/verify-email"];

/**
 * Optimistic guard: visitors without a session hint cookie are sent to the
 * login page before any console code loads. Real authorisation is enforced
 * by the API on every request.
 */
export function proxy(request: NextRequest) {
  const hasSession = request.cookies.has("po_session");
  const { pathname } = request.nextUrl;
  const isPublic = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + "/"));

  if (!hasSession && !isPublic) {
    const url = new URL("/login", request.url);
    if (pathname !== "/") url.searchParams.set("next", pathname);
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|brand).*)"],
};
