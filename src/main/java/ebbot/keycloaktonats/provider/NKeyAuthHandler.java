package ebbot.keycloaktonats.provider;

import io.nats.client.AuthHandler;
import io.nats.client.NKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

final class NKeyAuthHandler implements AuthHandler {

    private final NKey nkey;

    NKeyAuthHandler(String seed) {
        try {
            this.nkey = NKey.fromSeed(seed.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Invalid NKey seed", e);
        }
    }

    @Override
    public char[] getID() {
        try {
            return nkey.getPublicKey();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to get NKey public key", e);
        }
    }

    @Override
    public byte[] sign(byte[] nonce) {
        try {
            return nkey.sign(nonce);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to sign NATS nonce with NKey", e);
        }
    }

    @Override
    public char[] getJWT() {
        return null;
    }
}
