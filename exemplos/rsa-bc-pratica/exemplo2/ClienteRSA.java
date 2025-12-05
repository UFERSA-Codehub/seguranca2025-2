package exemplo2;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.PublicKey;
import java.security.Security;

public class ClienteRSA {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        Socket socket = new Socket("localhost", 9999);
        System.out.println("Conectado ao servidor.");

        ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());
        PublicKey chavePublica = (PublicKey) entrada.readObject();
        System.out.println("Chave pública recebida do servidor.");

        String mensagem = "Olá Servidor! Esta é uma mensagem secreta RSA.";
        Cipher cifrador =
                Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
        cifrador.init(Cipher.ENCRYPT_MODE, chavePublica);
        byte[] mensagemCifrada = cifrador.doFinal(mensagem.getBytes());

        ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
        saida.writeObject(mensagemCifrada);
        saida.flush();
        System.out.println("Mensagem criptografada enviada ao servidor.");

        saida.close();
        entrada.close();
        socket.close();
    }
}

