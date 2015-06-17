package resourceAgents.stationBehaviours.doActions;

import plcClient.Message;
import plcClient.PLCClient;
import resourceAgents.Requirement;

public class FillStationBehaviour {
	
	private final String FILL_QUANTITY_ARGUMENT = "quantity";
	private PLCClient client;
	private Requirement requirement;
	private String request, reply;
	private Message req, msgReply;
	
	//This code taken from Lavindra's SMCAgent1
	
	
	public FillStationBehaviour(Requirement requirement, PLCClient client) {
		
		if (requirement != null) {
			this.requirement = requirement;
		} else {
			System.out.println("FillStationBehaviour passed null requirement");
		}
		
		if (client != null) {
			this.client = client;
		} else {
			System.out.println("FillStationBehaviour passed null client");
		}
	}
	
	public boolean sendMessagesToPLC() {
		
		try {
	
			int loadMode = Integer.parseInt(requirement.getArguments().get(FILL_QUANTITY_ARGUMENT));
		
		    req = new Message(Message.WRITE, Message.SHORT, String.valueOf(loadMode), Message.LOCAL, "Load_Mode");
		    reply = client.send(req.toString());
		    Message replyMsg = new Message(reply);
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
		    
		    Thread.sleep(7000 + (1500 * loadMode));
		    
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
