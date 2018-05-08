package io.codepace.cozy;

import java.util.ArrayList;

import static io.codepace.cozy.Util.*;
/**
 * TransactionUtility simplifies a few basic tasks dealing with transaction parsing and verification.
 */
public class TransactionUtility
{
    /**
     * Tests whether a transaction is valid. Doesn't test account balances, but tests formatting and signature verification.
     *
     * @param transaction Transaction String to test
     *
     * @return boolean Whether the transaction is formatted and signed correctly
     */
    public static boolean isTransactionValid(String transaction)
    {
        System.out.println("Checking transaction: " + transaction);
        MerkleAddressUtility merkleAddressUtility = new MerkleAddressUtility();
        try
        {
            String[] transactionParts = transaction.split(";");
            if (transactionParts.length % 2 != 0 || transactionParts.length < 6)
            {
                return false; //Each address should line up with an output, and no explicit transaction is possible with fewer than six parts (see above)
            }
            for (int j = 0; j < transactionParts.length - 2; j+=2) //Last two parts are signatureData and signatureIndex,respectively
            {
                if (!merkleAddressUtility.isAddressFormattedCorrectly(transactionParts[j]))
                {
                    return false; //Address in transaction is misformatted
                }
            }
            long inputAmount = Long.parseLong(transactionParts[1]);
            long outputAmount = 0L;
            for (int j = 3; j < transactionParts.length - 2; j+=2) //Element 3 (4th element) and each subsequent odd-numbered index up to transactionParts should be an output amount.
            {
                if (Long.parseLong(transactionParts[j]) <= 0)
                {
                    return false;
                }
                outputAmount += Long.parseLong(transactionParts[j]);
            }
            if (inputAmount - outputAmount < 0)
            {
                return false; //Coins can't be created out of thin air!
            }
            String transactionData = "";
            for (int j = 0; j < transactionParts.length - 2; j++)
            {
                transactionData += transactionParts[j] + ";";
            }
            transactionData = transactionData.substring(0, transactionData.length() - 1);
            if (!merkleAddressUtility.verifyMerkleSignature(transactionData, transactionParts[transactionParts.length - 2], transactionParts[0], Long.parseLong(transactionParts[transactionParts.length - 1])))
            {
                return false; //Siganture doesn't match
            }
        } catch (Exception e) //Likely an error parsing a Long or performing some String manipulation task. Maybe array bounds exceptions.
        {
            return false;
        }
        return true;
    }

    /**
     * Transactions on the Cozycoin 2.0 network from the same address must occur in a certain order, dictated by the signature index.
     * As such, We want to order all transactions from the same address in order.
     * The order of transactions from different addresses does not matter--coins will not be received and spent in the same transaction.
     *
     * @param transactionsToSort ArrayList<String> containing String representations of all the addresses to sort
     *
     * @return ArrayList<String> All of the transactions sorted in order for block inclusion, with any self-invalidating transactions removed.
     */
    public static ArrayList<String> sortTransactionsBySignatureIndex(ArrayList<String> transactionsToSort)
    {
        for (int i = 0; i < transactionsToSort.size(); i++)
        {
            if (!isTransactionValid(transactionsToSort.get(i)))
            {
                transactionsToSort.remove(i);
                i--; //Compensate for changing ArrayList size
            }
        }
        ArrayList<String> sortedTransactions = new ArrayList<>();
        for (int i = 0; i < transactionsToSort.size(); i++)
        {
            System.out.println("spin1");
            if (sortedTransactions.size() == 0)
            {
                sortedTransactions.add(transactionsToSort.get(0));
            }
            else
            {
                String address = transactionsToSort.get(i).split(";")[0];
                long index = Long.parseLong(transactionsToSort.get(i).split(";")[transactionsToSort.get(i).split(";").length  - 1]);
                boolean added = false;
                for (int j = 0; j < sortedTransactions.size(); j++)
                {
                    System.out.println("spin2");
                    if (sortedTransactions.get(j).split(";")[0].equals(address))
                    {
                        String[] parts = sortedTransactions.get(j).split(";");
                        int indexToGrab = parts.length - 1;
                        String sigIndexToParse = sortedTransactions.get(j).split(";")[indexToGrab];
                        long existingSigIndex = Long.parseLong(sigIndexToParse);
                        if (index < existingSigIndex)
                        {
                            //Insertion should occur before the currently-studied element
                            sortedTransactions.add(j, transactionsToSort.get(i));
                            added = true;
                            break;
                        }
                        else if (index == existingSigIndex)
                        {
                            //This should never happen--double-signed transaction. Discard the new one!
                            j = sortedTransactions.size();
                        }
                    }
                }
                if (!added)
                {
                    sortedTransactions.add(transactionsToSort.get(i));
                }
            }
        }
        return sortedTransactions;
    }

    /**
     * Signs a Transaction built with the provided sending address and amount, and destination address(es) and amount(s).
     *
     * @param privateKey The private key for inputAddress
     * @param inputAddress Address to send coins from
     * @param inputAmount Total amount to send
     * @param outputAddresses Addresses to send coins to
     * @param outputAmounts Amounts lined up with addresses to send
     * @param index The signature index to use
     *
     * @return String The full transaction, formatted for use in the Cozycoin 2.0 network, including the signature and signature index. Returns null if transaction is incorrect for any reason.
     */
    public static String signTransaction(String privateKey, String inputAddress, long inputAmount, ArrayList<String> outputAddresses, ArrayList<Long> outputAmounts, long index)
    {
        if (inputAddress == null || outputAddresses == null || inputAmount <= 0) //Immediate red flags
        {
            return null;
        }
        if (outputAddresses.size() != outputAmounts.size()) //Output addresses and amounts go together, and so each ArrayList must be the same size
        {
            return null;
        }
        String fullTransaction = inputAddress + ";" + inputAmount; //The start of the Transaction
        for (int i = 0; i < outputAddresses.size(); i++) //Didn't bother doing address checks here, as they will be conducted in isTransactionValid()
        {
            fullTransaction += ";" + outputAddresses.get(i) + ";" + outputAmounts.get(i);
        }
        fullTransaction += ";" + new MerkleAddressUtility().getMerkleSignature(fullTransaction, privateKey, index, inputAddress) + ";" + index; //Now it's actually the 'full transaction'
        if (isTransactionValid(fullTransaction))
        {
            return fullTransaction;
        }
        return null;
    }
}