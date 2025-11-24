import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import java.security.Key;
import java.util.Date;
public class Exemplo1Jwt {
public static void main(String[] args) {
// Gera uma chave secreta aleatória (HMAC com SHA-256)
Key chaveSecreta = Keys.secretKeyFor(SignatureAlgorithm.HS256);
// Define tempo de expiração (10 minutos)
// 10 minutos em milissegundos
long duracao = 10 * 60 * 1000;
Date expiracao = new Date(System.currentTimeMillis() + duracao);
// Cria o token JWT com dados personalizados (claims)
String token = Jwts.builder()
.setSubject("usuario123")
// Identifica o "dono" do token
.claim("email", "usuario@exemplo.com")
// Claim personalizado
.claim("perfil", "admin")
// Outro claim
.setIssuedAt(new Date())
// Data de criação
.setExpiration(expiracao)
// Data de expiração
.signWith(chaveSecreta)
// Assina com a chave secreta
.compact();
// Gera o token final
// Exibe o token JWT
System.out.println("--- Token gerado:");
System.out.println(token);
// Validação e leitura do token (parse)
// Obtém o conteúdo (claims)
Claims claims = Jwts.parser()
.setSigningKey(chaveSecreta)
// Usa a mesma chave para validar
.build()
// Decodifica e valida o JWT
.parseClaimsJws(token)
.getBody();
// Dados recuperados
System.out.println("\n--- Dados do token:");
System.out.println("Usuário: " + claims.getSubject());
System.out.println("Email: " + claims.get("email"));
System.out.println("Perfil: " + claims.get("perfil"));
System.out.println("Expira em: " + claims.getExpiration());
}
}