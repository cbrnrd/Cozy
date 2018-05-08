package io.codepace.cozy;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static io.codepace.cozy.Util.*;

public class LedgerManager {
    private File addressDatabase;
    public String addressDatabaseName;
    private ConcurrentHashMap<String, Long> addressBalances;
    private ConcurrentHashMap<String, Integer> addressSignatureCounts;
    private ArrayList<String> addresses;
    private MerkleAddressUtility merkleAddressUtility = new MerkleAddressUtility();
    public int lastBlockNum = -1;

    /**
     * Constructor for LedgerManager. All that is needed is the path to the address database file.
     * <p>
     * All peers on the network should have an identical copy of the LedgerManager object at any given time.
     * Due to latency and whatnot, that doesn't happen, but a fully synchronized network would have the same ledger on every node at any time.
     *
     * @param addressDatabase The String representation of the address database file
     */
    public LedgerManager(String addressDatabase) {
        this.addressDatabaseName = addressDatabase;
        this.addressDatabase = new File(addressDatabase);
        this.addresses = new ArrayList<>();
        addressBalances = new ConcurrentHashMap<String, Long>(16384);
        addressSignatureCounts = new ConcurrentHashMap<String, Integer>(16384);
        if (this.addressDatabase.exists()) {
            try {
                Scanner readAddressDatabase = new Scanner(this.addressDatabase);
                this.lastBlockNum = Integer.parseInt(readAddressDatabase.nextLine());
                while (readAddressDatabase.hasNextLine()) {
                    String input = readAddressDatabase.nextLine();
                    if (input.contains(":")) {
                        String[] parts = input.split(":");
                        String address = parts[0];
                        if (merkleAddressUtility.isAddressFormattedCorrectly(address)) {
                            try {
                                long addressBalance = Long.parseLong(parts[1]);
                                int currentSignatureCount = Integer.parseInt(parts[2]);
                                addressBalances.put(address, addressBalance);
                                addressSignatureCounts.put(address, currentSignatureCount);
                                addresses.add(address);
                            } catch (Exception e) {
                                System.out.println("[CRITICAL ERROR] parsing line \"" + input + "\"!");
                                e.printStackTrace();
                            }
                        }
                    }
                }
                readAddressDatabase.close();
            } catch (Exception e) {
                System.out.println("[CRITICAL ERROR] Unable to read addressDatabase file!");
                e.printStackTrace();
                System.exit(-1);
            }
        } else {
            File f = new File(addressDatabase);
            try {
                PrintWriter out = new PrintWriter(f);
                out.println("-1");
                out.close();
                this.lastBlockNum = -1; //Just in case...? Shouldn't be required.
            } catch (Exception e) {
                System.out.println("[CRITICAL ERROR] UNABLE TO WRITE LEDGER RECORD FILE!");
                e.printStackTrace();
                System.exit(-1);
            }
            System.out.println("Address Database \"" + addressDatabase + "\" does not exist! Creating...");
        }
    }

    /**
     * Hashes the entire ledger, to compare against blocks.
     *
     * @return HEX SHA256 hash of the ledger
     */
    public String getLedgerHash() {
        String ledger = "";
        for (int i = 0; i < addresses.size(); i++) {
            ledger += addresses.get(i) + ":" + addressBalances.get(addresses.get(i)) + ":" + addressSignatureCounts.get(addresses.get(i)) + "\n";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return DatatypeConverter.printHexBinary(md.digest(ledger.getBytes("UTF-8")));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[CRITICAL ERROR] Unable to generate hash of ledger! Exiting...");
            System.exit(-1);
        }
        return null;
    }

    /**
     * Sets the last block num.
     *
     * @param lastBlockNum The latest block applied to this tree
     */
    public void setLastBlockNum(int lastBlockNum) {
        this.lastBlockNum = lastBlockNum;
    }

    /**
     * This method executes a given transaction String of the format InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
     *
     * @param transaction String-formatted transaction to execute
     * @return boolean Whether execution of the transaction was successful
     */
    public boolean executeTransaction(String transaction) {
        try {
            String[] transactionParts = transaction.split(";");
            String transactionMessage = "";
            for (int i = 0; i < transactionParts.length - 2; i++) {
                transactionMessage += transactionParts[i] + ";";
            }
            transactionMessage = transactionMessage.substring(0, transactionMessage.length() - 1);
            String sourceAddress = transactionParts[0];
            String signatureData = transactionParts[transactionParts.length - 2];
            long signatureIndex = Long.parseLong(transactionParts[transactionParts.length - 1]);
            if (!merkleAddressUtility.verifyMerkleSignature(transactionMessage, signatureData, sourceAddress, signatureIndex)) {
                return false; //Signature does not sign transaction message!
            }
            if (getAddressSignatureCount(sourceAddress) + 1 != signatureIndex) {
                return false; //The signature is valid, however it isn't using the expected signatureIndex. Blocked to ensure a compromised Lamport key from a previous transaction can't be used.
            }
            if (!merkleAddressUtility.isAddressFormattedCorrectly(sourceAddress)) {
                return false; //Incorrect sending address
            }
            long sourceAmount = Long.parseLong(transactionParts[1]);
            if (getAddressBalance(sourceAddress) < sourceAmount) //sourceAddress has an insufficient balance
            {
                return false; //Insufficient balance
            }
            ArrayList<String> destinationAddresses = new ArrayList<>();
            ArrayList<Long> destinationAmounts = new ArrayList<>();
            for (int i = 2; i < transactionParts.length - 2; i += 2) //-2 because last two parts of transaction are the signature and signature index
            {
                destinationAddresses.add(transactionParts[i]);
                destinationAmounts.add(Long.parseLong(transactionParts[i + 1]));
            }
            if (destinationAddresses.size() != destinationAmounts.size()) {
                return false; //This should never happen. But if it does...
            }
            for (int i = 0; i < destinationAddresses.size(); i++) {
                if (!merkleAddressUtility.isAddressFormattedCorrectly(destinationAddresses.get(i))) {
                    return false; //A destination address is not a valid address
                }
            }
            long outputTotal = 0L;
            for (int i = 0; i < destinationAmounts.size(); i++) {
                outputTotal += destinationAmounts.get(i);
            }
            if (sourceAmount < outputTotal) {
                return false;
            }
            //Looks like everything is correct--transaction should be executed correctly
            addressBalances.put(sourceAddress, getAddressBalance(sourceAddress) - sourceAmount);
            for (int i = 0; i < destinationAddresses.size(); i++) {
                addressBalances.put(destinationAddresses.get(i), getAddressBalance(destinationAddresses.get(i)) + destinationAmounts.get(i));
            }
            adjustAddressSignatureCount(sourceAddress, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * This method reverse-executes a given transaction String of the format InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
     * Used primarily when a blockchain fork is resolved, and transactions have to be reversed that existed in the now-forked block(s).
     *
     * @param transaction String-formatted transaction to execute
     * @return boolean Whether execution of the transaction was successful
     */
    public boolean reverseTransaction(String transaction) {
        try {
            String[] transactionParts = transaction.split(";");
            String transactionMessage = "";
            for (int i = 0; i < transactionParts.length - 2; i++) {
                transactionMessage += transactionParts[i] + ";";
            }
            transactionMessage = transactionMessage.substring(0, transactionMessage.length() - 1);
            String sourceAddress = transactionParts[0];
            String signatureData = transactionParts[transactionParts.length - 2];
            long signatureIndex = Long.parseLong(transactionParts[transactionParts.length - 1]);
            if (!merkleAddressUtility.verifyMerkleSignature(transactionMessage, signatureData, sourceAddress, signatureIndex)) {
                return false; //Signature does not sign transaction message!
            }
            if (getAddressSignatureCount(sourceAddress) + 1 != signatureIndex) {
                //We're not concerned with this when reversing!
                //return false; //The signature is valid, however it isn't using the expected signatureIndex. Blocked to ensure a compromised Lamport key from a previous transaction can't be used.
            }
            if (!merkleAddressUtility.isAddressFormattedCorrectly(sourceAddress)) {
                return false; //Incorrect sending address
            }
            long sourceAmount = Long.parseLong(transactionParts[1]);
            ArrayList<String> destinationAddresses = new ArrayList<>();
            ArrayList<Long> destinationAmounts = new ArrayList<>();
            for (int i = 2; i < transactionParts.length - 2; i += 2) //-2 because last two parts of transaction are the signature and signature index
            {
                destinationAddresses.add(transactionParts[i]);
                destinationAmounts.add(Long.parseLong(transactionParts[i + 1]));
            }
            if (destinationAddresses.size() != destinationAmounts.size()) {
                return false; //This should never happen. But if it does...
            }
            for (int i = 0; i < destinationAddresses.size(); i++) {
                if (!merkleAddressUtility.isAddressFormattedCorrectly(destinationAddresses.get(i))) {
                    return false; //A destination address is not a valid address
                }
            }
            long outputTotal = 0L;
            for (int i = 0; i < destinationAmounts.size(); i++) {
                outputTotal += destinationAmounts.get(i);
            }
            if (sourceAmount < outputTotal) {
                return false;
            }
            for (int i = 0; i < destinationAmounts.size(); i++) {
                if (getAddressBalance(destinationAddresses.get(i)) < destinationAmounts.get(i)) {
                    System.out.println("[CRITICAL ERROR] ADDRESS " + destinationAddresses.get(i) + " needs to return " + destinationAmounts.get(i) + " but only has " + getAddressBalance(destinationAddresses.get(i))); //BIG PROBLEM THIS SHOULD NEVER HAPPEN
                    return false; //One of the addresses has an insufficient balance to reverse!
                }
            }
            //Looks like everything is correct--transaction should be reversed correctly
            addressBalances.put(sourceAddress, getAddressBalance(sourceAddress) + sourceAmount);
            for (int i = 0; i < destinationAddresses.size(); i++) {
                addressBalances.put(destinationAddresses.get(i), getAddressBalance(destinationAddresses.get(i)) - destinationAmounts.get(i));
            }
            adjustAddressSignatureCount(sourceAddress, -1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Writes ledger to file.
     *
     * @return boolean Whether writing the ledger to the disk was successful.
     */
    public boolean writeToFile() {
        try {
            PrintWriter out = new PrintWriter(addressDatabase);
            for (int i = 0; i < addresses.size(); i++) {
                out.println(addresses.get(i) + ":" + addressBalances.get(addresses.get(i)) + ":" + addressSignatureCounts.get(addresses.get(i)));
            }
            out.close();
        } catch (Exception e) {
            System.out.println("[CRITICAL ERROR] UNABLE TO WRITE DB FILE!");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Returns the last-used signature index of an address.
     *
     * @param address Address to retrieve the latest index for
     * @return int Last signature index used by address
     */
    public int getAddressSignatureCount(String address) {
        if (addressSignatureCounts.containsKey(address)) {
            return addressSignatureCounts.get(address);
        } else {
            return -1;
        }
    }

    /**
     * Adjusts an address's signature count.
     *
     * @param address    Address to adjust
     * @param adjustment Amount to adjust address's signature count by. This can be negative.
     * @return boolean Whether the adjustment was successful
     */
    public boolean adjustAddressSignatureCount(String address, int adjustment) {
        int oldCount = getAddressSignatureCount(address);
        if (oldCount + adjustment < 0) //Adjustment is negative with an absolute value larger than oldBalance
        {
            return false;
        }
        return updateAddressSignatureCount(address, oldCount + adjustment);
    }

    /**
     * Updates an address's signature count.
     *
     * @param address  Address to update
     * @param newCount New signature index to use
     * @return boolean Whether the adjustment was successful
     */
    public boolean updateAddressSignatureCount(String address, int newCount) {
        try {
            if (addressSignatureCounts.containsKey(address)) {
                addressSignatureCounts.put(address, newCount);
            } else {
                addressBalances.put(address, 0L);
                addressSignatureCounts.put(address, newCount);
                addresses.add(address);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Returns the address balance for a given address.
     *
     * @param address Address to check balance of
     * @return long Balance of address
     */
    public long getAddressBalance(String address) {
        if (addressBalances.containsKey(address)) {
            return addressBalances.get(address);
        } else {
            return 0L;
        }
    }

    /**
     * Adjusts the balance of an address by a given adjustment, which can be positive or negative.
     *
     * @param address    Address to adjust the balance of
     * @param adjustment Amount to adjust account balance by
     * @return boolean Whether the adjustment was successful
     */
    public boolean adjustAddressBalance(String address, long adjustment) {
        long oldBalance = getAddressBalance(address);
        if (oldBalance + adjustment < 0) //Adjustment is negative with an absolute value larger than oldBalance
        {
            return false;
        }
        return updateAddressBalance(address, oldBalance + adjustment);
    }

    /**
     * Updates the balance of an address to a new amount
     *
     * @param address   Address to set the balance of
     * @param newAmount New amount to set as the balance of address
     * @return boolean Whether setting the new balance was successful
     */
    public boolean updateAddressBalance(String address, long newAmount) {
        try {
            if (addressBalances.containsKey(address)) {
                addressBalances.put(address, newAmount);
            } else {
                addressBalances.put(address, newAmount);
                addressSignatureCounts.put(address, 0);
                addresses.add(address);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}