package tech.ydb.spark.connector;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import tech.ydb.auth.iam.CloudAuthHelper;
import tech.ydb.auth.iam.CloudAuthIdentity;
import tech.ydb.core.auth.StaticCredentials;
import tech.ydb.core.grpc.GrpcTransport;
import tech.ydb.core.grpc.GrpcTransportBuilder;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.TableClient;
import tech.ydb.table.SchemeClient;
import tech.ydb.table.rpc.grpc.GrpcSchemeRpc;

/**
 *
 * @author zinal
 */
public class YdbConnector implements AutoCloseable {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(YdbConnector.class);

    public static final String YDB_URL = "url";
    public static final String YDB_POOL_SIZE = "pool.size";
    public static final String YDB_AUTH_MODE = "auth.mode";
    public static final String YDB_AUTH_LOGIN = "auth.login";
    public static final String YDB_AUTH_PASSWORD = "auth.password";
    public static final String YDB_AUTH_KEY_FILE = "auth.keyfile";
    public static final String YDB_AUTH_TOKEN = "auth.token";

    private final String catalogName;
    private final Map<String,String> connectOptions;
    private final GrpcTransport transport;
    private final TableClient tableClient;
    private final SchemeClient schemeClient;
    private final SessionRetryContext retryCtx;
    private final String database;

    public YdbConnector(String catalogName, Map<String, String> props) {
        this.catalogName = catalogName;
        this.connectOptions = new HashMap<>();
        for (Map.Entry<String,String> me : props.entrySet()) {
            this.connectOptions.put(me.getKey().toLowerCase(), me.getValue());
        }
        final int poolSize;
        try {
            poolSize = Integer.parseInt(props.getOrDefault(YDB_POOL_SIZE, "100"));
        } catch(NumberFormatException nfe) {
            throw new IllegalArgumentException("Incorrect value for property " + YDB_POOL_SIZE, nfe);
        }
        GrpcTransportBuilder builder = GrpcTransport
                .forConnectionString(props.get(YDB_URL));
        final YdbAuthMode authMode;
        try {
            authMode = YdbAuthMode.fromString(props.get(YDB_AUTH_MODE));
        } catch(IllegalArgumentException iae) {
            throw new IllegalArgumentException("Incorrect value for property " + YDB_AUTH_MODE, iae);
        }
        switch (authMode) {
            case ENV:
                builder = builder.withAuthProvider(
                        CloudAuthHelper.getAuthProviderFromEnviron());
                break;
            case STATIC:
                builder = builder.withAuthProvider(
                    new StaticCredentials(props.get(YDB_AUTH_LOGIN), props.get(YDB_AUTH_PASSWORD)));
                break;
            case KEY:
                final String keyFile = props.get(YDB_AUTH_KEY_FILE);
                builder = builder.withAuthProvider((opt) -> {
                    return CloudAuthIdentity.serviceAccountIdentity(Paths.get(keyFile));
                });
                break;
            case TOKEN:
                final String authToken = props.get(YDB_AUTH_TOKEN);
                builder = builder.withAuthProvider((opt) -> {
                    return CloudAuthIdentity.iamTokenIdentity(authToken);
                });
                break;
            case NONE:
                break;
        }
        GrpcTransport gt = builder.build();
        this.database = gt.getDatabase();
        try {
            this.tableClient = TableClient.newClient(gt)
                    .sessionPoolSize(0, poolSize)
                    .build();
            this.schemeClient = SchemeClient.newClient(GrpcSchemeRpc.useTransport(gt)).build();
            this.retryCtx = SessionRetryContext.create(tableClient).build();
            this.transport = gt;
            gt = null; // to avoid closing below
        } finally {
            if (gt != null)
                gt.close();
        }
    }

    public String getCatalogName() {
        return catalogName;
    }

    public Map<String, String> getConnectOptions() {
        return connectOptions;
    }

    public GrpcTransport getTransport() {
        return transport;
    }

    public TableClient getTableClient() {
        return tableClient;
    }

    public SchemeClient getSchemeClient() {
        return schemeClient;
    }

    public SessionRetryContext getRetryCtx() {
        return retryCtx;
    }

    public String getDatabase() {
        return database;
    }

    @Override
    public void close() {
        if (tableClient != null) {
            try {
                tableClient.close();
            } catch(Exception ex) {
                LOG.warn("TableClient closing threw an exception", ex);
            }
        }
        if (schemeClient != null) {
            try {
                schemeClient.close();
            } catch(Exception ex) {
                LOG.warn("SchemeClient closing threw an exception", ex);
            }
        }
        if (transport != null) {
            try {
                transport.close();
            } catch(Exception ex) {
                LOG.warn("GrpcTransport closing threw an exception", ex);
            }
        }
    }

}
