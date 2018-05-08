package io.codepace.cozy;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.util.ArrayList;


/**
 * This class provides all functionality required to pack and sign blocks.
 * In order for a block to be valid on the network, it must include a valid timestamp, blockIndex, prevBlockHash, difficulty, winningNonce, ledgerHash, cert, and be signed by the miner.
 * <p>
 * When Cozyd receives a certificate over RPC, it will gather the pool of pending transactions, test each transaction for available balances and such, and attempt to create a block, and send it to the network.
 * Block Format:
 * {timestamp:blockIndex:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions},{redeemAddress:arbitraryData:maxNonce:authorityName:blockIndex:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex},{blockHash},{minerSignature},{minerSignatureIndex}
 */
public class BlockGenerator {
    /**
     * The parameters are a mouthful. This method takes all of the building blocks (heh....) of a Cozycoin block, and compiles them together into a format that the network will accept.
     * Compiling a block consists of lining up all of the block elements until the block hash, hashing the partial block, appending the blockHash, and signing the entire String.
     * Peers on the network will be able to deconstruct the block in the same process to verify the signature's validity, blockHash, and signature.
     *
     * @param timestamp           Timestamp of block
     * @param blockNum            Number of block to create
     * @param difficulty          Difficulty to place into new block
     * @param winningNonce        The nonce that mines the certificate under the difficulty of the previous block
     * @param ledgerHash          Hash of the current ledger BEFORE transactions held in this block are applied
     * @param transactions        ArrayList<String> containing all transactions in order to be included in the block
     * @param certificate         Certificate used to create the block
     * @param signingAddress      Address to sign with, this should be the same as the redeemAddress of certificate
     * @param privateKey          Private key of signingAddress, used to create the signature
     * @param minerSignatureIndex Index to use for signing the block
     */
    public static String compileBlock(long timestamp, int blockNum, String previousBlockHash, long difficulty, int winningNonce, String ledgerHash, ArrayList<String> transactions, Certificate certificate, String signingAddress, String privateKey, long minerSignatureIndex) {
        System.out.println("Creating block...");
        System.out.println("timestamp: " + timestamp);
        System.out.println("blockNum: " + blockNum);
        System.out.println("previousBlockHash: " + previousBlockHash);
        System.out.println("difficulty: " + difficulty);
        System.out.println("winningNonce: " + winningNonce);
        System.out.println("ledgerHash: " + ledgerHash);
        System.out.println("signingAddress: " + signingAddress);
        System.out.println("Certificate: " + certificate.getFullCertificate());
        if (minerSignatureIndex < 0) {
            minerSignatureIndex = 0;
        }
        System.out.println("minerSignatureIndex: " + minerSignatureIndex);
        System.out.println("privateKey: " + privateKey);
        //This String will be added to a lot.
        String block = "";
        block += "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{";
        String transactionString = "";
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).length() > 10) {
                transactionString += transactions.get(i) + "*";
            }
        }
        if (transactionString.length() > 1) //Otherwise this will throw an indexOutOfBoundsError for blocks with no explicit transactions!
        {
            transactionString = transactionString.substring(0, transactionString.length() - 1);
        }
        block += transactionString + "}," + certificate.getFullCertificate();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String blockHash = DatatypeConverter.printHexBinary(md.digest(block.getBytes("UTF-8")));
            block += ",{" + blockHash + "}";
            System.out.println("pre-block: " + block);
            String signature = new MerkleAddressUtility().getMerkleSignature(block, privateKey, minerSignatureIndex, signingAddress);
            System.out.println("signature: " + signature);
            block += ",{" + signature + "},{" + minerSignatureIndex + "}";
            return block;
        } catch (Exception e) {
            System.out.println("[CRITICAL ERROR] UNABLE TO SIGN A BLOCK!");
            e.printStackTrace();
            return null;
        }
    }
}