"use client";

import { ReactNode } from "react";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { LandingHeader } from "@/components/LandingHeader";

/**
 * Coque commune des écrans de récupération de compte (mot de passe oublié, réinitialisation,
 * vérification d'email). Ces pages sont hors session : elles ne peuvent pas s'appuyer sur la
 * mise en page du portail, et sans coque partagée chacune redupliquerait le décor de /login.
 */
export function AuthCard({
  icon,
  title,
  description,
  error,
  success,
  children,
}: {
  icon: ReactNode;
  title: string;
  description?: string;
  error?: string | null;
  success?: string | null;
  children: ReactNode;
}) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-6 pt-24 relative overflow-hidden bg-background">
      <LandingHeader />

      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none opacity-[0.4]">
        <div className="absolute -top-64 -left-64 w-[800px] h-[800px] bg-secondary rounded-full blur-3xl mix-blend-multiply" />
        <div className="absolute -bottom-64 -right-64 w-[600px] h-[600px] bg-[#d7ccc8] rounded-full blur-3xl mix-blend-multiply opacity-50" />
      </div>

      <div className="w-full max-w-md z-10 space-y-8 bg-card p-10 rounded-xl shadow-xl shadow-primary/5 border border-border">
        <div className="space-y-3 text-center">
          <div className="mx-auto w-16 h-16 rounded-2xl bg-secondary flex items-center justify-center mb-6 shadow-sm border border-border">
            {icon}
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>

        {error && (
          <div className="bg-destructive/10 border border-destructive/20 text-destructive px-4 py-3 rounded-lg text-xs font-medium flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-lg text-xs font-medium flex items-start gap-2">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>{success}</span>
          </div>
        )}

        {children}
      </div>
    </main>
  );
}

/** Champ de saisie des écrans d'authentification, avec l'icône en préfixe. */
export function AuthField({
  icon,
  label,
  ...props
}: { icon: ReactNode; label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className="space-y-2">
      <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground ml-1">
        {label}
      </label>
      <div className="relative">
        <span className="absolute left-3 top-3.5 text-muted-foreground/60">{icon}</span>
        <input
          {...props}
          className="flex h-12 w-full rounded-lg border border-border bg-background pl-10 pr-4 py-2 text-sm shadow-sm transition-all placeholder:text-muted-foreground/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 focus-visible:border-primary"
        />
      </div>
    </div>
  );
}

/** Bouton principal plein largeur des écrans d'authentification. */
export function AuthSubmit({
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...props}
      className="inline-flex items-center justify-center whitespace-nowrap rounded-lg text-sm font-medium transition-all shadow-md bg-primary text-primary-foreground hover:bg-primary/90 h-12 px-4 py-2 w-full disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98]"
    >
      {children}
    </button>
  );
}
