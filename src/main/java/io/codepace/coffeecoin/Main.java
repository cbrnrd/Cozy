package io.codepace.coffeecoin;

// Argument parsing stuff
import io.codepace.coffeecoin.db.CoffeecoinDatabaseMaster;
import io.codepace.coffeecoin.p2p.PeerNetwork;
import io.codepace.coffeecoin.p2p.RPC;
import net.sourceforge.argparse4j.ArgumentParsers;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    public static void main(String[] args){
        launch();
        CoffeecoinDatabaseMaster databaseMaster = new CoffeecoinDatabaseMaster("coffeecoin.db");
        PendingTransactionContainer pendingTransactionContainer = new PendingTransactionContainer(databaseMaster);
        PeerNetwork peerNetwork = new PeerNetwork();
        peerNetwork.start();
        RPC rpcAgent = new RPC();
        rpcAgent.start();
        File peerFile = new File("peers.list");
        ArrayList<String> peers = new ArrayList<>();
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

        peerNetwork.broadcast("REQUEST_NET_STATE");
        int topBlock = 0;
        ArrayList<String> allBroadcastTransactions = new ArrayList<>();
        ArrayList<String> allBroadcastBlocks = new ArrayList<>();
        boolean catchupMode = true;
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
                    Util.logInfoAndPrint("Error: Unable to write to peer file.");
                    e.printStackTrace();
                }
            }

            // Look for new data to peers
            for (int i = 0; i < peerNetwork.peerThreads.size(); i++) {
                ArrayList<String> input = peerNetwork.peerThreads.get(i).inputThread.readData();
                if(input == null){
                    Util.logInfoAndPrint("Null ret retry.");
                    System.exit(-5);
                    break;
                }

                for (int j = 0; j < input.size(); j++) {
                    String data = input.get(j);
                    if(data.length() > 60){
                        Util.logInfoAndPrint("Got data: " + data.substring(0, 30) + "..." + data.substring(data.length() - 30, data.length()));
                    } else {
                        Util.logInfoAndPrint("Got data: " + data);
                    }
                    String[] parts = data.split(" ");
                    if (parts.length > 0){
                        if(parts[0].equalsIgnoreCase("NETWORK_STATE")){
                            topBlock = Integer.parseInt(parts[1]);
                        } else if (parts[0].equalsIgnoreCase("REQUEST_NET_STATE")){
                            peerNetwork.peerThreads.get(i).outputThread.write("NETWORK_STATE " + databaseMaster.getBlockchainLength() + " " + databaseMaster.getLatestBlock().blockHash);
                            for (int k = 0; k < pendingTransactionContainer.pending.size(); k++) {
                                peerNetwork.peerThreads.get(i).outputThread.write("TRANSACTION " + pendingTransactionContainer.pending.get(k));
                            }
                        } else if (parts[0].equalsIgnoreCase("BLOCK")){
                            Util.logInfoAndPrint("Attempting to add block...");
                            boolean hasSeenBlockBefore = false;
                            for (int k = 0; k < allBroadcastBlocks.size(); k++) {
                                if(parts[1].equals(allBroadcastBlocks.get(k))){
                                    hasSeenBlockBefore = true;
                                }
                            }

                            if(!hasSeenBlockBefore){
                                Util.logInfoAndPrint("Adding new block from network...");
                                Util.logInfoAndPrint("Block: ");
                                Util.logInfoAndPrint(parts[1].substring(0, 30) + "...");
                                allBroadcastBlocks.add(parts[1]);
                                
                            }
                        }
                    }
                }
            }
        }
    }

    public static void launch(){

    }

}
