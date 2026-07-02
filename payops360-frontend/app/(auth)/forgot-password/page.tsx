"use client";

import { useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import { ArrowLeft, Mail, Send, Loader2, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { toast } from "sonner";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!email) {
      toast.error("Please enter your email");
      return;
    }

    setIsLoading(true);
    // Simulate API call
    setTimeout(() => {
      setIsLoading(false);
      setIsSubmitted(true);
      toast.success("Reset link sent to your email");
    }, 2000);
  };

  return (
    <div className="min-h-screen bg-[#0f0a1a] flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md relative z-10"
      >
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-lg bg-gradient-to-br from-yellow-400 to-yellow-500 mb-4 shadow-lg">
            <span className="text-2xl font-bold text-purple-900">P360</span>
          </div>
          <h1 className="text-3xl font-bold text-white mb-2">Reset Password</h1>
          <p className="text-gray-400">We&apos;ll send you a reset link</p>
        </div>

        <div className="bg-[#1a1229] rounded-2xl p-8 border border-gray-800 shadow-2xl">
          {!isSubmitted ? (
            <>
              <CardHeader>
                <CardTitle className="text-2xl text-white">Forgot Password?</CardTitle>
                <CardDescription className="text-gray-400">Enter your email to receive a password reset link</CardDescription>
              </CardHeader>
              <form onSubmit={handleSubmit}>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <label htmlFor="email" className="text-sm font-medium text-gray-300 flex items-center gap-2">
                      <Mail size={16} /> Email Address
                    </label>
                    <Input
                      id="email"
                      type="email"
                      placeholder="admin@payops360.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      disabled={isLoading}
                      required
                      className="h-11 bg-[#0f0a1a] border-gray-700 text-white placeholder-gray-500 focus:ring-2 focus:ring-purple-600"
                    />
                  </div>
                </CardContent>
                <CardFooter className="flex-col space-y-4">
                  <Button type="submit" className="w-full h-11 text-base bg-purple-600 hover:bg-purple-700 text-white" disabled={isLoading}>
                    {isLoading ? (
                      <>
                        <Loader2 className="animate-spin" />
                        Sending...
                      </>
                    ) : (
                      <>
                        <Send size={18} />
                        Send Reset Link
                      </>
                    )}
                  </Button>
                  <Link href="/login" className="flex items-center gap-2 text-sm text-yellow-400 hover:text-yellow-300 font-medium mx-auto">
                    <ArrowLeft size={16} />
                    Back to Login
                  </Link>
                </CardFooter>
              </form>
            </>
          ) : (
            <>
              <CardHeader>
                <div className="flex justify-center mb-4">
                  <div className="w-16 h-16 rounded-full bg-green-600/20 flex items-center justify-center">
                    <CheckCircle2 size={32} className="text-green-500" />
                  </div>
                </div>
                <CardTitle className="text-2xl text-white text-center">Check Your Email</CardTitle>
                <CardDescription className="text-center text-gray-400">
                  We&apos;ve sent a password reset link to <strong className="text-white">{email}</strong>
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="text-sm text-gray-400 text-center space-y-2">
                  <p>Click the link in the email to reset your password.</p>
                  <p>Didn&apos;t receive it? Check your spam folder.</p>
                </div>
              </CardContent>
              <CardFooter className="flex-col space-y-4">
                <Button onClick={() => setIsSubmitted(false)} variant="outline" className="w-full bg-[#0f0a1a] border-gray-700 text-white hover:bg-purple-900/50">
                  Try Another Email
                </Button>
                <Link href="/login" className="flex items-center gap-2 text-sm text-yellow-400 hover:text-yellow-300 font-medium mx-auto">
                  <ArrowLeft size={16} />
                  Back to Login
                </Link>
              </CardFooter>
            </>
          )}
        </div>
      </motion.div>
    </div>
  );
}

