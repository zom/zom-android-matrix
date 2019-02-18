package info.guardianproject.keanu.matrix.plugin;

import android.text.TextUtils;
import android.util.Base64;

import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileKey;
import org.matrix.androidsdk.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KeanuMXEncryptedAttachments implements Serializable {
    private static final String LOG_TAG = org.matrix.androidsdk.crypto.MXEncryptedAttachments.class.getSimpleName();
    private static final int CRYPTO_BUFFER_SIZE = 32768;
    private static final String CIPHER_ALGORITHM = "AES/CTR/NoPadding";
    private static final String SECRET_KEY_SPEC_ALGORITHM = "AES";
    private static final String MESSAGE_DIGEST_ALGORITHM = "SHA-256";

    public KeanuMXEncryptedAttachments() {
    }

    public static MXEncryptedAttachments.EncryptionResult encryptAttachment(InputStream attachmentStream, String mimetype, OutputStream outStream) {
        long t0 = System.currentTimeMillis();
        SecureRandom secureRandom = new SecureRandom();
        byte[] initVectorBytes = new byte[16];
        Arrays.fill(initVectorBytes, (byte)0);
        byte[] ivRandomPart = new byte[8];
        secureRandom.nextBytes(ivRandomPart);
        System.arraycopy(ivRandomPart, 0, initVectorBytes, 0, ivRandomPart.length);
        byte[] key = new byte[32];
        secureRandom.nextBytes(key);

        try {
            Cipher encryptCipher = Cipher.getInstance("AES/CTR/NoPadding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(initVectorBytes);
            encryptCipher.init(1, secretKeySpec, ivParameterSpec);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] data = new byte['耀'];

            int read;
            byte[] encodedBytes;
            while(-1 != (read = attachmentStream.read(data))) {
                encodedBytes = encryptCipher.update(data, 0, read);
                messageDigest.update(encodedBytes, 0, encodedBytes.length);
                outStream.write(encodedBytes);
            }

            encodedBytes = encryptCipher.doFinal();
            messageDigest.update(encodedBytes, 0, encodedBytes.length);
            outStream.write(encodedBytes);
            outStream.flush();
            outStream.close();

            org.matrix.androidsdk.crypto.MXEncryptedAttachments.EncryptionResult result = new org.matrix.androidsdk.crypto.MXEncryptedAttachments.EncryptionResult();
            result.mEncryptedFileInfo = new EncryptedFileInfo();
            result.mEncryptedFileInfo.key = new EncryptedFileKey();
            result.mEncryptedFileInfo.mimetype = mimetype;
            result.mEncryptedFileInfo.key.alg = "A256CTR";
            result.mEncryptedFileInfo.key.ext = true;
            result.mEncryptedFileInfo.key.key_ops = Arrays.asList("encrypt", "decrypt");
            result.mEncryptedFileInfo.key.kty = "oct";
            result.mEncryptedFileInfo.key.k = base64ToBase64Url(Base64.encodeToString(key, 0));
            result.mEncryptedFileInfo.iv = Base64.encodeToString(initVectorBytes, 0).replace("\n", "").replace("=", "");
            result.mEncryptedFileInfo.v = "v2";
            result.mEncryptedFileInfo.hashes = new HashMap();
            result.mEncryptedFileInfo.hashes.put("sha256", base64ToUnpaddedBase64(Base64.encodeToString(messageDigest.digest(), 0)));
        //    result.mEncryptedStream = new ByteArrayInputStream(outStream.toByteArray());
            outStream.close();
            Log.d(LOG_TAG, "Encrypt in " + (System.currentTimeMillis() - t0) + " ms");
            return result;
        } catch (OutOfMemoryError var18) {
            Log.e(LOG_TAG, "## encryptAttachment failed " + var18.getMessage(), var18);
        } catch (Exception var19) {
            Log.e(LOG_TAG, "## encryptAttachment failed " + var19.getMessage(), var19);
        }

        try {
            outStream.close();
        } catch (Exception var17) {
            Log.e(LOG_TAG, "## encryptAttachment() : fail to close outStream", var17);
        }

        return null;
    }

    public static InputStream decryptAttachment(InputStream attachmentStream, EncryptedFileInfo encryptedFileInfo) {
        if (null != attachmentStream && null != encryptedFileInfo) {
            if (!TextUtils.isEmpty(encryptedFileInfo.iv) && null != encryptedFileInfo.key && null != encryptedFileInfo.hashes && encryptedFileInfo.hashes.containsKey("sha256")) {
                if (TextUtils.equals(encryptedFileInfo.key.alg, "A256CTR") && TextUtils.equals(encryptedFileInfo.key.kty, "oct") && !TextUtils.isEmpty(encryptedFileInfo.key.k)) {
                    try {
                        if (0 == attachmentStream.available()) {
                            return new ByteArrayInputStream(new byte[0]);
                        }
                    } catch (Exception var17) {
                        Log.e(LOG_TAG, "Fail to retrieve the file size", var17);
                    }

                    long t0 = System.currentTimeMillis();
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();

                    try {
                        byte[] key = Base64.decode(base64UrlToBase64(encryptedFileInfo.key.k), 0);
                        byte[] initVectorBytes = Base64.decode(encryptedFileInfo.iv, 0);
                        Cipher decryptCipher = Cipher.getInstance("AES/CTR/NoPadding");
                        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
                        IvParameterSpec ivParameterSpec = new IvParameterSpec(initVectorBytes);
                        decryptCipher.init(2, secretKeySpec, ivParameterSpec);
                        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                        byte[] data = new byte['耀'];

                        int read;
                        byte[] decodedBytes;
                        while(-1 != (read = attachmentStream.read(data))) {
                            messageDigest.update(data, 0, read);
                            decodedBytes = decryptCipher.update(data, 0, read);
                            outStream.write(decodedBytes);
                        }

                        decodedBytes = decryptCipher.doFinal();
                        outStream.write(decodedBytes);
                        String currentDigestValue = base64ToUnpaddedBase64(Base64.encodeToString(messageDigest.digest(), 0));
                        if (!TextUtils.equals((CharSequence)encryptedFileInfo.hashes.get("sha256"), currentDigestValue)) {
                            Log.e(LOG_TAG, "## decryptAttachment() :  Digest value mismatch");
                            outStream.close();
                            return null;
                        }

                        InputStream decryptedStream = new ByteArrayInputStream(outStream.toByteArray());
                        outStream.close();
                        Log.d(LOG_TAG, "Decrypt in " + (System.currentTimeMillis() - t0) + " ms");
                        return decryptedStream;
                    } catch (OutOfMemoryError var18) {
                        Log.e(LOG_TAG, "## decryptAttachment() :  failed " + var18.getMessage(), var18);
                    } catch (Exception var19) {
                        Log.e(LOG_TAG, "## decryptAttachment() :  failed " + var19.getMessage(), var19);
                    }

                    try {
                        outStream.close();
                    } catch (Exception var16) {
                        Log.e(LOG_TAG, "## decryptAttachment() :  fail to close the file", var16);
                    }

                    return null;
                } else {
                    Log.e(LOG_TAG, "## decryptAttachment() : invalid key fields");
                    return null;
                }
            } else {
                Log.e(LOG_TAG, "## decryptAttachment() : some fields are not defined");
                return null;
            }
        } else {
            Log.e(LOG_TAG, "## decryptAttachment() : null parameters");
            return null;
        }
    }

    private static String base64UrlToBase64(String base64Url) {
        if (null != base64Url) {
            base64Url = base64Url.replaceAll("-", "+");
            base64Url = base64Url.replaceAll("_", "/");
        }

        return base64Url;
    }

    private static String base64ToBase64Url(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("\n", "");
            base64 = base64.replaceAll("\\+", "-");
            base64 = base64.replaceAll("/", "_");
            base64 = base64.replaceAll("=", "");
        }

        return base64;
    }

    private static String base64ToUnpaddedBase64(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("\n", "");
            base64 = base64.replaceAll("=", "");
        }

        return base64;
    }


}
