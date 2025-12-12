package com.project.auth;

import java.util.Date;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JWT {
    private static final Logger logger = LoggerFactory.getLogger(JWT.class);
    private static final long EXPIRATION_MS = 30 * 60 * 1000; // 30 minutos
    
    private final SecretKey secretKey;
    private final String issuer;

    public JWT(String secret, String issuer) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.issuer = issuer;
    }

    public String generateToken(String sensorId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        String token = Jwts.builder()
                .subject(sensorId)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .claim("role", "sensor")
                .signWith(secretKey)
                .compact();

        logger.debug("Token gerado para sensor '{}', expira em {}", sensorId, expiration);
        return token;
    }

    private Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            logger.debug("Token válido para '{}'", claims.getSubject());
            return claims;
        } catch (ExpiredJwtException e) {
            logger.warn("Token expirado: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            logger.warn("Token inválido: {}", e.getMessage());
            return null;
        }
    }

    public String getSensorId(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    public boolean isValid(String token) {
        return validateToken(token) != null;
    }

    public String generateClientToken(String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        String token = Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .claim("role", "client")
                .signWith(secretKey)
                .compact();

        logger.debug("Token gerado para cliente '{}', expira em {}", username, expiration);
        return token;
    }
}
