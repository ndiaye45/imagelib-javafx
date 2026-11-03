package fr.ndiaye.imagelib.crypto;

import fr.ndiaye.imagelib.model.Raster;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Chiffre une image avec un mot de passe : AES-256 en mode GCM, clé dérivée par PBKDF2.
 * <p>
 * L'image chiffrée est elle-même une image (du bruit) qu'on peut enregistrer en PNG :
 * <pre>
 *   octets cachés dans les canaux R, V, B (alpha toujours opaque) :
 *   [ sel 16 | IV 12 | largeur 4 | hauteur 4 | pixels chiffrés 4·l·h | étiquette GCM 16 | remplissage aléatoire ]
 * </pre>
 * Le mode GCM authentifie aussi les données : un mauvais mot de passe, ou une image
 * retouchée après chiffrement, est détecté au lieu de produire une image fausse.
 * L'alpha n'est jamais utilisé pour stocker des octets, car JavaFX stocke les images
 * avec alpha prémultiplié, ce qui détruirait les couleurs des pixels transparents.
 */
public final class ImageCipher {

    /** Mot de passe incorrect, image modifiée, ou image qui n'a jamais été chiffrée. */
    public static final class ImageCipherException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ImageCipherException(String message) {
            super(message);
        }
    }

    public static final int MIN_PASSWORD_LENGTH = 4;

    private static final int SALT = 16;
    private static final int IV = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG = TAG_BITS / 8;
    private static final int HEADER = SALT + IV + 8;
    private static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ImageCipher() {
    }

    public static Raster encrypt(Raster image, char[] password) {
        requirePassword(password);
        int w = image.width();
        int h = image.height();
        byte[] plain = new byte[w * h * 4];
        ByteBuffer.wrap(plain).asIntBuffer().put(image.argb());

        byte[] salt = random(SALT);
        byte[] iv = random(IV);
        byte[] dims = ByteBuffer.allocate(8).putInt(w).putInt(h).array();
        byte[] sealed;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(dims);
            sealed = cipher.doFinal(plain);
        } catch (GeneralSecurityException e) {
            throw new ImageCipherException("Chiffrement impossible : " + e.getMessage());
        } finally {
            Arrays.fill(plain, (byte) 0);
        }

        byte[] all = ByteBuffer.allocate(HEADER + sealed.length)
                .put(salt).put(iv).put(dims).put(sealed).array();
        int pixels = (all.length + 2) / 3;
        int outHeight = (pixels + w - 1) / w;
        int[] out = new int[w * outHeight];
        byte[] padding = random(out.length * 3 - all.length);
        for (int i = 0; i < out.length; i++) {
            int r = byteAt(all, padding, i * 3);
            int g = byteAt(all, padding, i * 3 + 1);
            int b = byteAt(all, padding, i * 3 + 2);
            out[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        return new Raster(w, outHeight, out);
    }

    public static Raster decrypt(Raster encrypted, char[] password) {
        requirePassword(password);
        int[] px = encrypted.argb();
        byte[] all = new byte[px.length * 3];
        for (int i = 0; i < px.length; i++) {
            all[i * 3] = (byte) (px[i] >> 16);
            all[i * 3 + 1] = (byte) (px[i] >> 8);
            all[i * 3 + 2] = (byte) px[i];
        }
        if (all.length < HEADER + TAG) {
            throw notEncrypted();
        }
        ByteBuffer header = ByteBuffer.wrap(all, SALT + IV, 8);
        int w = header.getInt();
        int h = header.getInt();
        long sealedLength = w > 0 && h > 0 ? 4L * w * h + TAG : -1;
        long used = HEADER + sealedLength;
        // Le remplissage est strictement inférieur à une ligne d'image : sinon ce n'est pas notre format.
        if (sealedLength < 0 || used > all.length || all.length - used >= 3L * encrypted.width() + 3) {
            throw notEncrypted();
        }
        byte[] salt = Arrays.copyOfRange(all, 0, SALT);
        byte[] iv = Arrays.copyOfRange(all, SALT, SALT + IV);
        byte[] dims = Arrays.copyOfRange(all, SALT + IV, HEADER);
        byte[] sealed = Arrays.copyOfRange(all, HEADER, (int) used);

        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(dims);
            plain = cipher.doFinal(sealed);
        } catch (AEADBadTagException e) {
            throw new ImageCipherException("Mot de passe incorrect, ou image modifiée depuis le chiffrement.");
        } catch (GeneralSecurityException e) {
            throw new ImageCipherException("Déchiffrement impossible : " + e.getMessage());
        }
        int[] out = new int[w * h];
        ByteBuffer.wrap(plain).asIntBuffer().get(out);
        Arrays.fill(plain, (byte) 0);
        return new Raster(w, h, out);
    }

    private static ImageCipherException notEncrypted() {
        return new ImageCipherException("Cette image n'a pas été chiffrée par ce programme.");
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Le mot de passe doit faire au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
    }

    private static int byteAt(byte[] data, byte[] padding, int index) {
        return (index < data.length ? data[index] : padding[index - data.length]) & 0xFF;
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static SecretKeySpec key(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(k, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
