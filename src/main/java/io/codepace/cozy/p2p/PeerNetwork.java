package io.codepace.cozy.p2p;

import io.codepace.cozy.Util;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * This thread listens on a provided port (8015 by default) for incoming connections, and attempts to make connections to external peers based on guidance from MainClass.
 * It needs a bit of help with memory management and resource deallocation, but otherwise it works. Good enough for 0.2.01.
 * <p>
 * Future plans include some form of UPNP support and NAT punchthroughs.
 */
public class PeerNetwork extends Thread {
    public int listenPort;
    public boolean shouldRun = true;
    public ArrayList<PeerThread> peerThreads;

    public ArrayList<String> newPeers;

    /**
     * Default settings constructor
     */
    public PeerNetwork() {
        this.listenPort = 8015;
        this.peerThreads = new ArrayList<>();
        this.newPeers = new ArrayList<>();
    }

    /**
     * Attempts a connection to an external peer
     *
     * @param peer Peer to connect to
     * @param port Port on peer to connect to
     */
    public void connectToPeer(String peer, int port) {
        try {
            Socket socket = new Socket(peer, port);
            String remoteHost = socket.getInetAddress() + "";
            remoteHost = remoteHost.replace("/", "");
            remoteHost = remoteHost.replace("\\", "");
            int remotePort = socket.getPort();
            newPeers.add(remoteHost + ":" + remotePort);
            peerThreads.add(new PeerThread(socket));
            peerThreads.get(peerThreads.size() - 1).start();
        } catch (Exception e) {
            System.out.println("Unable to connect to " + peer + ":" + port);
        }
    }

    /**
     * Optional, currently-unused constructor for a non-default port selection
     *
     * @param port Port to listen on
     */
    public PeerNetwork(int port) {
        this.listenPort = port;
        this.peerThreads = new ArrayList<>();
    }

    /**
     * Runs as a separate thread, constantly listening for peer connections.
     */
    public void run() {
        try {
            ServerSocket listenSocket = new ServerSocket(listenPort);
            while (shouldRun) //Doesn't actually quit right when shouldRun is changed, as while loop is pending.
            {
                peerThreads.add(new PeerThread(listenSocket.accept()));
                peerThreads.get(peerThreads.size() - 1).start();
            }
            listenSocket.close();
        } catch (Exception e) {
            e.printStackTrace(); //Most likely tripped by the inability to bind the listenPort.
        }
    }

    /**
     * Announces the same message to all peers simultaneously. Useful when re-broadcasting messages.
     *
     * @param toBroadcast String to broadcast to peers
     */
    public void broadcast(String toBroadcast) {
        for (int i = 0; i < peerThreads.size(); i++) {
            System.out.println("Sent:: " + toBroadcast);
            peerThreads.get(i).send(toBroadcast);
        }
    }

    /**
     * Announces the same message to all peers except the ignored one simultaneously. Useful when re-broadcasting messages.
     * Peer ignored as it's the peer that sent you info.
     *
     * @param toBroadcast  String to broadcast to peers
     * @param peerToIgnore Peer to not send broadcast too--usually the peer who sent information that is being rebroadcast
     */
    public void broadcastIgnorePeer(String toBroadcast, String peerToIgnore) {
        for (int i = 0; i < peerThreads.size(); i++) {
            peerThreads.get(i).send(toBroadcast);
        }
    }
}