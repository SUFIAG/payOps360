import { redirect } from "next/navigation";

export default function Home() {
  // TEMPORARY: Redirect to dashboard for testing
  // TODO: Change back to /login for production
  redirect("/dashboard");
}

