package io.codepace.cozy.db;

import io.codepace.cozy.LedgerManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;

import static io.codepace.cozy.Util.*;

/**
 * Only one Blockchain object is created per instance of the daemon. It keeps track of ALL possible chains, and internally handles chain reorganization.
 * The decision to put LedgerManager as an object owned by Blockchain was for purposes of simplicity--there's only one Blockchain, and only one LedgerManager.
 * The Blockchain has fork management integrated, and should be able to appropriately handle any unexpected circumstances. Fork management is one of
 * the major things I'm watching for during 0.2.0. If you want to try to fork the network, please do. I'm sure there are ways.
 * <p>
 * As the blockchain has the most up-to-date information about blockchain data, it makes perfect sense for the ledger, which is based purely on the blockchain,
 * to be managed by the Blockchain object. Initial plans were to have separate Blockchain objects for each fork in a chainbut the overhead of cloning Blockchain
 * objects seemed unwarranted. Significant optimization still needs to be done on the fork management--climbing all the way down the shorter chain and back up
 * the longer chain is NOT a permanant solution and should be improved.
 * <p>
 * Additionally, there is no need to store identical blocks between multiple forks. The overhead of a fork suddenly requiring double the blockchain storage is
 * unacceptable. It works, I think. But unacceptable for production code, so that'll certainly change.
 * <p>
 * As blocks are added to the blockchain, the ledger is updated. While working beautifully in small-scale testing, I'm sure the signature count synchronization
 * between signed transactions and blocks will trip up at some point, and send the Blockchain object into either a loop of dispair, or an irrecoverable error.
 * Either is equally frightening.
 * <p>
 * You'll notice a loop which appears to retry transactions up to 10,000 times. Transactions must be executed in order--two transactions from the same address
 * need to go in order of signature index, otherwise the storage space required to maintain all used signature indexes would be astronomical.
 * <p>
 * As a caveat to the Merkle Tree signature scheme, Lamport Key reuse creates the potential for double-spend attacks, so once a Lamport Keypair is used, the network
 * rejects all future signatures from that keypair. Important to keep this in mind--
 */
public class Blockchain {
    ArrayList<ArrayList<Block>> chains = new ArrayList<>();

    private ArrayList<Block> blockQueue;

    public LedgerManager ledgerManager;

    private String dbFolder;

    private boolean gotGenesisBlock = false;

    /**
     * Constructor for Blockchain object. A Blockchain object represents an entire chain of blocks. Only one is created
     * in the entire execution of the program. All blocks will be added individually and in-order.
     * <p>
     * This blockchain object will handle all forks--it keeps a record of forks for ten blocks. After a fork falls more
     * than 10 blocks behind, it is deleted.
     * <p>
     * Blocks are stored in a 2D ArrayList, where one dimension is the blockchain 'version' (forks), and the other dimension is the blocks.
     *
     * @param dbFolder Folder for database to be contained inside
     */
    public Blockchain(String dbFolder) {
        this.dbFolder = dbFolder;
        this.ledgerManager = new LedgerManager(dbFolder + "/AccountBalances.bal");
        this.blockQueue = new ArrayList<>();
    }

    /**
     * Returns the length of the tallest tree
     *
     * @return int Length of longest tree in blockchain
     */
    public int getBlockchainLength() {
        int longestChain = 0;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChain) {
                longestChain = chains.get(i).size();
            }
        }
        return longestChain;
    }

    /**
     * Returns the difficulty of the latest block in the longest chain.
     *
     * @return long Curreny difficulty on longest chain
     */
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

    /**
     * This method is called periodically to attempt to add all queued blocks to the blockchain.
     */
    public void tryBlockQueue() {
        boolean addedABlock = false;
        do //Some blocks in the queue may be attempted before other dependency blocks, so while we are able to add blocks, we will continue to add them.
        {
            addedABlock = false;
            for (int i = 0; i < blockQueue.size(); i++) {
                if (blockQueue.get(i).validateBlock(this)) {
                    if (addBlock(blockQueue.get(i), false)) {
                        addedABlock = true;
                        blockQueue.remove(i);
                        i--; //Compensate for changing ArrayList size, don't want to skip an element!
                    }
                } else {
                    blockQueue.remove(i);
                }
            }
        } while (addedABlock);
    }

    /**
     * Retrieves the block at blockNum (starting from 0) from the longest chain.
     *
     * @param blockNum The block number to retrieve
     * @return Block Block at blockNum in longest chain
     */
    public Block getBlock(int blockNum) {
        int longestChainLength = 0;
        int longestChain = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainLength) {
                longestChain = i;
                longestChainLength = chains.get(i).size();
            }
        }
        return chains.get(longestChain).get(blockNum);
    }

    /**
     * This method attempts to add a block to the blockchain. No upstream handling is required to make sure the block is valid,
     * all of that is handled here. Additionally, the block will be automatically placed onto the correct fork, or a new fork will
     * be made if necessary.
     *
     * @param block              Block to add to the blockchain
     * @param fromBlockchainFile Whether the added block was read in from file; aka !fromBlockchainFile is whether to write it back out to the db.
     * @return boolean Whether adding the block was unsuccessful. Most common source of returning false is a block that doesn't verify.
     */
    public boolean addBlock(Block block, boolean fromBlockchainFile) {
        System.out.println("Attempting to add block " + block.blockNum + " with hash " + block.blockHash);
        try {
            boolean isPOS = false;
            if (block.difficulty == 100000) // 0.2.0 Hard-coded PoS difficulty.
            {
                isPOS = true;
            }
            if (block.difficulty != 150000 && !isPOS) //0.2.0 Hard-coded PoW difficulty. 1 in 150000 nonces will win. So a certificate with 15,000 nonces has a 10% chance of winning, etc.
            {
                System.out.println("Block detected with wrong difficulty");
                return false;
            }
            //A bit of cleanup--remove chains that are more than 10 blocks shorter than the largest chain.
            int largestChainLength = 0;
            ArrayList<Block> largestChain = new ArrayList<>();
            String largestChainLastBlockHash = "";
            for (int i = 0; i < chains.size(); i++) {
                if (chains.get(i).size() > largestChainLength) {
                    largestChain = chains.get(i);
                    largestChainLength = chains.get(i).size();
                    largestChainLastBlockHash = chains.get(i).get(chains.get(i).size() - 1).blockHash;
                }
            }
            //Now we have the longest chain, we remove any chains that are less than largestChainLength - 10 in length.
            for (int i = 0; i < chains.size(); i++) {
                if (chains.get(i).size() < largestChainLength - 10) {
                    chains.remove(i);
                    i--; //Compensate for resized ArrayList!
                }
            }
            if (!block.validateBlock(this)) {
                System.out.println("Block validation failed!");
                return false; //Block is not a valid block. Don't add it!
            }
            //Block looks fine on its own--we don't know how it's going to play with the chain. If the block's number is larger than the largest chain + 1, we'll put the block in a queue to attempt to add later.
            //Block numbering starts at 0.
            if (block.blockNum > largestChainLength) {
                //Add it to the queue.
                blockQueue.add(block);
                /*
                 * In the future, the addBlock() method may be changed to return an int, with values representing things like block above existing heights, validation error, block not on any chains, etc.
                 * For now, the boolean indicates simply whether immediate addition of the block to some internal blockchain was successful.
                 */
                System.out.println("Block " + block.blockNum + " with starting hash " + block.blockHash.substring(0, 20) + " added to queue...");
                System.out.println("LargestChainLength: " + largestChainLength);
                System.out.println("block.blockNum: " + block.blockNum);
                return false; //Block wasn't added onto any chain (yet)
            }
            //If no chains exist and this is the first block, we'll create our first chain:
            if (!gotGenesisBlock) {
                gotGenesisBlock = true;
                chains.add(new ArrayList<>());
                chains.get(0).add(block);
                largestChain = chains.get(0);
                largestChainLastBlockHash = block.blockHash;
                if (ledgerManager.lastBlockNum < 0) {
                    //We can't directly assign transactionsToApply to block.transactions as we are going to edit it, and we don't want to delete transactions from the actual block.
                    ArrayList<String> transactionsToApply = new ArrayList<>(block.transactions);
                    int loopCount = 0;
                    int transactionsApplied = 0;
                    //Yippee let's add our first chunk of transactions if we need to!
                    while (transactionsToApply.size() > transactionsApplied && !transactionsToApply.get(0).equals("")) {
                        loopCount++;
                        for (int k = 0; k < transactionsToApply.size(); k++) {
                            if (ledgerManager.executeTransaction(transactionsToApply.get(k))) {
                                transactionsToApply.remove(k); //Executed correctly
                                k--; //Compensate for changed ArrayList size
                            }
                        }
                        if (loopCount > 10000) {
                            System.out.println("Infinite block detected! Hash: " + block.blockHash + " and height: " + block.blockNum);
                            System.out.println(transactionsToApply.size());
                            System.exit(-1);
                        }
                    }
                }

                ledgerManager.adjustAddressBalance(chains.get(0).get(0).certificate.redeemAddress, 100); //Pay mining fee
                ledgerManager.adjustAddressSignatureCount(chains.get(0).get(0).certificate.redeemAddress, 1);
                if (!fromBlockchainFile) {
                    writeBlockToFile(block);
                }
                return true;
            }
            //Initially, check for duplicate blocks
            for (ArrayList<Block> chain1 : chains) {
                if (chain1.get(chain1.size() - 1).blockHash.equals(block.blockHash)) {
                    //Duplicate block; block has already been added. This happens all the time, as multiple peers can all broadcast the same block.
                    System.out.println(ANSI_CYAN + "[p2p/node] " + ANSI_RESET + "- Duplicate block received from peer");
                    return false;
                }
            }
            //Then, we will see whether it goes well onto the ends of any existing chains.
            for (ArrayList<Block> chain : chains) {
                //Block numbering starts at 0
                System.out.println(ANSI_YELLOW + "[node] " + ANSI_RESET + "- Previous block hash according to chain: " + chain.get(chain.size() - 1).blockHash);
                System.out.println(ANSI_YELLOW + "[node] " + ANSI_RESET + "- Previous block hash according to added block: " + block.previousBlockHash);
                System.out.println(ANSI_YELLOW + "[node] " + ANSI_RESET + "- Selected chain size: " + chain.size());
                System.out.println(ANSI_YELLOW + "[node] " + ANSI_RESET + "- Should be equal to block num: " + block.blockNum);
                if (chain.get(chain.size() - 1).blockHash.equals(block.previousBlockHash) && chain.size() == block.blockNum) //Great! New block stacks nicely onto one of the other blocks.
                {
                    chain.add(block);
                    //We might have created a longer fork! We need to check.
                    if (chain.size() > largestChainLength) //We just created a longer chain--but it might be the original that we added to!
                    {
                        if (!chain.get(chain.size() - 2).blockHash.equals(largestChainLastBlockHash)) //Then we didn't stack onto the correct chain... We need to reverse some blocks in the ledger
                        {
                            //Future implementations will be MUCH more efficient--they'll reverse down the fork and ride it back up.
                            //However, during the developmental time squeeze that is two hours before 0.2.01 launch when I realized the logic I had here wasn't good, this seemed like a great idea.
                            for (int j = largestChain.size() - 1; j > 0; j--) {
                                ArrayList<String> transactionsToReverse = largestChain.get(j).transactions;
                                for (String aTransactionsToReverse : transactionsToReverse) {
                                    ledgerManager.reverseTransaction(aTransactionsToReverse);
                                }
                                ledgerManager.adjustAddressBalance(largestChain.get(j).certificate.redeemAddress, -100); //Reverse mining income...
                                ledgerManager.adjustAddressSignatureCount(largestChain.get(j).certificate.redeemAddress, -1);
                            }
                            //The ledger is completely empty, basically. Good job. Efficiency at its finest. Will be fixed during upcoming refactoring.
                            for (Block aChain : chain) {
                                //We can't directly assign transactionsToApply to block.transactions as we are going to edit it, and we don't want to delete transactions from the actual block.
                                ArrayList<String> transactionsToApply = new ArrayList<>(block.transactions);
                                int loopCount = 0;
                                while (transactionsToApply.size() > 0) {
                                    loopCount++;
                                    for (int k = 0; k < transactionsToApply.size(); k++) {
                                        System.out.println(ANSI_YELLOW + "[node] " + ANSI_RESET + "- Attempting to execute transaction: " + transactionsToApply.get(k).substring(0, 45) + "..." + transactionsToApply.get(k).substring(transactionsToApply.get(k).length() - 20));
                                        if (ledgerManager.executeTransaction(transactionsToApply.get(k))) {
                                            System.out.println(ANSI_YELLOW + "[node] " + ANSI_RESET + "- Successfully executed transaction!");
                                            transactionsToApply.remove(k); //Executed correctly
                                            k--; //Compensate for changed ArrayList size
                                        } else {
                                            System.out.println(ANSI_RED + "[node] " + ANSI_RESET + "- Didn't execute transaction...");
                                        }
                                    }
                                    if (loopCount > 10000) {
                                        System.out.println(ANSI_RED + "[node] " + ANSI_RESET + "- Infinite block detected! Hash: " + aChain.blockHash + " and height: " + aChain.blockNum);
                                        System.exit(-1);
                                    }
                                }
                                ledgerManager.adjustAddressBalance(aChain.certificate.redeemAddress, 100); //Pay mining fee
                                ledgerManager.adjustAddressSignatureCount(aChain.certificate.redeemAddress, 1);
                            }
                        } else //Great, we added to the longest chain!
                        {
                            //We need to execute all the transactions...
                            if (ledgerManager.lastBlockNum < block.blockNum) //If the ledger was read in from a file, then we don't add the transactions again!
                            {
                                //We can't directly assign transactionsToApply to block.transactions as we are going to edit it, and we don't want to delete transactions from the actual block.
                                ArrayList<String> transactionsToApply = new ArrayList<>(block.transactions);
                                int loopCount = 0;
                                int completedTransactions = 0;
                                while (transactionsToApply.size() > completedTransactions) {
                                    loopCount++;
                                    for (int k = 0; k < transactionsToApply.size(); k++) {
                                        if (ledgerManager.executeTransaction(transactionsToApply.get(k))) {
                                            transactionsToApply.remove(k); //Executed correctly
                                            k--; //Compensate for changed ArrayList size
                                        } else if (transactionsToApply.get(k).equals("")) {
                                            completedTransactions++;
                                        }
                                    }
                                    if (loopCount > 10000) {
                                        System.out.println(ANSI_RED + "[node] " + ANSI_RESET + "- Infinite block detected! Hash: " + block.blockHash + " and height: " + block.blockNum);
                                        System.exit(-1);
                                    }
                                }
                                ledgerManager.adjustAddressBalance(block.certificate.redeemAddress, 100);
                                ledgerManager.adjustAddressSignatureCount(block.certificate.redeemAddress, 1);
                            }
                        }
                    }
                    if (!fromBlockchainFile) {
                        writeBlockToFile(block);
                    }
                    return true;
                } else {
                    System.out.println("Something went wrong with stacking...");
                    System.out.println(chain.get(chain.size() - 1).blockHash);
                    System.out.println(block.previousBlockHash);
                }
            }
            boolean foundPlaceForBlock = false;
            for (int i = 0; i < chains.size(); i++) {
                ArrayList<Block> tempChain = chains.get(i); //No working with this ArrayList indirectly; we've got a lot of work to do!
                for (int j = tempChain.size() - 11; j < tempChain.size(); j++) {
                    if (j < 0) //Pathetically-early network forks handled
                    {
                        j = 0; //Negative blocks aren't one of the features of 2.0
                    }
                    if (tempChain.get(j).blockHash.equals(block.previousBlockHash)) //Found where it forked!
                    {
                        ArrayList<Block> newChain = new ArrayList<>();
                        //Add all of the old chain until we get to the fork
                        for (int k = 0; k <= j; k++) {
                            newChain.add(tempChain.get(k));
                        }
                        //Add the fork block, which is an alternative to tempChain.get(j + 1)
                        newChain.add(block);
                        foundPlaceForBlock = true;
                        j = tempChain.size();
                        i = chains.size();
                        //As block didn't fit onto the end of another chain, we don't need to check for the new fork being longer.
                    }
                }
            }
            if (foundPlaceForBlock) //Was put on a fork. We need to make sure that the dominant blockchain is represented by the Ledger.
            {
                //Might be stuff here in the future...
            } else {
                //Didn't fit on any existing blockchain. Probably really old.
                System.out.println("Block didn't fit! :(");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!fromBlockchainFile) {
            writeBlockToFile(block);
        }
        return true;
    }

    /**
     * Writes a block to the blockchain file
     *
     * @return boolean Whether write was successful
     */
    public boolean writeBlockToFile(Block block) {
        System.out.println("Writing a block to file...");
        try (FileWriter fileWriter = new FileWriter(dbFolder + "/blockchain.dta", true);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
             PrintWriter out = new PrintWriter(bufferedWriter)) {
            out.println(block.getRawBlock());
        } catch (Exception e) {
            System.out.println(ANSI_RED + "[node] " + ANSI_RESET + "- ERROR: UNABLE TO SAVE BLOCK TO DATABASE!");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Saves entire blockchain to a file, useful to save the state of the blockchain so it doesn't have to be redownloaded later.
     * Blockchain is stored to a file called "blockchain.dta" inside the provided dbFolder.
     *
     * @param dbFolder Folder to save blockchain file in
     * @return boolean Whether saving to file was successful.
     */
    public boolean saveToFile(String dbFolder) {
        try {
            PrintWriter out = new PrintWriter(new File(dbFolder + "/blockchain.dta"));
            for (ArrayList<Block> chain : chains) {
                for (int j = 0; j < chain.size(); j++) {
                    out.println(chain.get(j).getRawBlock());
                }
            }
            out.close();
        } catch (Exception e) {
            System.out.println(ANSI_RED + "[node] " + ANSI_RESET + "- [CRITICAL ERROR] UNABLE TO WRITE BLOCKCHAIN FILE \"" + dbFolder + "/blockchain.dta!");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Calls getTransactionsInvolvingAddress() on all Block objects in the current Blockchain to get all relevant transactions.
     *
     * @param addressToFind Address to search through all block transaction pools for
     * @return ArrayList<String> All transactions in simplified form blocknum:sender:amount:receiver of
     */
    public ArrayList<String> getAllTransactionsInvolvingAddress(String addressToFind) {
        int longestChainLength = 0;
        int longestChainNum = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainLength) {
                longestChainNum = i;
                longestChainLength = chains.get(i).size();
            }
        }
        ArrayList<Block> longestChain = chains.get(longestChainNum);

        ArrayList<String> allTransactions = new ArrayList<>();

        for (int i = 0; i < longestChain.size(); i++) {
            ArrayList<String> transactionsFromBlock = longestChain.get(i).getTransactionsInvolvingAddress(addressToFind);
            for (int j = 0; j < transactionsFromBlock.size(); j++) {
                allTransactions.add(longestChain.get(i).blockNum + ":" + transactionsFromBlock.get(j));
            }
        }
        return allTransactions;
    }

    /**
     * Passthrough method to the LedgerManager object.
     *
     * @param address Address to check balance of
     * @return long Balance of address
     */
    public long getAddressBalance(String address) {
        return ledgerManager.getAddressBalance(address);
    }
}