package io.codepace.cozy.p2p;

import io.codepace.cozy.Util;

import java.net.Socket;

public class PeerThread extends Thread {

    private Socket socket;
    public InputThread inputThread;
    public OutputThread outputThread;

    public PeerThread(Socket socket) {
        this.socket = socket;
    }

    public void run(){
        Util.getLogger().info("Got connection from: " + socket.getInetAddress());
        inputThread = new InputThread(socket);
        inputThread.start();
        outputThread = new OutputThread(socket);
        outputThread.start();
    }

    public void send(String data){
        if (outputThread == null){
            Util.getLogger().info("Unable to send " + data + " to the network!");
        } else {
            outputThread.write(data);
        }
    }

}
