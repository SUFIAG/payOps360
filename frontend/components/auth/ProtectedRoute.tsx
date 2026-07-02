"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/store/authStore";

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  // TEMPORARY: Authentication disabled for testing
  // TODO: Re-enable authentication before production
  return <>{children}</>;

  /* Original authentication code - restore this later:
  const router = useRouter();
  const { isAuthenticated, isLoading, fetchUser } = useAuthStore();
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    const checkAuth = async () => {
      // Check if we have a token in localStorage
      const token = typeof window !== "undefined" ? localStorage.getItem("accessToken") : null;

      if (!token) {
        // No token, redirect immediately
        router.push("/login");
        return;
      }

      if (!isAuthenticated && !isLoading) {
        try {
          await fetchUser();
        } catch (error) {
          router.push("/login");
        } finally {
          setChecking(false);
        }
      } else {
        setChecking(false);
      }
    };

    checkAuth();
  }, [isAuthenticated, isLoading, fetchUser, router]);

  if (checking || isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center space-y-4">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600 mx-auto"></div>
          <p className="text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return <>{children}</>;
  */
}

