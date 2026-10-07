# Build:  docker build -t ggpower-api .
FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src
COPY GgPower.Backend.sln ./
COPY src/ src/
RUN dotnet publish src/GgPower.Api -c Release -o /app --nologo

FROM mcr.microsoft.com/dotnet/aspnet:9.0
WORKDIR /app
COPY --from=build /app ./
# Runs as the image's unprivileged user; uploads go to the mounted /data/storage.
USER $APP_UID
ENV ASPNETCORE_URLS=http://+:8080 DOTNET_gcServer=0
EXPOSE 8080
ENTRYPOINT ["dotnet", "GgPower.Api.dll"]
