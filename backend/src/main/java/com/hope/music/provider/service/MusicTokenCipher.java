package com.hope.music.provider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-GCM encrypted bindings, authenticated to the owning website user. */
@Component
public class MusicTokenCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public MusicTokenCipher(@Value("${app.qq-music.connection-key:}") String encodedKey) {
        key = encodedKey.isBlank() ? null : Base64.getDecoder().decode(encodedKey.strip());
        if (key != null && key.length != 32) throw new IllegalArgumentException("QQ_MUSIC_CONNECTION_KEY must encode 32 bytes");
    }

    public boolean configured() { return key != null; }
    public String encrypt(String userId, String token) {
        byte[] iv = new byte[12]; random.nextBytes(iv);
        return "v1." + Base64.getEncoder().encodeToString(iv) + "."
                + Base64.getEncoder().encodeToString(crypt(Cipher.ENCRYPT_MODE, userId, iv, token.getBytes(StandardCharsets.UTF_8)));
    }
    public String decrypt(String userId, String encrypted) {
        String[] parts = encrypted.split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalStateException("Invalid music binding");
        return new String(crypt(Cipher.DECRYPT_MODE, userId, Base64.getDecoder().decode(parts[1]),
                Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    }
    private byte[] crypt(int mode, String userId, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(("qq:" + userId).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(data);
        } catch (Exception e) { throw new IllegalStateException("Music binding encryption unavailable"); }
    }
}
