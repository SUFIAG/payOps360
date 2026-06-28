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
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-brand-purple-950 via-brand-purple-900 to-brand-purple-800 p-4">
      {/* Animated background */}
      <div className="absolute inset-0 overflow-hidden">
        <motion.div
          className="absolute top-1/4 left-1/4 w-96 h-96 bg-brand-lavender-500/10 rounded-full blur-3xl"
          animate={{ scale: [1, 1.2, 1], opacity: [0.3, 0.5, 0.3] }}
          transition={{ duration: 8, repeat: Infinity, ease: "easeInOut" }}
        />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md relative z-10"
      >
        <div className="text-center mb-8">
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
            className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-gradient-to-br from-brand-purple-600 to-brand-lavender-400 mb-4"
          >
            <span className="text-2xl font-bold text-white">P360</span>
          </motion.div>
          <h1 className="text-3xl font-bold text-white mb-2">Reset Password</h1>
          <p className="text-brand-lavender-200">We'll send you a reset link</p>
        </div>

        <Card className="border-brand-purple-700/50 bg-white/95 backdrop-blur-sm shadow-2xl">
          {!isSubmitted ? (
            <>
              <CardHeader>
                <CardTitle className="text-2xl text-brand-purple-900">Forgot Password?</CardTitle>
                <CardDescription>Enter your email to receive a password reset link</CardDescription>
              </CardHeader>
              <form onSubmit={handleSubmit}>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <label htmlFor="email" className="text-sm font-medium text-gray-700 flex items-center gap-2">
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
                      className="h-11"
                    />
                  </div>
                </CardContent>
                <CardFooter className="flex-col space-y-4">
                  <Button type="submit" className="w-full h-11 text-base" disabled={isLoading}>
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
                  <Link href="/login" className="flex items-center gap-2 text-sm text-brand-purple-600 hover:text-brand-purple-700 font-medium mx-auto">
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
                  <motion.div
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    transition={{ type: "spring", stiffness: 200 }}
                    className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center"
                  >
                    <CheckCircle2 size={32} className="text-green-600" />
                  </motion.div>
                </div>
                <CardTitle className="text-2xl text-brand-purple-900 text-center">Check Your Email</CardTitle>
                <CardDescription className="text-center">
                  We've sent a password reset link to <strong>{email}</strong>
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="text-sm text-gray-600 text-center space-y-2">
                  <p>Click the link in the email to reset your password.</p>
                  <p>Didn't receive it? Check your spam folder.</p>
                </div>
              </CardContent>
              <CardFooter className="flex-col space-y-4">
                <Button onClick={() => setIsSubmitted(false)} variant="outline" className="w-full">
                  Try Another Email
                </Button>
                <Link href="/login" className="flex items-center gap-2 text-sm text-brand-purple-600 hover:text-brand-purple-700 font-medium mx-auto">
                  <ArrowLeft size={16} />
                  Back to Login
                </Link>
              </CardFooter>
            </>
          )}
        </Card>
      </motion.div>
    </div>
  );
}

