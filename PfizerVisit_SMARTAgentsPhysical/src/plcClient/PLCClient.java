package plcClient;

import java.io.*;
import java.net.*;

public class PLCClient {

    private boolean verbose;
    private String IP;
    private int port;
    private Socket PLCSocket;
    private DataOutputStream outToServer;
    private BufferedReader inFromServer;
  
    private static final String prefix = "[PLC Client Library] ";

 
    public PLCClient(String IP, int port) throws Exception {
       this.IP = IP;
       this.port = port;
       outToServer = null;
       inFromServer = null;
       verbose = false;
       if(verbose) System.out.println(prefix+"Server IP Address: "+IP+". Server port:"+port);
       printIPAddress();
    }



    public PLCClient(String IP, int port, boolean verbose) throws Exception {
       this.IP = IP;
       this.port = port;
       outToServer = null;
       inFromServer = null;
       this.verbose = verbose;
       if(verbose) System.out.println(prefix+"Server IP Address: "+IP+". Server port:"+port);
       printIPAddress();
    }



    public boolean connectToPLC() {

       if(verbose) System.out.println(prefix+"Connecting to PLC...");
       if(verbose) System.out.println(prefix+"(If stuck here, check ethernet cable and whether JavaSMC server is running on PLC)");

       try {
          PLCSocket = new Socket(IP, port);
          outToServer = new DataOutputStream(PLCSocket.getOutputStream());
          inFromServer = new BufferedReader(new InputStreamReader(PLCSocket.getInputStream()));
       }
       catch(Exception e) {
	  //e.printStackTrace();
	  if(verbose) System.out.println(prefix+"Could not open a socket connection with JavaSMC server on PLC");
          if(verbose) System.out.println(prefix+"(Have you done 'sudo ifconfig eth0 192.168.1.4'?)");
          if(verbose) System.out.println(prefix+"(If so, try pinging the PLC ethernet port (192.168.1.2?)");

          return false;	
       }
       return true;
    }



    public boolean disconnectFromPLC() {

       try {
          //inFromServer.flush();
          //outToServer.flush();
          //outToServer=null;
	  PLCSocket.shutdownInput();
          PLCSocket.close();
       }
       catch(Exception e) {
          return false;	
       }
       return true;	
    }



    public String send(String request) throws Exception {
        //String sentence;
        //String modifiedSentence;
        //BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        //Socket clientSocket = new Socket("169.254.0.3", 850);
        //Socket clientSocket = new Socket(IP, port);
	//DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());

	boolean wasConnected = true;

	if(PLCSocket == null) {
	   wasConnected = false;
	   connectToPLC();
	}

	if(verbose) System.out.println(prefix+"Sending the following request: "+request);

	//outToServer.flush();

        //outToServer = new DataOutputStream(PLCSocket.getOutputStream());
        outToServer.writeBytes(request + '\n');
        //inFromServer = new BufferedReader(new InputStreamReader(PLCSocket.getInputStream()));
        String reply = inFromServer.readLine();
	if(verbose) System.out.println(prefix+"Received the following reply: "+reply);

	if(!wasConnected) 
	   disconnectFromPLC();

        return reply;
    }



    private void printIPAddress() {
	try {
	   if(verbose) System.out.println(prefix+"Client IP Address: "+Inet4Address.getLocalHost().getHostAddress());
	}
	catch(Exception e) {
	   if(verbose) System.out.println(prefix+"Could not obtain client IP Address");
	}
    }
}