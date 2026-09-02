"use client";

import { useEffect, useState } from "react";
import { Link } from "@/i18n/routing";
import { CheckCircle2, Lock, ShieldCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { AuthCard, AuthField, AuthSubmit } from "@/components/AuthCard";
import { authApi, ApiError } from "@/lib/api";

/** Même règle que @StrongPassword côté backend : refuser ici évite un aller-retour pour rien. */
const STRONG_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/;

export default function ResetPasswordPage() {
  const [token, setToken] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const t = useTranslations("Account");

  // Le jeton arrive dans l'URL du lien reçu par email. Le nom du paramètre dépend du
  // gabarit d'email de KernelCore, que ce dépôt ne contrôle pas : on accepte les deux
  // conventions plutôt que de rendre le lien inopérant sur un écart de nommage.
  // Lu depuis window plutôt que via useSearchParams, qui imposerait une frontière Suspense.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const fromUrl = params.get("token") ?? params.get("resetToken");
    // eslint-disable-next-line react-hooks/set-state-in-effect -- lecture unique de l'URL au montage
    setToken(fromUrl);
    if (!fromUrl) setError(t("missingToken"));
  }, [t]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) return;
    if (!STRONG_PASSWORD.test(password)) {
      setError(t("passwordTooWeak"));
      return;
    }
    if (password !== confirmation) {
      setError(t("passwordMismatch"));
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      await authApi.resetPassword({ token, newPassword: password });
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Reset failed");
    } finally {
      setIsLoading(false);
    }
  };

  if (done) {
    return (
      <AuthCard
        icon={<CheckCircle2 className="w-8 h-8 text-primary" />}
        title={t("resetDoneTitle")}
        success={t("resetDoneMessage")}
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
      icon={<ShieldCheck className="w-8 h-8 text-primary" />}
      title={t("resetTitle")}
      description={t("resetDescription")}
      error={error}
    >
      <form onSubmit={handleSubmit} className="space-y-6">
        <AuthField
          icon={<Lock className="w-5 h-5" />}
          label={t("newPasswordLabel")}
          type="password"
          autoComplete="new-password"
          autoFocus
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <AuthField
          icon={<Lock className="w-5 h-5" />}
          label={t("confirmPasswordLabel")}
          type="password"
          autoComplete="new-password"
          required
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
        />
        <AuthSubmit type="submit" disabled={isLoading || !token || !password || !confirmation}>
          {isLoading ? t("resetting") : t("resetSubmit")}
        </AuthSubmit>
        <Link
          href="/forgot-password"
          className="block text-center text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          {t("forgotTitle")}
        </Link>
      </form>
    </AuthCard>
  );
}
