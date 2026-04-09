package ebbot.keycloaktonats.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import org.keycloak.events.Event;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * NATS event listener with user data enrichment for client events.
 * Client events are augmented with a user representation fetched from the Keycloak session.
 * Admin events are published as-is.
 */
class NATSEnrichedEventListenerProvider extends NATSEventListenerProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(NATSEnrichedEventListenerProvider.class);

    private final KeycloakSession session;

    NATSEnrichedEventListenerProvider(final JetStream jetStream, final Connection natsConnection, final KeycloakSession session) {
        super(jetStream, natsConnection);
        this.session = session;
    }

    @Override
    public void onEvent(final Event event) {
        final String serialized = this.serializeEnriched(event);
        final String key = this.buildKey(event);
        this.send(key, serialized);
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

}
