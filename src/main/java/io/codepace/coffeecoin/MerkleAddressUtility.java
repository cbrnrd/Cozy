package io.codepace.coffeecoin;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Scanner;

import static io.codepace.coffeecoin.Util.*;

public class MerkleAddressUtility {
    private static final String C = RandomString.alphanum;
    private static org.apache.commons.codec.binary.Base32 base32 = new org.apache.commons.codec.binary.Base32();
    private static org.apache.commons.codec.binary.Base64 base64 = new org.apache.commons.codec.binary.Base64();
    private MessageDigest md;
    private MessageDigest md512;
    private static final boolean verbose = false;

    public MerkleAddressUtility(){
        try{
            SecureRandom.getInstance("SHA1PRNG");
        } catch (Exception e){
            System.out.println(ANSI_RED + "No SHA1PRNG support! Exiting." + ANSI_RESET);
            System.exit(-1);
        }
        try{
            md = MessageDigest.getInstance("SHA-256");
            md512 = MessageDigest.getInstance("SHA-512");
        } catch (Exception e){
            System.out.println(ANSI_RED + "No SHA-256/512 support. Exiting." + ANSI_RESET);
            e.printStackTrace();
            System.exit(-1);
        }
        try{
            File addrFolder = new File("addresses");
            if(!addrFolder.exists()){
                addrFolder.mkdir();
            }
        } catch (Exception e){
            System.out.println(ANSI_RED + "Unable to create folder for address storage. Exiting." + ANSI_RESET);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public boolean verifyMerkleSignature(String message, String signature, String address, long index){
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
            //Coffeecoin Lamport Signatures sign the first 100 bytes of the hash. To generate a message colliding with the signature, one would need on average 2^99 tries
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
            if (verbose)
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
                if (verbose)
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

    public String getMerkleSignature(String message, String privateKey, long index, String address){
        File infoFile = new File("addresses/" + address + "/info.dta");
        if (!infoFile.exists())
        {
            return null;
        }
        //Lamport Signatures work with binary, so we need a binary string representing the hash of the message we want to sign
        String binaryToSign = SHA256Binary(message);
        //Curecoin Lamport Signatures sign the first 100 bytes of the hash. To generate a message colliding with the signature, one would need on average 2^99 tries
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
                if (verbose)
                {
                    System.out.println("Consumed " + authPathIndexes[i] + " from layer " + i + ".");
                }
                String layerData = readLayerFile.nextLine();
                readLayerFile.close();
                if (verbose)
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

    public static long[] getAuthenticationPathIndexes(long start, int layers){
        long[] authPath = new long[layers - 1];
        long workingIndex = start;
        for (int i = 0; i < layers; i++) {
            if (workingIndex % 2 == 0) {
                authPath[i] = workingIndex + 1;
            } else {
                authPath[i] = workingIndex - 1;
            }
            workingIndex /= 2;
        }
        return authPath;
    }

    public String[] getLamportPrivateKeyParts(String privateKey, long index){
        try{
            String[] privateKeyParts = new String[200];
            SecureRandom generatePrivateSeeds = SecureRandom.getInstance("SHA1PRNG");
            generatePrivateSeeds.setSeed(privateKey.getBytes());
            // Loop through filling privateSeed until we reach the right index
            byte[] privateSeed = new byte[100];
            for (int i = 0; i <= index; i++) {
                generatePrivateSeeds.nextBytes(privateSeed);
            }
            SecureRandom lmpPrivGen = SecureRandom.getInstance("SHA1PRNG");
            lmpPrivGen.setSeed(privateSeed);
            for (int i = 0; i < 200; i++) {
                privateKeyParts[i] = getLamportPrivateKeyPart(lmpPrivGen);
            }
            return privateKeyParts;
        } catch (Exception e){
            logInfoAndPrint("Unable to generate lamport key parts. Exiting.");
            e.printStackTrace();
            System.exit(-2);
        }
        return null;
    }

    private String getLamportPrivateKeyPart(SecureRandom lmpPrivGen){
        int len = C.length();
        // My god it's ugly but it is faster than a loop. This will run A LOT OF TIMES (in the billions)
        //For a 14-layer address, this method is called 200*(2^13) times. That's 1,638,400 times. That's a lot, so every milisecond counts.
        return "" + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) +
                C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) +
                C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) +
                C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len)) + C.charAt(lmpPrivGen.nextInt(len));
    }

    /**
     * This method checks an address to ensure proper formatting.
     * Coffeecoin address format: Prefix + TreeRoot + VerificationHash
     * Prefix is CF(1-5). Any address starting with A1 is a special address used by signature authorities.
     * CF1 means 14 layer, CF2 means 15 layer, CF3 means 16 layer, CF4 means 17 layer, CF5 means 18 layer.
     * TreeRoot is an all-caps Base32 32-character-long SHA256 hash that represents the top of the Merkle Tree for the respective address.
     * VerificationHash is the first four digits of the Base32 SHA256 hash of TreeRoot, also in caps.
     *
     * @param addr The address to test for validity
     *
     * @return boolean Whether the address is formatted correctly
     */
    public boolean isAddressFormattedCorrectly(String addr){
        try{
            String prefix = addr.substring(0, 3); // Prefix is at the first 3 chars of the address
            if(!prefix.equals("CF1") && !prefix.equals("CF2") && !prefix.equals("CF3") && !prefix.equals("CF4") && !prefix.equals("CF5")){
                return false;
            }
            String treeRoot = addr.substring(3, 35); // 32 chars long. All caps
            String charset = RandomString.upper + "234567";
            for (int i = 0; i < treeRoot.length(); i++) {
                if(!charset.contains(treeRoot.charAt(i) + "")){
                    return false;
                }
            }
            String givenEnding = addr.substring(35);
            String correctEnding = SHA256ReturnBase32(prefix + treeRoot).substring(0, 4);
            if(!correctEnding.equals(givenEnding)){
                return false;
            }
            return true;
        } catch (Exception e){
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

