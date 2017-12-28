package io.codepace.coffeecoin.p2p;

import io.codepace.coffeecoin.Util;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class PeerNetwork extends Thread{

    public int port;
    public boolean shouldRun = true;
    public ArrayList<PeerThread> peerThreads;
    public ArrayList<String> newPeers;

    public PeerNetwork(){
        this.port = 2020;
        this.peerThreads = new ArrayList<>();
        this.newPeers = new ArrayList<>();
    }

    public void connectToPeer(String addr, int port){
        try{
            Socket socket = new Socket(addr, port);
            String remoteHost = socket.getInetAddress() + "";
            remoteHost = remoteHost.replace("/", "");
            remoteHost = remoteHost.replace("\\", "");
            int remotePort = socket.getPort();
            newPeers.add(remoteHost + ":" + remotePort);
            peerThreads.add(new PeerThread(socket));
            peerThreads.get(peerThreads.size() - 1).start();
        } catch (Exception e){
            Util.logInfoAndPrint("Unable to connect to: " + addr + ":" + port);
        }
    }

    public PeerNetwork(int port){
        this.port = port;
        this.peerThreads = new ArrayList<>();
    }

    public void run(){
        try{
            ServerSocket listenSocket = new ServerSocket(port);
            while (shouldRun){
                peerThreads.add(new PeerThread(listenSocket.accept()));
                peerThreads.get(peerThreads.size() - 1).start();
            }
            listenSocket.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void broadcast(String msg){
        for (int i = 0; i < peerThreads.size(); i++) {
            peerThreads.get(i).send(msg);
        }
    }


}
