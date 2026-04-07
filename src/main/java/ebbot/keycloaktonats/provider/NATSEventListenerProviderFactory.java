package ebbot.keycloaktonats.provider;

import io.nats.client.*;

import java.io.IOException;
import io.nats.client.api.StreamConfiguration;
import ebbot.keycloaktonats.config.Configuration;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class NatsConnectionListener implements ConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(NatsConnectionListener.class);
    public void connectionEvent(Connection natsConnection, Events event) {
        LOGGER.info("NATS Connection Status: {}", event.toString());
    }
}
/**
 * Provides the {@link NATSEventListenerProvider} or {@link NOOPEventListenerProvider} to Keycloak
 *
 * @author Lukas Schulte Pelkum
 * @version 0.1.0
 * @since 0.1.0
 */
public class NATSEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(NATSEventListenerProviderFactory.class);

    private Connection natsConnection;
    private JetStream jetStream;
    private boolean initialized = false;
    private boolean sendEnrichedClientEvents = false;

    @Override
    public EventListenerProvider create(final KeycloakSession session) {
        if (!this.initialized) {
            return new NOOPEventListenerProvider();
        }
        if (this.sendEnrichedClientEvents) {
            return new NATSEnrichedEventListenerProvider(this.jetStream, this.natsConnection, session);
        }
        return new NATSEventListenerProvider(this.jetStream, this.natsConnection);
    }

    @Override
    public void init(final Config.Scope unusedConfig) {
        // We use our own configuration as I don't want to mess around with XML from two thousand years ago
        final Configuration config = Configuration.loadFromEnv();

        try {
            Options.Builder optionsBuilder = new Options.Builder()
                .server(config.getUrl())
                .connectionListener(new NatsConnectionListener());

            config.getNkeySeed().ifPresent(seed -> optionsBuilder.authHandler(new NKeyAuthHandler(seed)));

            Options options = optionsBuilder.build();
            Connection natsConnection = Nats.connect(options);

            if (config.useJetStream()) {
                // Use JetStream connection
                if (config.jetStreamManageStreams()) {
                    buildAdminEventStream(natsConnection, config);
                    buildClientEventStream(natsConnection, config);
                }

                this.jetStream = natsConnection.jetStream();
            } else {
                // Use classic connection
                this.jetStream = null;
            }

            this.natsConnection = natsConnection;
            this.sendEnrichedClientEvents = config.sendEnrichedClientEvents();
            this.initialized = true;

        } catch (final IOException | InterruptedException | JetStreamApiException exception) {
            LOGGER.error("could not open NATS connection", exception);
        }
    }

    @Override
    public void postInit(final KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
        if (this.natsConnection == null) {
            return;
        }
        try {
            this.natsConnection.close();
        } catch (final InterruptedException exception) {
            LOGGER.error("could not close NATS connection", exception);
        }
    }

    @Override
    public String getId() {
        return "keycloak-nats-adapter";
    }

    private void buildAdminEventStream(Connection natsConnection, Configuration config) throws IOException, JetStreamApiException {
        final String streamName = "keycloak-admin-event-stream";
        JetStream jetStream = natsConnection.jetStream();
        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                .name(streamName)
                .subjects("keycloak.event.admin.>")
                .maxBytes(config.getJetStreamAdminSize() * 1024 * 1024)
                .build();

        try {
            jetStream.getStreamContext(streamName);
            natsConnection.jetStreamManagement().updateStream(streamConfiguration);
        } catch (JetStreamApiException e) {
            natsConnection.jetStreamManagement().addStream(streamConfiguration);
        }
    }

    private void buildClientEventStream(Connection natsConnection, Configuration config) throws IOException, JetStreamApiException {
        final String streamName = "keycloak-client-event-stream";
        JetStream jetStream = natsConnection.jetStream();
        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                .name(streamName)
                .subjects("keycloak.event.client.>")
                .maxBytes(config.getJetStreamClientSize() * 1024 * 1024)
                .build();

        try {
            jetStream.getStreamContext(streamName);
            natsConnection.jetStreamManagement().updateStream(streamConfiguration);
        } catch (JetStreamApiException e) {
            natsConnection.jetStreamManagement().addStream(streamConfiguration);
        }
    }

}
