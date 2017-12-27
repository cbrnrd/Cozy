package io.codepace.coffeecoin;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;

import static io.codepace.coffeecoin.Util.getLogger;


/**
 * This class provides all functionality required to pack and sign blocks.
 * In order for a block to be valid on the network, it must include a valid timestamp, blockIndex, prevBlockHash, difficulty, winningNonce, ledgerHash, cert, and be signed by the miner.
 *
 * When Coffeecoind receives a certificate over RPC, it will gather the pool of pending transactions, test each transaction for available balances and such, and attempt to create a block, and send it to the network.
 * Block Format:
 *  {timestamp:blockIndex:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions},{redeemAddress:arbitraryData:maxNonce:authorityName:blockIndex:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex},{blockHash},{minerSignature},{minerSignatureIndex}
 */
public class BlockGenerator {

    public static String compileBlock(long timestamp, int blockIndex, String prevBlockHash, long difficulty, int winningNonce, String ledgerHash, ArrayList<String> txs, Certificate cert, String signingAddress, String privateKey, long minerSigIndex){
        System.out.println(new Timestamp(System.currentTimeMillis())+ " [DAEMON] - " + Util.ANSI_BLUE + "Creating block...");
        System.out.println("\tTimestamp: " + timestamp);
        System.out.println("\tBlock index: " + blockIndex);
        System.out.println("\tPrevious block hash: " + prevBlockHash);
        System.out.println("\tDifficulty: " + difficulty);
        System.out.println("\tWinning nonce: " + winningNonce);
        System.out.println("\tLedger hash: " + ledgerHash);
        System.out.println("\tSigning address: " + signingAddress);
        System.out.println("\tCertificate: " + cert);

        // Safety check
        if(minerSigIndex < 0){
            minerSigIndex = 0;
        }
        System.out.println("\tMiner signature index: " + minerSigIndex);
        System.out.println("\tPrivate key: " + privateKey);
        System.out.print(Util.ANSI_RESET);

        getLogger().log(Level.INFO, "New block created and compiled");

        String block = "{" + timestamp + ":" + blockIndex + ":" + prevBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{";
        String txString = "";
        for (int i = 0; i < txs.size(); i++) {
            if(txs.get(i).length() > 10){
                txString += txs.get(i) + "*";
            }
        }

        if (txString.length() > 1){
            txString = txString.substring(0, txString.length() - 1);
        }

        block += txString + "}," + cert.getFullCertificate();

        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String blockHash = DatatypeConverter.printHexBinary(digest.digest(block.getBytes("UTF-8")));
            block += ",{" + blockHash + "}";
            System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] Pre-block: " + block);
            String sig = new MerkleAddressUtility().getMerkleSignature(block, privateKey, minerSigIndex, signingAddress);
            System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - Signature: " + sig);
            block += ",{" + sig + "},{" + minerSigIndex + "}";
            return block;
        } catch (Exception e){
            getLogger().log(Level.SEVERE, "***Unable to sign a block***");
            System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + Util.ANSI_RED + "Unable to sign a block!" + Util.ANSI_RESET);
            e.printStackTrace();
            return null;
        }

    }

}
