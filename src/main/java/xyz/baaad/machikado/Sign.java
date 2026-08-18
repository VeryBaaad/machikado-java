package xyz.baaad.machikado;

import org.jetbrains.annotations.NotNull;

import java.security.*;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("unused")
public class Sign {
    public static class FileEntry {
        public String relativePath;
        public byte[] content;

        public FileEntry(String relativePath, byte[] content) {
            this.relativePath = relativePath;
            this.content = content;
        }
    }

    public record SignedBlob(byte[] signature, byte[] publicKey) {
        public static SignedBlob fromBytes(byte[] bytes) throws SignException {
                if (bytes.length != 96) {
                    throw new SignException("invalid signed blob (expected 96 bytes)");
                }
                byte[] sig = Arrays.copyOfRange(bytes, 0, 64);
                byte[] pk = Arrays.copyOfRange(bytes, 64, 96);
                return new SignedBlob(sig, pk);
            }

            public byte[] asBytes() {
                byte[] bytes = new byte[96];
                System.arraycopy(signature, 0, bytes, 0, 64);
                System.arraycopy(publicKey, 0, bytes, 64, 32);
                return bytes;
            }
        }

    private record ProcessedData(byte[] data, int offset) {}

    public static class SignException extends Exception {
        public SignException(String message) { super(message); }
    }

    public static class VerifyResult {
        public boolean ok;
        public String error;
        public VerifyResult(boolean ok, String error) {
            this.ok = ok;
            this.error = error;
        }
    }

    private static byte[] buildSigningData(List<FileEntry> entries) {
        int totalLen = 0;
        for (FileEntry e : entries) {
            totalLen += e.relativePath.getBytes(StandardCharsets.UTF_8).length + 1 + 8 + e.content.length;
        }
        byte[] data = new byte[totalLen];
        int offset = 0;
        for (FileEntry e : entries) {
            byte[] pathBytes = e.relativePath.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(pathBytes, 0, data, offset, pathBytes.length);
            offset += pathBytes.length;
            data[offset++] = 0;

            long len = e.content.length;
            for (int i = 0; i < 8; i++) {
                data[offset++] = (byte) ((len >>> (i * 8)) & 0xFF);
            }
            System.arraycopy(e.content, 0, data, offset, e.content.length);
            offset += e.content.length;
        }
        return data;
    }

    public static SignedBlob signFileEntries(List<FileEntry> entries, byte[] privateKey64) throws Exception {
        PrivateKey privKey = buildPrivateKey(privateKey64);
        byte[] data = buildSigningData(entries);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privKey);
        sig.update(data);
        byte[] signatureBytes = sig.sign();

        byte[] publicKey = Arrays.copyOfRange(privateKey64, 32, 64);
        return new SignedBlob(signatureBytes, publicKey);
    }

    public static VerifyResult verify(byte[] machikadoBlob, byte[] mazokuBlob, List<FileEntry> entries, String moduleId, byte[] expectedOrgPk) {
        SignedBlob machikado, mazoku;
        try {
            machikado = SignedBlob.fromBytes(machikadoBlob);
            mazoku = SignedBlob.fromBytes(mazokuBlob);
        } catch (SignException e) {
            return new VerifyResult(false, e.getMessage());
        }

        if (!Arrays.equals(mazoku.publicKey(), expectedOrgPk)) {
            return new VerifyResult(false, "public key mismatch");
        }

        try {
            PublicKey orgKey = buildPublicKey(mazoku.publicKey());
            ProcessedData processedData = processData(moduleId);
            System.arraycopy(machikado.publicKey(), 0, processedData.data(), processedData.offset(), 32);

            Signature vSig = Signature.getInstance("Ed25519");
            vSig.initVerify(orgKey);
            vSig.update(processedData.data());
            if (!vSig.verify(mazoku.signature())) {
                return new VerifyResult(false, "signature verification failed");
            }
        } catch (Exception e) {
            return new VerifyResult(false, "signature verification failed");
        }

        return getVerifyResult(entries, machikado);
    }

    public static VerifyResult verifyMachikado(byte[] machikadoBlob, List<FileEntry> entries, byte[] expectedPk) {
        SignedBlob machikado;
        try {
            machikado = SignedBlob.fromBytes(machikadoBlob);
        } catch (SignException e) {
            return new VerifyResult(false, e.getMessage());
        }

        if (!Arrays.equals(machikado.publicKey(), expectedPk)) {
            return new VerifyResult(false, "public key mismatch");
        }

        return getVerifyResult(entries, machikado);
    }

    private static boolean isValidModuleId(String id) {
        if (id == null || id.isEmpty()) return false;
        char first = id.charAt(0);
        if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z'))) return false;
        for (char b : id.toCharArray()) {
            if (!((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') || b == '.' || b == '_' || b == '-')) {
                return false;
            }
        }
        return true;
    }

    public static SignedBlob signMazoku(String moduleId, byte[] projectPublicKey, byte[] orgPrivateKey64) throws Exception {
        if (!isValidModuleId(moduleId)) {
            throw new SignException("invalid module id: must match ^[a-zA-Z][a-zA-Z0-9._-]+$");
        }

        PrivateKey privKey = buildPrivateKey(orgPrivateKey64);
        byte[] orgPublicKey = Arrays.copyOfRange(orgPrivateKey64, 32, 64);

        ProcessedData processedData = processData(moduleId);
        System.arraycopy(projectPublicKey, 0, processedData.data(), processedData.offset(), 32);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privKey);
        sig.update(processedData.data());
        byte[] signatureBytes = sig.sign();

        return new SignedBlob(signatureBytes, orgPublicKey);
    }

    @NotNull
    private static Sign.VerifyResult getVerifyResult(List<FileEntry> entries, SignedBlob machikado) {
        try {
            PublicKey memberKey = buildPublicKey(machikado.publicKey());
            byte[] fileData = buildSigningData(entries);

            Signature vSig = Signature.getInstance("Ed25519");
            vSig.initVerify(memberKey);
            vSig.update(fileData);
            if (!vSig.verify(machikado.signature())) {
                return new VerifyResult(false, "signature verification failed");
            }
        } catch (Exception e) {
            return new VerifyResult(false, "signature verification failed");
        }

        return new VerifyResult(true, null);
    }

    @NotNull
    private static ProcessedData processData(String moduleId) {
        byte[] moduleIdBytes = moduleId.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[moduleIdBytes.length + 1 + 32];
        int offset = 0;
        System.arraycopy(moduleIdBytes, 0, data, offset, moduleIdBytes.length);
        offset += moduleIdBytes.length;
        data[offset++] = 0;
        return new ProcessedData(data, offset);
    }

    private static PrivateKey buildPrivateKey(byte[] privateKey64) throws Exception {
        if (privateKey64.length != 64) {
            throw new IllegalArgumentException("Invalid private key length");
        }
        byte[] seed = Arrays.copyOfRange(privateKey64, 0, 32);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        EdECPrivateKeySpec keySpec = new EdECPrivateKeySpec(NamedParameterSpec.ED25519, seed);
        return kf.generatePrivate(keySpec);
    }

    private static PublicKey buildPublicKey(byte[] publicKey32) throws Exception {
        if (publicKey32.length != 32) {
            throw new IllegalArgumentException("Invalid public key length");
        }
        byte[] x509Header = new byte[] {
                0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00
        };
        byte[] x509Bytes = new byte[44];
        System.arraycopy(x509Header, 0, x509Bytes, 0, 12);
        System.arraycopy(publicKey32, 0, x509Bytes, 12, 32);

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(x509Bytes);
        return kf.generatePublic(pubSpec);
    }
}
