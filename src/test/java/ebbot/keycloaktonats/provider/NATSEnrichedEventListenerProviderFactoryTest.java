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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NATSEnrichedEventListenerProviderFactoryTest {

    @Test
    void listenerNoop_create_returnsNoop() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        factory.listener = new NOOPEventListenerProvider();

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NOOPEventListenerProvider.class, provider);
    }

    @Test
    void sendEnrichedFlagOff_create_returnsBaseProvider() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        factory.listener = new NATSEventListenerProvider(mock(JetStream.class), mock(Connection.class));
        factory.sendEnrichedClientEvents = false;

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NATSEventListenerProvider.class, provider);
    }

    @Test
    void sendEnrichedFlagOn_create_returnsEnrichedProvider() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        factory.listener = new NATSEventListenerProvider(mock(JetStream.class), mock(Connection.class));
        factory.natsConnection = mock(Connection.class);
        factory.jetStream = mock(JetStream.class);
        factory.sendEnrichedClientEvents = true;

        EventListenerProvider provider = factory.create(mock(KeycloakSession.class));
        assertInstanceOf(NATSEnrichedEventListenerProvider.class, provider);
    }

    @Test
    void init_noNkeySeed_connectionFails_setsNoop() {
        NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
        factory.init(null);

        assertInstanceOf(NOOPEventListenerProvider.class, factory.create(mock(KeycloakSession.class)));
    }

    @Test
    void init_noNkeySeed_connectionSucceeds_setsBaseProvider() throws Exception {
        try (MockedStatic<Configuration> configMock = mockStatic(Configuration.class);
             MockedStatic<Nats> natsMock = mockStatic(Nats.class)) {
            Configuration config = mock(Configuration.class);
            when(config.getUrl()).thenReturn("nats://localhost:4222");
            when(config.getNkeySeed()).thenReturn(Optional.empty());
            when(config.sendEnrichedClientEvents()).thenReturn(false);
            when(config.useJetStream()).thenReturn(false);
            configMock.when(Configuration::loadFromEnv).thenReturn(config);
            natsMock.when(() -> Nats.connect(any(Options.class))).thenReturn(mock(Connection.class));

            NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
            factory.init(null);

            assertInstanceOf(NATSEventListenerProvider.class, factory.create(mock(KeycloakSession.class)));
        }
    }

    @Test
    void init_withNkeySeed_connectionFails_setsNoop() throws Exception {
        NKey nkey = NKey.createUser(null);
        String seed = new String(nkey.getSeed());

        try (MockedStatic<Configuration> configMock = mockStatic(Configuration.class)) {
            Configuration config = mock(Configuration.class);
            when(config.getUrl()).thenReturn("nats://localhost:4222");
            when(config.getNkeySeed()).thenReturn(Optional.of(seed));
            when(config.sendEnrichedClientEvents()).thenReturn(false);
            when(config.useJetStream()).thenReturn(false);
            configMock.when(Configuration::loadFromEnv).thenReturn(config);

            NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
            factory.init(null);

            assertInstanceOf(NOOPEventListenerProvider.class, factory.create(mock(KeycloakSession.class)));
        }
    }

    @Test
    void init_withNkeySeed_connectionSucceeds_setsBaseProvider() throws Exception {
        NKey nkey = NKey.createUser(null);
        String seed = new String(nkey.getSeed());

        try (MockedStatic<Configuration> configMock = mockStatic(Configuration.class);
             MockedStatic<Nats> natsMock = mockStatic(Nats.class)) {
            Configuration config = mock(Configuration.class);
            when(config.getUrl()).thenReturn("nats://localhost:4222");
            when(config.getNkeySeed()).thenReturn(Optional.of(seed));
            when(config.sendEnrichedClientEvents()).thenReturn(false);
            when(config.useJetStream()).thenReturn(false);
            configMock.when(Configuration::loadFromEnv).thenReturn(config);
            natsMock.when(() -> Nats.connect(any(Options.class))).thenReturn(mock(Connection.class));

            NATSEnrichedEventListenerProviderFactory factory = new NATSEnrichedEventListenerProviderFactory();
            factory.init(null);

            assertInstanceOf(NATSEventListenerProvider.class, factory.create(mock(KeycloakSession.class)));
        }
    }
}
