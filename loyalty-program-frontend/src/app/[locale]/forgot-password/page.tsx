"use client";

import { useState } from "react";
import { Link } from "@/i18n/routing";
import { KeyRound, Mail, MailCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { AuthCard, AuthField, AuthSubmit } from "@/components/AuthCard";
import { authApi, ApiError } from "@/lib/api";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Le backend répond la même chose que le compte existe ou non : cet écran ne peut donc
  // rien affirmer de plus que « si un compte correspond ».
  const [sent, setSent] = useState(false);

  const t = useTranslations("Account");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setIsLoading(true);
    setError(null);
    try {
      await authApi.forgotPassword({ email: email.trim() });
      setSent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Request failed");
    } finally {
      setIsLoading(false);
    }
  };

  if (sent) {
    return (
      <AuthCard
        icon={<MailCheck className="w-8 h-8 text-primary" />}
        title={t("forgotSentTitle")}
        success={t("forgotSentMessage")}
      >
        <Link
          href="/login"
          className="flex w-full items-center justify-center rounded-lg text-sm font-medium transition-all shadow-md bg-primary text-primary-foreground hover:bg-primary/90 h-12 px-4 active:scale-[0.98]"
        >
          {t("backToLogin")}
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      icon={<KeyRound className="w-8 h-8 text-primary" />}
      title={t("forgotTitle")}
      description={t("forgotDescription")}
      error={error}
    >
      <form onSubmit={handleSubmit} className="space-y-6">
        <AuthField
          icon={<Mail className="w-5 h-5" />}
          label={t("emailLabel")}
          type="email"
          autoComplete="email"
          autoFocus
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <AuthSubmit type="submit" disabled={isLoading || !email.trim()}>
          {isLoading ? t("sending") : t("sendLink")}
        </AuthSubmit>
        <Link
          href="/login"
          className="block text-center text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          {t("backToLogin")}
        </Link>
      </form>
    </AuthCard>
  );
}
