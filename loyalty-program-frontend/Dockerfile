# ── deps ──────────────────────────────────────────────────────
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ── build ─────────────────────────────────────────────────────
FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Next.js resolves next.config.ts `rewrites()` at BUILD time and freezes the
# destination into .next/routes-manifest.json + the standalone config. Setting
# NEXT_PUBLIC_API_URL only at runtime (docker-compose) has NO effect: the proxy
# would stay pinned on the http://localhost:8081 fallback and every /backend/*
# call would answer 500. It must therefore be present here, at build time.
# Alternative destination when the frontend runs on the same docker network as
# the backend: http://loyalty-program:8081 (no Traefik hop, no /loyalty-api).
ARG NEXT_PUBLIC_API_URL=https://loyalty.yowyob.com/loyalty-api
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN npm run build

# ── run ───────────────────────────────────────────────────────
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0

RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000
ENV PORT=3000

CMD ["node", "server.js"]
