package io.codepace.coffeecoin.p2p;

import io.codepace.coffeecoin.Util;

import java.net.Socket;

public class PeerThread extends Thread {

    private Socket socket;
    public InputThread inputThread;
    public OutputThread outputThread;

    public PeerThread(Socket socket) {
        this.socket = socket;
    }

    public void run(){
        Util.logInfoAndPrint("Got connection from: " + socket.getInetAddress());
        inputThread = new InputThread(socket);
        inputThread.start();
        outputThread = new OutputThread(socket);
        outputThread.start();
    }

    public void send(String data){
        if (outputThread == null){
            Util.logInfoAndPrint("Unable to send " + data + " to the network!");
        } else {
            outputThread.write(data);
        }
    }

}
