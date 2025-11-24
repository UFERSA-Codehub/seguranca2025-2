package com.project.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.Date;
import java.util.Map;

public class JWTManager {
    
    // Chave secreta para assinar JWTs (256 bits = 32 caracteres mínimo)
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private static final String SECRET_KEY = dotenv.get("JWT_SECRET_KEY");
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    
    // Validade padrão do token: 24 horas (em milissegundos)
    private static final long VALIDADE_MS = 24 * 60 * 60 * 1000;

    public static String gerarToken(String usuario, Map<String, Object> claims) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + VALIDADE_MS);
        
        JwtBuilder builder = Jwts.builder()
            .subject(usuario)                                    // "sub" - subject (identificador do usuário)
            .issuedAt(agora)                                     // "iat" - issued at (quando foi criado)
            .expiration(expiracao)                               // "exp" - expiration (quando expira)
            .signWith(KEY, Jwts.SIG.HS256);                     // Assinar com HMAC-SHA256
        
        // Adicionar claims customizados ao payload
        if (claims != null && !claims.isEmpty()) {
            builder.claims().add(claims);
        }
        
        return builder.compact();
    }

    public static String gerarToken(String usuario) {
        return gerarToken(usuario, null);
    }

    public static boolean validarToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        try {
            Jwts.parser()
                .verifyWith(KEY)           // Verificar assinatura com nossa chave
                .build()
                .parseSignedClaims(token); // Parse e valida estrutura + expiração
            return true;
            
        } catch (ExpiredJwtException e) {
            System.err.println("[JWT] ❌ Token expirado: " + e.getMessage());
            return false;
            
        } catch (MalformedJwtException e) {
            System.err.println("[JWT] ❌ Token malformado: " + e.getMessage());
            return false;
            
        } catch (io.jsonwebtoken.security.SignatureException e) {
            System.err.println("[JWT] ❌ Assinatura inválida: " + e.getMessage());
            return false;
            
        } catch (JwtException e) {
            System.err.println("[JWT] ❌ Token inválido: " + e.getMessage());
            return false;
        }
    }

    public static Claims extrairClaims(String token) throws JwtException {
        return Jwts.parser()
            .verifyWith(KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public static String extrairUsuario(String token) {
        try {
            Claims claims = extrairClaims(token);
            return claims.getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    public static <T> T extrairClaim(String token, String claimName, Class<T> claimType) {
        try {
            Claims claims = extrairClaims(token);
            return claims.get(claimName, claimType);
        } catch (JwtException e) {
            return null;
        }
    }

    public static boolean isAdmin(String token) {
        try {
            Claims claims = extrairClaims(token);
            Boolean admin = claims.get("admin", Boolean.class);
            return admin != null && admin;
        } catch (JwtException e) {
            return false;
        }
    }

    public static Date extrairExpiracao(String token) {
        try {
            Claims claims = extrairClaims(token);
            return claims.getExpiration();
        } catch (JwtException e) {
            return null;
        }
    }

    public static boolean isExpirado(String token) {
        try {
            Date expiracao = extrairExpiracao(token);
            if (expiracao == null) return true;
            return expiracao.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public static String infoToken(String token) {
        try {
            Claims claims = extrairClaims(token);
            return String.format("JWT{sub='%s', name='%s', admin=%s, exp=%s}",
                claims.getSubject(),
                claims.get("name"),
                claims.get("admin"),
                claims.getExpiration()
            );
        } catch (JwtException e) {
            return "JWT{INVÁLIDO: " + e.getMessage() + "}";
        }
    }
}
