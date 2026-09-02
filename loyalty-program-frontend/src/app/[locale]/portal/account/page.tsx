"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Building2, CheckCircle2, Clock, Lock, LogOut } from "lucide-react";
import { authApi, ApiError } from "@/lib/api";
import { clearSession, getOrganizationId, getRefreshToken } from "@/lib/session";
import { useRouter } from "@/i18n/routing";

/** Même règle que @StrongPassword côté backend : refuser ici évite un aller-retour pour rien. */
const STRONG_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/;

export default function AccountPage() {
  const t = useTranslations("Account");
  const router = useRouter();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [organizationId, setOrganizationId] = useState<string | null>(null);

  // Le stockage n'existe pas au rendu serveur : la session ne se lit qu'une fois monté.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- lecture unique de la session au montage
    setOrganizationId(getOrganizationId());
  }, []);

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setDone(false);
    if (!STRONG_PASSWORD.test(newPassword)) {
      setError(t("passwordTooWeak"));
      return;
    }
    if (newPassword !== confirmation) {
      setError(t("passwordMismatch"));
      return;
    }
    setIsSaving(true);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      setDone(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmation("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Change failed");
    } finally {
      setIsSaving(false);
    }
  };

  const handleLogout = async () => {
    try {
      await authApi.logout(getRefreshToken() ?? undefined);
    } catch {
      // révocation best-effort : la déconnexion locale a lieu quoi qu'il arrive
    }
    clearSession();
    router.push("/");
  };

  const field = (
    label: string,
    value: string,
    onChange: (v: string) => void,
    autoComplete: string
  ) => (
    <div className="space-y-2">
      <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </label>
      <div className="relative">
        <span className="absolute left-3 top-3.5 text-muted-foreground/60">
          <Lock className="w-5 h-5" />
        </span>
        <input
          type="password"
          autoComplete={autoComplete}
          required
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="flex h-12 w-full rounded-lg border border-border bg-background pl-10 pr-4 py-2 text-sm shadow-sm transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 focus-visible:border-primary"
        />
      </div>
    </div>
  );

  return (
    <div className="space-y-8 max-w-2xl">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t("accountTitle")}</h1>
        <p className="text-sm text-muted-foreground mt-1">{t("accountDescription")}</p>
      </div>

      <section className="border border-border rounded-xl bg-card p-6 space-y-4">
        <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
          <Clock className="w-4 h-4 text-primary" />
          {t("sessionTitle")}
        </h2>
        <dl className="text-sm space-y-2">
          <div className="flex items-center justify-between gap-4">
            <dt className="text-muted-foreground flex items-center gap-2">
              <Building2 className="w-4 h-4" />
              {t("sessionOrganization")}
            </dt>
            <dd className="font-mono text-xs text-foreground truncate">{organizationId ?? "—"}</dd>
          </div>
        </dl>
        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex items-center gap-2 rounded-lg border border-border px-4 h-10 text-sm font-medium text-foreground hover:bg-secondary transition-colors"
        >
          <LogOut className="w-4 h-4" />
          {t("logout")}
        </button>
      </section>

      <section className="border border-border rounded-xl bg-card p-6 space-y-5">
        <div>
          <h2 className="text-sm font-semibold text-foreground">{t("changeTitle")}</h2>
          <p className="text-xs text-muted-foreground mt-1">{t("changeDescription")}</p>
        </div>

        {error && (
          <div className="bg-destructive/10 border border-destructive/20 text-destructive px-4 py-3 rounded-lg text-xs font-medium flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {done && (
          <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-lg text-xs font-medium flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
            <span>{t("changeDone")}</span>
          </div>
        )}

        <form onSubmit={handleChangePassword} className="space-y-4">
          {field(t("currentPasswordLabel"), currentPassword, setCurrentPassword, "current-password")}
          {field(t("newPasswordLabel"), newPassword, setNewPassword, "new-password")}
          {field(t("confirmPasswordLabel"), confirmation, setConfirmation, "new-password")}
          <button
            type="submit"
            disabled={isSaving || !currentPassword || !newPassword || !confirmation}
            className="inline-flex items-center justify-center rounded-lg text-sm font-medium transition-all shadow-md bg-primary text-primary-foreground hover:bg-primary/90 h-11 px-5 disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98]"
          >
            {isSaving ? t("changing") : t("changeSubmit")}
          </button>
        </form>
      </section>
    </div>
  );
}
