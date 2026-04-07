package ebbot.keycloaktonats.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.PublishAck;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * NATS event listener with user data enrichment for client events.
 * Client events are augmented with a user representation fetched from the Keycloak session.
 * Admin events are published as-is.
 */
class NATSEnrichedEventListenerProvider implements EventListenerProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(NATSEnrichedEventListenerProvider.class);

    private final ObjectMapper objectMapper;
    private final JetStream jetStream;
    private final Connection natsConnection;
    private final KeycloakSession session;

    NATSEnrichedEventListenerProvider(final JetStream jetStream, final Connection natsConnection, final KeycloakSession session) {
        this.objectMapper = new ObjectMapper();
        this.jetStream = jetStream;
        this.natsConnection = natsConnection;
        this.session = session;
    }

    @Override
    public void onEvent(final Event event) {
        final String serialized = this.serializeEnriched(event);
        final String key = this.buildKey(event);
        this.send(key, serialized);
    }

    @Override
    public void onEvent(final AdminEvent event, final boolean includeRepresentation) {
        final String serialized = this.serialize(event);
        final String key = this.buildKey(event);
        this.send(key, serialized);
    }

    @Override
    public void close() {
        // We re-use this object so we don't care about this method
        // To close the connection we use NATSEventListenerProviderFactory#close instead
    }

    private void send(final String key, final String value) {
        if (this.jetStream != null) {
            try {
                PublishAck ack = this.jetStream.publish(key, value.getBytes(StandardCharsets.UTF_8));
                LOGGER.debug("Published new event {} to JetStream with sequence: {}", key, ack.getSeqno());
            } catch (final IOException | JetStreamApiException exception) {
                LOGGER.error("could not send message to JetStream", exception);
            }
        } else {
            try {
                LOGGER.debug("Published new event to NATS: {} \n {}", key, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
            } catch (JsonProcessingException e) {
                LOGGER.error("could not serialize new event", e);
            }
            LOGGER.info("Published new event key: {}  value: {}", key, value);
            this.natsConnection.publish(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String serialize(final Object object) {
        try {
            return this.objectMapper.writeValueAsString(object);
        } catch (final JsonProcessingException exception) {
            LOGGER.error("could not serialize event", exception);
            return "{}";
        }
    }

    private String serializeEnriched(final Event event) {
        try {
            final ObjectNode node = this.objectMapper.valueToTree(event);

            if (event.getUserId() != null && this.session != null) {
                final RealmModel realm = this.session.realms().getRealm(event.getRealmId());
                if (realm == null) {
                    LOGGER.warn("could not find realm {} for event enrichment", event.getRealmId());
                    return node.toString();
                }
                final UserModel user = this.session.users().getUserById(realm, event.getUserId());
                if (user == null) {
                    LOGGER.warn("could not find user {} for event enrichment", event.getUserId());
                    return node.toString();
                }

                final Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("firstName", user.getFirstName());
                userMap.put("lastName", user.getLastName());
                userMap.put("email", user.getEmail());
                userMap.put("emailVerified", user.isEmailVerified());
                userMap.put("enabled", user.isEnabled());
                userMap.put("attributes", user.getAttributes());
                userMap.put("createdTimestamp", user.getCreatedTimestamp());

                node.put("representation", this.objectMapper.writeValueAsString(userMap));
            }

            return node.toString();
        } catch (final JsonProcessingException exception) {
            LOGGER.error("could not serialize enriched event", exception);
            return "{}";
        }
    }

    private String buildKey(final Event event) {
        // keycloak.event.client.<realm>.<result>.<clientId>.<type>
        return this.normalizeKey(String.format(
                "keycloak.event.client.%s.%s.%s.%s",
                event.getRealmId().replace(".", ""),
                event.getError() != null ? "error" : "success",
                event.getClientId().replace(".", ""),
                event.getType().toString().toLowerCase()
        ));
    }

    private String buildKey(final AdminEvent event) {
        // keycloak.event.admin.<realm>.<result>.<resourceType>.<operation>
        return this.normalizeKey(String.format(
                "keycloak.event.admin.%s.%s.%s.%s",
                event.getRealmId().replace(".", ""),
                event.getError() != null ? "error" : "success",
                event.getResourceTypeAsString().toLowerCase(),
                event.getOperationType().toString().toLowerCase()
        ));
    }

    private String normalizeKey(final String key) {
        // Remove everything except 'a-z', 'A-Z', '0-9', ' ', '_', '.' and '-' and replace spaces with underscores
        return key.replaceAll("[^a-zA-Z0-9 _.-]", "").
                replace(" ", "_");
    }

}
