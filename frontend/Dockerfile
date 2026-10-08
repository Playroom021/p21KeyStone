# ---- Frontend image (Vite build served by nginx) ----
# nginx serves the SPA and proxies /api, /v3 and /swagger-ui to the backend service,
# so the browser only ever talks to one origin (no CORS, no VITE_API_BASE_URL needed).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
