"use client";

import { useState } from "react";
import {
    Send,
    RefreshCw,
    Info,
    ArrowUpCircle,
    ArrowDownCircle,
    History,
    AlertTriangle,
} from "lucide-react";
import { bonificationApi } from "@/lib/api";
import { useAdminLogs } from "@/hooks/useBackend";
import { BonificationCredentialsCard } from "@/components/BonificationCredentialsCard";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function extractErrorMessage(err: unknown): string {
    let message = err instanceof Error ? err.message : "Erreur lors de l'ajustement";
    // Le backend renvoie un ProblemDetail JSON ("[400] {...}") : extraire le champ detail.
    const jsonStart = message.indexOf("{");
    if (jsonStart >= 0) {
        try {
            const problem = JSON.parse(message.slice(jsonStart));
            if (problem.detail || problem.title) {
                message = problem.detail ?? problem.title;
            }
        } catch {
            // corps non-JSON : garder le message brut
        }
    }
    return message;
}

function formatDate(iso: string): string {
    return new Date(iso).toLocaleString("fr-FR", {
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

// ─── Page principale ──────────────────────────────────────────────────────────

export default function BonificationPage() {
    // Journal persistant, tenant-wide (ajustements manuels uniquement)
    const { data: logs, isLoading: logsLoading, error: logsError, refetch: refetchLogs } = useAdminLogs(0, 20);
    const adjustments = (logs ?? []).filter((l) => l.source === "MANUAL_ADJUSTMENT");

    // Formulaire
    const [memberId, setMemberId] = useState("");
    const [amount, setAmount] = useState("");
    const [reason, setReason] = useState("");
    const [isDebit, setIsDebit] = useState(false);

    // UI
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [toast, setToast] = useState<{
        message: string;
        type: "success" | "error";
    } | null>(null);

    const showToast = (message: string, type: "success" | "error") => {
        setToast({ message, type });
        setTimeout(() => setToast(null), 3500);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!memberId.trim() || !amount || !reason.trim()) return;
        setIsSubmitting(true);

        try {
            const res = await bonificationApi.adjustPoints(memberId, {
                amount: Number(amount),
                debit: isDebit,
                reason,
            });
            showToast(
                `${Number(amount)} points ${isDebit ? "débités" : "crédités"} (nouveau solde : ${res.availablePoints} pts).`,
                "success"
            );
            setMemberId("");
            setAmount("");
            setReason("");
            refetchLogs();
        } catch (err) {
            showToast(extractErrorMessage(err), "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="space-y-6">
            {/* Toast */}
            {toast && (
                <div
                    className={`fixed top-4 right-4 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg border text-sm transition-all ${toast.type === "success"
                            ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                            : "bg-rose-50 border-rose-200 text-rose-800"
                        }`}
                >
                    <Info className="w-4 h-4" />
                    <span>{toast.message}</span>
                </div>
            )}

            {/* Header */}
            <div className="space-y-1">
                <h1 className="text-3xl font-semibold tracking-tight">Bonification</h1>
                <p className="text-muted-foreground text-sm">
                    Créditez ou débitez manuellement des points de fidélité pour un membre.
                </p>
            </div>

            {/* ── Identifiants de l'API partenaire ─────────────────────────────── */}
            <BonificationCredentialsCard />

            <div className="space-y-6">
                {/* ── Formulaire ajustement ────────────────────────────────────────── */}
                <div className="border border-border bg-card rounded-xl shadow-sm overflow-hidden">
                    <div className="bg-secondary px-6 py-4 border-b border-border flex items-center gap-2.5">
                        <Send className="w-5 h-5 text-primary" />
                        <h3 className="font-semibold text-foreground">
                            Ajuster les points d&apos;un membre
                        </h3>
                    </div>

                    <form onSubmit={handleSubmit} className="p-6 space-y-5">
                        {/* Type d'opération */}
                        <div className="space-y-2">
                            <label className="text-xs font-semibold uppercase text-muted-foreground tracking-wider ml-1">
                                Type d&apos;opération *
                            </label>
                            <div className="flex gap-3">
                                <button
                                    type="button"
                                    onClick={() => setIsDebit(false)}
                                    className={`flex-1 flex items-center justify-center gap-2 py-3 rounded-lg border text-sm font-medium transition-all ${!isDebit
                                            ? "bg-primary text-primary-foreground border-primary shadow-sm"
                                            : "bg-background text-muted-foreground border-border hover:bg-secondary"
                                        }`}
                                >
                                    <ArrowUpCircle className="w-4 h-4" />
                                    Crédit
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setIsDebit(true)}
                                    className={`flex-1 flex items-center justify-center gap-2 py-3 rounded-lg border text-sm font-medium transition-all ${isDebit
                                            ? "bg-destructive text-white border-destructive shadow-sm"
                                            : "bg-background text-muted-foreground border-border hover:bg-secondary"
                                        }`}
                                >
                                    <ArrowDownCircle className="w-4 h-4" />
                                    Débit
                                </button>
                            </div>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {/* Membre */}
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold uppercase text-muted-foreground tracking-wider ml-1">
                                    ID Membre *
                                </label>
                                <input
                                    type="text"
                                    required
                                    value={memberId}
                                    onChange={(e) => setMemberId(e.target.value)}
                                    placeholder="UUID du membre"
                                    className="w-full bg-background border border-border rounded-lg px-4 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary shadow-sm"
                                />
                            </div>

                            {/* Montant */}
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold uppercase text-muted-foreground tracking-wider ml-1">
                                    Points *
                                </label>
                                <input
                                    type="number"
                                    min="1"
                                    step="1"
                                    required
                                    value={amount}
                                    onChange={(e) => setAmount(e.target.value)}
                                    placeholder="Ex: 100"
                                    className="w-full bg-background border border-border rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary shadow-sm"
                                />
                            </div>
                        </div>

                        {/* Motif */}
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold uppercase text-muted-foreground tracking-wider ml-1">
                                Motif *
                            </label>
                            <input
                                type="text"
                                required
                                value={reason}
                                onChange={(e) => setReason(e.target.value)}
                                placeholder="Ex: Geste commercial, correction de solde…"
                                className="w-full bg-background border border-border rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary shadow-sm"
                            />
                        </div>

                        <div className="flex justify-end pt-2">
                            <button
                                type="submit"
                                disabled={isSubmitting || !memberId.trim() || !amount || !reason.trim()}
                                className="inline-flex items-center gap-2 bg-primary text-primary-foreground px-6 py-2.5 rounded-lg text-sm font-medium hover:bg-primary/90 transition-all shadow-md active:scale-95 disabled:opacity-50 disabled:pointer-events-none"
                            >
                                {isSubmitting ? (
                                    <RefreshCw className="w-4 h-4 animate-spin" />
                                ) : (
                                    <Send className="w-4 h-4" />
                                )}
                                {isSubmitting ? "Envoi…" : "Appliquer"}
                            </button>
                        </div>
                    </form>
                </div>

                {/* ── Historique persistant ────────────────────────────────────────── */}
                <div className="border border-border bg-card rounded-xl shadow-sm overflow-hidden">
                    <div className="bg-secondary px-6 py-4 border-b border-border flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                            <History className="w-4 h-4 text-primary" />
                            <h3 className="font-semibold text-sm text-foreground">
                                Ajustements récents
                            </h3>
                        </div>
                        <button
                            onClick={() => refetchLogs()}
                            className="text-muted-foreground hover:text-foreground transition-colors"
                            title="Rafraîchir"
                        >
                            <RefreshCw className={`w-3.5 h-3.5 ${logsLoading ? "animate-spin" : ""}`} />
                        </button>
                    </div>

                    {logsLoading && (
                        <div className="p-6 space-y-3">
                            {[1, 2, 3].map((i) => (
                                <div key={i} className="h-10 bg-muted rounded-lg animate-pulse" />
                            ))}
                        </div>
                    )}

                    {logsError && (
                        <div className="p-6 flex items-start gap-2.5 text-xs text-destructive">
                            <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                            <span>Impossible de charger l&apos;historique : {logsError}</span>
                        </div>
                    )}

                    {!logsLoading && !logsError && adjustments.length === 0 && (
                        <div className="p-8 text-center text-muted-foreground text-sm italic">
                            Aucun ajustement enregistré
                        </div>
                    )}

                    {!logsLoading && !logsError && adjustments.length > 0 && (
                        <div className="divide-y divide-border">
                            {adjustments.map((tx) => {
                                const isDebitTx = tx.type === "DEBIT";
                                return (
                                    <div
                                        key={tx.id}
                                        className="px-5 py-3 flex items-center justify-between hover:bg-muted/10 transition-colors"
                                    >
                                        <div className="flex items-center gap-2.5">
                                            <div
                                                className={`w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0 ${isDebitTx
                                                        ? "bg-rose-50 text-destructive"
                                                        : "bg-emerald-50 text-emerald-600"
                                                    }`}
                                            >
                                                {isDebitTx ? (
                                                    <ArrowDownCircle className="w-4 h-4" />
                                                ) : (
                                                    <ArrowUpCircle className="w-4 h-4" />
                                                )}
                                            </div>
                                            <div>
                                                <p className="text-xs font-medium text-foreground font-mono">
                                                    {tx.memberId ? `${tx.memberId.substring(0, 8)}…` : "—"}
                                                </p>
                                                <p className="text-xs text-muted-foreground">
                                                    {tx.reason ?? "—"} · {formatDate(tx.createdAt)}
                                                </p>
                                            </div>
                                        </div>
                                        <div className="text-right">
                                            <p
                                                className={`text-xs font-bold font-mono ${isDebitTx ? "text-destructive" : "text-emerald-600"
                                                    }`}
                                            >
                                                {isDebitTx ? "-" : "+"}
                                                {tx.amount}
                                            </p>
                                            <p className="text-xs text-muted-foreground">
                                                solde : {tx.balanceAfter}
                                            </p>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
