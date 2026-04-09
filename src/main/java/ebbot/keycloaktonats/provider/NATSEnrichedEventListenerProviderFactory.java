package ebbot.keycloaktonats.provider;

import ebbot.keycloaktonats.config.Configuration;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.models.KeycloakSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NATSEnrichedEventListenerProviderFactory extends NATSEventListenerProviderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(NATSEnrichedEventListenerProviderFactory.class);

    boolean sendEnrichedClientEvents = false;
    Connection natsConnection;
    JetStream jetStream;

    @Override
    public void init(final Config.Scope unusedConfig) {
        final Configuration config = Configuration.loadFromEnv();
        this.sendEnrichedClientEvents = config.sendEnrichedClientEvents();

        try {
            Options.Builder optionsBuilder = new Options.Builder()
                .server(config.getUrl())
                .connectionListener(new NatsConnectionListener());

            config.getNkeySeed().ifPresent(seed -> optionsBuilder.authHandler(new NKeyAuthHandler(seed)));

            this.natsConnection = Nats.connect(optionsBuilder.build());

            if (config.useJetStream()) {
                // Use JetStream connection
                if (config.jetStreamManageStreams()) {
                    buildAdminEventStream(this.natsConnection, config);
                    buildClientEventStream(this.natsConnection, config);
                }

                this.jetStream = this.natsConnection.jetStream();
            }

            this.listener = new NATSEventListenerProvider(this.jetStream, this.natsConnection);

        } catch (final IOException | InterruptedException | JetStreamApiException exception) {
            LOGGER.error("could not open NATS connection", exception);
            this.listener = new NOOPEventListenerProvider();
        }
    }

    @Override
    public EventListenerProvider create(final KeycloakSession session) {
        if (this.sendEnrichedClientEvents) {
            return new NATSEnrichedEventListenerProvider(this.jetStream, this.natsConnection, session);
        }

        return super.create(session);
    }
}
