package io.codepace.coffeecoin.address;

import java.security.*;


public class LamportGenThread extends Thread
{
    private byte[][] seeds;
    private int count;
    private SecureRandom lmpPrivGen;
    public String[] publicKeys;
    private static final String CS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; //Character set used in Lamport Private Key Parts
    private MessageDigest md;
    private MessageDigest md512;
    private org.apache.commons.codec.binary.Base64 base64 = new org.apache.commons.codec.binary.Base64();
    public LamportGenThread()
    {
        try
        {
            md = MessageDigest.getInstance("SHA-256"); //Initializes md for SHA256 functions to use
            md512 = MessageDigest.getInstance("SHA-512"); //Initializes md512 for SHA-512
        } catch (Exception e)
        {
            System.out.println("CRITICAL ERROR: NO SHA-256 SUPPORT! EXITING APPLICATION");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Sets the 2D-byte-array seeds and count
     *
     * @param seeds 2D byte array of seeds
     * @param count Number of keys per thread to run
     */
    public void setData(byte[][] seeds, int count)
    {
        if (publicKeys == null)
        {
            publicKeys = new String[count];
        }
        this.seeds = seeds;
        this.count = count;
    }
    /**
     * Returns the public keys of the Lamport Signature pair
     *
     * @return String[] Public keys
     */
    public String[] getPublicKeys()
    {
        return publicKeys;
    }

    public void run()
    {
        try
        {
            for (int i = 0; i < count; i++)
            {
                publicKeys[i] = SHA256(getLamportPublicKey(seeds[i]));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Yeah, it's ugly. This is a manual unroll of the loops required to generate a Lamport Public Key. It used to be very pretty. This is 2x as fast.
     * The ugly code is worth the speedup.
     * This method takes a seed, creates a SecureRandom seeded with the input seed, and then directly generates the public key, without ever storing the
     * private key, as that is unnecessary. Each Lamport Private Key Part is 20 psuedo-random (from seeded SecureRandom) characters. There are 200
     * of these, to support signing a 100-bit message.
     * The Lamport Public Key is the 200 SHA256Short hashes of the 200 Lamport Private Keys concatenated together in order.
     * The Lamport Public Keys returned from calling this method many times build the bottom layer of the Merkle Tree.
     * Uses SHA256Short in order to reduce the total size of a 200-part Lamport Public Key down to reduce the size of the blockchain.
     *
     * @param seed The byte array seed for the desired Lamport Signature Keypair
     *
     * @return String This is the Public key of the Lamport Signature defined by byte[] seed
     */
    private String getLamportPublicKey(byte[] seed)
    {
        try
        {
            lmpPrivGen = SecureRandom.getInstance("SHA1PRNG");
        } catch (Exception e)
        {
            System.out.println("ERROR GETTING SecureRandom OBJECT!");
            e.printStackTrace();
        }
        lmpPrivGen.setSeed(seed);
        return SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) +
                SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA256Short(getLamportPrivateKey()) + SHA512(getLamportPrivateKey()) + SHA512(getLamportPrivateKey());
    }

    /**
     * This method uses the lmpPrivGen object to generate the next Lamport Private Key part. Each Lamport Private Key Part is 20 psuedo-random characters.
     *
     * @return String The next 20-character Lamport Private Key part.
     */
    private String getLamportPrivateKey()
    {
        int len = CS.length();
        return "" + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) +
                CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) +
                CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) +
                CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len));
    }

    /**
     * This SHA256 function returns a 16-character, base64 String. The String is shortened to reduce space on the blockchain, and is sufficiently long for security purposes.
     *
     * @param toHash The String to hash using SHA256
     *
     * @return String The 16-character base64 String resulting from hashing toHash and truncating
     */
    private String SHA256Short(String toHash) //Each hash is shortened to 16 characters based on a 64-character charset. 64^16=79,228,162,514,264,337,593,543,950,336 (Aka more than enough for Lamport)
    {
        try
        {
            return base64.encodeAsString(md.digest(toHash.getBytes("UTF-8"))).substring(0, 16);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This SHA256 function returns a base64 String repesenting the full SHA256 hash of the String toHash
     * The full-length SHA256 hashes are used for the non-Lamport and non-Address levels of the Merkle Tree
     *
     * @param toHash The String to hash using SHA256
     *
     * @return String the base64 String representing the entire SHA256 hash of toHash
     */
    private String SHA256(String toHash)
    {
        try
        {
            return base64.encodeAsString(md.digest(toHash.getBytes("UTF-8")));
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This SHA512 function returns the full-length SHA512 hash of the String toHash.
     * SHA512 is used for the last 2 elements of the Lamport Signature, in order to require any attacker to break one SHA512 hash if they were to crack a Lamport Public Key.
     *
     * @param toHash The String to hash using SHA512
     *
     * @return String the 128-character base64 String resulting from hashing toHash
     */
    private String SHA512(String toHash)
    {
        try
        {
            return base64.encodeAsString(md512.digest(toHash.getBytes("UTF-8")));
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }
}