package com.intellidoc.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "intellidoc")
public class IntelliDocProperties {

    @NestedConfigurationProperty
    private final Security security = new Security();

    @NestedConfigurationProperty
    private final KeyVault keyVault = new KeyVault();

    @NestedConfigurationProperty
    private final Storage storage = new Storage();

    @NestedConfigurationProperty
    private final Search search = new Search();

    @NestedConfigurationProperty
    private final Ai ai = new Ai();

    @NestedConfigurationProperty
    private final Ingestion ingestion = new Ingestion();

    @NestedConfigurationProperty
    private final Chat chat = new Chat();

    @NestedConfigurationProperty
    private final Upload upload = new Upload();

    @NestedConfigurationProperty
    private final Observability observability = new Observability();

    public Security getSecurity() {
        return security;
    }

    public Storage getStorage() {
        return storage;
    }

    public KeyVault getKeyVault() {
        return keyVault;
    }

    public Search getSearch() {
        return search;
    }

    public Ai getAi() {
        return ai;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Chat getChat() {
        return chat;
    }

    public Upload getUpload() {
        return upload;
    }

    public Observability getObservability() {
        return observability;
    }

    public static class Security {

        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));
        private String apiAudience;
        private String principalClaimName = "preferred_username";

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
        }

        public String getApiAudience() {
            return apiAudience;
        }

        public void setApiAudience(String apiAudience) {
            this.apiAudience = apiAudience;
        }

        public String getPrincipalClaimName() {
            return principalClaimName;
        }

        public void setPrincipalClaimName(String principalClaimName) {
            this.principalClaimName = principalClaimName;
        }
    }

    public static class KeyVault {

        private boolean enabled = true;
        private String endpoint = "https://kv-intellidoc.vault.azure.net/";
        private String managedIdentityClientId;
        private String tenantId;
        private List<String> requiredSecrets = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getManagedIdentityClientId() {
            return managedIdentityClientId;
        }

        public void setManagedIdentityClientId(String managedIdentityClientId) {
            this.managedIdentityClientId = managedIdentityClientId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public List<String> getRequiredSecrets() {
            return requiredSecrets;
        }

        public void setRequiredSecrets(List<String> requiredSecrets) {
            this.requiredSecrets = requiredSecrets == null ? new ArrayList<>() : new ArrayList<>(requiredSecrets);
        }
    }

    public static class Storage {

        private String accountName = "stintellidoc";
        private String containerName = "documents";

        public String getAccountName() {
            return accountName;
        }

        public void setAccountName(String accountName) {
            this.accountName = accountName;
        }

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }
    }

    public static class Search {

        private String indexName = "intellidoc-chunks-index";
        private String semanticConfiguration = "intellidoc-semantic-config";

        @Min(1)
        private int topK = 8;

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getSemanticConfiguration() {
            return semanticConfiguration;
        }

        public void setSemanticConfiguration(String semanticConfiguration) {
            this.semanticConfiguration = semanticConfiguration;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }

    public static class Ai {

        private String chatDeployment = "gpt-4o";
        private String embeddingDeployment = "text-embedding-3-large";

        public String getChatDeployment() {
            return chatDeployment;
        }

        public void setChatDeployment(String chatDeployment) {
            this.chatDeployment = chatDeployment;
        }

        public String getEmbeddingDeployment() {
            return embeddingDeployment;
        }

        public void setEmbeddingDeployment(String embeddingDeployment) {
            this.embeddingDeployment = embeddingDeployment;
        }
    }

    public static class Ingestion {

        private String queueName = "ingestion-queue";

        public String getQueueName() {
            return queueName;
        }

        public void setQueueName(String queueName) {
            this.queueName = queueName;
        }
    }

    public static class Chat {

        private Duration sessionTtl = Duration.ofMinutes(30);

        @Min(1)
        private int conversationWindowSize = 5;

        public Duration getSessionTtl() {
            return sessionTtl;
        }

        public void setSessionTtl(Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
        }

        public int getConversationWindowSize() {
            return conversationWindowSize;
        }

        public void setConversationWindowSize(int conversationWindowSize) {
            this.conversationWindowSize = conversationWindowSize;
        }
    }

    public static class Upload {

        private DataSize maxFileSize = DataSize.ofMegabytes(50);
        private DataSize maxRequestSize = DataSize.ofMegabytes(50);

        public DataSize getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(DataSize maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public DataSize getMaxRequestSize() {
            return maxRequestSize;
        }

        public void setMaxRequestSize(DataSize maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
        }
    }

    public static class Observability {

        @NestedConfigurationProperty
        private final ApplicationInsights applicationInsights = new ApplicationInsights();

        private String environment = "local";

        public ApplicationInsights getApplicationInsights() {
            return applicationInsights;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public static class ApplicationInsights {

            private boolean enabled = true;
            private String roleName = "intellidoc-backend";
            private String roleInstance;
            private String connectionString;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getRoleName() {
                return roleName;
            }

            public void setRoleName(String roleName) {
                this.roleName = roleName;
            }

            public String getRoleInstance() {
                return roleInstance;
            }

            public void setRoleInstance(String roleInstance) {
                this.roleInstance = roleInstance;
            }

            public String getConnectionString() {
                return connectionString;
            }

            public void setConnectionString(String connectionString) {
                this.connectionString = connectionString;
            }
        }
    }
}
