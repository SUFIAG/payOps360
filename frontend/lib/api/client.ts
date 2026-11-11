import type { ApiEnvelope, ApiError, AuthResponse } from "@/types/api";
import { useSession } from "@/lib/auth/session";

/**
 * Same-origin fetch wrapper. The access token lives only in memory; the
 * refresh token is an HttpOnly cookie the browser sends to /api/v1/auth.
 * Concurrent 401s share a single refresh (single-flight).
 */
export class ApiRequestError extends Error {
  constructor(public status: number, public error: ApiError) {
    super(error.message);
  }
}

const CSRF_HEADER = { "X-Requested-With": "payops" };
let refreshInFlight: Promise<boolean> | null = null;

export async function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch("/api/v1/auth/refresh", { method: "POST", headers: CSRF_HEADER, credentials: "same-origin" });
        if (!res.ok) return false;
        const body = (await res.json()) as ApiEnvelope<AuthResponse>;
        if (body.data.status !== "AUTHENTICATED" || !body.data.accessToken) return false;
        useSession.getState().signIn(body.data);
        return true;
      } catch {
        return false;
      } finally {
        setTimeout(() => (refreshInFlight = null), 0);
      }
    })();
  }
  return refreshInFlight;
}

type Options = { method?: string; body?: unknown; headers?: Record<string, string>; signal?: AbortSignal; auth?: boolean };

export async function api<T>(path: string, options: Options = {}, retried = false): Promise<T> {
  const { method = "GET", body, headers = {}, signal, auth = true } = options;
  const token = useSession.getState().accessToken;
  const res = await fetch(`/api/v1${path}`, {
    method,
    signal,
    credentials: "same-origin",
    headers: {
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(auth && token ? { Authorization: `Bearer ${token}` } : {}),
      ...(path.startsWith("/auth/") ? CSRF_HEADER : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && auth && !retried && !path.startsWith("/auth/")) {
    if (await refreshSession()) return api<T>(path, options, true);
    useSession.getState().expire();
  }

  const text = await res.text();
  const json = text ? (JSON.parse(text) as ApiEnvelope<T>) : null;
  if (!res.ok || !json?.success) {
    throw new ApiRequestError(res.status, json?.error ?? { code: "HTTP_" + res.status, message: res.statusText || "Request failed" });
  }
  return json.data;
}

/** Authenticated fetch with the same single-flight refresh as {@link api}, for non-JSON bodies. */
async function authedFetch(path: string, init: RequestInit, retried = false): Promise<Response> {
  const token = useSession.getState().accessToken;
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    credentials: "same-origin",
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(init.headers as Record<string, string> | undefined) },
  });
  if (res.status === 401 && !retried) {
    if (await refreshSession()) return authedFetch(path, init, true);
    useSession.getState().expire();
  }
  return res;
}

async function failure(res: Response): Promise<ApiRequestError> {
  const text = await res.text().catch(() => "");
  try {
    const json = text ? (JSON.parse(text) as ApiEnvelope<unknown>) : null;
    if (json?.error) return new ApiRequestError(res.status, json.error);
  } catch {
    // not JSON
  }
  return new ApiRequestError(res.status, { code: "HTTP_" + res.status, message: res.statusText || "Request failed" });
}

/** Multipart upload; the browser sets the multipart boundary header. */
export async function apiUpload<T>(path: string, form: FormData): Promise<T> {
  const res = await authedFetch(path, { method: "POST", body: form });
  if (!res.ok) throw await failure(res);
  const json = (await res.json()) as ApiEnvelope<T>;
  if (!json.success) throw new ApiRequestError(res.status, json.error ?? { code: "HTTP_" + res.status, message: "Request failed" });
  return json.data;
}

/** Downloads a file response and saves it with the server-provided file name. */
export async function apiDownload(path: string): Promise<void> {
  const res = await authedFetch(path, { method: "GET" });
  if (!res.ok) throw await failure(res);
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? "download";
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    const fields = error.error.fields ? Object.values(error.error.fields).join(" ") : "";
    return [error.error.message, fields].filter(Boolean).join(" — ");
  }
  return error instanceof Error ? error.message : "Something went wrong";
}

export function qs(params: Record<string, string | number | boolean | undefined | null | string[]>): string {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    if (Array.isArray(v)) v.forEach((item) => search.append(k, item));
    else search.set(k, String(v));
  }
  const s = search.toString();
  return s ? `?${s}` : "";
}
