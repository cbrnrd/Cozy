package io.codepace.cozy.db;

import io.codepace.cozy.Certificate;
import io.codepace.cozy.MerkleAddressUtility;
import io.codepace.cozy.Util;

import java.security.MessageDigest;
import java.util.ArrayList;
import javax.xml.bind.DatatypeConverter;

/**
 * This class provides all functionality related to block usage and block verification.
 * <p>
 * PROTOCOL RULE:
 * <p>
 * A block contains:
 * - A Timestamp (Unix epoch)
 * - Block number (index)
 * - The hash of the previous block
 * - The Certificate
 * - Difficulty
 * - Winning nonce
 * - List of transactions
 */
public class Block {

    public long timestamp;
    public int blockIndex;
    public String prevBlockHash;
    public String blockHash;
    public Certificate cert;
    public long difficulty;
    public int winningNonce;
    public String ledgerHash;
    public ArrayList<String> txs;
    public String minerSig;
    public long minerSigIndex;

    /**
     * TODO: docs
     */
    public Block(long timestamp, int blockIndex, String prevBlockHash, Certificate cert, long difficulty, int winningNonce, String ledgerHash, ArrayList<String> txs, String minerSig, int minerSigIndex) {
        this.timestamp = timestamp;
        this.blockIndex = blockIndex;
        this.prevBlockHash = prevBlockHash;
        this.cert = cert;
        this.difficulty = difficulty;
        this.winningNonce = winningNonce;
        this.txs = txs;
        this.ledgerHash = ledgerHash;
        this.minerSig = minerSig;
        this.minerSigIndex = minerSigIndex;

        try {
            String txString = "";

            for (int i = 0; i < txs.size(); i++) {

                if (txs.get(i).length() > 10) {
                    txString += txs.get(i) + "*";
                }

            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            txString = txString.substring(0, txString.length() - 1);
            String blockData = "{" + timestamp + ":" + blockIndex + ":" + prevBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + txString + "}," + cert.getFullCertificate();
            this.blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Determines whether or not a block is PoW or not. Blocks that aren't PoW have an all zero certificate
     *
     * @return Whether the block is a PoW or not
     */
    public boolean isPoWBlock() {
        return cert.isPoWCertificate();
    }

    public Block(String rawBlock) {
        /*
         * Using a workaround for the unknown number of transactions, which would each be split into multiple parts as they
         * contain a comma as part of the signature. As such, all part up to and including the list of transactions are parsed
         * manually. Then, the remainder can be separated using the split command.
         */
        String[] parts = new String[11];
        parts[0] = rawBlock.substring(0, rawBlock.indexOf("}") + 1);
        rawBlock = rawBlock.substring(rawBlock.indexOf("}") + 2); //Account for comma
        parts[1] = rawBlock.substring(0, rawBlock.indexOf("}") + 1);
        rawBlock = rawBlock.substring(rawBlock.indexOf("}") + 2); //Account for comma, again
        parts[2] = rawBlock.substring(0, rawBlock.indexOf("}") + 1);
        rawBlock = rawBlock.substring(rawBlock.indexOf("}") + 2); //Account for comma a third time
        String[] partsInitial = rawBlock.split(",");
        for (int i = 3; i < 11; i++) {
            parts[i] = partsInitial[i - 3];
        }
        System.out.println("Block parts: " + parts.length);
        for (int i = 0; i < parts.length; i++) {
            String toPrint = parts[i];
            if (parts[i].length() > 40)
                toPrint = parts[i].substring(0, 20) + "..." + parts[i].substring(parts[i].length() - 20);
            System.out.println("     " + i + ": " + toPrint);
        }
        String firstPart = parts[0].replace("{", "");
        firstPart = firstPart.replace("}", "");
        String[] firstPartParts = firstPart.split(":");
        try {
            this.timestamp = Long.parseLong(firstPartParts[0]);
            this.blockIndex = Integer.parseInt(firstPartParts[1]);
            this.prevBlockHash = firstPartParts[2];
            this.difficulty = Long.parseLong(firstPartParts[3]);
            this.winningNonce = Integer.parseInt(firstPartParts[4]);
            this.ledgerHash = parts[1].replace("{", "").replace("}", "");
            String transactionsString = parts[2].replace("{", "").replace("}", "");
            this.txs = new ArrayList<String>();
            String[] rawTransactions = transactionsString.split("\\*"); //Transactions are separated by an asterisk, as the colon, double-colon, and comma are all used in other places, and would be a pain to use here.
            for (int i = 0; i < rawTransactions.length; i++) {
                this.txs.add(rawTransactions[i]);
            }
            this.cert = new Certificate(parts[3] + "," + parts[4] + "," + parts[5] + "," + parts[6]);
            //parts[7] is a block hash
            this.minerSig = parts[8].replace("{", "") + "," + parts[9].replace("}", "");
            this.minerSigIndex = Integer.parseInt(parts[10].replace("{", "").replace("}", ""));
            /*
             * Ugly, will fix later.
             */
            try {
                transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < txs.size(); i++) {
                    if (txs.get(i).length() > 10) //Arbitrary number, make sure a transaction has some size to it
                    {
                        transactionsString += txs.get(i) + "*";
                    }
                }
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) //Protect against empty transaction sets tripping errors with negative substring indices
                {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockIndex + ":" + prevBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + cert.getFullCertificate();
                this.blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));

            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the address of whatever node mined the last block
     * @return String Address of block miner
     */
    public String getMiner(){
        return cert.redeemAddress;
    }

    public boolean validateBlock(Blockchain chain){
        Util.getLogger().info("Validating block " + blockIndex + "...");
        Util.getLogger().info("Difficulty:" + difficulty);

        if (difficulty == 100000){

            // No validation needed, cert is filled with zeros
            if (winningNonce > cert.maxNonce){
                return false;
            }
            // No PoS before block 500
            if (blockIndex < 500){
                return false;
            }

            // Address can not have mined a PoS block or sent a transaction in the last 50 blocks
            for (int i = blockIndex - 1; i > blockIndex - 50; i--) {
                if (!chain.getBlock(i).isPoWBlock()){
                    if(chain.getBlock(i).getMiner().equals(cert.redeemAddress)){
                        return false; // Addr has mined a PoS block too recently
                    }
                }

                ArrayList<String> tsx = chain.getBlock(i).getTransactionsInvolvingAddress(cert.redeemAddress);
                for (String tx: txs) {
                    if (tx.split(":")[0].equals(cert.redeemAddress)){
                        return false;
                    }
                }
            }
            try
            {
                String transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < txs.size(); i++)
                {
                    if (txs.get(i).length() > 10) //Arbitrary number, makes sure empty transaction sets still function
                    {
                        transactionsString += txs.get(i) + "*";
                    }
                }
                //Recalculate block hash
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) //Prevent empty transaction sets from tripping with a negative substring index
                {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockIndex + ":" + prevBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + cert.getFullCertificate();
                String blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
                String fullBlock = blockData + ",{" + blockHash + "}"; //This is the message signed by the block miner
                MerkleAddressUtility MerkleAddressUtility = new MerkleAddressUtility();
                if (!MerkleAddressUtility.verifyMerkleSignature(fullBlock, minerSig, cert.redeemAddress, minerSigIndex))
                {
                    System.out.println("Block didn't verify for " + cert.redeemAddress + " with index " + minerSigIndex);
                    System.out.println("Signature mismatch error");
                    System.out.println("fullBlock: " + fullBlock);
                    System.out.println("minerSignature: " + minerSig);
                    return false; //Block mining signature is not valid
                }
                if (txs.size() == 1 && txs.get(0).equals(""))
                {
                    //Block has no explicit transactions
                    return true;
                }
                else if (txs.size() == 0)
                {
                    //Block has no explicit transactions
                    return true;
                }
                for (int i = 0; i < txs.size(); i++)
                {
	                /*
	                 * Transaction format:
	                 * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
	                 */
                    try
                    {
                        String tempTransaction = txs.get(i);
                        String[] transactionParts = tempTransaction.split(";");
                        if (transactionParts.length % 2 != 0 || transactionParts.length < 6)
                        {
                            System.out.println("Error validating block: transactionParts.length = " + transactionParts.length);
                            for (int j = 0; j < transactionParts.length; j++)
                            {
                                System.out.println("     " + j + ": " + transactionParts[j]);
                            }
                            return false; //Each address should line up with an output, and no explicit transaction is possible with fewer than six parts (see above)
                        }
                        for (int j = 0; j < transactionParts.length - 2; j+=2) //Last two parts are signatureData and signatureIndex,respectively
                        {
                            if (!MerkleAddressUtility.isAddressFormattedCorrectly(transactionParts[j]))
                            {
                                System.out.println("Error validating block: address " + transactionParts[j] + " is invalid.");
                                return false; //Address in transaction is misformatted
                            }
                        }
                        long inputAmount = Long.parseLong(transactionParts[1]);
                        long outputAmount = 0L;
                        for (int j = 3; j < transactionParts.length - 2; j+=2) //Element #3 (4th element) and each subsequent odd-numbered index up to transactionParts should be an output amount.
                        {
                            outputAmount += Long.parseLong(transactionParts[j]);
                        }
                        if (inputAmount - outputAmount < 0)
                        {
                            System.out.println("Error validating block: more coins output than input!");
                            return false; //Coins can't be created out of thin air!
                        }
                        String transactionData = "";
                        for (int j = 0; j < transactionParts.length - 2; j++)
                        {
                            transactionData += transactionParts[j] + ";";
                        }
                        transactionData = transactionData.substring(0, transactionData.length() - 1);
                        if (!MerkleAddressUtility.verifyMerkleSignature(transactionData, transactionParts[transactionParts.length - 2], transactionParts[0], Long.parseLong(transactionParts[transactionParts.length - 1])))
                        {
                            System.out.println("Error validating block: signature does not match!");
                            return false; //Signature doesn't match
                        }
                    } catch (Exception e) //Likely an error parsing a Long or performing some String manipulation task. Maybe array bounds exceptions.
                    {
                        e.printStackTrace();
                        return false;
                    }
                }
            } catch (Exception e) { }
            // PoS block appears to be formatted correctly
            return true;
        }
        else if (difficulty == 150000) // PoW block
        {
            try
            {
                if (!cert.validCertificate())
                {
                    System.out.println("Certificate validation error");
                    return false; //Certificate is not valid.
                }
                if (winningNonce > cert.maxNonce)
                {
                    System.out.println("Winning nonce error");
                    return false; //winningNonce is outside of the nonce range!
                }
                if (blockIndex != cert.blockIndex)
                {
                    System.out.println("Block height does not match certificate height!");
                    return false; //Certificate and block height are not equal
                }
                long certificateScore = cert.getScoreAtNonce(winningNonce); //Lower score is better
                long target = Long.MAX_VALUE/(difficulty/2);
                if (certificateScore < target)
                {
                    System.out.println("Certificate score error");
                    return false; //Certificate doesn't fall below the target difficulty when mined.
                }
                String transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < txs.size(); i++)
                {
                    if (txs.get(i).length() > 10) //Arbitrary number, makes sure empty transaction sets still function
                    {
                        transactionsString += txs.get(i) + "*";
                    }
                }
                //Recalculate block hash
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) //Prevent empty transaction sets from tripping with a negative substring index
                {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockIndex + ":" + prevBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + cert.getFullCertificate();
                String blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
                String fullBlock = blockData + ",{" + blockHash + "}"; //This is the message signed by the block miner
                MerkleAddressUtility MerkleAddressUtility = new MerkleAddressUtility();
                if (!MerkleAddressUtility.verifyMerkleSignature(fullBlock, minerSig, cert.redeemAddress, minerSigIndex))
                {
                    System.out.println("Block didn't verify for " + cert.redeemAddress + " with index " + minerSigIndex);
                    System.out.println("Signature mismatch error");
                    System.out.println("fullBlock: " + fullBlock);
                    System.out.println("minerSignature: " + minerSig);
                    return false; //Block mining signature is not valid
                }
                if (txs.size() == 1 && txs.get(0).equals(""))
                {
                    //Block has no explicit transactions
                    return true;
                }
                else if (txs.size() == 0)
                {
                    //Block has no explicit transactions
                    return true;
                }
                for (int i = 0; i < txs.size(); i++)
                {
                    /*
                     * Transaction format:
                     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
                     */
                    try
                    {
                        String tempTransaction = txs.get(i);
                        String[] transactionParts = tempTransaction.split(";");
                        if (transactionParts.length % 2 != 0 || transactionParts.length < 6)
                        {
                            System.out.println("Error validating block: transactionParts.length = " + transactionParts.length);
                            for (int j = 0; j < transactionParts.length; j++)
                            {
                                System.out.println("     " + j + ": " + transactionParts[j]);
                            }
                            return false; //Each address should line up with an output, and no explicit transaction is possible with fewer than six parts (see above)
                        }
                        for (int j = 0; j < transactionParts.length - 2; j+=2) //Last two parts are signatureData and signatureIndex,respectively
                        {
                            if (!MerkleAddressUtility.isAddressFormattedCorrectly(transactionParts[j]))
                            {
                                System.out.println("Error validating block: address " + transactionParts[j] + " is invalid.");
                                return false; //Address in transaction is misformatted
                            }
                        }
                        long inputAmount = Long.parseLong(transactionParts[1]);
                        long outputAmount = 0L;
                        for (int j = 3; j < transactionParts.length - 2; j+=2) //Element 3 (4th element) and each subsequent odd-numbered index up to transactionParts should be an output amount.
                        {
                            outputAmount += Long.parseLong(transactionParts[j]);
                        }
                        if (inputAmount - outputAmount < 0)
                        {
                            System.out.println("Error validating block: more coins output than input!");
                            return false; //Coins can't be created out of thin air!
                        }
                        String transactionData = "";
                        for (int j = 0; j < transactionParts.length - 2; j++)
                        {
                            transactionData += transactionParts[j] + ";";
                        }
                        transactionData = transactionData.substring(0, transactionData.length() - 1);
                        if (!MerkleAddressUtility.verifyMerkleSignature(transactionData, transactionParts[transactionParts.length - 2], transactionParts[0], Long.parseLong(transactionParts[transactionParts.length - 1])))
                        {
                            System.out.println("Error validating block: signature does not match!");
                            return false; //Siganture doesn't match
                        }
                    } catch (Exception e) //Likely an error parsing a Long or performing some String manipulation task. Maybe array bounds exceptions.
                    {
                        e.printStackTrace();
                        return false;
                    }
                }
            } catch (Exception e)
            {
                e.printStackTrace();
                return false;
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    public ArrayList<String> getTransactionsInvolvingAddress(String addr){
        ArrayList<String> relevantParts = new ArrayList<>();
        for (int i = 0; i < txs.size(); i++) {
            String temp = txs.get(i);
            String[] txParts = temp.split("::");
            String sender = txParts[0];

            if (addr.equals(cert.redeemAddress)){
                relevantParts.add("COINBASE:100:" + cert.redeemAddress);
            }
            if(sender.equalsIgnoreCase(addr)){
                for (int j = 2; j < txParts.length - 2; j+=2) {
                    relevantParts.add(sender + ":" + txParts[j+1]);
                }
            } else {
                for(int j = 2; j < txParts.length - 2; j+=2){
                    if(txParts[j].equalsIgnoreCase(addr)){
                        relevantParts.add(sender + ":" + txParts[j+1] + ":" + txParts[j]);
                    }
                }
            }
        }
        return relevantParts;
    }

    /**
     * Returns the raw (String) representation of the block and its data.
     * @return String The raw block
     */
    public String getRawBlock(){

        String raw = "{" + timestamp + ":" + blockIndex + ":" + prevBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{";
        String txString = "";
        for (int i = 0; i < txs.size(); i++) {
            if(txs.get(i).length() > 10){
                txString += txs.get(i) + "*";
            }
        }
        if(txString.length() > 2) {  // Prevent empty tx strings from throwing an IndexOutOfBounds exception
             txString = txString.substring(0, txString.length() - 1);
        }
        raw += txString + "}," + cert.getFullCertificate() + ",{" + blockHash + "},{" + minerSig + "},{" + minerSigIndex + "}";
        return raw;
    }

}
