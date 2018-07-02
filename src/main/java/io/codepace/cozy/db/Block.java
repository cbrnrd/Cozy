package io.codepace.cozy.db;

import io.codepace.cozy.Certificate;
import io.codepace.cozy.MerkleAddressUtility;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.util.ArrayList;
/**
 * This class provides all functionality related to block verification and usage.
 * A block contains:
 * -Timestamp (Unix Epoch)
 * -Block number
 * -Previous block hash
 * -Certificate
 * -Difficulty
 * -Winning nonce
 * -Transaction list
 */
public class Block
{
    public long timestamp;
    public int blockNum;
    public String previousBlockHash;
    public String blockHash;
    public Certificate certificate;
    public long difficulty;
    public int winningNonce;
    public String ledgerHash;
    public ArrayList<String> transactions;
    public String minerSignature;
    public long minerSignatureIndex;

    /**
     * Constructor for Block object. A block object is made for any confirmed or potential network block, and requires all pieces of data in this constructor
     * to be a valid network block. The timestamp is the result of the miner's initial call to System.currentTimeMillis(). When peers are receiving new blocks
     * (synced with the network, not catching up) they will refuse any blocks that are more than 2 hours off their internal adjusted time. This makes difficulty
     * malleability impossible in the long-run, ensures that timestamps are reasonably accurate, etc. As a result, any clients far off from the true network time
     * will be forked off the network as they won't accept valid network blocks. Make sure your computer's time is set correctly!
     *
     * All blocks stack in one particular order, and each block contains the hash of the previous block, to clear any ambiguities about which chain a block belongs
     * to during a fork. The winning nonce is concatenated with the certificate and hashed to get a certificate mining score, which is then used to determine
     * whether a block is under the target difficulty.
     *
     * Blocks are hashed to create a block hash, which ensures blocks are not altered, and is used in block stacking. The data hashed is formatted as a String:
     * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions},{redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex}
     * The last three chunks of the above are returned by calling getFullCertificate() on a certificate object.
     * Then, the full block (including the hash) is signed by the miner. So:
     * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions},{redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex},{blockHash}
     * will be hashed and signed by the redeemAddress, which should be held by the miner. The final block format:
     * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions},{redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex},{blockHash},{minerSignature},{minerSignatureIndex}
     *
     * A higher difficulty means a block is harder to mine. However, a higher difficulty means the TARGET is smaller. Targets can be calculated from the difficulty. A target is simply Long.MAX_VALUE-difficulty.
     *
     * Explicit transactions are represented as Strings in an {@link ArrayList}. Each explicit transaction follows the following format:
     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
     * At a bare minimum, ALL transactions must have an InputAddress, InputAmount, and one OutputAddress and one OutputAmount
     * Anything left over after all OutputAmounts have been subtracted from the InputAmount is the transaction fee which goes to a block miner.
     * The payment of transaction fees and block rewards are IMPLICIT transactions. They never actually appear on the network. Clients, when processing blocks, automatically adjust the ledger as required.
     *
     * @param timestamp Timestamp originally set into the block by the miner
     * @param blockNum The block number
     * @param previousBlockHash The hash of the previous block
     * @param certificate The certificate of the block
     * @param difficulty The difficulty at the time this block was mined
     * @param winningNonce The nonce selected by a miner to create the block
     * @param ledgerHash The hash of the ledger as it existed before this block's transactions occurred
     * @param transactions {@link ArrayList} of all the transactions included in the block
     * @param minerSignature Miner's signature of the block
     * @param minerSignatureIndex Miner's signature index used when generating minerSignature
     */
    public Block(long timestamp, int blockNum, String previousBlockHash, Certificate certificate, long difficulty, int winningNonce, String ledgerHash, ArrayList<String> transactions, String minerSignature, int minerSignatureIndex)
    {
        this.timestamp = timestamp;
        this.blockNum = blockNum;
        this.previousBlockHash = previousBlockHash;
        this.certificate = certificate;
        this.difficulty = difficulty;
        this.winningNonce = winningNonce;
        this.ledgerHash = ledgerHash;
        this.transactions = transactions;
        this.minerSignature = minerSignature;
        this.minerSignatureIndex = minerSignatureIndex;
        try
        {
            String transactionsString = "";
            //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
            for (int i = 0; i < transactions.size(); i++)
            {
                if (transactions.get(i).length() > 10)
                {
                    transactionsString += transactions.get(i) + "*";
                }
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
            String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
            this.blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Determines whether the block is PoW or not. Blocks that are not PoW (as in, PoS) have a certificate
     * filled with zeros.
     *
     * @return Whether the block is a PoW block or not
     */
    public boolean isPoWBlock()
    {
        return certificate.isPoWCertificate();
    }

    /**
     * See above for a lot of information. This constructor accepts the raw block format instead of all the arguments separately!
     *
     * @param rawBlock String representing the raw data of a block
     */
    public Block(String rawBlock)
    {
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
        for (int i = 3; i < 11; i++)
        {
            parts[i] = partsInitial[i - 3];
        }
        System.out.println("Block parts: " + parts.length);
        for (int i = 0; i < parts.length; i++)
        {
            String toPrint = parts[i];
            if (parts[i].length() > 40)
                toPrint = parts[i].substring(0, 20) + "..." + parts[i].substring(parts[i].length() - 20);
            System.out.println("     " + i + ": " + toPrint);
        }
        String firstPart = parts[0].replace("{", "");
        firstPart = firstPart.replace("}", "");
        String[] firstPartParts = firstPart.split(":"); //Great name, huh?
        try
        {
            this.timestamp = Long.parseLong(firstPartParts[0]);
            this.blockNum = Integer.parseInt(firstPartParts[1]);
            this.previousBlockHash = firstPartParts[2];
            this.difficulty = Long.parseLong(firstPartParts[3]);
            this.winningNonce = Integer.parseInt(firstPartParts[4]);
            this.ledgerHash = parts[1].replace("{", "").replace("}", "");
            String transactionsString = parts[2].replace("{", "").replace("}", "");
            this.transactions = new ArrayList<>();
            String[] rawTransactions = transactionsString.split("\\*"); //Transactions are separated by an asterisk, as the colon, double-colon, and comma are all used in other places, and would be a pain to use here.
            for (int i = 0; i < rawTransactions.length; i++)
            {
                this.transactions.add(rawTransactions[i]);
            }
            this.certificate = new Certificate(parts[3] + "," + parts[4] + "," + parts[5] + "," + parts[6]);
            //parts[7] is a block hash
            this.minerSignature = parts[8].replace("{", "") + "," + parts[9].replace("}", "");
            this.minerSignatureIndex = Integer.parseInt(parts[10].replace("{", "").replace("}", ""));
            /*
             * Ugly, will fix later.
             */
            try
            {
                transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < transactions.size(); i++)
                {
                    if (transactions.get(i).length() > 10) //Arbitrary number, make sure a transaction has some size to it
                    {
                        transactionsString += transactions.get(i) + "*";
                    }
                }
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) //Protect against empty transaction sets tripping errors with negative substring indices
                {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
                this.blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Gets the address which mined this block.
     * @return String Address of block miner
     */
    public String getMiner()
    {
        return certificate.redeemAddress;
    }

    /**
     * Used to check a variety of conditions to ensure that a block is valid.
     * Valid block requirements:
     * -Certificate is valid
     * -Certificate when mined with winningNonce falls below the target
     * -'Compiled' block format is signed correctly by miner
     * -Miner signature is valid
     * -Transactions are formatted correctly
     *
     * @param blockchain The blockchain in which to validate the block
     * @return boolean Whether the self-contained block is valid. Does not represent inclusion in the network, or existence of the previous block.
     */
    public boolean validateBlock(Blockchain blockchain)
    {
        System.out.println("Validating block " + blockNum);
        System.out.println("Difficulty: " + difficulty);
        if (difficulty == 100000)
        {
            // No certificate validation required, certificate is simply filled with zeros.
            if (winningNonce > certificate.maxNonce)
            {
                return false; // PoS difficulty exceeded
            }
            if (blockNum < 500)
            {
                // No PoS blocks allowed before block 500
                return false;
            }

            // Address can not have mined a PoS block or sent a transaction in the last 50 blocks
            for (int i = blockNum - 1; i > blockNum - 50; i--)
            {
                if (!blockchain.getBlock(i).isPoWBlock()) // Then PoS block
                {
                    if (blockchain.getBlock(i).getMiner().equals(certificate.redeemAddress))
                    {
                        return false; // Address has mined PoS block too recently!
                    }
                }
                ArrayList<String> transactions = blockchain.getBlock(i).getTransactionsInvolvingAddress(certificate.redeemAddress);
                for (String transaction : transactions)
                {
                    if (transaction.split(":")[0].equals(certificate.redeemAddress))
                    {
                        return false; // Address has sent coins too recently!
                    }
                }
            }



            try
            {
                String transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < transactions.size(); i++)
                {
                    if (transactions.get(i).length() > 10) //Arbitrary number, makes sure empty transaction sets still function
                    {
                        transactionsString += transactions.get(i) + "*";
                    }
                }
                //Recalculate block hash
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) //Prevent empty transaction sets from tripping with a negative substring index
                {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
                String blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
                String fullBlock = blockData + ",{" + blockHash + "}"; //This is the message signed by the block miner
                MerkleAddressUtility MerkleAddressUtility = new MerkleAddressUtility();
                if (!MerkleAddressUtility.verifyMerkleSignature(fullBlock, minerSignature, certificate.redeemAddress, minerSignatureIndex))
                {
                    System.out.println("Block didn't verify for " + certificate.redeemAddress + " with index " + minerSignatureIndex);
                    System.out.println("Signature mismatch error");
                    System.out.println("fullBlock: " + fullBlock);
                    System.out.println("minerSignature: " + minerSignature);
                    return false; //Block mining signature is not valid
                }
                if (transactions.size() == 1 && transactions.get(0).equals(""))
                {
                    //Block has no explicit transactions
                    return true;
                }
                else if (transactions.size() == 0)
                {
                    //Block has no explicit transactions
                    return true;
                }
                for (int i = 0; i < transactions.size(); i++)
                {
                    /*
                     * Transaction format:
                     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
                     */
                    try
                    {
                        String tempTransaction = transactions.get(i);
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
                if (!certificate.validateCertificate())
                {
                    System.out.println("Certificate validation error");
                    return false; //Certificate is not valid.
                }
                if (winningNonce > certificate.maxNonce)
                {
                    System.out.println("Winning nonce error");
                    return false; //winningNonce is outside of the nonce range!
                }
                if (blockNum != certificate.blockNum)
                {
                    System.out.println("Block height does not match certificate height!");
                    return false; //Certificate and block height are not equal
                }
                long certificateScore = certificate.getScoreAtNonce(winningNonce); //Lower score is better
                long target = Long.MAX_VALUE/(difficulty/2);
                if (certificateScore < target)
                {
                    System.out.println("Certificate score error");
                    return false; //Certificate doesn't fall below the target difficulty when mined.
                }
                String transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < transactions.size(); i++)
                {
                    if (transactions.get(i).length() > 10) //Arbitrary number, makes sure empty transaction sets still function
                    {
                        transactionsString += transactions.get(i) + "*";
                    }
                }
                //Recalculate block hash
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) //Prevent empty transaction sets from tripping with a negative substring index
                {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
                String blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
                String fullBlock = blockData + ",{" + blockHash + "}"; //This is the message signed by the block miner
                MerkleAddressUtility MerkleAddressUtility = new MerkleAddressUtility();
                if (!MerkleAddressUtility.verifyMerkleSignature(fullBlock, minerSignature, certificate.redeemAddress, minerSignatureIndex))
                {
                    System.out.println("Block didn't verify for " + certificate.redeemAddress + " with index " + minerSignatureIndex);
                    System.out.println("Signature mismatch error");
                    System.out.println("fullBlock: " + fullBlock);
                    System.out.println("minerSignature: " + minerSignature);
                    return false; //Block mining signature is not valid
                }
                if (transactions.size() == 1 && transactions.get(0).equals(""))
                {
                    //Block has no explicit transactions
                    return true;
                }
                else if (transactions.size() == 0)
                {
                    //Block has no explicit transactions
                    return true;
                }
                for (int i = 0; i < transactions.size(); i++)
                {
                    /*
                     * Transaction format:
                     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
                     */
                    try
                    {
                        String tempTransaction = transactions.get(i);
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

    /**
     * Scans the block for any transactions that involve the provided address.
     * Returns {@link ArrayList} containing "simplified" transactions, in the format of sender:amount:receiver
     * Each of these "simplified" transaction formats don't necessarily express an entire transaction, but rather only portions
     * of a transaction which involve either the target address sending or receiving coins.
     *
     * @param addressToFind Address to search through block transaction pool for
     *
     * @return {@link ArrayList} Simplified-transaction-format list of all related transactions.
     */
    public ArrayList<String> getTransactionsInvolvingAddress(String addressToFind)
    {
        ArrayList<String> relevantTransactionParts = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i++)
        {
            String tempTransaction = transactions.get(i);
            //InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
            String[] transactionParts = tempTransaction.split(";");
            String sender = transactionParts[0];
            if (addressToFind.equals(certificate.redeemAddress))
            {
                relevantTransactionParts.add("COINBASE" + ":" + "100" + ":" + certificate.redeemAddress);
            }
            if (sender.equalsIgnoreCase(addressToFind))
            {
                for (int j = 2; j < transactionParts.length - 2; j+=2)
                {
                    relevantTransactionParts.add(sender + ":" + transactionParts[j+1] + ":" + transactionParts[j]);
                }
            }
            else
            {
                for (int j = 2; j < transactionParts.length - 2; j+=2)
                {
                    if (transactionParts[j].equalsIgnoreCase(addressToFind))
                    {
                        relevantTransactionParts.add(sender + ":" + transactionParts[j+1] + ":" + transactionParts[j]);
                    }
                }
            }
        }
        return relevantTransactionParts;
    }

    /**
     * Returns the raw String representation of the block, useful when saving the block or sending it to a peer.
     *
     * @return String The raw block
     */
    public String getRawBlock()
    {
        String rawBlock = "";
        rawBlock = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{";
        String transactionString = "";
        for (int i = 0; i < transactions.size(); i++)
        {
            if (transactions.get(i).length() > 10)
            {
                transactionString += transactions.get(i) + "*";
            }
        }
        if (transactionString.length() > 2) //Protect against empty transaction strings tripping an index out of bounds error with a negative substring ending index
        {
            transactionString = transactionString.substring(0, transactionString.length() - 1);
        }
        rawBlock += transactionString + "}," + certificate.getFullCertificate() + ",{" + blockHash + "},{" + minerSignature + "},{" + minerSignatureIndex + "}";
        return rawBlock;
    }
}