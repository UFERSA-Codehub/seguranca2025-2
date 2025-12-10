package exemplo1;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;

public class ImplRSA {
    public static void main(String[] args) throws Exception {

        // provider do Bouncy Castle
        Security.addProvider(new BouncyCastleProvider());

        // par de chaves RSA
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair parDeChaves = kpg.generateKeyPair();

        byte[] mensagem = "Mensagem Secreta".getBytes();

        Cipher cifrador =
                Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
        cifrador.init(Cipher.ENCRYPT_MODE, parDeChaves.getPublic());
        byte[] criptografado = cifrador.doFinal(mensagem);

        Cipher decifrador =
                Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
        decifrador.init(Cipher.DECRYPT_MODE, parDeChaves.getPrivate());
        byte[] decifrado = decifrador.doFinal(criptografado);

        System.out.println("Texto original: " + new String(decifrado));
    }
}

