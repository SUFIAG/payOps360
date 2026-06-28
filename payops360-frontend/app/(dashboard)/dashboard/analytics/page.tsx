"use client";

import { BarChart3 } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export default function AnalyticsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Analytics</h1>
        <p className="text-gray-500 mt-1">Payment trends and performance analytics</p>
      </div>

      <Card>
        <CardContent className="flex flex-col items-center justify-center py-12">
          <BarChart3 className="text-gray-400 mb-4" size={64} />
          <p className="text-gray-600 font-medium">Analytics dashboard coming soon</p>
          <p className="text-sm text-gray-500 mt-2">
            This page will display payment trends, provider performance, and AI insights
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

