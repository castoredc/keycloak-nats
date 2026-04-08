package ebbot.keycloaktonats.provider;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.models.KeycloakSession;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class NATSEventListenerProviderFactoryTest {

    @Test
    void baseFactory_listenerNotSet_returnsNull() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        // listener is null before init() — create() returns null (NOOP handled by Keycloak)
        assert factory.create(mock(KeycloakSession.class)) == null;
    }

    @Test
    void baseFactory_listenerNoop_returnsNoop() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        factory.listener = new NOOPEventListenerProvider();

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NOOPEventListenerProvider.class, provider);
    }

    @Test
    void baseFactory_listenerSet_returnsBaseProvider() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        factory.listener = new NATSEventListenerProvider(mock(JetStream.class), mock(Connection.class));

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NATSEventListenerProvider.class, provider);
    }

    @Test
    void enrichedFactory_noNatsSeed_initFails_returnsNoop() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        // No NATS server available — init() should fall back to NOOP
        factory.init(null);

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NOOPEventListenerProvider.class, provider);
    }

    @Test
    void enrichedFactory_listenerNoop_returnsNoop() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        factory.listener = new NOOPEventListenerProvider();

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NOOPEventListenerProvider.class, provider);
    }

    @Test
    void enrichedFactory_flagOff_returnsBaseProvider() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        factory.listener = new NATSEventListenerProvider(mock(JetStream.class), mock(Connection.class));
        factory.sendEnrichedClientEvents = false;

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NATSEventListenerProvider.class, provider);
    }

    @Test
    void enrichedFactory_flagOn_returnsEnrichedProvider() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        factory.listener = new NATSEventListenerProvider(mock(JetStream.class), mock(Connection.class));
        factory.natsConnection = mock(Connection.class);
        factory.jetStream = mock(JetStream.class);
        factory.sendEnrichedClientEvents = true;

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NATSEnrichedEventListenerProvider.class, provider);
    }
}
