package io.codepace.coffeecoin.p2p;

import java.io.File;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

public class OutputThread extends Thread{

    private Socket socket;

    private ArrayList<String> outputBuffer;
    private boolean shouldContinue = true;

    /**
     * Constructor to set class socket variable
     */
    public OutputThread(Socket socket)
    {
        this.socket = socket;
    }

    /**
     * Constantly checks outputBuffer for contents, and writes any contents in outputBuffer.
     */
    public void run()
    {
        try
        {
            outputBuffer = new ArrayList<String>();
            PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
            while (shouldContinue)
            {
                //System.out.println("LOOP ALIVE FOR " + socket.getInetAddress());
                //System.out.println("LOOP SIZE : " + outputBuffer.size() + " for " + socket.getInetAddress());
                if (outputBuffer.size() > 0)
                {
                    if (outputBuffer.get(0) != null)
                    {
                        for (int i = 0; i < outputBuffer.size(); i++)
                        {
                            if (outputBuffer.get(i).length() > 20)
                            {
                                System.out.println("Sending " + outputBuffer.get(i).substring(0, 20) + " to " + socket.getInetAddress());
                            }
                            else
                            {
                                System.out.println("Sending " + outputBuffer.get(i) + " to " + socket.getInetAddress());
                            }
                            out.println(outputBuffer.get(i));
                        }
                        outputBuffer = new ArrayList<String>();
                        outputBuffer.add(null);
                    }
                }
                Thread.sleep(100);
            }
            System.out.println("WHY AM I HERE");
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Technically not writing to the network socket, but instead putting the passed-in data in a buffer to be written to the socket as soon as possible.
     *
     * @param data Data to write
     */
    public void write(String data)
    {
        if (data.length() > 20)
        {
            System.out.println("PUTTING INTO WRITE BUFFER: " + data.substring(0, 20) + "...");
        }
        else
        {
            System.out.println("PUTTING INTO WRITE BUFFER: " + data);
        }
        File f = new File("writebuffer");
        try
        {
            PrintWriter out = new PrintWriter(f);
            out.println("SENDING: " + data);
            out.close();
        } catch (Exception e)
        {
        }
        if (outputBuffer.size() > 0)
        {
            if (outputBuffer.get(0) == null)
            {
                outputBuffer.remove(0);
            }
        }
        outputBuffer.add(data);
    }

    /**
     * Stops thread during the next write cycle. I couldn't call it stop() like I wanted to, cause you can't overwrite that method of Thread. :'(
     */
    public void shutdown()
    {
        shouldContinue = false;
    }

}
