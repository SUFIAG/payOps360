"use client";

import { Settings } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Settings</h1>
        <p className="text-gray-600 mt-1">Configure system and user preferences</p>
      </div>

      <Card>
        <CardContent className="flex flex-col items-center justify-center py-12">
          <Settings className="text-gray-400 mb-4" size={64} />
          <p className="text-gray-900 font-medium">Settings coming soon</p>
          <p className="text-sm text-gray-600 mt-2">
            This page will include user profile, notifications, and system configuration
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

