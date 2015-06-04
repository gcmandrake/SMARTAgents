package resourceAgents.stationBehaviours.passThrough;

import plcClient.Message;
import plcClient.PLCClient;
import resourceAgents.Requirement;

public class LidContainerPassThroughBehaviour {	
	
	final String LABEL_LID_ARGUMENT = "label_lid";
	private PLCClient client;
	private Requirement requirement;
	private String request, reply;
	private Message req, msgReply;
	
	//This code taken from Lavindra's SMCAgent4
	
	
	public LidContainerPassThroughBehaviour(PLCClient client) {
		
		if (client != null) {
			this.client = client;
		} else {
			System.out.println("LidContainerPassThroughBehaviour passed null client");
		}
	}
	
	public boolean sendMessagesToPLC() {
		
		try {	
			
			Message replyMsg;
		
			req = new Message(Message.WRITE, Message.BOOL, String.valueOf(false), Message.LOCAL, "Go_Lid");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg);

            req = new Message(Message.WRITE, Message.BOOL, String.valueOf(true), Message.LOCAL, "Go_Passthrough");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg);        

            // pulse the boolean variable `Go' 
            req = new Message(Message.WRITE, Message.BOOL, String.valueOf(true), Message.LOCAL, "Go");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg);

            Thread.sleep(2000);

            req = new Message(Message.WRITE, Message.BOOL, String.valueOf(false), Message.LOCAL, "Go");
            reply = client.send(req.toString());
          	replyMsg = new Message(reply);
          	interpret(replyMsg);
          	
          	Thread.sleep(10000);
		    
		  return true;
	    
		} catch(Exception e) {
	        System.out.println("Could not communicate with PLC");
	        
	        return false;
	    }

	}
	
	  private void interpret(Message msgReply) {

		    if(msgReply.getResult() == Message.ERROR) {
		      System.out.println("Operation could not be successfully performed on the PLC");
		    }
		    else if(msgReply.getResult() == Message.SUCCESS) {
		      System.out.println("Operation was successful on the PLC, with value: " + msgReply.getValue());
		    }
		  }
}
