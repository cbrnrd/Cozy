package io.codepace.cozy.p2p;

import static io.codepace.cozy.Util.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;

public class InputThread extends Thread{

    private Socket socket;
    private ArrayList<String> recievedData = new ArrayList<>();

    public InputThread(Socket socket){
        this.socket = socket;
    }

    /**
     * Constantly reads data in from socket and saves it to the received data ArrayList
     */
    public void run(){
        try{
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String input;
            while ((input = in.readLine()) != null){
                recievedData.add(input);
            }
        } catch (Exception e){
            getLogger().info("Peer: " + socket.getInetAddress() + " disconnected.");
        }
    }

    public ArrayList<String> readData(){
        ArrayList<String> inputBuffer = new ArrayList<>(recievedData);
        if (inputBuffer == null) {
            inputBuffer = new ArrayList<>();
        }
        recievedData = new ArrayList<>();
        return inputBuffer;
    }

}
