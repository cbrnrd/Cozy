package io.codepace.cozy;

import io.codepace.cozy.db.Block;
import io.codepace.cozy.db.CozyDatabaseMaster;

import java.util.ArrayList;

/**
 * This class offers basic functionality for storing transactions until they make it into a block.
 * It could be just an {@link ArrayList} inside of MainClass, however it seemed easier and more OOP-ish to give it its own object.
 * Adding future functionality to pending transaction pool management is much easier when it has its own object.
 */
public class PendingTransactionContainer {
    public ArrayList<String> pendingTransactions;
    private CozyDatabaseMaster databaseMaster;

    //ArrayList holding objects that pair addresses with their pending transaction amounts, so transactions above an account's spendable balance are rejected.
    public ArrayList<StringLongPair> accountBalanceDeltaTables;

    /**
     * Constructor for PendingTransactionContainer sets up required ArrayList for holding transactions. The database manager object is passed in, for checking balances
     * when a transaction is being added.
     * @param databaseMaster The local database master
     */
    public PendingTransactionContainer(CozyDatabaseMaster databaseMaster) {
        this.databaseMaster = databaseMaster;
        this.pendingTransactions = new ArrayList<>();
        this.accountBalanceDeltaTables = new ArrayList<>();
    }

    /**
     * Adds a transaction to the pending transaction list if it is formatted correctly and accompanied by a correct signature. Does not check for account balances!
     * Rejects duplicate transactions.
     * Transaction format:
     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
     * Additional work in the future on this method will include keeping track of signature indexes and prioritizing lower-index transactions.
     *
     * @param transaction Transaction to add
     * @return boolean Whether adding the transaction was valid
     */
    public boolean addTransaction(String transaction) {
        try {
            for (int i = 0; i < pendingTransactions.size(); i++) {
                if (pendingTransactions.get(i).equals(transaction)) {
                    return false;
                }
            }
            if (!TransactionUtility.isTransactionValid(transaction)) {
                System.out.println("Throwing out a transaction deemed invalid");
                return false;
            }
            String[] transactionParts = transaction.split(";");
            //We need to check to make sure the input address isn't sending coins they don't own.
            String inputAddress = transactionParts[0];
            long inputAmount = Long.parseLong(transactionParts[1]);
            //Check for the outstanding outgoing amount for this address
            long outstandingOutgoingAmount = 0L;
            int indexOfDelta = -1;
            for (int i = 0; i < accountBalanceDeltaTables.size(); i++) {
                if (accountBalanceDeltaTables.get(i).stringToHold.equals(inputAddress)) {
                    outstandingOutgoingAmount = accountBalanceDeltaTables.get(i).longToHold;
                    indexOfDelta = i;
                    break;
                }
            }
            long previousBalance = databaseMaster.getAddressBalance(inputAddress);
            if (previousBalance < inputAmount + outstandingOutgoingAmount) {
                System.out.println("Account " + inputAddress + " tried to spend " + inputAmount + " but only had " + (previousBalance - outstandingOutgoingAmount) + " coins.");
                return false; //Account does not have the coins to spend!
            }
            if (indexOfDelta >= 0) {
                accountBalanceDeltaTables.get(indexOfDelta).longToHold += inputAmount;
            } else {
                accountBalanceDeltaTables.add(new StringLongPair(inputAddress, inputAmount)); //No existing entry in the pending delta tables, so we create an ew one
            }
            pendingTransactions.add(transaction); //Can only get to here if the transaction is valid, accounted for, and the balance checks out.
            System.out.println("Added transaction " + transaction.substring(0, 20) + "..." + transaction.substring(transaction.length() - 20, transaction.length()));
        } catch (Exception e) {
            System.out.println("An exception has occurred...");
            e.printStackTrace();
            return false;
            //e.printStackTrace();
        }
        return true;
    }

    /**
     * Self-explanatory method called whenever the daemon desires to reset the pending transaction pool to be blank.
     */
    public void reset() {
        pendingTransactions = new ArrayList<>();
        accountBalanceDeltaTables = new ArrayList<>();
    }

    /**
     * Removes an identical transaction from the pending transactions pool
     *
     * @param transaction The transaction to remove
     * @return boolean Whether removal was successful
     */
    public boolean removeTransaction(String transaction) {
        for (int i = 0; i < pendingTransactions.size(); i++) {
            if (pendingTransactions.get(i).equals(transaction)) {
                pendingTransactions.remove(i);
                return true;
            }
        }
        return false; //Transaction was not found in pending transaction pool
    }

    /**
     * This method is the most useful method in this class--it allows the mass removal of all transactions from the pending transaction pool that were included
     * in a network block, all in one call. The returned boolean is not currently utilized in MainClass, proper handling of blocks with transaction issues will be addressed
     * in a future alpha, probably 0.2.06/7 given my schedule.
     *
     * @param rawBlock The raw String representing the block holding transactions to remove
     * @return boolean Whether all transactions in the block were successfully removed
     */
    public boolean removeTransactionsInBlock(String rawBlock) {
        //This try-catch method wraps around more than it needs to, in the name of easy code management, and making colors line up nicely in my IDE.
        try {
            //We could use the raw String data, but it's easier to use a Block object to avoid repetition of code, and the verification is an added bonus.
            Block tempBlock = new Block(rawBlock);
            /* Transaction format:
             * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
             *
             * We are removing only transactions that match the exact String from the block. If the block validation fails, NO transactions are removed from the pool.
             * In a late-night coding session, not removing any transactions of an invalid block seemed like the bset idea--transactions should never be discarded
             * if they haven't made it into the blockchain, and any block that doesn't validate won't make it through Blockchain's block screening, so these transactions
             * that we aren't removing will never happen on-chain if we remove them from the pool when an invalid block says we should. Also closes a potential attack
             * vector where someone could submit false blocks in order to be a nuisance and empty the pending transaction pool.
             */
            if (!tempBlock.validateBlock(databaseMaster.blockchain)) {
                return false; //No transactions remove at all!
            }
            ArrayList<String> transactions = tempBlock.transactions;
            boolean allSuccessful = true;
            for (int i = 0; i < transactions.size(); i++) {
                if (!removeTransaction(transactions.get(i))) {
                    allSuccessful = false; //This might happen if a transaction was in a block before it made it across the network to a peer, so not always a big deal!
                }
            }
            return allSuccessful;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This method scans through all of the pending transactions to calculate the total (net) balance change pending on an address. A negative value represents
     * coins that were sent from the address in question, and a positive value represents coins awaiting confirmations to arrive.
     *
     * @param address Cozycoin 2.0 address to search the pending transaction pool for
     * @return long The pending total (net) change for the address in question
     */
    public long getPendingBalance(String address) {
        long totalChange = 0L;
        for (int i = 0; i < pendingTransactions.size(); i++) {
            String transaction = pendingTransactions.get(i);
            try {
                if (transaction.contains(address)) {
                    String[] transactionParts = transaction.split(";");
                    String senderAddress = transactionParts[0];
                    if (senderAddress.equals(address)) {
                        totalChange -= Long.parseLong(transactionParts[1]);
                    }
                    for (int j = 2; j < transactionParts.length - 2; j += 2) {
                        if (transactionParts[j].equals(address)) {
                            totalChange += Long.parseLong(transactionParts[j + 1]);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Major problem: Transaction in the pending transaction pool is incorrectly formatted!");
                System.err.println("Transaction in question: " + transaction);
            }
        }
        return totalChange;
    }
}
