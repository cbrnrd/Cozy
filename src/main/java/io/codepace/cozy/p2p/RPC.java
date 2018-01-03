package io.codepace.cozy.p2p;

import java.net.ServerSocket;
import java.util.ArrayList;

public class RPC extends Thread {
    private int port;
    public String req = null;

    public ArrayList<RPCThread> rpcThreads;

    public boolean shouldRun = true;

    public RPC(){
        this.port = 2021;
        this.rpcThreads = new ArrayList<>();
    }

    public RPC(int port){
        this.port = port;
        this.rpcThreads = new ArrayList<>();
    }

    public void run(){
        try{
            ServerSocket socket = new ServerSocket(port);
            while (shouldRun){
                rpcThreads.add(new RPCThread(socket.accept()));
                rpcThreads.get(rpcThreads.size() - 1).start();
            }
            socket.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
