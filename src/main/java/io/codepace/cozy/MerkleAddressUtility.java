package io.codepace.cozy;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Scanner;

/**
 * This class provides all methods necessary to use an address after it has been generated.
 */
public class MerkleAddressUtility
{
    private static final String CS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; //Character set used in Lamport Private Key Parts
    private static org.apache.commons.codec.binary.Base32 base32 = new org.apache.commons.codec.binary.Base32();
    private static org.apache.commons.codec.binary.Base64 base64 = new org.apache.commons.codec.binary.Base64();
    private MessageDigest md;
    private MessageDigest md512;
    private static final boolean verboseMode = false;

    public static void main(String[] args) //Another test method to be removed, currently blank
    {
    }

    /**
     * Constructor readies the MessageDigest md object to compute SHA-256 hashes, and ensures existance
     * of address folder for storing the Merkle Trees. Also checks for availability of SHA1PRNG.
     */
    public MerkleAddressUtility()
    {
        try
        {
            SecureRandom.getInstance("SHA1PRNG");
        } catch (Exception e)
        {
            System.out.println("CRITICAL ERROR: NO SHA1PRNG SUPPORT! EXITING APPLICATION");
        }
        try
        {
            md = MessageDigest.getInstance("SHA-256");
            md512 = MessageDigest.getInstance("SHA-512");
        } catch (Exception e)
        {
            System.out.println("CRITICAL ERROR: NO SHA-256 SUPPORT! EXITING APPLICATION");
            e.printStackTrace();
            System.exit(-1);
        }
        try
        {
            File addressFolder = new File("addresses");
            if (!addressFolder.exists())
            {
                addressFolder.mkdir();
            }
        } catch (Exception e)
        {
            System.out.println("CRITICAL ERROR: UNABLE TO CREATE FOLDER FOR ADDRESS STORAGE! EXITING APPLICATION");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * This method will verify that the supplied address signed the supplied message to generate the supplied signature.
     *
     * @param message The message of which to verify the signature
     * @param signature The signature to verify
     * @param address The address to check the signature against
     * @param index The index of the Lamport Keypair used (position on bottom of Merkle tree)
     *
     * @return boolean Whether the message was signed by the provided address using the provided index
     */
    public boolean verifyMerkleSignature(String message, String signature, String address, long index)
    {
        try
        {
            String lamportSignature = signature.substring(0, signature.indexOf(","));
            String merkleAuthPath = signature.substring(signature.indexOf(",") + 1);
            //Holds 100 pairs, each pair containing one public and one private Lamport Key part
            String[] lamportSignaturePairs = lamportSignature.split("::");
            //Will hold all 200 parts in the same order as they appear in lamportSignaturePairs
            String[] lamportSignatureParts = new String[200];
            //Populate lamportSignatureParts from lamportSignaturePairs
            for (int i = 0; i < lamportSignaturePairs.length; i++)
            {
                lamportSignatureParts[i*2] = lamportSignaturePairs[i].substring(0, lamportSignaturePairs[i].indexOf(":"));
                lamportSignatureParts[i*2+1] = lamportSignaturePairs[i].substring(lamportSignaturePairs[i].indexOf(":") + 1);
            }
            //Lamport Signatures work with binary, so we need a binary string representing the hash of the message we want verify the signature of
            String binaryToCheck = SHA256Binary(message);
            //Cozycoin Lamport Signatures sign the first 100 bytes of the hash. To generate a message colliding with the signature, one would need on average 2^99 tries
            binaryToCheck = binaryToCheck.substring(0, 100);
            //The Lamport Public Key will be 200 Strings in size
            String[] lamportPublicKey = new String[200];
            for (int i = 0; i < binaryToCheck.length(); i++)
            {
                if (binaryToCheck.charAt(i) == '0')
                {
                    if (i < binaryToCheck.length() - 1) //Part of the SHA256Short group. This logic could be shortened slightly by moving one of the assignments out of these two branches--not done for clarity.
                    {
                        lamportPublicKey[i*2] = SHA256Short(lamportSignatureParts[i*2]);
                        lamportPublicKey[i*2+1] = lamportSignatureParts[i*2+1];
                    }
                    else //This one is the last pair, and needs to be hashed with SHA512
                    {
                        lamportPublicKey[i*2] = SHA512(lamportSignatureParts[i*2]);
                        lamportPublicKey[i*2+1] = lamportSignatureParts[i*2+1];
                    }
                }
                else if (binaryToCheck.charAt(i) == '1')
                {
                    if (i < binaryToCheck.length() - 1) //Part of the SHA256Short group. This logic could be shortened slightly by moving one of the assignments out of these two branches--not done for clarity.
                    {
                        lamportPublicKey[i*2] = lamportSignatureParts[i*2];
                        lamportPublicKey[i*2+1] = SHA256Short(lamportSignatureParts[i*2+1]);
                    }
                    else //This one is the last pair, and needs to be hashed with SHA512
                    {
                        lamportPublicKey[i*2] = lamportSignatureParts[i*2];
                        lamportPublicKey[i*2+1] = SHA512(lamportSignatureParts[i*2+1]);
                    }
                }
                else
                {
                    return false;
                }
            }
            String lamportPublicSignatureFull = "";
            //Populate full String to hash to get first leaf component
            for (int i = 0; i < lamportPublicKey.length; i++)
            {
                lamportPublicSignatureFull += lamportPublicKey[i];
            }
            if (verboseMode)
            {
                System.out.println("lmpSig: " + lamportPublicSignatureFull);
            }
            //First leaf component; bottom layer of Merkle Tree
            String leafStart = SHA256(lamportPublicSignatureFull);
            //Split on : in order to get the auth paths into a String array
            String[] merkleAuthPathComponents = merkleAuthPath.split(":");
            //This position variable will store where on the tree we are. Important for order of concatenation: rollingHash first, or Component first
            long position = index;
            //This rollingHash will contain the hash as we calculate up the hash tree
            String rollingHash;
            if (position % 2 == 0) //Even; rollingHash goes first
            {
                rollingHash = SHA256(leafStart + merkleAuthPathComponents[0]);
            }
            else //Odd; path component should go first
            {
                rollingHash = SHA256(merkleAuthPathComponents[0] + leafStart);
            }
            position /= 2;
            for (int i = 1; i < merkleAuthPathComponents.length - 1; i++) //Go to merkleAuthPathComponents.length - 1 because the final hash is returned in base32 and is truncated
            {
                //Combine the current hash with the next component, which visually would lie on the same Merkle Tree layer
                if (position % 2 == 0) //Even; rollingHash goes first
                {
                    rollingHash = SHA256(rollingHash + merkleAuthPathComponents[i]);
                }
                else //Odd; path component should go first
                {
                    rollingHash = SHA256(merkleAuthPathComponents[i] + rollingHash);
                }
                if (verboseMode)
                {
                    System.out.println("rollingHash: " + rollingHash + " and auth component: " + merkleAuthPathComponents[i]);
                }
                position /= 2;
            }
            //Final hash, done differently for formatting of address (base32, set length of 32 characters for the top of the Merkle Tree)
            if (position % 2 == 0) //Even; rollingHash goes first
            {
                rollingHash = SHA256ReturnBase32(rollingHash + merkleAuthPathComponents[merkleAuthPathComponents.length - 1]);
            }
            else //Odd; path component should go first
            {
                rollingHash = SHA256ReturnBase32(merkleAuthPathComponents[merkleAuthPathComponents.length - 1] + rollingHash);
            }
            if (address.substring(2, address.length() - 4).equals(rollingHash)) //Remove the prefix and hash suffix
            {
                return true; //Address matches, so signature is legitimate!
            }
        } catch (Exception e)
        {
            e.printStackTrace();
            return false; //Some form of error was triggered, signature was likely malformed (wrong length)
        }
        return false; //Fell through at some point, likely the address didn't match
    }

    /**
     * This method will completely sign a message using the privateKey and Lamport Keypair Index supplied.
     *
     * @param message The message to sign
     * @param privateKey The original private key
     * @param index The index of the Lamport Keypair to sign with
     * @param address The Cozycoin 0.2.0 address
     *
     * @return String The Merkle Signature consisting of a 200-part Lamport Signature along with the authentication path up the Merkle Tree
     */
    public String getMerkleSignature(String message, String privateKey, long index, String address)
    {
        File infoFile = new File("addresses/" + address + "/info.dta");
        if (!infoFile.exists())
        {
            return null;
        }
        //Lamport Signatures work with binary, so we need a binary string representing the hash of the message we want to sign
        String binaryToSign = SHA256Binary(message);
        //Cozycoin Lamport Signatures sign the first 100 bytes of the hash. To generate a message colliding with the signature, one would need on average 2^99 tries
        binaryToSign = binaryToSign.substring(0, 100);
        //The 200 Lamport Private Key Parts, 100 of which will appear as-is in the final signature
        String[] lamportPrivateKeyParts = getLamportPrivateKeyParts(privateKey, index);
        String lamportSignature = "";
        for (int i = 0; i < binaryToSign.length(); i++) //Add a public and private key part to signature for each digit of signable binary
        {
            if (binaryToSign.charAt(i) == '0') //A zero means we reveal the first private key
            {
                if (i == binaryToSign.length() - 1) //If it is part of the last pair, we want to use SHA512 (Full Length)
                {
                    lamportSignature += lamportPrivateKeyParts[i*2] + ":" + SHA512(lamportPrivateKeyParts[i*2+1]);
                }
                else
                {
                    lamportSignature += lamportPrivateKeyParts[i*2] + ":" + SHA256Short(lamportPrivateKeyParts[i*2+1]);
                }
            }
            else if (binaryToSign.charAt(i) == '1')//A one means we reveal the second private key
            {
                if (i == binaryToSign.length() - 1) //If it is part of the last pair, we want to use SHA512 (Full Length)
                {
                    lamportSignature += SHA512(lamportPrivateKeyParts[i*2]) + ":" + lamportPrivateKeyParts[i*2+1];
                }
                else //If it is any one of the other previous pairs, use SHA256Short
                {
                    lamportSignature += SHA256Short(lamportPrivateKeyParts[i*2]) + ":" + lamportPrivateKeyParts[i*2+1];
                }
            }
            else //Something has gone terribly wrong, our binary string isn't made of binary.
            {
                System.out.println("CRITICAL ERROR: BINARY STRING IS NOT BINARY");
                System.exit(-4);
            }
            if (i < binaryToSign.length() - 1) //Add a double-colon separator between pairs
            {
                lamportSignature += "::";
            }
        }
        //Now we need to get the authentication path
        String merklePath = "";
        int layers = -1;
        try
        {
            Scanner scanAddressInfo = new Scanner(infoFile);
            while (scanAddressInfo.hasNextLine())
            {
                String input = scanAddressInfo.nextLine();
                if (input.contains("layers: "))
                {
                    String layerTemp = input.substring(input.indexOf("layers: ") + 8);
                    if (isInteger(layerTemp))
                    {
                        layers = Integer.parseInt(layerTemp);
                    }
                }
            }
            scanAddressInfo.close();
        } catch (Exception e)
        {
            System.out.println("ERROR: UNABLE TO READ INFORMATION FILE FOR ADDRESS " + address + "!");
            e.printStackTrace();
            return null;
        }
        if (layers == -1)
        {
            return null;
        }
        long[] authPathIndexes = getAuthenticationPathIndexes(index, layers);
        for (int i = 0; i < authPathIndexes.length; i++)
        {
            try
            {
                File layerFile = new File("addresses/" + address + "/layer" + i + ".lyr");
                Scanner readLayerFile = new Scanner(layerFile);
                for (long j = 0; j < authPathIndexes[i]; j++) //Consume authPathIndexes[i] of components to get to the one we want
                {
                    readLayerFile.nextLine(); //Nom-nom
                }
                if (verboseMode)
                {
                    System.out.println("Consumed " + authPathIndexes[i] + " from layer " + i + ".");
                }
                String layerData = readLayerFile.nextLine();
                readLayerFile.close();
                if (verboseMode)
                {
                    System.out.println("We think the " + (authPathIndexes[i]) + "th index is " + layerData + ".");
                }
                merklePath += layerData; //readLayerFile.nextLine() will now return the correct hash
                if (i < authPathIndexes.length - 1) //We want all elements in merklePath to be separated by a colon
                {
                    merklePath += ":";
                }
            } catch (Exception e)
            {
                System.out.println("ERROR: UNABLE TO READ ABOUT LAYER " + i + " FROM ADDRESS " + address);
                e.printStackTrace();
                return null;
            }
        }
        return lamportSignature + "," + merklePath;
    }

    /**
     * This method is useful when reading integers from configuration files.
     *
     * @param toTest String to test integer-ness of
     *
     * @return Whether the number is an integer or not
     */
    public boolean isInteger(String toTest)
    {
        try
        {
            Integer.parseInt(toTest);
            return true;
        } catch (Exception e)
        {
            return false;
        }
    }

    /**
     * This method returns a Long array of the required authentication path's locations.
     * The authentication path represents, starting at layer 0, what element of each layer must be revealed to allow peers to verify
     * The signature as legitimate.
     *
     * @param startingIndex The index of the Lamport Keypair used
     * @param layers The number of layers in the tree
     *
     * @return long[] Long array holding the authentication path indexes generated from a given startingIndex and moving up for a given number of layers.
     */
    public static long[] getAuthenticationPathIndexes(long startingIndex, int layers)
    {
        //Top layer will always be the address, no need to return this part, so only need layers-1 total layers.
        long[] authPath = new long[layers - 1];
        long workingIndex = startingIndex;
        for (int i = 0; i < layers - 1; i++)
        {
            if (workingIndex % 2 == 0) //workingIndex is even
            {
                authPath[i] = workingIndex + 1;
            }
            else //workingIndex is odd
            {
                authPath[i] = workingIndex - 1;
            }
            workingIndex /= 2;
        }
        return authPath;
    }

    /**
     * This method uses an original private key for an address, and returns the Lamport private key (capable of signing a 100-bit mesasge) from the spot defined by index
     * with each element separated by a colon.
     *
     * @param privateKey The original private key of the Cozycoin address in question
     * @param index The index of the Lamport Signature (bottom layer of Merkle Tree) to return
     *
     * @return String[] A String[] containing the 200 Lamport Private Key Parts
     */
    public String[] getLamportPrivateKeyParts(String privateKey, long index)
    {
        try
        {
            String[] privateKeyParts = new String[200]; //Holds 200 Private Key Parts, to sign a 100-bit message
            //Need to create a seeded SecureRandom so we can acquire the private seed for the desired Lamport Keypair
            SecureRandom generatePrivateSeeds = SecureRandom.getInstance("SHA1PRNG");
            generatePrivateSeeds.setSeed(privateKey.getBytes());
            //Will loop through filling privateSeed until we reach the correct index
            byte[] privateSeed = new byte[100];
            for (int i = 0; i <= index; i++)
            {
                generatePrivateSeeds.nextBytes(privateSeed);
            }
            //lmpPrivGen seeded with the above-found privateSeed, and then used by getLamportPrivateKey
            SecureRandom lmpPrivGen = SecureRandom.getInstance("SHA1PRNG");
            lmpPrivGen.setSeed(privateSeed);
            for (int i = 0; i < 200; i++) //Can sign a 100-bit message with 200 private key parts
            {
                privateKeyParts[i] = getLamportPrivateKeyPart(lmpPrivGen);
            }
            return privateKeyParts;
        } catch (Exception e)
        {
            System.out.println("CRITICAL ERROR: UNABLE TO GENERATE LAMPORT PRIVATE KEY PARTS");
            e.printStackTrace();
            System.exit(-2);
        }
        return null;
    }

    /**
     * This method generates a 20-character String for Lamport Keypairs from the SecureRandom object passed to it, pulling characters for the String from the alphanumeric global String CS
     *
     * @param lmpPrivGen the SecureRandom seeded with the proper Lamport Private Key
     *
     * @return String One Private Key Part for the Lamport Private Key
     */
    private String getLamportPrivateKeyPart(SecureRandom lmpPrivGen)
    {
        int len = CS.length();
        //It's ugly. I know. A loop would be prettier, but in benchmarks this is slightly faster. And for something that's gonna be run billions of times, that's important.
        //For a 14-layer address, this method is called 200*(2^13) times. That's 1,638,400 times. That's a lot.
        return "" + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) +
                CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) +
                CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) +
                CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len)) + CS.charAt(lmpPrivGen.nextInt(len));
    }

    /**
     * This method checks an address to ensure proper formatting.
     * Cozycoin address format: Prefix + TreeRoot + VerificationHash
     * Prefix can be C1, C2, C3, C4, or C5. Any address starting with A1 is a special address used by signature authorities.
     * C1 means 14 layer, C2 means 15 layer, C3 means 16 layer, C4 means 17 layer, C5 means 18 layer.
     * TreeRoot is an all-caps Base32 32-character-long SHA256 hash that represents the top of the Merkle Tree for the respective address.
     * VerificationHash is the first four digits of the Base32 SHA256 hash of TreeRoot, also in caps.
     *
     * @param address The address to test for validity
     *
     * @return boolean Whether the address is formatted correctly
     */
    public boolean isAddressFormattedCorrectly(String address)
    {
        try
        {
            String prefix = address.substring(0, 2); //Prefix is 2 characters long
            if (!prefix.equals("C1") && !prefix.equals("C2") && !prefix.equals("C3") && !prefix.equals("C4") && !prefix.equals("C5"))
            {
                return false;
            }
            String treeRoot = address.substring(2, 34); //32 characters long. Should be all-caps Base32
            String characterSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"; //Normal Base32 character set. All upper case! Omission of 1 is normal. :)
            for (int i = 0; i < treeRoot.length(); i++)
            {
                if (!characterSet.contains(treeRoot.charAt(i) + ""))
                {
                    return false;
                }
            }
            String givenEnding = address.substring(34); //Characters 34 to 37 should be all that's left. Remember we start counting at 0.
            String correctEnding = SHA256ReturnBase32(prefix + treeRoot).substring(0, 4); //First four characters of Base32-formatted SHA256 of treeRoot
            if (!correctEnding.equals(givenEnding))
            {
                return false;
            }
            return true; //We didn't return false for a failure, it must be valid!
        } catch (Exception e) //Not printing exceptions or logging them on purpose. Any time an address too short is passed in, this will snag it.
        {
            return false;
        }
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
     * This SHA256 function returns a 256-character binary String representing the full SHA256 hash of the String toHash
     * This binary String is useful when signing a message with a Lamport Signature.
     *
     * @param toHash The String to hash using SHA256
     *
     * @return String The binary String representing the entire SHA256 hash of toHash
     */
    private String SHA256Binary(String toHash)
    {
        try
        {
            byte[] messageHash = md.digest(toHash.getBytes("UTF-8"));
            return new BigInteger(1, messageHash).toString(2);
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
     * Used for the generation of an address. Base32 is more practical for real-world addresses, due to a more convenient ASCII charset.
     * Shortened to 32 characters, as that provides 32^32=1,461,501,637,330,902,918,203,684,832,716,283,019,655,932,542,976 possible addresses.
     *
     * @param toHash The String to hash using SHA256
     *
     * @return String the base32-encoded String representing the entire SHA256 hash of toHash
     */
    private String SHA256ReturnBase32(String toHash)
    {
        try
        {
            return base32.encodeAsString(md.digest(toHash.getBytes("UTF-8"))).substring(0, 32);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }
}
