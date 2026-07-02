"use client";

import { ReactNode, useState, useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "framer-motion";
import {
  LayoutDashboard,
  CreditCard,
  Server,
  AlertTriangle,
  Activity,
  BarChart3,
  Settings,
  LogOut,
  Menu,
  X,
} from "lucide-react";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";
import { useAuthStore } from "@/lib/store/authStore";
import { useNotificationStore } from "@/lib/store/notification-store";
import { wsClient } from "@/lib/websocket/client";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import NotificationPanel from "@/components/notifications/notification-panel";
import ToastNotification from "@/components/notifications/toast-notification";

interface NavItem {
  href: string;
  label: string;
  icon: ReactNode;
}

const navItems: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: <LayoutDashboard size={20} /> },
  { href: "/dashboard/payments", label: "Payments", icon: <CreditCard size={20} /> },
  { href: "/dashboard/providers", label: "Providers", icon: <Server size={20} /> },
  { href: "/dashboard/alerts", label: "Alerts", icon: <AlertTriangle size={20} /> },
  { href: "/dashboard/incidents", label: "Incidents", icon: <Activity size={20} /> },
  { href: "/dashboard/analytics", label: "Analytics", icon: <BarChart3 size={20} /> },
  { href: "/dashboard/settings", label: "Settings", icon: <Settings size={20} /> },
];

export default function DashboardLayout({ children }: { children: ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const pathname = usePathname();
  const { user, logout, token } = useAuthStore();
  const addNotification = useNotificationStore((state) => state.addNotification);

  // TEMPORARY: Mock user for testing
  const displayUser = user || {
    fullName: "Test User",
    role: "ADMIN",
    organizationName: "Demo Organization",
  };

  // Initialize WebSocket connection
  useEffect(() => {
    if (token) {
      wsClient.connect(token);

      // Setup WebSocket event listeners
      const unsubscribePaymentCreated = wsClient.on('payment:created', (data) => {
        addNotification({
          type: 'info',
          title: 'Payment Created',
          message: `New payment of ${data.amount} ${data.currency} created`,
          actionUrl: `/dashboard/payments/${data.id}`,
          actionLabel: 'View Details',
        });
      });

      const unsubscribePaymentFailed = wsClient.on('payment:failed', (data) => {
        addNotification({
          type: 'error',
          title: 'Payment Failed',
          message: `Payment ${data.transactionId} has failed`,
          actionUrl: `/dashboard/payments/${data.id}`,
          actionLabel: 'View Details',
        });
      });

      const unsubscribeAlertCreated = wsClient.on('alert:created', (data) => {
        addNotification({
          type: 'warning',
          title: 'New Alert',
          message: data.message || 'A new alert has been created',
          actionUrl: `/dashboard/alerts`,
          actionLabel: 'View Alerts',
        });
      });

      const unsubscribeIncidentCreated = wsClient.on('incident:created', (data) => {
        addNotification({
          type: 'error',
          title: 'Incident Created',
          message: data.description || 'A new incident requires attention',
          actionUrl: `/dashboard/incidents`,
          actionLabel: 'View Incidents',
        });
      });

      const unsubscribeProviderHealth = wsClient.on('provider:health_changed', (data) => {
        const notificationType = data.health === 'HEALTHY' ? 'success' : 'warning';
        addNotification({
          type: notificationType,
          title: 'Provider Health Changed',
          message: `${data.providerName} is now ${data.health}`,
          actionUrl: `/dashboard/providers`,
          actionLabel: 'View Providers',
        });
      });

      // Cleanup on unmount
      return () => {
        unsubscribePaymentCreated();
        unsubscribePaymentFailed();
        unsubscribeAlertCreated();
        unsubscribeIncidentCreated();
        unsubscribeProviderHealth();
        wsClient.disconnect();
      };
    }
  }, [token, addNotification]);

  const handleLogout = async () => {
    await logout();
    window.location.href = "/login";
  };

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50">
        {/* Toast Notifications */}
        <ToastNotification />

        {/* Sidebar - Deep Purple */}
        <motion.aside
          initial={false}
          animate={{ width: sidebarOpen ? 280 : 80 }}
          className="fixed left-0 top-0 h-full bg-gradient-to-b from-purple-900 to-purple-950 text-white shadow-2xl z-50"
        >
          <div className="flex flex-col h-full">
            {/* Logo with Yellow accent */}
            <div className="h-16 flex items-center justify-between px-4 border-b border-purple-800">
              {sidebarOpen ? (
                <div className="flex items-center gap-2">
                  <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-yellow-400 to-yellow-500 flex items-center justify-center font-bold text-purple-900 shadow-lg">
                    P360
                  </div>
                  <span className="font-bold text-lg">PayOps360</span>
                </div>
              ) : (
                <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-yellow-400 to-yellow-500 flex items-center justify-center font-bold text-purple-900 mx-auto shadow-lg">
                  P
                </div>
              )}
            </div>

            {/* Navigation */}
            <nav className="flex-1 py-4 overflow-y-auto">
              {navItems.map((item) => {
                const isActive = pathname === item.href;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={cn(
                      "flex items-center gap-3 px-4 py-3 mx-2 rounded-lg transition-all",
                      isActive
                        ? "bg-yellow-500 text-purple-900 font-semibold shadow-md"
                        : "hover:bg-purple-800 text-gray-200"
                    )}
                  >
                    {item.icon}
                    {sidebarOpen && <span className="font-medium">{item.label}</span>}
                  </Link>
                );
              })}
            </nav>

            {/* User Section */}
            <div className="border-t border-purple-800 p-4">
              {sidebarOpen ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-3 pb-3">
                    <div className="w-10 h-10 rounded-full bg-yellow-400 flex items-center justify-center text-purple-900 font-bold">
                      {displayUser.fullName.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{displayUser.fullName}</p>
                      <p className="text-xs text-gray-300 truncate">{displayUser.role}</p>
                    </div>
                  </div>
                  <Button
                    onClick={handleLogout}
                    variant="outline"
                    className="w-full bg-purple-800 hover:bg-purple-700 border-purple-700 text-white"
                  >
                    <LogOut size={16} />
                    Logout
                  </Button>
                </div>
              ) : (
                <button
                  onClick={handleLogout}
                  className="w-full flex justify-center py-2 hover:bg-purple-800 rounded-lg"
                >
                  <LogOut size={20} />
                </button>
              )}
            </div>
          </div>
        </motion.aside>

        {/* Main Content */}
        <div
          className={cn(
            "transition-all duration-300",
            sidebarOpen ? "ml-[280px]" : "ml-[80px]"
          )}
        >
          {/* Header */}
          <header className="h-16 bg-white border-b border-gray-200 sticky top-0 z-40 shadow-sm">
            <div className="h-full px-6 flex items-center justify-between">
              <button
                onClick={() => setSidebarOpen(!sidebarOpen)}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors text-gray-700"
              >
                {sidebarOpen ? <X size={24} /> : <Menu size={24} />}
              </button>

              <div className="flex items-center gap-4">
                <NotificationPanel />
                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <p className="text-sm font-medium text-gray-900">{displayUser.fullName}</p>
                    <p className="text-xs text-gray-500">{displayUser.organizationName}</p>
                  </div>
                  <div className="w-10 h-10 rounded-full bg-yellow-400 flex items-center justify-center text-purple-900 font-bold">
                    {displayUser.fullName.charAt(0)}
                  </div>
                </div>
              </div>
            </div>
          </header>

          {/* Page Content */}
          <main className="p-6">{children}</main>
        </div>
      </div>
    </ProtectedRoute>
  );
}

