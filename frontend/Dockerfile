# syntax=docker/dockerfile:1
FROM node:24.15.0-alpine AS dependencies
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

FROM dependencies AS test
COPY frontend/ ./
RUN npm test

FROM dependencies AS build
COPY frontend/ ./
COPY samples/inventory-valid.xlsx ./public/samples/inventory-valid.xlsx
RUN npm run build

FROM nginx:1.28.2-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/inventory/browser /usr/share/nginx/html
EXPOSE 80
