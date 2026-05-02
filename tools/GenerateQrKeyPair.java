import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class GenerateQrKeyPair {
    public static void main(String[] args) {
        String kid = args.length > 0 ? args[0] : "qr-key-local-v1";

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = generator.generateKeyPair();

            System.out.println("QR_KEY_ID=" + kid);
            System.out.println("QR_PRIVATE_KEY_BASE64="
                    + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            System.out.println("QR_PUBLIC_KEY_BASE64="
                    + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate QR key pair", e);
        }
    }
}
