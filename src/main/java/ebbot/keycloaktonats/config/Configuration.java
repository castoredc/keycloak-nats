package ebbot.keycloaktonats.config;

import io.nats.client.Options;

import java.util.Optional;

/**
 * Represents our own configuration based on environment variables because the stone age where we used XML is over
 *
 * @author Lukas Schulte Pelkum
 * @version 0.1.0
 * @since 0.1.0
 */
public class Configuration {

    private final boolean useJetStream;
    private final String url;
    private final int jetStreamAdminSize;
    private final int jetStreamClientSize;
    private final boolean jetStreamManageStreams;
    private final Optional<String> nkeySeed;

    private Configuration(
            final boolean useJetStream,
            final String url,
            final int jetStreamAdminSize,
            final int jetStreamClientSize,
            final boolean jetStreamManageStreams,
            final Optional<String> nkeySeed
    ) {
        this.useJetStream = useJetStream;
        this.url = url;
        this.jetStreamAdminSize = jetStreamAdminSize;
        this.jetStreamClientSize = jetStreamClientSize;
        this.jetStreamManageStreams = jetStreamManageStreams;
        this.nkeySeed = nkeySeed;
    }

    /**
     * Loads the configuration using the systems environment variables
     *
     * @return The loaded configuration
     */
    public static Configuration loadFromEnv() {
        final boolean useJetStream = "true".equalsIgnoreCase(System.getenv("KEYCLOAK_NATS_JETSTREAM"));
        final String url = Optional.ofNullable(System.getenv("KEYCLOAK_NATS_URL")).orElse(Options.DEFAULT_URL);
        final int jetStreamAdminSize = Integer.parseInt(Optional.ofNullable(System.getenv("JETSTREAM_ADMIN_SIZE")).orElse("1"));
        final int jetStreamClientSize = Integer.parseInt(Optional.ofNullable(System.getenv("JETSTREAM_CLIENT_SIZE")).orElse("1"));
        final boolean jetStreamManageStreams = !"false".equalsIgnoreCase(System.getenv("KEYCLOAK_NATS_JETSTREAM_MANAGE_STREAMS"));
        final Optional<String> nkeySeed = Optional.ofNullable(System.getenv("KEYCLOAK_NATS_NKEY_SEED"));

        return new Configuration(
                useJetStream,
                url,
                jetStreamAdminSize,
                jetStreamClientSize,
                jetStreamManageStreams,
                nkeySeed
        );
    }

    public boolean useJetStream() {
        return this.useJetStream;
    }

    public boolean jetStreamManageStreams() {
        return this.jetStreamManageStreams;
    }

    public String getUrl() {
        return this.url;
    }

    public int getJetStreamAdminSize() {
        return jetStreamAdminSize;
    }

    public int getJetStreamClientSize() {
        return jetStreamClientSize;
    }

    public Optional<String> getNkeySeed() {
        return nkeySeed;
    }
}
