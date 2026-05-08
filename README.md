# intelli-doc-backend

## Key Vault Configuration

The backend resolves secrets from Azure Key Vault by default using the Container App managed identity.

Expected Key Vault secret names:
- `datasource-url`
- `datasource-username`
- `datasource-password`
- `entra-issuer-uri`
- `entra-jwk-set-uri`
- `openai-endpoint`
- `openai-api-key`

For local development, you can create `.env.properties` in the repository root from [.env.properties.example](/D:/projects/intellidoc/.env.properties.example:1). The application imports that file automatically and also supports standard environment variables like `DATASOURCE_URL` and `OPENAI_API_KEY`.

By default:
- `INTELLIDOC_KEY_VAULT_ENABLED=true`
- `INTELLIDOC_KEY_VAULT_ENDPOINT=https://kv-intellidoc.vault.azure.net/`

Set `INTELLIDOC_KEY_VAULT_ENABLED=false` for local runs if you want to rely entirely on `.env.properties` or environment variables.

## Application Insights

The backend uses the Azure Monitor Application Insights Java agent in container/runtime environments.

Based on Microsoft Learn, the current Spring Boot guidance is to enable Application Insights Java with the JVM `-javaagent` option and configure it through `applicationinsights.json` and environment variables. In this repository:
- the agent is baked into the container image
- `applicationinsights.json` is copied to `/opt/applicationinsights/applicationinsights.json`
- the container entrypoint only enables the agent when `APPLICATIONINSIGHTS_ENABLED=true` and `APPLICATIONINSIGHTS_CONNECTION_STRING` is present

Important environment variables:
- `APPLICATIONINSIGHTS_CONNECTION_STRING`
- `APPLICATIONINSIGHTS_ENABLED`
- `APPLICATIONINSIGHTS_ROLE_NAME`
- `APPLICATIONINSIGHTS_ROLE_INSTANCE`
- `INTELLIDOC_ENVIRONMENT`

The application also emits request correlation and request-completion logs so backend logs and API error payloads include a stable `traceId` even when Application Insights export is not active yet.
