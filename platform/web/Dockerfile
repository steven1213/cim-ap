# 构建阶段
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json ./
RUN pnpm install
COPY . .
RUN pnpm build

# 运行阶段
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
