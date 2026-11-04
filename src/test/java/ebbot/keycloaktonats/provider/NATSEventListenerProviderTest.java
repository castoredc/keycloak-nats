package ebbot.keycloaktonats.provider;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NATSEventListenerProviderTest {

    private JetStream jetStream;
    private Connection connection;

    @BeforeEach
    void setUp() throws IOException, JetStreamApiException {
        jetStream = mock(JetStream.class);
        connection = mock(Connection.class);
        when(jetStream.publish(anyString(), any(byte[].class))).thenReturn(mock(PublishAck.class));
    }

    @Test
    void onEvent_withClientId_publishesToClientSubject() throws IOException, JetStreamApiException {
        Event event = clientEvent("my.client", EventType.LOGIN, null);

        new NATSEventListenerProvider(jetStream, connection).onEvent(event);

        verify(jetStream).publish(eq("keycloak.event.client.test-realm.success.myclient.login"), any(byte[].class));
    }

    @Test
    void onEvent_errorWithClientId_publishesToErrorSubject() throws IOException, JetStreamApiException {
        Event event = clientEvent("my-client", EventType.LOGIN_ERROR, "invalid_user_credentials");

        new NATSEventListenerProvider(jetStream, connection).onEvent(event);

        verify(jetStream).publish(eq("keycloak.event.client.test-realm.error.my-client.login_error"), any(byte[].class));
    }

    @Test
    void onEvent_nullClientId_publishesWithUnknownClientPlaceholder() throws IOException, JetStreamApiException {
        // Shaped like SessionCodeChecks.initialVerifyAuthSession on an expired auth session
        Event event = clientEvent(null, EventType.LOGIN_ERROR, "expired_code");

        new NATSEventListenerProvider(jetStream, connection).onEvent(event);

        verify(jetStream).publish(eq("keycloak.event.client.test-realm.error.unknown.login_error"), any(byte[].class));
    }

    @Test
    void onEvent_nullClientIdIdentityProviderLogoutError_publishesWithUnknownClientPlaceholder() throws IOException, JetStreamApiException {
        // Shaped like OIDCIdentityProvider$OIDCEndpoint.logoutResponse on an external IdP logout failure
        Event event = clientEvent(null, EventType.LOGOUT_ERROR, "invalid_request");

        new NATSEventListenerProvider(jetStream, connection).onEvent(event);

        verify(jetStream).publish(eq("keycloak.event.client.test-realm.error.unknown.logout_error"), any(byte[].class));
    }

    @Test
    void onEvent_nullClientIdWithoutJetStream_publishesToCoreNats() {
        Event event = clientEvent(null, EventType.LOGIN_ERROR, "expired_code");

        new NATSEventListenerProvider(null, connection).onEvent(event);

        verify(connection).publish(eq("keycloak.event.client.test-realm.error.unknown.login_error"), any(byte[].class));
    }

    @Test
    void onAdminEvent_nullAuthClientId_publishesToAdminSubject() throws IOException, JetStreamApiException {
        AuthDetails authDetails = new AuthDetails();
        authDetails.setRealmId("master");
        authDetails.setClientId(null);

        AdminEvent event = new AdminEvent();
        event.setRealmId("test-realm");
        event.setAuthDetails(authDetails);
        event.setResourceType(ResourceType.USER);
        event.setOperationType(OperationType.UPDATE);

        new NATSEventListenerProvider(jetStream, connection).onEvent(event, false);

        verify(jetStream).publish(eq("keycloak.event.admin.test-realm.success.user.update"), any(byte[].class));
    }

    static Event clientEvent(final String clientId, final EventType type, final String error) {
        Event event = new Event();
        event.setRealmId("test-realm");
        event.setUserId("user-id");
        event.setClientId(clientId);
        event.setType(type);
        event.setError(error);
        return event;
    }
}
