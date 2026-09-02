"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, KeyRound, Lock, Trash2, User } from "lucide-react";
import { bonificationApi, type BonificationCredentials } from "@/lib/api";

/**
 * Identifiants de l'API Bonification propres au tenant.
 *
 * Ils n'étaient jusqu'ici modifiables qu'en écrivant à la main dans le JSONB tenants.config,
 * et y étaient stockés en clair. Le mot de passe est désormais chiffré côté backend et n'est
 * jamais relu : cet écran affiche l'identifiant configuré, pas le secret.
 */
export function BonificationCredentialsCard() {
  const [credentials, setCredentials] = useState<BonificationCredentials | null>(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = () => {
    bonificationApi
      .getCredentials()
      .then((data) => {
        setCredentials(data);
        setUsername(data.configured ? data.username : "");
      })
      .catch(() => setCredentials(null));
  };

  useEffect(load, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await bonificationApi.saveCredentials({ username: username.trim(), password });
      setCredentials(updated);
      setPassword("");
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible");
    } finally {
      setIsSaving(false);
    }
  };

  const handleClear = async () => {
    setIsSaving(true);
    setError(null);
    try {
      await bonificationApi.clearCredentials();
      setUsername("");
      setPassword("");
      setSaved(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Suppression impossible");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <section className="border border-border rounded-xl bg-card p-6 space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
            <KeyRound className="w-4 h-4 text-primary" />
            Identifiants Bonification
          </h2>
          <p className="text-xs text-muted-foreground mt-1">
            {credentials?.configured
              ? "Ce tenant utilise ses propres identifiants. Le mot de passe est chiffré en base et n'est jamais réaffiché."
              : "Ce tenant utilise les identifiants globaux du déploiement. Renseignez-en de spécifiques ci-dessous."}
          </p>
        </div>
        {credentials?.configured && (
          <button
            type="button"
            onClick={handleClear}
            disabled={isSaving}
            title="Revenir aux identifiants globaux"
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 h-9 text-xs font-medium text-muted-foreground hover:text-destructive hover:border-destructive/40 transition-colors disabled:opacity-50"
          >
            <Trash2 className="w-3.5 h-3.5" />
            Effacer
          </button>
        )}
      </div>

      {error && (
        <div className="bg-destructive/10 border border-destructive/20 text-destructive px-4 py-3 rounded-lg text-xs font-medium flex items-start gap-2">
          <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      {saved && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-lg text-xs font-medium flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
          <span>Identifiants enregistrés.</span>
        </div>
      )}

      <form onSubmit={handleSave} className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Identifiant
          </label>
          <div className="relative">
            <span className="absolute left-3 top-3 text-muted-foreground/60">
              <User className="w-4 h-4" />
            </span>
            <input
              type="text"
              autoComplete="off"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="flex h-10 w-full rounded-lg border border-border bg-background pl-9 pr-4 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 focus-visible:border-primary"
            />
          </div>
        </div>

        <div className="space-y-2">
          <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Mot de passe
          </label>
          <div className="relative">
            <span className="absolute left-3 top-3 text-muted-foreground/60">
              <Lock className="w-4 h-4" />
            </span>
            <input
              type="password"
              autoComplete="new-password"
              required
              placeholder={credentials?.configured ? "•••••••• (inchangé)" : ""}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="flex h-10 w-full rounded-lg border border-border bg-background pl-9 pr-4 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 focus-visible:border-primary"
            />
          </div>
        </div>

        <div className="sm:col-span-2">
          <button
            type="submit"
            disabled={isSaving || !username.trim() || !password}
            className="inline-flex items-center justify-center rounded-lg text-sm font-medium transition-all shadow-md bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-5 disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98]"
          >
            {isSaving ? "Enregistrement..." : "Enregistrer"}
          </button>
        </div>
      </form>
    </section>
  );
}
