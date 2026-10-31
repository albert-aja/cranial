# =============================================================================
# Stage 1: Build CRANIAL Admin UI (React + TypeScript)
# =============================================================================
FROM node:22-alpine AS builder
WORKDIR /workspace

COPY package.json package-lock.json* ./
RUN npm install

COPY . .
RUN npm run build

# =============================================================================
# Stage 2: Unprivileged Nginx Runtime
# =============================================================================
FROM nginxinc/nginx-unprivileged:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/dist /usr/share/nginx/html

EXPOSE 8080

