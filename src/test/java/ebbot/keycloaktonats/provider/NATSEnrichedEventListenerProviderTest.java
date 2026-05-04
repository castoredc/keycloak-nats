package ebbot.keycloaktonats.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.MockedConstruction;

import io.nats.client.JetStreamApiException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class NATSEnrichedEventListenerProviderTest {

    @Test
    void onEvent_serializationFails_throwsUncheckedIOException() throws JsonProcessingException {
        KeycloakSession session = mock(KeycloakSession.class);
        RealmProvider realmProvider = mock(RealmProvider.class);
        RealmModel realm = mock(RealmModel.class);
        UserProvider userProvider = mock(UserProvider.class);
        UserModel user = mock(UserModel.class);

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm(anyString())).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(any(RealmModel.class), anyString())).thenReturn(user);
        when(user.getId()).thenReturn("user-id");
        when(user.getUsername()).thenReturn("testuser");
        when(user.getAttributes()).thenReturn(Map.of());

        Event event = new Event();
        event.setRealmId("test-realm");
        event.setUserId("user-id");
        event.setClientId("test-client");
        event.setType(EventType.LOGIN);

        // Use mockConstruction to make the ObjectMapper inside the provider throw on writeValueAsString
        try (MockedConstruction<ObjectMapper> ignored = mockConstruction(ObjectMapper.class, (objectMapper, context) -> {
            // valueToTree should work normally for the initial node creation — delegate to a real mapper
            ObjectMapper realMapper = new ObjectMapper();
            when(objectMapper.valueToTree(any())).thenAnswer(inv -> realMapper.valueToTree(inv.getArgument(0)));
            // writeValueAsString should throw to simulate serialization failure
            when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("test failure") {});
        })) {
            NATSEnrichedEventListenerProvider provider = new NATSEnrichedEventListenerProvider(
                    mock(JetStream.class), mock(Connection.class), session);

            assertThrows(UncheckedIOException.class, () -> provider.onEvent(event));
        }
    }

    @Test
    void onEvent_validEvent_doesNotThrow() throws IOException, JetStreamApiException {
        KeycloakSession session = mock(KeycloakSession.class);
        RealmProvider realmProvider = mock(RealmProvider.class);
        JetStream jetStream = mock(JetStream.class);

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm(anyString())).thenReturn(null);
        when(jetStream.publish(anyString(), any(byte[].class))).thenReturn(mock(PublishAck.class));

        Event event = new Event();
        event.setRealmId("test-realm");
        event.setUserId("user-id");
        event.setClientId("test-client");
        event.setType(EventType.LOGIN);

        NATSEnrichedEventListenerProvider provider = new NATSEnrichedEventListenerProvider(
                jetStream, mock(Connection.class), session);

        assertDoesNotThrow(() -> provider.onEvent(event));
    }
}
