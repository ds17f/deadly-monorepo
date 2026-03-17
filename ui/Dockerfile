FROM node:22-slim AS builder

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM busybox:stable AS export
COPY --from=builder /app/out /out
CMD ["true"]
