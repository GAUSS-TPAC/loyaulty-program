"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/routing";
import { Shield, LogOut, Building2, Eye, EyeOff } from "lucide-react";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";

const PLATFORM_KEY_STORAGE_KEY = "loyalty_platform_admin_key";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const t = useTranslations("PlatformAdmin");
  const [platformKey, setPlatformKey] = useState<string | null>(null);
  const [keyInput, setKeyInput] = useState("");
  const [checked, setChecked] = useState(false);
  const [showKey, setShowKey] = useState(false);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- one-time session read on mount
    setPlatformKey(sessionStorage.getItem(PLATFORM_KEY_STORAGE_KEY));
    setChecked(true);
  }, []);

  const handleUnlock = (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyInput.trim()) return;
    sessionStorage.setItem(PLATFORM_KEY_STORAGE_KEY, keyInput.trim());
    setPlatformKey(keyInput.trim());
  };

  const handleLogout = () => {
    sessionStorage.removeItem(PLATFORM_KEY_STORAGE_KEY);
    setPlatformKey(null);
    setKeyInput("");
  };

  if (!checked) return <div className="min-h-screen bg-background" />;

  if (!platformKey) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-6">
        <form
          onSubmit={handleUnlock}
          className="w-full max-w-md bg-card border border-border rounded-xl shadow-sm p-8 space-y-5"
        >
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-secondary flex items-center justify-center border border-border">
              <Shield className="w-4.5 h-4.5 text-primary" />
            </div>
            <h1 className="font-semibold text-foreground">{t("unlock.title")}</h1>
          </div>
          <p className="text-sm text-muted-foreground">{t("unlock.description")}</p>
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground">{t("unlock.secretLabel")}</label>
            <div className="relative">
              <input
                type={showKey ? "text" : "password"}
                value={keyInput}
                onChange={(e) => setKeyInput(e.target.value)}
                placeholder="X-Platform-Admin-Key"
                className="w-full px-3 py-2 pr-10 text-sm rounded-md border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
              />
              <button
                type="button"
                onClick={() => setShowKey((v) => !v)}
                tabIndex={-1}
                aria-label={showKey ? t("unlock.hideSecret") : t("unlock.showSecret")}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground/60 hover:text-foreground transition-colors"
              >
                {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>
          <button
            type="submit"
            className="w-full py-2.5 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition-opacity"
          >
            {t("unlock.submit")}
          </button>
          <p className="text-xs text-center text-muted-foreground">
            {t("unlock.tenantPrompt")}{" "}
            <Link href="/portal" className="text-primary hover:underline">
              {t("unlock.tenantLink")}
            </Link>
          </p>
        </form>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background text-foreground font-sans">
      <header className="h-16 border-b border-border flex items-center justify-between gap-3 px-4 sm:px-6 md:px-8 bg-card/80 backdrop-blur sticky top-0 z-10 shadow-sm">
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-8 h-8 rounded-lg bg-secondary flex items-center justify-center border border-border flex-shrink-0">
            <Shield className="w-4 h-4 text-primary" />
          </div>
          <span className="font-semibold tracking-wide text-foreground truncate">{t("header.title")}</span>
          <span className="hidden sm:flex items-center gap-1.5 text-xs text-muted-foreground border border-border rounded-full px-2.5 py-0.5 ml-2 flex-shrink-0">
            <Building2 className="w-3 h-3" /> {t("header.badge")}
          </span>
        </div>
        <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0">
          <LanguageSwitcher />
          <button
            onClick={handleLogout}
            aria-label={t("header.logout")}
            className="flex items-center gap-2 px-2 sm:px-3 py-1.5 text-xs text-destructive hover:bg-destructive/10 rounded-md transition-colors"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">{t("header.logout")}</span>
          </button>
        </div>
      </header>
      <div className="p-4 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-8 pb-12">{children}</div>
    </div>
  );
}
