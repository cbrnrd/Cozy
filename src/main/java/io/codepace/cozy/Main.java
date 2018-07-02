package io.codepace.cozy;

import io.codepace.cozy.address.AddressManager;
import io.codepace.cozy.db.Block;
import io.codepace.cozy.db.CozyDatabaseMaster;
import io.codepace.cozy.p2p.PeerNetwork;
import io.codepace.cozy.p2p.RPC;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import static io.codepace.cozy.Util.*;

/**
 * This is the class that ties everything together
 */
public class Main {

    public static void main(String[] args) throws IOException{
        launch();
        CozyDatabaseMaster databaseMaster = new CozyDatabaseMaster("cozy-db");
        PendingTransactionContainer pendingTransactionContainer = new PendingTransactionContainer(databaseMaster);

        System.out.print("Initiating peer network...  ");
        PeerNetwork peerNetwork = new PeerNetwork();
        peerNetwork.start();
        System.out.println("[  " + ANSI_GREEN + "OK" + ANSI_RESET + "  ]");

        System.out.print("Starting RPC daemon...  ");
        RPC rpcAgent = new RPC();
        rpcAgent.start();
        System.out.println("[  " + ANSI_GREEN + "OK" + ANSI_RESET + "  ]");

        File peerFile = new File("peers.list");
        ArrayList<String> peers = new ArrayList<>();
        AddressManager addressManager = new AddressManager();
        if(!peerFile.exists()){
            try{
                PrintWriter out = new PrintWriter(peerFile);
                for (int i = 0; i < Constants.FIXED_PEERS.length; i++) {
                    out.println(Constants.FIXED_PEERS[i]);
                }
                out.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        try{
            Scanner sc = new Scanner(peerFile);
            while(sc.hasNextLine()){
                String combo = sc.nextLine();
                peers.add(combo);
                String host = combo.substring(0, combo.indexOf(":"));
                int port = Integer.parseInt(combo.substring(combo.indexOf(":") + 1));
                peerNetwork.connectToPeer(host, port);
            }
            sc.close();
            Thread.sleep(2000);
        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println(ANSI_CYAN + "[p2p]" + ANSI_RESET + " - Sending REQUEST_NET_STATE out to network");
        peerNetwork.broadcast("REQUEST_NET_STATE");
        int topBlock = 0;
        ArrayList<String> allBroadcastTransactions = new ArrayList<>();
        ArrayList<String> allBroadcastBlocks = new ArrayList<>();
        boolean catchupMode = true;

        /*
        MARKER: p2p communications
         */
        while (true){
            if(peerNetwork.newPeers.size() > 0){
                for (int i = 0; i < peerNetwork.newPeers.size(); i++) {
                    if(peers.indexOf(peerNetwork.newPeers.get(i)) < 0){
                        peers.add(peerNetwork.newPeers.get(i));
                    }
                }

                peerNetwork.newPeers = new ArrayList<>();
                try{
                    PrintWriter writePeerFile = new PrintWriter(new File("peers.list"));
                    for (int i = 0; i < peers.size(); i++) {
                        writePeerFile.println(peers.get(i));
                    }
                    writePeerFile.close();
                } catch (Exception e){
                    System.out.println("Error: Unable to write to peer file.");
                    e.printStackTrace();
                }
            }

            // Look for new data to peers
            for (int i = 0; i < peerNetwork.peerThreads.size(); i++) {
                ArrayList<String> input = peerNetwork.peerThreads.get(i).inputThread.readData();
                if(input == null){
                    System.out.println("Null ret retry.");
                    System.exit(-5);
                    break;
                }

                for (int j = 0; j < input.size(); j++) {
                    String data = input.get(j);
                    if(data.length() > 60){
                        System.out.println("Got data: " + data.substring(0, 30) + "..." + data.substring(data.length() - 30, data.length()));
                    } else {
                        System.out.println("Got data: " + data);
                    }
                    String[] parts = data.split(" ");
                    if (parts.length > 0){
                        if(parts[0].equalsIgnoreCase("NETWORK_STATE")){
                            topBlock = Integer.parseInt(parts[1]);
                        } else if (parts[0].equalsIgnoreCase("REQUEST_NET_STATE")){
                            System.out.println("DBLEN: " + databaseMaster.getBlockchainLength());
                            System.out.println("HASH: " + databaseMaster.getLatestBlock().blockHash);
                            peerNetwork.peerThreads.get(i).outputThread.write("NETWORK_STATE " + databaseMaster.getBlockchainLength() + " " + databaseMaster.getLatestBlock().blockHash);
                            for (int k = 0; k < pendingTransactionContainer.pendingTransactions.size(); k++) {
                                peerNetwork.peerThreads.get(i).outputThread.write("TRANSACTION " + pendingTransactionContainer.pendingTransactions.get(k));
                            }
                        } else if (parts[0].equalsIgnoreCase("BLOCK")){
                            System.out.println("Attempting to add block...");
                            boolean hasSeenBlockBefore = false;
                            for (int k = 0; k < allBroadcastBlocks.size(); k++) {
                                if(parts[1].equals(allBroadcastBlocks.get(k))){
                                    hasSeenBlockBefore = true;
                                }
                            }

                            if(!hasSeenBlockBefore){
                                System.out.println("Adding new block from network...");
                                System.out.println("Block: ");
                                System.out.println(parts[1].substring(0, 30) + "...");
                                allBroadcastBlocks.add(parts[1]);
                                Block blockToAdd = new Block(parts[1]);
                                if(databaseMaster.addBlock(blockToAdd) && !catchupMode){
                                    System.out.println("Added block " + blockToAdd.blockNum + " with hash: [" + blockToAdd.blockHash.substring(0, 30) + "..." + blockToAdd.blockHash.substring(blockToAdd.blockHash.length() - 30, blockToAdd.blockHash.length() - 1) + "]");
                                    peerNetwork.broadcast("BLOCK " + parts[1]);
                                }
                                pendingTransactionContainer.removeTransactionsInBlock(parts[1]);
                            }
                        } else if (parts[0].equalsIgnoreCase("TRANSACTION")){
                            boolean alreadyExisted = false;
                            for (int k = 0; k < allBroadcastBlocks.size(); k++) {
                                if(parts[1].equalsIgnoreCase(allBroadcastTransactions.get(k))){
                                    alreadyExisted = true;
                                }
                            }
                            if(!alreadyExisted){
                                allBroadcastTransactions.add(parts[1]);
                                pendingTransactionContainer.addTransaction(parts[1]);
                                if(TransactionUtility.isTransactionValid(parts[1])){
                                    System.out.println("New tx on network: ");
                                    String[] txParts = parts[1].split("::");
                                    for (int k = 2; k < txParts.length - 2; k+=2) {
                                        System.out.println("     " + txParts[k + 1] + " cozy(s) from " + txParts[0] + " to " + txParts[k]);
                                    }
                                    System.out.println("Total cozy sent: "+ txParts[1]);
                                    peerNetwork.broadcast("TRANSACTION " + parts[1]);
                                } else {
                                    System.out.println("Invalid transaction: " + parts[1]);
                                }
                            }
                        } else if (parts[0].equalsIgnoreCase("PEER")){
                            boolean exists = false;
                            for (int k = 0; k < peers.size(); k++) {
                                if (peers.get(k).equals(parts[1] + ":" + parts[2])){
                                    exists = true;
                                }
                            }

                            if (!exists){
                                try{
                                    String peerAddr = parts[1].substring(0, parts[1].indexOf(":"));
                                    int peerPort = Integer.parseInt(parts[1].substring(parts[1].indexOf(":") + 1));
                                    peerNetwork.connectToPeer(peerAddr, peerPort);
                                    peers.add(parts[1]);
                                    PrintWriter out = new PrintWriter(peerFile);
                                    for (int k = 0; k < peers.size(); k++) {
                                        out.println(peers.get(k));
                                    }
                                    out.close();
                                } catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                        } else if (parts[0].equalsIgnoreCase("GET_PEER")){
                            Random random = new Random();
                            peerNetwork.peerThreads.get(i).outputThread.write("PEER " + peers.get(random.nextInt(peers.size())));
                        } else if (parts[0].equalsIgnoreCase("GET_BLOCK")){
                            try{
                                Block block = databaseMaster.getBlock(Integer.parseInt(parts[1]));
                                if (block != null){
                                    System.out.println("Sending block " + parts[1] + " to peer");
                                    peerNetwork.peerThreads.get(i).outputThread.write("BLOCK " + block.getRawBlock());
                                }
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            //****************************
            // BREAK BIG FOR LOOP
            //****************************

            int currentChainHeight = databaseMaster.getBlockchainLength();

            if(topBlock > currentChainHeight){
                catchupMode = true;
                System.out.println("Current chain height: " + currentChainHeight);
                System.out.println("Top block: " + topBlock);
                try{
                    Thread.sleep(300);
                } catch (InterruptedException e){
                    System.out.println("Main thread sleep interrupted.");
                    e.printStackTrace();
                }
                for (int i = currentChainHeight; i < topBlock; i++) {
                    System.out.println("Requesting block " + i + "...");
                    peerNetwork.broadcast("GET_BLOCK " + i);
                }
            } else {
                if (catchupMode){
                    System.out.println(ANSI_CYAN + "[p2p] " + ANSI_RESET + "- Caught up with network.");
                }
                catchupMode = false;
            }
            /*
            Loop through RPC threads checking for new input
            MARKER: RPC parsing
             */
            for (int i = 0; i < rpcAgent.rpcThreads.size(); i++) {
                String request = rpcAgent.rpcThreads.get(i).req;
                if(request != null){
                    String[] parts = request.split(" ");
                    parts[0] = parts[0].toLowerCase();
                    if (parts[0].equals("getbalance")){
                        if (parts.length > 1){
                            rpcAgent.rpcThreads.get(i).res = databaseMaster.getAddressBalance(parts[1]) + "";
                        } else {
                            rpcAgent.rpcThreads.get(i).res = databaseMaster.getAddressBalance(addressManager.getDefaultAddress()) + "";
                        }
                    } else if(parts[0].equals("getinfo")){
                        // TODO have this give more info
                        String res = "Blocks: " + databaseMaster.getBlockchainLength();
                        res += "\nLast block hash: " + databaseMaster.getBlock(databaseMaster.getBlockchainLength() - 1).blockHash;
                        res += "\nDifficulty: " + databaseMaster.getDifficulty();
                        res += "\nMain address (default): " + addressManager.getDefaultAddress();
                        res += "\nMain address balance: " + databaseMaster.getAddressBalance(addressManager.getDefaultAddress());
                        res += "\nLatest transaction: " + databaseMaster.getLatestBlock().transactions.get(databaseMaster.getLatestBlock().transactions.size() - 1);
                        rpcAgent.rpcThreads.get(i).res = res;
                    } else if (parts[0].equals("send")){
                        try{
                            long amount = Long.parseLong(parts[1]);
                            String destAddr = parts[2];
                            String addr = addressManager.getDefaultAddress();
                            String fullTx = addressManager.getSignedTransaction(destAddr, amount, databaseMaster.getAddressSignatureIndex(addr) + addressManager.getDefaultAddressIndexOffset());
                            addressManager.incrementDefaultAddressIndexOffset();
                            System.out.println("Trying to verify transaction... " + TransactionUtility.isTransactionValid(fullTx));
                            if (TransactionUtility.isTransactionValid(fullTx)){
                                pendingTransactionContainer.addTransaction(fullTx);
                                peerNetwork.broadcast("TRANSACTION " + fullTx);
                                System.out.println("Sending " + amount + " to " + destAddr + " from " + addr);
                                rpcAgent.rpcThreads.get(i).res = "Sent " + amount + " from " + addr + " to " + destAddr;
                            } else {
                                rpcAgent.rpcThreads.get(i).res = "Unable to send: invalid transaction :(";
                            }
                        } catch (Exception e){
                            rpcAgent.rpcThreads.get(i).res = "Syntax (no '<' or '>'): send <amount> <dest>";
                        }
                    } else if (parts[0].equals("submit_tx")){
                        if(TransactionUtility.isTransactionValid(parts[1])){
                            pendingTransactionContainer.addTransaction(parts[0]);
                            peerNetwork.broadcast("TRANSACTION " + parts[1]);
                            rpcAgent.rpcThreads.get(i).res = "Sent raw tx.";
                        } else {
                            rpcAgent.rpcThreads.get(i).res = "Invalid transaction";
                        }
                    } else if (parts[0].equals("trypos")){
                        rpcAgent.rpcThreads.get(i).req = null;
                        // Address can not have mined a PoS block or sent a transaction in the last 50 blocks

                        String PoSAddress = addressManager.getDefaultAddress();

                        boolean conditionsMet = true;

                        for (int j = databaseMaster.getBlockchainLength() - 1; j > databaseMaster.getBlockchainLength() - 50; j--)
                        {
                            if (!databaseMaster.getBlock(j).isPoWBlock()) // Then PoS block
                            {
                                if (databaseMaster.getBlock(j).getMiner().equals(PoSAddress))
                                {
                                    // Address has mined PoS block too recently!
                                    rpcAgent.rpcThreads.get(i).res = "A PoS block was mined too recently: " + j;
                                    conditionsMet = false;
                                }
                            }
                            ArrayList<String> transactions = databaseMaster.getBlock(i).getTransactionsInvolvingAddress(PoSAddress);
                            for (String transaction : transactions)
                            {
                                if (transaction.split(":")[0].equals(PoSAddress))
                                {
                                    // Address has sent coins too recently!
                                    rpcAgent.rpcThreads.get(i).res = "A PoS block was mined too recently: " + j;
                                    conditionsMet = false;
                                }
                            }
                        }

                        if (conditionsMet)
                        {
                            System.out.println("Last block: " + databaseMaster.getBlockchainLength());
                            System.out.println("That block's hash: " + databaseMaster.getBlock(databaseMaster.getBlockchainLength() - 1).blockHash);
                            String previousBlockHash = databaseMaster.getBlock(databaseMaster.getBlockchainLength() - 1).blockHash;
                            double currentBalance = databaseMaster.getAddressBalance(PoSAddress);
                            Certificate certificate = new Certificate(PoSAddress, "0", (int)currentBalance * 100, "0", databaseMaster.getBlockchainLength() + 1, previousBlockHash, 0, "0,0");

                            String[] scoreAndNonce = certificate.getMinCertificateScoreWithNonce().split(":");
                            int bestNonce = Integer.parseInt(scoreAndNonce[0]);
                            long lowestScore = Long.parseLong(scoreAndNonce[1]);
                            long target = Long.MAX_VALUE/(100000/2); // Hard-coded PoS difficulty for this test
                            if (lowestScore < target)
                            {
                                try //Some stuff here may throw exceptions
                                {
                                    //Great, certificate is a winning certificate!
                                    //Gather all of the transactions from pendingTransactionContainer, check them.
                                    ArrayList<String> allPendingTransactions = pendingTransactionContainer.pendingTransactions;
                                    System.out.println("Initial pending pool size: " + allPendingTransactions.size());
                                    allPendingTransactions = TransactionUtility.sortTransactionsBySignatureIndex(allPendingTransactions);
                                    System.out.println("Pending pool size after sorting: " + allPendingTransactions.size());
                                    //All transactions have been ordered, and tested for validity. Now, we need to check account balances to make sure transactions are valid. 
                                    //As all transactions are grouped by address, we'll check totals address-by-address
                                    ArrayList<String> finalTransactionList = new ArrayList<>();
                                    for (int j = 0; j < allPendingTransactions.size(); j++)
                                    {
                                        String transaction = allPendingTransactions.get(j);
                                        String address = transaction.split("::")[0];
                                        //Begin at 0D, and add all outputs to exitBalance
                                        double exitBalance = 0D;
                                        double originalBalance = databaseMaster.getAddressBalance(address);
                                        //Used to keep track of the offset from j while still working on the same address, therefore not going through the entire for-loop again
                                        int counter = 0;
                                        //Previous signature count for an address--in order to ensure transactions use the correct indices
                                        long previousSignatureCount = databaseMaster.getAddressSignatureIndex(address);
                                        boolean foundNewAddress = false;
                                        while (!foundNewAddress && j + counter < allPendingTransactions.size())
                                        {
                                            transaction = allPendingTransactions.get(j + counter);
                                            if (!address.equals(transaction.split("::")[0]))
                                            {
                                                foundNewAddress = true;
                                                address = transaction.split("::")[0];
                                                j = j + counter;
                                            }
                                            else
                                            {
                                                exitBalance += Long.parseLong(transaction.split("::")[1]); //Element at index 1 (2nd element) is the full output amount!
                                                if (exitBalance <= originalBalance && previousSignatureCount + 1 == Long.parseLong(transaction.split(";")[transaction.split(";").length - 1])) //Transaction looks good!
                                                {
                                                    //Add seemingly-good transaction to the list, and increment previousSignatureCount for signature order assurance. 
                                                    finalTransactionList.add(transaction);
                                                    System.out.println("While making block, added transaction " + transaction);
                                                    previousSignatureCount++;
                                                }
                                                else
                                                {
                                                    System.out.println("Transaction failed final validation...");
                                                    System.out.println("exitBalance: " + exitBalance);
                                                    System.out.println("originalBalance: " + originalBalance);
                                                    System.out.println("previousSignatureCount: " + previousSignatureCount);
                                                    System.out.println("signature count of new tx: " + Long.parseLong(transaction.split("::")[transaction.split("::").length - 1]));
                                                }
                                                //Counter keeps track of the sub-2nd-layer-for-loop incrementation along the ArrayList. It's kinda 3D.
                                                counter++;
                                            }
                                        }
                                    }
                                    //We have the transaction list; now we need to assemble the block.
                                    //databaseMaster.getBlockchainLength() doesn't have one added to it to account for starting from 0!
                                    String fullBlock = BlockGenerator.compileBlock(System.currentTimeMillis(), databaseMaster.getBlockchainLength(), databaseMaster.getLatestBlock().blockHash, 100000 /*fixed testnet PoS difficulty for now...*/, bestNonce, "0000000000000000000000000000000000000000000000000000000000000000", finalTransactionList, certificate, certificate.redeemAddress, addressManager.getDefaultPrivateKey(), databaseMaster.getAddressSignatureIndex(certificate.redeemAddress));

                                    System.out.println("Compiled PoS block: " + fullBlock);

                                    //We finally have the full block. Now to submit it to ourselves...
                                    Block toAdd = new Block(fullBlock);
                                    boolean success = databaseMaster.addBlock(toAdd);

                                    System.out.println("Block add success: " + success);

                                    if (success) //The block appears legitimate to ourselves! Send it to others!
                                    {
                                        peerNetwork.broadcast("BLOCK " + fullBlock);
                                        System.out.println("PoS Block added to network successfully!");
                                        pendingTransactionContainer.reset(); //Any transactions left in pendingTransactionContainer that didn't get submitted into the block should be cleared anyway--they probably aren't valid for some reason, likely balance issues.
                                        addressManager.resetDefaultAddressIndexOffset();
                                    }
                                    else
                                    {
                                        System.out.println("Block was not added successfully! :(");
                                    }
                                    rpcAgent.rpcThreads.get(i).res = "Successfully submitted block! \nCertificate earned score " + lowestScore + "\nWhich is below target " + target + " so earned PoS!";
                                } catch (Exception e)
                                {
                                    rpcAgent.rpcThreads.get(i).res = "Failure to construct certificate!";
                                    System.out.println("Constructing certificate failed!");
                                    e.printStackTrace();
                                }
                            }
                            else
                            {
                                rpcAgent.rpcThreads.get(i).res = "Pos mining failed with target score " + lowestScore + "\nWhich is above target " + target;
                            }
                        }
                    } else if (parts[0].equals("submit_cert")){
                        rpcAgent.rpcThreads.get(i).req = null;
                        /*
                         * We have seven things to do:
                         * 1.) Check certificate for all nonces
                         * If 1. shows a difficulty above the network difficulty (below the target), proceed with creating a block:
                         * 2.) Gather all transactions from the pending transaction pool. Test all for validity. Test all under a max balance test.
                         * 3.) Put correct transactions in any arbitrary order, except for multiple transactions from the same address, which are ordered by signature index.
                         * 4.) Input the ledger hash (In 0.2.05, this is 0000000000000000000000000000000000000000000000000000000000000000, as ledger hashing isn't fully implemented)
                         * 5.) Hash the block
                         * 6.) Sign the block
                         * 7.) Return full block
                         * Steps 5, 6, and 7 are handled outside of MainClass, by a static method inside BlockGenerator.
                         */
                        //First, we'll check for the max difficulty.
                        Certificate certificate = new Certificate(parts[1]);
                        String[] scoreAndNonce = certificate.getMinCertificateScoreWithNonce().split(":");
                        int bestNonce = Integer.parseInt(scoreAndNonce[0]);
                        long lowestScore = Long.parseLong(scoreAndNonce[1]);
                        long target = Long.MAX_VALUE/(databaseMaster.getDifficulty()/2); //Difficulty and target have an inverse relationship.
                        if (lowestScore < target)
                        {
                            try //Some stuff here may throw exceptions
                            {
                                //Great, certificate is a winning certificate!
                                //Gather all of the transactions from pendingTransactionContainer, check them.
                                ArrayList<String> allPendingTransactions = pendingTransactionContainer.pendingTransactions;
                                System.out.println("Initial pending pool size: " + allPendingTransactions.size());
                                allPendingTransactions = TransactionUtility.sortTransactionsBySignatureIndex(allPendingTransactions);
                                System.out.println("Pending pool size after sorting: " + allPendingTransactions.size());
                                //All transactions have been ordered, and tested for validity. Now, we need to check account balances to make sure transactions are valid.
                                //As all transactions are grouped by address, we'll check totals address-by-address
                                ArrayList<String> finalTransactionList = new ArrayList<>();
                                for (int j = 0; j < allPendingTransactions.size(); j++)
                                {
                                    String transaction = allPendingTransactions.get(j);
                                    String address = transaction.split("::")[0];
                                    //Begin at 0D, and add all outputs to exitBalance
                                    double exitBalance = 0L;
                                    double originalBalance = databaseMaster.getAddressBalance(address);
                                    //Used to keep track of the offset from j while still working on the same address, therefore not going through the entire for-loop again
                                    int counter = 0;
                                    //Previous signature count for an address--in order to ensure transactions use the correct indices
                                    long previousSignatureCount = databaseMaster.getAddressSignatureIndex(address);
                                    boolean foundNewAddress = false;
                                    while (!foundNewAddress && j + counter < allPendingTransactions.size())
                                    {
                                        transaction = allPendingTransactions.get(j + counter);
                                        if (!address.equals(transaction.split("::")[0]))
                                        {
                                            foundNewAddress = true;
                                            address = transaction.split("::")[0];
                                            j = j + counter;
                                        }
                                        else
                                        {
                                            exitBalance += Long.parseLong(transaction.split("::")[1]); //Element at index 1 (2nd element) is the full output amount!
                                            if (exitBalance <= originalBalance && previousSignatureCount + 1 == Long.parseLong(transaction.split("::")[transaction.split("::").length - 1])) //Transaction looks good!
                                            {
                                                //Add seemingly-good transaction to the list, and increment previousSignatureCount for signature order assurance.
                                                finalTransactionList.add(transaction);
                                                System.out.println("While making block, added transaction " + transaction);
                                                previousSignatureCount++;
                                            }
                                            else
                                            {
                                                System.out.println("Transaction failed final validation...");
                                                System.out.println("exitBalance: " + exitBalance);
                                                System.out.println("originalBalance: " + originalBalance);
                                                System.out.println("previousSignatureCount: " + previousSignatureCount);
                                                System.out.println("signature count of new tx: " + Long.parseLong(transaction.split("::")[transaction.split("::").length - 1]));
                                            }
                                            //Counter keeps track of the sub-2nd-layer-for-loop incrementation along the ArrayList. It's kinda 3D.
                                            counter++;
                                        }
                                    }
                                }

                                String fullBlock = BlockGenerator.compileBlock(System.currentTimeMillis(), databaseMaster.getBlockchainLength(), databaseMaster.getLatestBlock().blockHash, 150000, bestNonce, "0000000000000000000000000000000000000000000000000000000000000000", finalTransactionList, certificate, certificate.redeemAddress, addressManager.getDefaultPrivateKey(), databaseMaster.getAddressSignatureIndex(certificate.redeemAddress));
                                //We finally have the full block. Now to submit it to ourselves...
                                Block toAdd = new Block(fullBlock);
                                boolean success = databaseMaster.addBlock(toAdd);
                                if (success) //The block appears legitimate to ourselves! Send it to others!
                                {
                                    System.out.println("Block added to network successfully!");
                                    peerNetwork.broadcast("BLOCK " + fullBlock);
                                    pendingTransactionContainer.reset(); //Any transactions left in pendingTransactionContainer that didn't get submitted into the block should be cleared anyway--they probably aren't valid for some reason, likely balance issues.
                                    addressManager.resetDefaultAddressIndexOffset();
                                }
                                else
                                {
                                    System.out.println("Block was not added successfully! :(");
                                }
                                rpcAgent.rpcThreads.get(i).res = "Successfully submitted block! \nCertificate earned target score " + lowestScore + "\nWhich is below target " + target;
                            } catch (Exception e)
                            {
                                rpcAgent.rpcThreads.get(i).res = "Failure to construct certificate!";
                                System.out.println("Constructing certificate failed!");
                                e.printStackTrace();
                            }
                        }
                        else
                        {
                            rpcAgent.rpcThreads.get(i).res = "Certificate failed with target score " + lowestScore + "\nWhich is above target " + target;
                        }
                    } else if (parts[0].equals("get_history")){
                        if (parts.length > 1){
                            ArrayList<String> allTransactions = databaseMaster.getAllTransactionsInvolvingAddress(parts[1]);
                            String allTransactionsFlat = "";
                            for (int j = 0; j < allTransactions.size(); j++) {
                                allTransactionsFlat += allTransactions.get(j) + "\n";
                            }
                            rpcAgent.rpcThreads.get(i).res = allTransactionsFlat;
                        } else {
                            rpcAgent.rpcThreads.get(i).res = "Syntax: get_history <address>";
                        }
                    } else if (parts[0].equals("get_pending")){
                        if(parts.length > 1){
                            rpcAgent.rpcThreads.get(i).res = "" + pendingTransactionContainer.getPendingBalance(parts[1]);
                        } else rpcAgent.rpcThreads.get(i).res = "get_pending <address>";
                    } else {
                        rpcAgent.rpcThreads.get(i).res = "Unknown command: \"" + parts[0] + "\"";
                    }
                }
            }

            //****************
            // MARKER: End rpc cmd loop
            //****************
            try{
                Thread.sleep(100);
            } catch (Exception e){}
        }
    }

    static void launch() throws IOException{
        System.out.println("Launching Cozy daemon, welcome!");
        File initialCheck = new File(".initial");
        if(!initialCheck.exists()){
            initialCheck.createNewFile();
            System.out.println("Detected first time run, setting up a few things...");
            System.out.print("\nChecking for a valid JRE version... ");

            // This is super hackish but it works
            int minor = Integer.parseInt(String.valueOf(System.getProperty("java.version").charAt(2)));

            // They changed the java version notation in Java 9 to be 9.0.x instead of 1.9.x
            if ((minor < 8) && (Integer.parseInt(String.valueOf(System.getProperty("java.version").charAt(0))) != 9)){
                System.out.println("[  " + Util.ANSI_RED + "FAIL" + Util.ANSI_WHITE + "  ]");
                System.out.println("Cozyd needs a JRE version of 1.8 or greater, yours is " + Util.ANSI_RED +
                        System.getProperty("java.version") + Util.ANSI_WHITE);
                System.exit(-1);
            } else {
                System.out.println("[  " + Util.ANSI_GREEN + "OK" + Util.ANSI_RESET + "  ]");
            }
        }
    }
}
