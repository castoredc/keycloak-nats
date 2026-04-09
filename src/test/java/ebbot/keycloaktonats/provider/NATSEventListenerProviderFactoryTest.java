package ebbot.keycloaktonats.provider;

import ebbot.keycloaktonats.config.Configuration;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.NKey;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.models.KeycloakSession;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NATSEventListenerProviderFactoryTest {

    @Test
    void listenerNotSet_create_returnsNull() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        // listener is null before init() — create() returns null (NOOP handled by Keycloak)
        assert factory.create(mock(KeycloakSession.class)) == null;
    }

    @Test
    void listenerNoop_create_returnsNoop() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        factory.listener = new NOOPEventListenerProvider();

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NOOPEventListenerProvider.class, provider);
    }

    @Test
    void listenerSet_create_returnsProvider() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        factory.listener = new NATSEventListenerProvider(mock(JetStream.class), mock(Connection.class));

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NATSEventListenerProvider.class, provider);
    }

    @Test
    void init_noNkeySeed_connectionSucceeds_setsNATSProvider() throws Exception {
        try (MockedStatic<Configuration> configMock = mockStatic(Configuration.class);
             MockedStatic<Nats> natsMock = mockStatic(Nats.class)) {
            Configuration config = mock(Configuration.class);
            when(config.getUrl()).thenReturn("nats://localhost:4222");
            when(config.getNkeySeed()).thenReturn(Optional.empty());
            when(config.useJetStream()).thenReturn(false);
            configMock.when(Configuration::loadFromEnv).thenReturn(config);
            natsMock.when(() -> Nats.connect(any(Options.class))).thenReturn(mock(Connection.class));

            NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
            factory.init(null);

            assertInstanceOf(NATSEventListenerProvider.class, factory.create(mock(KeycloakSession.class)));
        }
    }

    @Test
    void init_withNkeySeed_connectionSucceeds_setsNATSProvider() throws Exception {
        NKey nkey = NKey.createUser(null);
        String seed = new String(nkey.getSeed());

        try (MockedStatic<Configuration> configMock = mockStatic(Configuration.class);
             MockedStatic<Nats> natsMock = mockStatic(Nats.class)) {
            Configuration config = mock(Configuration.class);
            when(config.getUrl()).thenReturn("nats://localhost:4222");
            when(config.getNkeySeed()).thenReturn(Optional.of(seed));
            when(config.useJetStream()).thenReturn(false);
            configMock.when(Configuration::loadFromEnv).thenReturn(config);
            natsMock.when(() -> Nats.connect(any(Options.class))).thenReturn(mock(Connection.class));

            NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
            factory.init(null);

            assertInstanceOf(NATSEventListenerProvider.class, factory.create(mock(KeycloakSession.class)));
        }
    }

    @Test
    void init_connectionFails_setsNoop() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        // No NATS server available — init() should fall back to NOOP
        factory.init(null);

        assertInstanceOf(NOOPEventListenerProvider.class, factory.create(mock(KeycloakSession.class)));
    }

    @Test
    void close_listenerIsNoop_doesNotThrow() {
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        factory.listener = new NOOPEventListenerProvider();

        assertDoesNotThrow(factory::close);
    }

    @Test
    void close_listenerIsNATSProvider_closesConnection() throws Exception {
        Connection mockConnection = mock(Connection.class);
        NATSEventListenerProviderFactory factory = new NATSEventListenerProviderFactory();
        factory.listener = new NATSEventListenerProvider(null, mockConnection);

        factory.close();

        verify(mockConnection).close();
    }
}
