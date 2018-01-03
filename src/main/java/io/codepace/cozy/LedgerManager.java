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

    private File addrDb;
    public String addrDbName;
    private ConcurrentHashMap<String, Double> addressBalances;
    private ConcurrentHashMap<String, Integer> addressSignatureCounts;
    private ArrayList<String> addrs;
    private MerkleAddressUtility merkle = new MerkleAddressUtility();
    public int lastBlockIndex = -1;

    public LedgerManager(String addrDb) {
        this.addrDbName = addrDb;
        this.addrDb = new File(addrDb);
        this.addrs = new ArrayList<String>();
        addressBalances = new ConcurrentHashMap<>(0x4000);
        addressSignatureCounts = new ConcurrentHashMap<>(0x4000);
        if (this.addrDb.exists()) {
            try {
                Scanner sc = new Scanner(this.addrDb);
                this.lastBlockIndex = Integer.parseInt(sc.nextLine());
                while (sc.hasNextLine()) {
                    String in = sc.nextLine();
                    if (in.contains(":")) {
                        String[] parts = in.split(":");
                        String addr = parts[0];
                        if (merkle.isAddressFormattedCorrectly(addr)) {
                            try {
                                double balance = Double.parseDouble(parts[1]);
                                int currentSigCount = Integer.parseInt(parts[2]);
                                addressBalances.put(addr, balance);
                                addressSignatureCounts.put(addr, currentSigCount);
                                addrs.add(addr);
                            } catch (Exception e) {
                                System.out.println("Error parsing line \"" + in + "\"");
                                e.printStackTrace();
                            }
                        }
                    }
                }
                sc.close();
            } catch (Exception e) {
                System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + ANSI_RED + "Unable to read address database file!" + ANSI_RESET);
                getLogger().severe("Unable to read address database file!");
                e.printStackTrace();
                System.exit(-1);
            }
        } else {
            File f = new File(addrDb);
            try {
                PrintWriter out = new PrintWriter(f);
                out.println("-1");
                out.close();
                this.lastBlockIndex = -1; // Just in case
            } catch (Exception e) {
                System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + ANSI_RED + "Unable to write to the ledger record file!");
                e.printStackTrace();
                getLogger().severe("Unable to write to the ledger record file!");
                System.exit(-1);
            }
            System.out.println("Creating \"" + addrDbName + "\" database file...");
        }
    }

    public String getLedgerHash() {
        String ledger = "";
        for (int i = 0; i < addrs.size(); i++) {
            ledger += addrs.get(i) + ":" + addressBalances.get(addrs.get(i)) + ":" + addressSignatureCounts.get(addrs.get(i)) + "\n";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return DatatypeConverter.printHexBinary(md.digest(ledger.getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            System.out.println(ANSI_RED + "Unable to generate ledger hash. Exiting...");
            e.printStackTrace();
            System.exit(-1);
        }
        return null;  // Maybe change this to "" to prevent a null pointer somewhere else
    }

    public void setLastBlockIndex(int lastBlockIndex) {
        this.lastBlockIndex = lastBlockIndex;
    }

    public boolean executeTransaction(String tx) {
        try {
            String[] txParts = tx.split("::");
            String txMessage = "";
            for (int i = 0; i < txParts.length - 2; i++) {
                txMessage += txParts[i] + "::";
            }
            txMessage = txMessage.substring(0, txMessage.length() - 1);
            String source = txParts[0];
            String sigData = txParts[txParts.length - 2];
            long sigIndex = Long.parseLong(txParts[txParts.length - 1]);

            if (!merkle.verifyMerkleSignature(txMessage, sigData, source, sigIndex)) {
                return false; // sig didnt sign tx message
            }

            if (getAddressSignatureCount(source) + 1 != sigIndex) {
                return false; // good sig, bad index
            }

            if (!merkle.isAddressFormattedCorrectly(source)) {
                return false; //bad sending addr
            }

            double sourceAmt = Double.parseDouble(txParts[1]);
            if (getAddressBalance(source) < sourceAmt) {
                return false; // Bad balance (not enough funds)
            }

            ArrayList<String> destAddrs = new ArrayList<>();
            ArrayList<Double> destAmounts = new ArrayList<>();

            for (int i = 2; i < txParts.length - 2; i += 2) {
                destAddrs.add(txParts[1]);
                destAmounts.add(Double.parseDouble(txParts[i + 1]));
            }

            if (destAmounts.size() != destAmounts.size()) {
                System.out.println("YOU SHOULD NOT SEE THIS. IF YOU DO< THIS IS VERY BAD");
                return false;
            }

            for (int i = 0; i < destAddrs.size(); i++) {
                if (!merkle.isAddressFormattedCorrectly(destAddrs.get(i))) {
                    return false; // Not a valid address (bad format)
                }
            }
            long output = 0L;
            for (int i = 0; i < destAmounts.size(); i++) {
                output += destAmounts.get(i);
            }

            if (sourceAmt < output) {
                return false;
            }

            // If we get here, everything is correct
            addressBalances.put(source, getAddressBalance(source) - sourceAmt);
            for (int i = 0; i < destAddrs.size(); i++) {
                addressBalances.put(destAddrs.get(i), getAddressBalance(destAddrs.get(i)) + destAmounts.get(i));
            }
            adjustAddressSignatureCount(source, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean reverseTransaction(String transaction) {
        try {
            String[] transactionParts = transaction.split("::");
            String transactionMessage = "";
            for (int i = 0; i < transactionParts.length - 2; i++) {
                transactionMessage += transactionParts[i] + ";";
            }
            transactionMessage = transactionMessage.substring(0, transactionMessage.length() - 1);
            String sourceAddress = transactionParts[0];
            String signatureData = transactionParts[transactionParts.length - 2];
            long signatureIndex = Long.parseLong(transactionParts[transactionParts.length - 1]);
            if (!merkle.verifyMerkleSignature(transactionMessage, signatureData, sourceAddress, signatureIndex)) {
                return false; //Signature does not sign transaction message!
            }
            if (getAddressSignatureCount(sourceAddress) + 1 != signatureIndex) {
                //We're not concerned with this when reversing!
                return false; //The signature is valid, however it isn't using the expected signatureIndex. Blocked to ensure a compromised Lamport key from a previous transaction can't be used.
            }
            if (!merkle.isAddressFormattedCorrectly(sourceAddress)) {
                return false; //Incorrect sending address
            }
            long source = Long.parseLong(transactionParts[1]);
            ArrayList<String> destinationAddresses = new ArrayList<String>();
            ArrayList<Long> destinationAmounts = new ArrayList<Long>();
            for (int i = 2; i < transactionParts.length - 2; i += 2) //-2 because last two parts of transaction are the signature and signature index
            {
                destinationAddresses.add(transactionParts[i]);
                destinationAmounts.add(Long.parseLong(transactionParts[i + 1]));
            }
            if (destinationAddresses.size() != destinationAmounts.size()) {
                return false; //This should never happen. But if it does...
            }
            for (int i = 0; i < destinationAddresses.size(); i++) {
                if (!merkle.isAddressFormattedCorrectly(destinationAddresses.get(i))) {
                    return false; //A destination address is not a valid address
                }
            }
            long outputTotal = 0L;
            for (int i = 0; i < destinationAmounts.size(); i++) {
                outputTotal += destinationAmounts.get(i);
            }
            if (source < outputTotal) {
                return false;
            }
            for (int i = 0; i < destinationAmounts.size(); i++) {
                if (getAddressBalance(destinationAddresses.get(i)) < destinationAmounts.get(i)) {
                    System.out.println("[CRITICAL ERROR] ADDRESS " + destinationAddresses.get(i) + " needs to return " + destinationAmounts.get(i) + " but only has " + getAddressBalance(destinationAddresses.get(i))); //BIG PROBLEM THIS SHOULD NEVER HAPPEN
                    return false; //One of the addresses has an insufficient balance to reverse!
                }
            }
            //Looks like everything is correct--transaction should be reversed correctly
            addressBalances.put(sourceAddress, getAddressBalance(sourceAddress) + source);
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
     * Writes the whole ledger to a file.
     *
     * @return boolean Whether the write was successful.
     */
    public boolean writeToFile() {
        try {
            PrintWriter out = new PrintWriter(addrDb);
            for (int i = 0; i < addrs.size(); i++) {
                out.println(addrs.get(i) + ":" + addressBalances.get(addrs.get(i)) + ":" + addressSignatureCounts.get(addrs.get(i)));
            }
            out.close();
        } catch (Exception e) {
            System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + ANSI_RED + "Unable to write to db file!" + ANSI_RESET);
            e.printStackTrace();
            getLogger().severe("Unable to write to db file!");
            return false;
        }
        return true;
    }

    public int getAddressSignatureCount(String addr) {
        return addressSignatureCounts.getOrDefault(addr, -1);
    }

    public boolean adjustAddressSignatureCount(String addr, int adjustment) {
        int old = getAddressSignatureCount(addr);
        if (old + adjustment < 0) {
            return false; // negative balance
        }
        return updateAddressSignatureCount(addr, old + adjustment);
    }

    public boolean updateAddressSignatureCount(String addr, int newCount) {
        try {
            if (addressSignatureCounts.containsKey(addr)) {
                addressSignatureCounts.put(addr, newCount);
            } else {
                addressBalances.put(addr, 0D);
                addressSignatureCounts.put(addr, newCount);
                addrs.add(addr);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public double getAddressBalance(String addr) {
        return addressBalances.getOrDefault(addr, 0d);
    }

    public boolean adjustAddressBalance(String addr, double adjustment) {
        double old = getAddressBalance(addr);
        if (old + adjustment < 0) {
            return false;
        }
        return updateAddressBalance(addr, old + adjustment);
    }

    public boolean updateAddressBalance(String addr, double newAmount) {
        try {
            if (addressBalances.containsKey(addr)) {
                addressBalances.put(addr, newAmount);
            } else {
                addressBalances.put(addr, newAmount);
                addressSignatureCounts.put(addr, 0);
                addrs.add(addr);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


}
