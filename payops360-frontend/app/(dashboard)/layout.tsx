"use client";

import { ReactNode, useState } from "react";
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
  Bell,
  User,
} from "lucide-react";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";
import { useAuthStore } from "@/lib/store/authStore";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

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
  const { user, logout } = useAuthStore();

  const handleLogout = async () => {
    await logout();
    window.location.href = "/login";
  };

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50">
        {/* Sidebar */}
        <motion.aside
          initial={false}
          animate={{ width: sidebarOpen ? 280 : 80 }}
          className="fixed left-0 top-0 h-full bg-gradient-to-b from-brand-purple-900 to-brand-purple-800 text-white shadow-2xl z-50"
        >
          <div className="flex flex-col h-full">
            {/* Logo */}
            <div className="h-16 flex items-center justify-between px-4 border-b border-brand-purple-700">
              {sidebarOpen ? (
                <div className="flex items-center gap-2">
                  <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-brand-lavender-400 to-brand-yellow-400 flex items-center justify-center font-bold text-brand-purple-900">
                    P360
                  </div>
                  <span className="font-bold text-lg">PayOps360</span>
                </div>
              ) : (
                <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-brand-lavender-400 to-brand-yellow-400 flex items-center justify-center font-bold text-brand-purple-900 mx-auto">
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
                        ? "bg-brand-purple-700 text-white"
                        : "hover:bg-brand-purple-800 text-brand-lavender-200"
                    )}
                  >
                    {item.icon}
                    {sidebarOpen && <span className="font-medium">{item.label}</span>}
                  </Link>
                );
              })}
            </nav>

            {/* User Section */}
            <div className="border-t border-brand-purple-700 p-4">
              {sidebarOpen ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-3 pb-3">
                    <div className="w-10 h-10 rounded-full bg-brand-lavender-400 flex items-center justify-center text-brand-purple-900 font-bold">
                      {user?.fullName?.charAt(0) || "U"}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{user?.fullName}</p>
                      <p className="text-xs text-brand-lavender-300 truncate">{user?.role}</p>
                    </div>
                  </div>
                  <Button
                    onClick={handleLogout}
                    variant="outline"
                    className="w-full bg-brand-purple-800 hover:bg-brand-purple-700 border-brand-purple-600 text-white"
                  >
                    <LogOut size={16} />
                    Logout
                  </Button>
                </div>
              ) : (
                <button
                  onClick={handleLogout}
                  className="w-full flex justify-center py-2 hover:bg-brand-purple-800 rounded-lg"
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
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
              >
                {sidebarOpen ? <X size={24} /> : <Menu size={24} />}
              </button>

              <div className="flex items-center gap-4">
                <button className="relative p-2 hover:bg-gray-100 rounded-lg transition-colors">
                  <Bell size={20} />
                  <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full"></span>
                </button>
                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <p className="text-sm font-medium">{user?.fullName}</p>
                    <p className="text-xs text-gray-500">{user?.organizationName}</p>
                  </div>
                  <div className="w-10 h-10 rounded-full bg-brand-purple-100 flex items-center justify-center text-brand-purple-700 font-bold">
                    {user?.fullName?.charAt(0) || "U"}
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

