"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { motion } from "framer-motion";
import {
  ArrowLeft,
  Copy,
  Clock,
  AlertCircle,
  CheckCircle,
  XCircle,
  RefreshCw,
  ExternalLink,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { paymentApi } from "@/lib/api";
import { formatCurrency, formatDate, getStatusColor, cn } from "@/lib/utils";
import { toast } from "sonner";

export default function PaymentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const paymentId = Number(params.id);

  const { data: payment, isLoading } = useQuery({
    queryKey: ["payment", paymentId],
    queryFn: () => paymentApi.getPayment(paymentId),
    enabled: !!paymentId,
  });

  const { data: timeline } = useQuery({
    queryKey: ["payment-timeline", paymentId],
    queryFn: () => paymentApi.getPaymentTimeline(paymentId),
    enabled: !!paymentId,
    refetchInterval: 5000,
  });

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success("Copied to clipboard");
  };

  if (isLoading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-gray-200 rounded w-1/4"></div>
          <div className="h-64 bg-gray-200 rounded"></div>
        </div>
      </div>
    );
  }

  if (!payment) {
    return (
      <div className="p-8">
        <div className="text-center">
          <AlertCircle className="mx-auto text-gray-400 mb-4" size={48} />
          <p className="text-gray-600">Payment not found</p>
          <Button onClick={() => router.back()} className="mt-4" variant="outline">
            <ArrowLeft size={16} />
            Go Back
          </Button>
        </div>
      </div>
    );
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "SETTLED":
        return <CheckCircle className="text-green-600" size={20} />;
      case "FAILED":
        return <XCircle className="text-red-600" size={20} />;
      case "PROCESSING":
        return <RefreshCw className="text-yellow-600 animate-spin" size={20} />;
      default:
        return <Clock className="text-gray-600" size={20} />;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button onClick={() => router.back()} variant="ghost" size="sm">
            <ArrowLeft size={16} />
          </Button>
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Payment Details</h1>
            <p className="text-gray-600 mt-1">Transaction ID: {payment.externalId}</p>
          </div>
        </div>
        <Badge className={getStatusColor(payment.status)}>{payment.status}</Badge>
      </div>

      {/* Main Info Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">Amount</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold text-gray-900">
              {formatCurrency(payment.amount, payment.currency)}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">Provider</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold text-gray-900">{payment.providerName}</p>
            <p className="text-xs text-gray-500 mt-1">{payment.paymentMethod}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">Created</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-lg font-semibold text-gray-900">
              {formatDate(payment.createdAt, "long")}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">Retries</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold text-gray-900">{payment.retryCount}</p>
            <p className="text-xs text-gray-500 mt-1">Attempts</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Payment Details */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Transaction Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-sm font-medium text-gray-600 mb-1">Transaction ID</p>
                <div className="flex items-center gap-2">
                  <p className="text-sm text-gray-900 font-mono">{payment.externalId}</p>
                  <button
                    onClick={() => copyToClipboard(payment.externalId)}
                    className="text-purple-600 hover:text-purple-700"
                  >
                    <Copy size={14} />
                  </button>
                </div>
              </div>

              <div>
                <p className="text-sm font-medium text-gray-600 mb-1">Merchant Reference</p>
                <div className="flex items-center gap-2">
                  <p className="text-sm text-gray-900 font-mono">{payment.merchantReference}</p>
                  <button
                    onClick={() => copyToClipboard(payment.merchantReference)}
                    className="text-purple-600 hover:text-purple-700"
                  >
                    <Copy size={14} />
                  </button>
                </div>
              </div>

              <div>
                <p className="text-sm font-medium text-gray-600 mb-1">Customer Email</p>
                <p className="text-sm text-gray-900">{payment.customerEmail}</p>
              </div>

              <div>
                <p className="text-sm font-medium text-gray-600 mb-1">Customer Name</p>
                <p className="text-sm text-gray-900">{payment.customerName}</p>
              </div>

              <div>
                <p className="text-sm font-medium text-gray-600 mb-1">Payment Method</p>
                <p className="text-sm text-gray-900">{payment.paymentMethod}</p>
              </div>

              <div>
                <p className="text-sm font-medium text-gray-600 mb-1">Currency</p>
                <p className="text-sm text-gray-900">{payment.currency}</p>
              </div>

              {payment.errorCode && (
                <div className="col-span-2">
                  <p className="text-sm font-medium text-red-600 mb-1">Error Details</p>
                  <p className="text-sm text-red-900 bg-red-50 p-2 rounded">
                    [{payment.errorCode}] {payment.errorMessage}
                  </p>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Timeline */}
        <Card>
          <CardHeader>
            <CardTitle>Timeline</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {timeline && timeline.length > 0 ? (
                timeline.map((event, index) => (
                  <motion.div
                    key={event.id}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: index * 0.1 }}
                    className="flex gap-3 relative"
                  >
                    {index !== timeline.length - 1 && (
                      <div className="absolute left-2 top-8 bottom-0 w-0.5 bg-gray-200"></div>
                    )}
                    <div className="flex-shrink-0 mt-1">
                      {getStatusIcon(event.eventType)}
                    </div>
                    <div className="flex-1">
                      <p className="text-sm font-medium text-gray-900">{event.eventType}</p>
                      {event.message && (
                        <p className="text-xs text-gray-600 mt-1">{event.message}</p>
                      )}
                      <p className="text-xs text-gray-500 mt-1">
                        {formatDate(event.timestamp, "long")}
                      </p>
                    </div>
                  </motion.div>
                ))
              ) : (
                <p className="text-sm text-gray-600">No timeline events available</p>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Metadata */}
      {payment.metadata && Object.keys(payment.metadata).length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Additional Metadata</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
              {Object.entries(payment.metadata).map(([key, value]) => (
                <div key={key}>
                  <p className="text-sm font-medium text-gray-600 mb-1">{key}</p>
                  <p className="text-sm text-gray-900">{String(value)}</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

