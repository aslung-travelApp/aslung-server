package com.trip.aslung.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JWTUtil {
    private final Key key;
    private final long accessTokenExpTime;
    private final long refreshTokenExpTime;

    public JWTUtil(
            @Value("${jwt.key}") String secretKey,
            @Value("${jwt.accesstoken.expiretime}") long acessTokenExpTime,
            @Value("${jwt.refreshtoken.expiretime}") long refreshTokenExpTime
    ){
        //byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpTime = acessTokenExpTime;
        this.refreshTokenExpTime = refreshTokenExpTime;
    }

    public String createAccessToken(Long userId){
        return createToken(userId, accessTokenExpTime);
    }

    public String createRefreshToken(Long userId){
        return createToken(userId, refreshTokenExpTime);
    }

    private String createToken(Long userId, long expireTime){
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis()+expireTime*1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    public Long getUserId(String token){
        String subject = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        return Long.parseLong(subject);
    }

    public boolean validateToken(String token){
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e){
            return false;
        }
    }

}
