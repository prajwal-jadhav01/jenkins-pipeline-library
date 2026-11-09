# Shared template for ASP.NET Core APIs. Used when an app doesn't ship its own Dockerfile.
# Build args: PROJECT = path to the API's .csproj (default: the only .csproj under src/)
ARG DOTNET_VERSION=10.0

FROM mcr.microsoft.com/dotnet/sdk:${DOTNET_VERSION} AS build
ARG PROJECT=""
WORKDIR /src
COPY . .
RUN set -e; \
    proj="${PROJECT:-$(find src -name '*.csproj' | head -n 1)}"; \
    echo "Publishing $proj"; \
    dotnet publish "$proj" -c Release -o /app -p:AssemblyName=app

FROM mcr.microsoft.com/dotnet/aspnet:${DOTNET_VERSION}
WORKDIR /app
COPY --from=build /app .
USER app
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
ENTRYPOINT ["dotnet", "app.dll"]
