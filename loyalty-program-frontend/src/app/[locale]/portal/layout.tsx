"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, Link } from "@/i18n/routing";
import { usePathname } from "next/navigation";
import { Terminal, Settings, LogOut, Code2, Cpu, Wallet, AppWindow, LayoutDashboard, Menu, X, Zap, Gift, Tag, Megaphone, CreditCard, WifiOff, Key, Webhook, BookOpen, UserPlus, Boxes, UserCog } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";
import { useAccess } from "@/hooks/useBackend";
import { flushQueue } from "@/lib/offlineQueue";
import { authApi } from "@/lib/api";
import { clearSession, getAccessToken, getApiKey, getRefreshToken, hasSession } from "@/lib/session";

export default function PortalLayout({ children }: { children: React.ReactNode }) {
  const [authenticated, setAuthenticated] = useState(false);
  // Aperçu du credential actif (début du JWT ou de la clé API), pour distinguer d'un coup
  // d'œil la session utilisée. Jamais affiché en entier : c'est un secret.
  const [credentialPreview, setCredentialPreview] = useState("");
  const [checked, setChecked] = useState(false);
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const router = useRouter();
  const pathname = usePathname();
  const tNav = useTranslations("Navigation");
  const tSide = useTranslations("Sidebar");
  const tHeader = useTranslations("Header");
  const isOnline = useOnlineStatus();
  const wasOnline = useRef(isOnline);
  const { data: access } = useAccess();

  useEffect(() => {
    const trySync = async () => {
      const { synced } = await flushQueue();
      if (synced > 0) {
        toast.success(`${synced} événement(s) synchronisé(s) avec succès`);
      }
    };

    if (isOnline && !wasOnline.current) {
      toast.success("Connexion rétablie");
      trySync();
    } else if (!isOnline && wasOnline.current) {
      toast.warning("Vous êtes hors ligne — les données affichées peuvent être obsolètes");
    } else if (isOnline) {
      // Tab reopened while already back online: flush anything left queued.
      trySync();
    }
    wasOnline.current = isOnline;
  }, [isOnline]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- one-time session read on mount
    setAuthenticated(hasSession());
    setCredentialPreview((getAccessToken() ?? getApiKey() ?? "").substring(0, 14));
    setChecked(true);
  }, []);

  // Sans session (JWT ou clé API), le portail n'est pas accessible : retour à la
  // landing page, point d'entrée du parcours d'onboarding (login / register).
  useEffect(() => {
    if (checked && !authenticated) {
      router.replace("/");
    }
  }, [checked, authenticated, router]);

  // Close sidebar drawer on screen transitions or nav clicks
  useEffect(() => {
    setIsMobileOpen(false);
  }, [pathname]);

  // La révocation côté KernelCore est tentée d'abord, mais ne conditionne pas la
  // déconnexion : un backend injoignable ne doit pas retenir l'utilisateur dans sa session.
  const handleLogout = async () => {
    const refreshToken = getRefreshToken();
    try {
      await authApi.logout(refreshToken ?? undefined);
    } catch {
      // révocation best-effort
    }
    clearSession();
    router.push("/");
  };

  if (!checked || !authenticated) return <div className="min-h-screen bg-background" />;

  const navItems = [
    { name: tNav("overview"), href: "/portal", exact: true, icon: LayoutDashboard },
    { name: tNav("rulesConfig"), href: "/portal/rules", icon: Code2 },
    { name: tNav("establishment"), href: "/portal/establishment", icon: Settings },
    { name: tNav("walletPolicy"), href: "/portal/wallet/config", icon: Wallet },
    { name: tNav("membersDirectory"), href: "/portal/members", icon: AppWindow },
    { name: "Événements", href: "/portal/events", icon: Zap },
    { name: "Bonification", href: "/portal/bonification", icon: Gift },
    { name: "Codes Promo", href: "/portal/promo", icon: Tag },
    { name: "Campagnes", href: "/portal/campaigns", icon: Megaphone },
    { name: "Abonnement", href: "/portal/subscriptions", icon: CreditCard },
    { name: "Applications", href: "/portal/applications", icon: Boxes },
    { name: "Clés API", href: "/portal/api-keys", icon: Key },
    { name: "Webhooks", href: "/portal/webhooks", icon: Webhook },
    { name: tNav("eventLogs"), href: "/portal/logs", icon: Terminal },
    { name: "Livraisons Webhook", href: "/portal/webhook-logs", icon: Terminal },
    { name: "Documentation", href: "/portal/docs", icon: BookOpen },
    { name: "Mon compte", href: "/portal/account", icon: UserCog },
    ...(access?.tenantAdmin
      ? [{ name: "Développeurs", href: "/portal/developers", icon: UserPlus }]
      : []),
  ];

  return (
    <div className="flex min-h-screen bg-background text-foreground font-sans selection:bg-primary selection:text-white">
      {/* Sidebar Overlay on mobile */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 bg-background/60 backdrop-blur-sm z-20 md:hidden"
          onClick={() => setIsMobileOpen(false)}
        />
      )}

      {/* Sidebar drawer */}
      <aside className={`fixed inset-y-0 left-0 w-64 border-r border-border bg-card flex flex-col shadow-sm z-35 transform transition-transform duration-300 md:translate-x-0 md:static ${isMobileOpen ? "translate-x-0" : "-translate-x-full"
        }`}>
        <div className="p-6 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-secondary flex items-center justify-center border border-border">
              <Cpu className="w-4 h-4 text-primary" />
            </div>
            <span className="font-semibold tracking-wide text-foreground">{tSide("loyaltyCore")}</span>
          </div>
          {/* Close Toggle */}
          <button
            onClick={() => setIsMobileOpen(false)}
            className="md:hidden p-1.5 rounded-lg hover:bg-secondary text-muted-foreground active:scale-95 transition-all"
          >
            <X className="w-4.5 h-4.5" />
          </button>
        </div>

        <div className="p-6 pt-4 pb-4 border-b border-border">
          <div className="text-xs text-muted-foreground truncate bg-muted px-2 py-1 rounded-md border border-border">
            {tSide("key")}{credentialPreview ? `${credentialPreview}...` : ""}
          </div>
        </div>

        <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
          {navItems.map((item) => {
            const cleanPath = pathname.replace(/^\/[a-z]{2}(?=\/|$)/, "") || "/";
            const isActive = item.exact
              ? cleanPath === item.href
              : cleanPath.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-3 px-3 py-2.5 text-sm transition-all rounded-md ${isActive
                    ? "bg-primary text-primary-foreground font-medium shadow-sm"
                    : "text-muted-foreground hover:bg-secondary hover:text-foreground"
                  }`}
              >
                <item.icon className={`w-4 h-4 ${isActive ? "text-primary-foreground" : ""}`} />
                {item.name}
              </Link>
            );
          })}
        </nav>

        <div className="p-4 border-t border-border">
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 px-3 py-2.5 text-sm text-destructive hover:bg-destructive/10 rounded-md transition-colors"
          >
            <LogOut className="w-4 h-4" />
            {tSide("disconnect")}
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden relative">
        <header className="h-16 border-b border-border flex items-center justify-between px-6 md:px-8 bg-card/80 backdrop-blur sticky top-0 z-10 shadow-sm">
          <div className="flex items-center gap-3">
            {/* Hamburger toggle */}
            <button
              onClick={() => setIsMobileOpen(true)}
              className="md:hidden p-2 -ml-2 rounded-lg hover:bg-secondary text-muted-foreground active:scale-95 transition-all"
            >
              <Menu className="w-5 h-5" />
            </button>
            <h2 className="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              {tHeader("dashboard")}
            </h2>
          </div>
          <div className="flex items-center gap-3">
            {!isOnline && (
              <span className="flex items-center gap-1.5 text-xs font-medium text-amber-700 bg-amber-100 border border-amber-200 px-2.5 py-1 rounded-full">
                <WifiOff className="w-3.5 h-3.5" />
                Hors ligne
              </span>
            )}
            <LanguageSwitcher />
          </div>
        </header>
        <div className="flex-1 overflow-auto p-4 sm:p-6 md:p-8 bg-background">
          <div className="max-w-6xl mx-auto space-y-8 pb-12">
            {children}
          </div>
        </div>
      </main>
    </div>
  );
}
