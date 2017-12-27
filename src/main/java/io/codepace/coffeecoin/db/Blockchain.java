package io.codepace.coffeecoin.db;

import io.codepace.coffeecoin.LedgerManager;
import io.codepace.coffeecoin.db.Block;

import java.io.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Level;

import static io.codepace.coffeecoin.Util.*;

public class Blockchain {
    ArrayList<ArrayList<Block>> chains = new ArrayList<>();

    private ArrayList<Block> queue;

    public LedgerManager ledgerManager;

    private String dbFolder;

    private boolean hasGenesisBlock = false;

    public Blockchain(String dbFolder) {
        this.dbFolder = dbFolder;
        this.ledgerManager = new LedgerManager(dbFolder + "balances.dat");
        this.queue = new ArrayList<>();
    }

    public int getBlockchainLength() {
        int longestChain = 0;

        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChain) {
                longestChain = chains.get(i).size();
            }
        }
        return longestChain;
    }

    public long getDifficulty() {
        int longestChainIndex = -1;
        int longestChainSize = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainSize) {
                longestChainSize = chains.get(i).size();
                longestChainIndex = i;
            }
        }
        return chains.get(longestChainIndex).get(longestChainSize - 1).difficulty;
    }

    public void tryBlockQueue() {
        boolean addedBlock = false;
        do {
            addedBlock = false;
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i).validateBlock(this)) {
                    if (addBlock(queue.get(i), false)) {
                        addedBlock = true;
                        queue.remove(i);
                        i--;
                    }
                } else {
                    queue.remove(i);
                }
            }
        } while (addedBlock);
    }

    /**
     * Gets the block at blockIndex starting at 0 (Genesis) from whatever the longest chain is.
     *
     * @param blockIndex The block number to get
     * @return Block The block at blocknum in the longest chain
     */
    public Block getBlock(int blockIndex) {
        int longestChainLength = 0;
        int longestChain = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainLength) {
                longestChain = i;
                longestChainLength = chains.get(i).size();
            }
        }
        return chains.get(longestChain).get(blockIndex);
    }

    public boolean addBlock(Block block, boolean fromChainFile) {
        logInfoAndPrint("Attempting to add block " + block.blockIndex + " with hash " + block.blockHash);
        try {
            boolean isPoS = false;

            if (block.difficulty == 100000) { // TODO: hard coded, maybe change later
                isPoS = true;
            }

            // 1 nonce in every 150000 will win. Could change later to make it quicker.
            if (block.difficulty != 150000 && !isPoS) {
                logInfoAndPrint("Block detected with wrong difficulty");
                return false;
            }

            // Remove chains that are falling behind (by 15 blocks) the longest chain
            int largestChainLen = 0;
            ArrayList<Block> largestChain = new ArrayList<>();
            String largestChainLastBlockHash = "";

            for (int i = 0; i < chains.size(); i++) {

                if (chains.get(i).size() > largestChainLen) {
                    largestChain = chains.get(i);
                    largestChainLen = largestChain.size();
                    largestChainLastBlockHash = chains.get(i).get(chains.get(i).size() - 1).blockHash;
                }

            }

            // Now that we have the largest chain, remove any that are falling 15 or more behind
            for (int i = 0; i < chains.size(); i++) {
                if (chains.get(i).size() < largestChainLen - 15) {
                    chains.remove(i);
                    i--; // Compensate for resized arraylist
                }
            }

            if (!block.validateBlock(this)) {
                System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + ANSI_RED + "Failed to validate block!" + ANSI_RESET);
                return false; // If it's not a valid block, don't add it of course
            }

            if (block.blockIndex > largestChainLen) {
                queue.add(block);
                logInfoAndPrint("Block " + block.blockIndex + " with starting hash " + block.blockHash.substring(0, 8) + " added to queue.");
                logInfoAndPrint("Largest chain length: " + largestChainLen);
                logInfoAndPrint("Block index: " + block.blockIndex);
                return false; // The block hasn't yet been added to the chain, it's in the queue
            }

            // If no chains exist, create it and add genesis block
            if (!hasGenesisBlock) {
                hasGenesisBlock = true;
                chains.add(new ArrayList<>());
                chains.get(0).add(block);
                largestChain = chains.get(0);
                largestChainLastBlockHash = block.blockHash;

                if (ledgerManager.lastBlockIndex < 0) {
                    ArrayList<String> txsToApply = new ArrayList<>();
                    txsToApply.addAll(block.txs);
                    int loopCount = 0;
                    int txsApplied = 0;

                    while (txsToApply.size() > txsApplied && !txsToApply.get(0).equals("")) {
                        loopCount++;
                        for (int x = 0; x < txsToApply.size(); x++) {
                            if (ledgerManager.executeTransaction(txsToApply.get(x))) {
                                txsToApply.remove(x);
                                x--; // Compensate for resized ArrayList
                            }
                        }
                        if (loopCount > 10000) {
                            String exitMsg = "Infinite block detected. Hash: " + block.blockHash + " at index " + block.blockIndex;
                            getLogger().log(Level.SEVERE, exitMsg);
                            System.out.println(new Timestamp(System.currentTimeMillis()) + "[DAEMON] - " + ANSI_RED + exitMsg + ANSI_RESET);
                            System.out.println(txsToApply.size());
                            System.exit(-1);
                        }
                    }
                }

                ledgerManager.adjustAddressBalance(chains.get(0).get(0).cert.redeemAddress, 100); // Mining fee
                ledgerManager.adjustAddressSignatureCount(chains.get(0).get(0).cert.redeemAddress, 1);
                if (!fromChainFile)
                    writeBlockToFile(block);
                return true;
            }

            // Check for duplicate blocks
            for (int i = 0; i < chains.size(); i++) {
                if (chains.get(i).get(chains.get(i).size() - 1).blockHash.equals(block.blockHash)) {
                    logInfoAndPrint("Duplicate block received from peer, ignoring it.");
                    return false;
                }
            }

            for (int i = 0; i < chains.size(); i++) {
                logInfoAndPrint("Previous block hash according to chain: " + chains.get(0).get(chains.get(i).size() - 1).blockHash);
                logInfoAndPrint("Previous block hash according to added block: " + block.prevBlockHash);
                logInfoAndPrint("Selected chain size: " + chains.get(i).size());
                logInfoAndPrint("Should be equal to block index: " + block.blockIndex);

                // New blocks stack nicely on to one of the others
                if (chains.get(i).get(chains.get(i).size() - 1).blockHash.equals(block.prevBlockHash) && chains.get(i).size() == block.blockIndex) {

                    chains.get(i).add(block);

                    // Check if we created a longer fork
                    if (chains.get(i).size() > largestChainLen) {
                        if (!chains.get(i).get(chains.get(i).size() - 2).blockHash.equals(largestChainLastBlockHash)) {
                            for (int x = largestChain.size() - 1; x > 0; x--) {
                                ArrayList<String> txsToReverse = largestChain.get(x).txs;
                                for (int k = 0; k < txsToReverse.size(); k++)
                                    ledgerManager.reverseTransaction(txsToReverse.get(k));
                                ledgerManager.adjustAddressBalance(largestChain.get(x).cert.redeemAddress, -100); // Reverse mining payment
                                ledgerManager.adjustAddressSignatureCount(largestChain.get(x).cert.redeemAddress, -1);
                            }
                            for (int j = 0; j < chains.get(i).size(); j++) {
                                ArrayList<String> txsToApply = new ArrayList<>();
                                txsToApply.addAll(block.txs);
                                int loopCount = 0;
                                while (txsToApply.size() > 0) {
                                    loopCount++;
                                    for (int k = 0; k < txsToApply.size(); k++) {
                                        logInfoAndPrint("Attempting to execute transaction: " + txsToApply.get(k).substring(0, 45) + "..." + txsToApply.get(k).substring(txsToApply.get(k).length() - 20));
                                        if (ledgerManager.executeTransaction(txsToApply.get(k))) {
                                            logInfoAndPrint("Successfully executed transaction!");
                                            txsToApply.remove(k);
                                            k--; //Compensate for changed ArrayList size
                                        } else {
                                            logInfoAndPrint("Didn't execute transaction...");
                                        }
                                    }
                                    if (loopCount > 10000) {
                                        logInfoAndPrint("Infinite block detected! Hash: " + chains.get(i).get(j).blockHash + " and height: " + chains.get(i).get(j).blockIndex);
                                        System.exit(-1);
                                    }
                                }
                                ledgerManager.adjustAddressBalance(chains.get(i).get(j).cert.redeemAddress, 100); //Pay mining fee
                                ledgerManager.adjustAddressSignatureCount(chains.get(i).get(j).cert.redeemAddress, 1);
                            }
                        } else {  // We added to the longest chain
                            if (ledgerManager.lastBlockIndex < block.blockIndex) {
                                ArrayList<String> txsToApply = new ArrayList<>();
                                txsToApply.addAll(block.txs);

                                int loopCount = 0;
                                int completedTxs = 0;
                                while (txsToApply.size() > completedTxs) {
                                    loopCount++;
                                    for (int k = 0; k < txsToApply.size(); k++) {
                                        if (ledgerManager.executeTransaction(txsToApply.get(k))) {
                                            txsToApply.remove(k);
                                            k--;
                                        } else if (txsToApply.get(k).equals("")) {
                                            completedTxs++;
                                        }
                                    }
                                    if (loopCount > 10000) {
                                        logInfoAndPrint("Infinite block detected with hash: " + block.blockHash + " at index " + block.blockIndex);
                                        System.exit(-1);
                                    }
                                }
                                ledgerManager.adjustAddressBalance(block.cert.redeemAddress, 100);
                                ledgerManager.adjustAddressSignatureCount(block.cert.redeemAddress, 1);
                            }
                        }
                    }

                    if (!fromChainFile)
                        writeBlockToFile(block);
                    return true;
                } else {
                    logInfoAndPrint("Something went wrong with block stacking.");
                    logInfoAndPrint(chains.get(i).get(chains.get(i).size() - 1).blockHash);
                    logInfoAndPrint(block.prevBlockHash);
                }
            }
            // Whether or not we've found a place for our block in the chain
            boolean foundPlace = false;
            for (int i = 0; i < chains.size(); i++) {
                ArrayList<Block> tempChain = chains.get(i);
                for (int j = tempChain.size() - 16; j < tempChain.size(); j++) {
                    if (j < 0) { // Handle early network forks
                        j = 0;
                    }
                    if (tempChain.get(j).blockHash.equals(block.prevBlockHash)) {  // Found wherever it forked
                        ArrayList<Block> newChain = new ArrayList<>();

                        // Add all blocks to the new chain until we get to the forked block
                        for (int k = 0; k <= j; k++) {
                            newChain.add(tempChain.get(k));
                        }

                        newChain.add(block);
                        foundPlace = true;
                        j = tempChain.size();
                        i = chains.size();
                    }
                }
            }

            if (!foundPlace) {
                // Block must have been very old.
                logInfoAndPrint("Block didn't fit anywhere.");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!fromChainFile) {
            writeBlockToFile(block);
        }
        return true;
    }


    /**
     * Writes a block to the blockchain file
     *
     * @return boolean Whether or not the write was successful
     */
    public boolean writeBlockToFile(Block block) {
        logInfoAndPrint("Writing a block to the chainfile...");
        try (FileWriter writer = new FileWriter(dbFolder + "/blockchain-DONOTTOUCH.dat", true);
             BufferedWriter bufferedWriter = new BufferedWriter(writer);
             PrintWriter out = new PrintWriter(bufferedWriter)) {
            out.println(block.getRawBlock());
        } catch (IOException ioe) {
            getLogger().severe("Unable to save block to database (chainfile)!");
            System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + ANSI_RED + "Unable to write block to database (" + ioe.getMessage() + ")" + ANSI_RESET);
            return false;
        }
        return true;
    }

    /**
     * Saves the entire blockchain to a file
     *
     * @param dbFolder Folder to save the blockchain file in
     * @return boolean Whether saving was successful
     */
    public boolean saveToFile(String dbFolder) {
        try {
            PrintWriter out = new PrintWriter(new File(dbFolder + "blockchain-DONOTTOUCH.dat"));
            for (int i = 0; i < chains.size(); i++) {
                for (int j = 0; j < chains.get(i).size(); j++) {
                    out.println(chains.get(i).get(j).getRawBlock());
                }
            }
            out.close();
        } catch (IOException ioe) {
            getLogger().severe("Unable to write blockchain file (" + ioe.getMessage() + ")");
            System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + ANSI_RED + "Unable to write blockchain file" + ANSI_RESET);
            ioe.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Gets all relevant transaction involving addr
     *
     * @param addr The address to search through the tx pool for
     * @return ArrayList<String> All transactions in the form blockindex:sender:amount:reciever
     * @see Block#getTransactionsInvolvingAddress(String)
     */
    public ArrayList<String> getAllTransactionsInvolvingAddress(String addr) {
        int longestChainLen = 0;
        int longestChainIndex = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainLen) {
                longestChainIndex = i;
                longestChainLen = chains.get(i).size();
            }
        }
        ArrayList<Block> longestChain = chains.get(longestChainIndex);
        ArrayList<String> allTxs = new ArrayList<>();
        for (int i = 0; i < longestChain.size(); i++) {
            ArrayList<String> allFromBlock = longestChain.get(i).getTransactionsInvolvingAddress(addr);
            for (int j = 0; j < allFromBlock.size(); j++) {
                allTxs.add(longestChain.get(i).blockIndex + ":" + allFromBlock.get(j));
            }
        }
        return allTxs;
    }


    /**
     * Passthrough to the LedgerManager
     *
     * @param addr Address to check the balance of
     * @return double The balance of addr
     */
    public double getAddressBalance(String addr) {
        return ledgerManager.getAddressBalance(addr);
    }


}
