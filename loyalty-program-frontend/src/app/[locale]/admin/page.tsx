"use client";

import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import {
  Building2,
  Coins,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  Sparkles,
  Search,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Download,
  Copy,
  Check,
  Inbox,
  X,
} from "lucide-react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from "recharts";
import { usePlatformTenants } from "@/hooks/useBackend";
import type { PlatformTenantResponse } from "@/lib/api";

// Le statut porte une couleur *sémantique* (état), toujours accompagnée d'une
// pastille + d'un libellé traduit : jamais la couleur seule comme canal d'information.
const STATUS_STYLES: Record<string, { badge: string; dot: string }> = {
  ACTIVE: { badge: "bg-emerald-50 border-emerald-200 text-emerald-700", dot: "bg-emerald-500" },
  TRIAL: { badge: "bg-blue-50 border-blue-200 text-blue-700", dot: "bg-blue-500" },
  PAST_DUE: { badge: "bg-amber-50 border-amber-200 text-amber-700", dot: "bg-amber-500" },
  CANCELLED: { badge: "bg-muted border-border text-muted-foreground", dot: "bg-muted-foreground/40" },
  EXPIRED: { badge: "bg-muted border-border text-muted-foreground", dot: "bg-muted-foreground/40" },
};

const FALLBACK_STYLE = { badge: "bg-muted border-border text-muted-foreground", dot: "bg-muted-foreground/40" };

type SortKey =
  | "tenantName"
  | "planName"
  | "subscriptionStatus"
  | "currentPeriodEnd"
  | "totalPointsGenerated"
  | "totalPaidAmount";

interface SortState {
  key: SortKey;
  dir: "asc" | "desc";
}

function ChartTooltip({
  active,
  payload,
  format,
  unitLabel,
}: {
  active?: boolean;
  payload?: { payload: { name: string; points: number } }[];
  format: (value: number) => string;
  unitLabel: string;
}) {
  if (!active || !payload?.length) return null;
  const { name, points } = payload[0].payload;
  return (
    <div className="bg-card border border-border rounded-lg shadow-md px-3 py-2 text-xs">
      <p className="font-semibold text-foreground mb-0.5">{name}</p>
      <p className="text-muted-foreground">
        <span className="font-semibold text-primary tabular-nums">{format(points)}</span> {unitLabel}
      </p>
    </div>
  );
}

function StatTile({
  icon: Icon,
  label,
  value,
  hint,
  tone = "primary",
}: {
  icon: typeof Building2;
  label: string;
  value: string;
  hint?: string;
  tone?: "primary" | "success";
}) {
  return (
    <div className="bg-card border border-border rounded-2xl p-5 shadow-sm hover:border-primary/30 transition-colors">
      <div className="flex items-center gap-2.5 mb-4">
        <div className="p-2 bg-secondary rounded-lg">
          <Icon className={`w-4 h-4 ${tone === "success" ? "text-emerald-600" : "text-primary"}`} />
        </div>
        <p className="text-[11px] font-semibold text-muted-foreground uppercase tracking-widest leading-tight">
          {label}
        </p>
      </div>
      <p className="text-3xl font-semibold tracking-tight text-foreground">{value}</p>
      {hint && <p className="text-xs text-muted-foreground mt-1.5">{hint}</p>}
    </div>
  );
}

function SortHeader({
  label,
  sortKey,
  sort,
  onSort,
  align = "left",
}: {
  label: string;
  sortKey: SortKey;
  sort: SortState;
  onSort: (key: SortKey) => void;
  align?: "left" | "right";
}) {
  const active = sort.key === sortKey;
  const Icon = !active ? ArrowUpDown : sort.dir === "asc" ? ArrowUp : ArrowDown;
  return (
    <th
      scope="col"
      aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}
      className={`px-6 py-3.5 font-semibold tracking-wider whitespace-nowrap ${align === "right" ? "text-right" : "text-left"}`}
    >
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className={`inline-flex items-center gap-1.5 uppercase hover:text-foreground transition-colors ${active ? "text-foreground" : ""
          } ${align === "right" ? "flex-row-reverse" : ""}`}
      >
        {label}
        <Icon className={`w-3 h-3 ${active ? "text-primary" : "text-muted-foreground/40"}`} />
      </button>
    </th>
  );
}

function toCsv(rows: PlatformTenantResponse[], header: string[], separator: string): string {
  const escape = (value: string | number | null) => {
    const s = value === null || value === undefined ? "" : String(value);
    return s.includes(separator) || /["\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = rows.map((t) =>
    [
      t.tenantName,
      t.tenantId,
      t.planName,
      t.planCode,
      t.subscriptionStatus,
      t.trialEndDate,
      t.currentPeriodEnd,
      t.totalPointsGenerated,
      t.totalPaidAmount,
      t.currency,
    ]
      .map(escape)
      .join(separator)
  );
  // BOM en tête : Excel reconnaît l'UTF-8 et ouvre le fichier sans étape d'import.
  return `\uFEFF${[header.join(separator), ...lines].join("\n")}`;
}

export default function PlatformOrganisationsPage() {
  const t = useTranslations("PlatformAdmin");
  const locale = useLocale();
  const { data: tenants, isLoading, error, refetch } = usePlatformTenants();

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [sort, setSort] = useState<SortState>({ key: "totalPointsGenerated", dir: "desc" });
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const { nf, compactNf, formatDate } = useMemo(() => {
    const numberFormat = new Intl.NumberFormat(locale);
    const compactFormat = new Intl.NumberFormat(locale, { notation: "compact", maximumFractionDigits: 1 });
    const dateFormat = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "short", year: "numeric" });
    return {
      nf: (value: number) => numberFormat.format(value),
      compactNf: (value: number) => compactFormat.format(value),
      formatDate: (iso: string | null) => (iso ? dateFormat.format(new Date(iso)) : "—"),
    };
  }, [locale]);

  // Un statut inconnu du backend s'affiche tel quel plutôt que de casser le rendu.
  const statusLabel = (status: string) => (t.has(`status.${status}`) ? t(`status.${status}`) : status);
  const statusStyle = (status: string) => STATUS_STYLES[status] ?? FALLBACK_STYLE;

  // `useQuery` conserve les données précédentes pendant un refetch : on distingue
  // le premier chargement (squelette) du rafraîchissement (contenu en retrait).
  const isFirstLoad = isLoading && !tenants;
  const isRefreshing = isLoading && !!tenants;

  const statusCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const tenant of tenants ?? [])
      counts.set(tenant.subscriptionStatus, (counts.get(tenant.subscriptionStatus) ?? 0) + 1);
    return [...counts.entries()].sort((a, b) => b[1] - a[1]);
  }, [tenants]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (tenants ?? []).filter((tenant) => {
      if (statusFilter !== "ALL" && tenant.subscriptionStatus !== statusFilter) return false;
      if (!q) return true;
      return (
        tenant.tenantName.toLowerCase().includes(q) ||
        tenant.tenantId.toLowerCase().includes(q) ||
        tenant.planName.toLowerCase().includes(q) ||
        tenant.planCode.toLowerCase().includes(q)
      );
    });
  }, [tenants, search, statusFilter]);

  const sorted = useMemo(() => {
    const rows = [...filtered];
    const { key, dir } = sort;
    rows.sort((a, b) => {
      const av = a[key];
      const bv = b[key];
      // Les dates absentes restent en fin de liste quel que soit le sens du tri.
      if (av === null) return bv === null ? 0 : 1;
      if (bv === null) return -1;
      const cmp =
        typeof av === "number" && typeof bv === "number" ? av - bv : String(av).localeCompare(String(bv), locale);
      return dir === "asc" ? cmp : -cmp;
    });
    return rows;
  }, [filtered, sort, locale]);

  // Les statistiques et le graphique portent sur la sélection courante : un seul
  // jeu de filtres au-dessus, tout le reste s'y recale.
  const totalsByCurrency = filtered.reduce<Record<string, number>>((acc, tenant) => {
    acc[tenant.currency] = (acc[tenant.currency] ?? 0) + tenant.totalPaidAmount;
    return acc;
  }, {});
  const currencyEntries = Object.entries(totalsByCurrency).sort((a, b) => b[1] - a[1]);
  const activeCount = filtered.filter((tenant) => tenant.subscriptionStatus === "ACTIVE").length;
  const totalPointsGenerated = filtered.reduce((sum, tenant) => sum + tenant.totalPointsGenerated, 0);

  const chartData = useMemo(
    () =>
      filtered
        .filter((tenant) => tenant.totalPointsGenerated > 0)
        .map((tenant) => ({ name: tenant.tenantName, points: tenant.totalPointsGenerated }))
        .sort((a, b) => b.points - a.points)
        .slice(0, 10),
    [filtered]
  );

  const isFiltered = search.trim() !== "" || statusFilter !== "ALL";

  const toggleSort = (key: SortKey) =>
    setSort((prev) =>
      prev.key === key
        ? { key, dir: prev.dir === "asc" ? "desc" : "asc" }
        : { key, dir: key === "tenantName" || key === "planName" ? "asc" : "desc" }
    );

  const clearFilters = () => {
    setSearch("");
    setStatusFilter("ALL");
  };

  const handleCopyId = async (tenantId: string) => {
    await navigator.clipboard.writeText(tenantId);
    setCopiedId(tenantId);
    setTimeout(() => setCopiedId((current) => (current === tenantId ? null : current)), 1500);
  };

  const handleExport = () => {
    const header = [
      t("csv.organisation"),
      t("csv.tenantId"),
      t("csv.plan"),
      t("csv.planCode"),
      t("csv.status"),
      t("csv.trialEnd"),
      t("csv.periodEnd"),
      t("csv.points"),
      t("csv.paid"),
      t("csv.currency"),
    ];
    // Excel choisit le séparateur selon la locale du système : `;` en français,
    // `,` dans les locales anglaises.
    const separator = locale === "fr" ? ";" : ",";
    const blob = new Blob([toCsv(sorted, header, separator)], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${t("csv.filename")}-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6">
      {/* En-tête */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">{t("title")}</h1>
          <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
        </div>
        <div className="flex items-center gap-2 self-start md:self-auto">
          <button
            onClick={handleExport}
            disabled={sorted.length === 0}
            className="inline-flex items-center gap-2 text-xs font-medium text-muted-foreground hover:text-foreground border border-border px-3 py-2 rounded-lg hover:bg-secondary transition-colors disabled:opacity-40 disabled:pointer-events-none"
          >
            <Download className="w-3.5 h-3.5" /> {t("actions.export")}
          </button>
          <button
            onClick={refetch}
            disabled={isLoading}
            className="inline-flex items-center gap-2 text-xs font-medium text-muted-foreground hover:text-foreground border border-border px-3 py-2 rounded-lg hover:bg-secondary transition-colors disabled:opacity-60"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? "animate-spin" : ""}`} /> {t("actions.refresh")}
          </button>
        </div>
      </div>

      {error && (
        <div className="border border-destructive/30 bg-destructive/5 rounded-xl p-4 flex items-start gap-3">
          <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0 text-destructive" />
          <div className="flex-1 text-sm">
            <p className="font-semibold text-destructive">{t("error.title")}</p>
            <p className="text-xs mt-0.5 text-destructive/80">{error}</p>
          </div>
          <button
            onClick={refetch}
            className="text-xs font-medium text-destructive border border-destructive/30 px-2.5 py-1.5 rounded-md hover:bg-destructive/10 transition-colors"
          >
            {t("actions.retry")}
          </button>
        </div>
      )}

      {isFirstLoad ? (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="h-32 bg-card border border-border rounded-2xl animate-pulse" />
            ))}
          </div>
          <div className="h-80 bg-card border border-border rounded-xl animate-pulse" />
        </div>
      ) : (
        <div className={`space-y-6 ${isRefreshing ? "opacity-60" : ""} transition-opacity`}>
          {/* Statistiques (portée : sélection courante) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatTile
              icon={Building2}
              label={t("stats.organisations")}
              value={nf(filtered.length)}
              hint={
                isFiltered && tenants
                  ? t("stats.organisationsHintFiltered", { total: nf(tenants.length) })
                  : t("stats.organisationsHint")
              }
            />
            <StatTile
              icon={CheckCircle2}
              label={t("stats.active")}
              tone="success"
              value={nf(activeCount)}
              hint={
                filtered.length > 0
                  ? t("stats.activeHint", { percent: Math.round((activeCount / filtered.length) * 100) })
                  : t("stats.activeHintEmpty")
              }
            />
            <StatTile
              icon={Sparkles}
              label={t("stats.points")}
              value={compactNf(totalPointsGenerated)}
              hint={
                totalPointsGenerated > 0
                  ? t("stats.pointsHint", { count: nf(totalPointsGenerated) })
                  : t("stats.pointsHintEmpty")
              }
            />
            <StatTile
              icon={Coins}
              label={t("stats.revenue")}
              value={
                currencyEntries.length === 0 ? "—" : `${compactNf(currencyEntries[0][1])} ${currencyEntries[0][0]}`
              }
              hint={
                currencyEntries.length > 1
                  ? currencyEntries
                    .slice(1)
                    .map(([currency, total]) => `${compactNf(total)} ${currency}`)
                    .join(" · ")
                  : t("stats.revenueHint")
              }
            />
          </div>

          {/* Filtres : une seule barre, qui cadre le graphique et le tableau */}
          <div className="bg-card border border-border rounded-xl p-3 flex flex-col lg:flex-row lg:items-center gap-3">
            <div className="relative flex-1 min-w-0">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground/60 pointer-events-none" />
              <input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t("filters.searchPlaceholder")}
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground placeholder:text-muted-foreground/70 focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary transition-shadow"
              />
            </div>
            <div className="flex items-center gap-1.5 flex-wrap">
              <button
                onClick={() => setStatusFilter("ALL")}
                className={`px-2.5 py-1.5 text-xs font-medium rounded-lg border transition-colors ${statusFilter === "ALL"
                  ? "bg-primary text-primary-foreground border-primary"
                  : "border-border text-muted-foreground hover:bg-secondary hover:text-foreground"
                  }`}
              >
                {t("filters.all")} {tenants ? `(${tenants.length})` : ""}
              </button>
              {statusCounts.map(([status, count]) => {
                const selected = statusFilter === status;
                return (
                  <button
                    key={status}
                    onClick={() => setStatusFilter(status)}
                    className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-lg border transition-colors ${selected
                      ? "bg-primary text-primary-foreground border-primary"
                      : "border-border text-muted-foreground hover:bg-secondary hover:text-foreground"
                      }`}
                  >
                    <span
                      className={`w-1.5 h-1.5 rounded-full ${selected ? "bg-primary-foreground" : statusStyle(status).dot}`}
                    />
                    {statusLabel(status)} ({count})
                  </button>
                );
              })}
              {isFiltered && (
                <button
                  onClick={clearFilters}
                  className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium text-muted-foreground hover:text-foreground rounded-lg transition-colors"
                >
                  <X className="w-3 h-3" /> {t("filters.reset")}
                </button>
              )}
            </div>
          </div>

          {/* Points générés par organisation — une seule série, une seule teinte */}
          {chartData.length >= 2 && (
            <div className="border border-border bg-card rounded-xl shadow-sm overflow-hidden">
              <div className="px-6 py-4 border-b border-border flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="font-semibold text-foreground text-sm">
                  {chartData.length === 10 ? t("chart.titleTop") : t("chart.title")}
                </h2>
                <p className="text-xs text-muted-foreground">
                  {t("chart.leader")}{" "}
                  <span className="font-medium text-foreground">{chartData[0].name}</span>{" "}
                  <span className="tabular-nums">{nf(chartData[0].points)}</span>
                </p>
              </div>
              <div className="p-6" style={{ height: chartData.length * 38 + 76 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={chartData}
                    layout="vertical"
                    margin={{ top: 0, right: 12, left: 0, bottom: 0 }}
                    barCategoryGap="28%"
                  >
                    <CartesianGrid stroke="var(--border)" horizontal={false} />
                    <XAxis
                      type="number"
                      tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
                      axisLine={false}
                      tickLine={false}
                      tickFormatter={(value: number) => compactNf(value)}
                    />
                    <YAxis
                      type="category"
                      dataKey="name"
                      width={196}
                      tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
                      axisLine={false}
                      tickLine={false}
                      // Nom tronqué pour tenir sur une ligne ; le nom complet reste dans
                      // l'infobulle et dans le tableau.
                      tickFormatter={(value: string) => (value.length > 24 ? `${value.slice(0, 23)}…` : value)}
                    />
                    <Tooltip
                      content={<ChartTooltip format={nf} unitLabel={t("chart.points")} />}
                      cursor={{ fill: "var(--secondary)" }}
                    />
                    <Bar dataKey="points" fill="var(--primary)" radius={[0, 4, 4, 0]} maxBarSize={20} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* Tableau */}
          <div className="border border-border bg-card rounded-xl shadow-sm overflow-hidden">
            {!tenants || tenants.length === 0 ? (
              <div className="p-12 flex flex-col items-center text-center gap-2">
                <div className="p-3 bg-secondary rounded-xl mb-1">
                  <Inbox className="w-5 h-5 text-muted-foreground" />
                </div>
                <p className="font-medium text-foreground text-sm">{t("empty.title")}</p>
                <p className="text-xs text-muted-foreground max-w-sm">{t("empty.description")}</p>
              </div>
            ) : sorted.length === 0 ? (
              <div className="p-12 flex flex-col items-center text-center gap-2">
                <div className="p-3 bg-secondary rounded-xl mb-1">
                  <Search className="w-5 h-5 text-muted-foreground" />
                </div>
                <p className="font-medium text-foreground text-sm">{t("noResults.title")}</p>
                <p className="text-xs text-muted-foreground">{t("noResults.description")}</p>
                <button onClick={clearFilters} className="mt-2 text-xs font-medium text-primary hover:underline">
                  {t("noResults.reset")}
                </button>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm text-left">
                    <thead className="text-xs text-muted-foreground bg-muted/30 border-b border-border">
                      <tr>
                        <SortHeader
                          label={t("table.organisation")}
                          sortKey="tenantName"
                          sort={sort}
                          onSort={toggleSort}
                        />
                        <SortHeader label={t("table.plan")} sortKey="planName" sort={sort} onSort={toggleSort} />
                        <SortHeader
                          label={t("table.status")}
                          sortKey="subscriptionStatus"
                          sort={sort}
                          onSort={toggleSort}
                        />
                        <SortHeader
                          label={t("table.periodEnd")}
                          sortKey="currentPeriodEnd"
                          sort={sort}
                          onSort={toggleSort}
                        />
                        <SortHeader
                          label={t("table.points")}
                          sortKey="totalPointsGenerated"
                          sort={sort}
                          onSort={toggleSort}
                          align="right"
                        />
                        <SortHeader
                          label={t("table.paid")}
                          sortKey="totalPaidAmount"
                          sort={sort}
                          onSort={toggleSort}
                          align="right"
                        />
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {sorted.map((tenant) => {
                        const style = statusStyle(tenant.subscriptionStatus);
                        const isTrial = tenant.subscriptionStatus === "TRIAL" && tenant.trialEndDate;
                        return (
                          <tr key={tenant.tenantId} className="hover:bg-muted/40 transition-colors">
                            <td className="px-6 py-3.5 whitespace-nowrap">
                              <p className="font-medium text-foreground">{tenant.tenantName}</p>
                              <button
                                type="button"
                                onClick={() => handleCopyId(tenant.tenantId)}
                                title={t("table.copyId")}
                                className="group inline-flex items-center gap-1.5 text-xs font-mono text-muted-foreground hover:text-foreground transition-colors"
                              >
                                {tenant.tenantId}
                                {copiedId === tenant.tenantId ? (
                                  <Check className="w-3 h-3 text-emerald-600" />
                                ) : (
                                  <Copy className="w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                                )}
                              </button>
                            </td>
                            <td className="px-6 py-3.5 whitespace-nowrap">
                              <p className="text-foreground">{tenant.planName}</p>
                              <p className="text-xs font-mono text-muted-foreground">{tenant.planCode}</p>
                            </td>
                            <td className="px-6 py-3.5 whitespace-nowrap">
                              <span
                                className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md text-xs font-medium border ${style.badge}`}
                              >
                                <span className={`w-1.5 h-1.5 rounded-full ${style.dot}`} />
                                {statusLabel(tenant.subscriptionStatus)}
                              </span>
                            </td>
                            <td className="px-6 py-3.5 text-muted-foreground text-xs tabular-nums whitespace-nowrap">
                              {formatDate(tenant.currentPeriodEnd)}
                              {isTrial && (
                                <span className="block text-[11px] text-blue-600">
                                  {t("table.trialUntil", { date: formatDate(tenant.trialEndDate) })}
                                </span>
                              )}
                            </td>
                            <td className="px-6 py-3.5 font-medium text-right tabular-nums whitespace-nowrap">
                              {nf(tenant.totalPointsGenerated)}
                            </td>
                            <td className="px-6 py-3.5 font-medium text-right tabular-nums whitespace-nowrap">
                              {nf(tenant.totalPaidAmount)}{" "}
                              <span className="text-xs font-normal text-muted-foreground">{tenant.currency}</span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                <div className="px-6 py-3 border-t border-border bg-muted/20 text-xs text-muted-foreground">
                  {sorted.length === tenants.length
                    ? t("table.count", { count: sorted.length })
                    : t("table.countFiltered", { count: sorted.length, total: nf(tenants.length) })}
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
