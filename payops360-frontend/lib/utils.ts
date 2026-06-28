import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatCurrency(amount: number, currency: string = "USD"): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
  }).format(amount);
}

export function formatDate(date: string | Date, format: "short" | "long" | "relative" = "short"): string {
  const d = typeof date === "string" ? new Date(date) : date;

  if (format === "relative") {
    const now = new Date();
    const diff = now.getTime() - d.getTime();
    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (minutes > 0) return `${minutes}m ago`;
    return `${seconds}s ago`;
  }

  if (format === "long") {
    return d.toLocaleDateString("en-US", {
      year: "numeric",
      month: "long",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  return d.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function getStatusColor(status: string): string {
  const statusColors: Record<string, string> = {
    // Payment statuses
    INITIATED: "bg-brand-lavender-200 text-brand-purple-700",
    AUTHORIZED: "bg-blue-100 text-blue-700",
    CAPTURED: "bg-indigo-100 text-indigo-700",
    PROCESSING: "bg-brand-yellow-200 text-yellow-800",
    SETTLED: "bg-green-100 text-green-700",
    FAILED: "bg-red-100 text-red-700",
    RETRY_PENDING: "bg-orange-100 text-orange-700",
    RETRY_IN_PROGRESS: "bg-amber-100 text-amber-700",
    RETRY_FAILED: "bg-red-200 text-red-800",
    REFUNDED: "bg-gray-100 text-gray-700",
    CHARGEBACK: "bg-pink-100 text-pink-700",
    CANCELLED: "bg-gray-200 text-gray-800",

    // Alert severities
    CRITICAL: "bg-red-100 text-red-800",
    HIGH: "bg-orange-100 text-orange-800",
    MEDIUM: "bg-yellow-100 text-yellow-800",
    LOW: "bg-blue-100 text-blue-800",
    INFO: "bg-gray-100 text-gray-800",

    // Generic
    ACTIVE: "bg-green-100 text-green-700",
    INACTIVE: "bg-gray-100 text-gray-700",
    PENDING: "bg-yellow-100 text-yellow-700",
    RESOLVED: "bg-green-100 text-green-700",
  };

  return statusColors[status] || "bg-gray-100 text-gray-700";
}

