# Shared template for single-page web apps (React, Angular, Vue): build with Node, serve with nginx.
# Build args: BUILD_DIR = folder the build writes to (default: dist)
ARG NODE_VERSION=22

FROM node:${NODE_VERSION}-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ARG BUILD_DIR=dist
COPY --from=build /src/${BUILD_DIR} /usr/share/nginx/html
# Send unknown paths to index.html so client-side routes work on refresh.
RUN printf 'server {\n  listen 80;\n  root /usr/share/nginx/html;\n  location / { try_files $uri /index.html; }\n}\n' \
    > /etc/nginx/conf.d/default.conf
EXPOSE 80
