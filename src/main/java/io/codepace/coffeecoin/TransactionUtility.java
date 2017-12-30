package io.codepace.coffeecoin;

import java.util.ArrayList;

import static io.codepace.coffeecoin.Util.*;

public class TransactionUtility {

    public static boolean isTransactionValid(String tx){
        getLogger().info("Checking validity of transaction: " + tx);
        MerkleAddressUtility merkle = new MerkleAddressUtility();
        try{
            String[] txParts = tx.split("::");
            if (txParts.length % 2 == 0 || txParts.length < 6){
                //XXX
                return false;
            }
            for (int i = 0; i < txParts.length - 2; i+=2) {
                if(!merkle.isAddressFormattedCorrectly(txParts[i])){
                    return false; // Invalid address
                }

            }

            double inAmount = Double.parseDouble(txParts[1]);
            double outAmount = 0D;
            for (int i = 3; i < txParts.length - 2; i+=2) {
                if (Double.parseDouble(txParts[i]) <= 0){
                    return false;
                }
                outAmount += Double.parseDouble(txParts[i]);
            }

            if (inAmount - outAmount < 0){
                return false; // No sudden creation of coins
            }

            String txData = "";
            for (int i = 0; i < txParts.length - 2; i++) {
                txData += txParts[i] + "::";
            }
            txData = txData.substring(0, txData.length() - 1);
            if(!merkle.verifyMerkleSignature(txData, txParts[txParts.length - 2], txParts[0], Long.parseLong(txParts[txParts.length - 1]))){
                return false; // Signature doesn't match
            }
        } catch (Exception e){
            return false;
        }
        return true;
    }

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
        ArrayList<String> sortedTransactions = new ArrayList<String>();
        for (int i = 0; i < transactionsToSort.size(); i++)
        {
            System.out.println("spin1");
            if (sortedTransactions.size() == 0)
            {
                sortedTransactions.add(transactionsToSort.get(0));
            }
            else
            {
                String address = transactionsToSort.get(i).split("::")[0];
                long index = Long.parseLong(transactionsToSort.get(i).split("::")[transactionsToSort.get(i).split("::").length  - 1]);
                boolean added = false;
                for (int j = 0; j < sortedTransactions.size(); j++)
                {
                    System.out.println("spin2");
                    if (sortedTransactions.get(j).split("::")[0].equals(address))
                    {
                        String[] parts = sortedTransactions.get(j).split("::");
                        int indexToGrab = parts.length - 1;
                        String sigIndexToParse = sortedTransactions.get(j).split("::")[indexToGrab];
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
     * @return String The full transaction, formatted for use in the Curecoin 2.0 network, including the signature and signature index. Returns null if transaction is incorrect for any reason.
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
