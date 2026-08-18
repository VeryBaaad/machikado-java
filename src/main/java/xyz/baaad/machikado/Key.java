package xyz.baaad.machikado;

import java.security.*;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.*;
import java.util.Arrays;

@SuppressWarnings("unused")
public class Key {
    public static class Ed25519KeyPair {
        public byte[] publicKey;
        public byte[] privateKey;

        public Ed25519KeyPair(byte[] publicKey, byte[] privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

    public static Ed25519KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        byte[] pkX509 = kp.getPublic().getEncoded();
        byte[] publicKey = Arrays.copyOfRange(pkX509, pkX509.length - 32, pkX509.length);

        PrivateKey pKey = kp.getPrivate();
        byte[] seed;
        if (pKey instanceof EdECPrivateKey) {
            seed = ((EdECPrivateKey) pKey).getBytes()
                    .orElseThrow(() -> new RuntimeException("Failed to extract Ed25519 seed"));
        } else {
            byte[] pkcs8 = pKey.getEncoded();
            seed = Arrays.copyOfRange(pkcs8, pkcs8.length - 32, pkcs8.length);
        }

        byte[] privateKey = new byte[64];
        System.arraycopy(seed, 0, privateKey, 0, 32);
        System.arraycopy(publicKey, 0, privateKey, 32, 32);

        return new Ed25519KeyPair(publicKey, privateKey);
    }
}
