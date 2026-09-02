"use client";

import { useEffect, useState } from "react";
import { Link } from "@/i18n/routing";
import { Mail, MailCheck, MailWarning } from "lucide-react";
import { useTranslations } from "next-intl";
import { AuthCard, AuthField, AuthSubmit } from "@/components/AuthCard";
import { authApi, ApiError } from "@/lib/api";

/**
 * Cible du lien de vérification envoyé par KernelCore. Le jeton est à usage unique : en cas
 * d'échec (lien expiré, déjà consommé), l'écran propose d'en redemander un plutôt que de
 * laisser l'utilisateur devant une impasse.
 */
export default function VerifyEmailPage() {
  const [status, setStatus] = useState<"pending" | "done" | "failed">("pending");
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [isResending, setIsResending] = useState(false);
  const [resent, setResent] = useState(false);

  const t = useTranslations("Account");
  const tRegister = useTranslations("Register");

  useEffect(() => {
    // Nom du paramètre non contrôlé par ce dépôt (gabarit d'email KernelCore) : on accepte
    // les deux conventions plutôt que de rendre le lien inopérant sur un écart de nommage.
    const params = new URLSearchParams(window.location.search);
    const token = params.get("token") ?? params.get("verificationToken");
    if (!token) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- lecture unique de l'URL au montage
      setStatus("failed");
      setError(t("verifyMissingToken"));
      return;
    }
    authApi
      .confirmEmail({ token })
      .then(() => setStatus("done"))
      .catch((err) => {
        setStatus("failed");
        setError(err instanceof ApiError ? err.message : "Verification failed");
      });
  }, [t]);

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setIsResending(true);
    try {
      await authApi.resendVerification({ email: email.trim() });
      setResent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Request failed");
    } finally {
      setIsResending(false);
    }
  };

  if (status === "pending") {
    return (
      <AuthCard icon={<Mail className="w-8 h-8 text-primary" />} title={t("verifyTitle")}>
        <p className="text-center text-sm text-muted-foreground">{t("verifying")}</p>
      </AuthCard>
    );
  }

  if (status === "done") {
    return (
      <AuthCard
        icon={<MailCheck className="w-8 h-8 text-primary" />}
        title={t("verifyDoneTitle")}
        success={t("verifyDoneMessage")}
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
      icon={<MailWarning className="w-8 h-8 text-primary" />}
      title={t("verifyFailedTitle")}
      error={error}
      success={resent ? tRegister("resendDone") : null}
    >
      <form onSubmit={handleResend} className="space-y-6">
        <AuthField
          icon={<Mail className="w-5 h-5" />}
          label={t("resendEmailLabel")}
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <AuthSubmit type="submit" disabled={isResending || !email.trim()}>
          {isResending ? tRegister("resendSending") : t("resend")}
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
