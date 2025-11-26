package exemplo2;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

public class ServidorRSA {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator kpg =
                KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair parDeChaves = kpg.generateKeyPair();
        PublicKey chavePublica = parDeChaves.getPublic();
        PrivateKey chavePrivada = parDeChaves.getPrivate();

        ServerSocket servidor = new ServerSocket(9999);
        System.out.println("Servidor aguardando conexão na porta 9999...");

        Socket cliente = servidor.accept();
        System.out.println("Cliente conectado: " + cliente.getInetAddress());

        ObjectOutputStream saida = new ObjectOutputStream(cliente.getOutputStream());
        saida.writeObject(chavePublica);
        saida.flush();
        System.out.println("Chave pública enviada ao cliente.");

        ObjectInputStream entrada = new ObjectInputStream(cliente.getInputStream());
        byte[] mensagemCifrada = (byte[]) entrada.readObject();

        Cipher decifrador =
                Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
        decifrador.init(Cipher.DECRYPT_MODE, chavePrivada);
        byte[] mensagemDecifrada = decifrador.doFinal(mensagemCifrada);

        System.out.println("Mensagem recebida (decifrada): " + new String(mensagemDecifrada));

        entrada.close();
        saida.close();
        cliente.close();
        servidor.close();
    }
}

