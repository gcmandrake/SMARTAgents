package resourceAgents.stationBehaviours.doActions;

import plcClient.Message;
import plcClient.PLCClient;
import resourceAgents.Requirement;

public class MeasureContainerBehaviour {	

	
	//This code taken from Lavindra's SMCAgent5
	
	final String TEST_QUANTITY_ARGUMENT = "test_quantity";	
	private PLCClient client;
	private Requirement requirement;
	private String request, reply;
	private Message req, msgReply;
	
	
	public MeasureContainerBehaviour(Requirement requirement, PLCClient client) {
		
		if (requirement != null) {
			this.requirement = requirement;
		} else {
			System.out.println("MeasureContainerBehaviour passed null requirement");
		}
		
		if (client != null) {
			this.client = client;
		} else {
			System.out.println("MeasureContainerBehaviour passed null client");
		}
	}
	
	public boolean sendMessagesToPLC() {
		
		try {	
			
			int expectedResult = Integer.parseInt(requirement.getArguments().get(TEST_QUANTITY_ARGUMENT));
			Boolean testPassed = true;
			Message replyMsg;
			
			req = new Message(Message.WRITE, Message.BOOL, String.valueOf(true), Message.LOCAL, "Go_Test");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg);      	    

            req = new Message(Message.WRITE, Message.SHORT, String.valueOf(expectedResult), Message.LOCAL, "Load_Mode");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg);

            req = new Message(Message.WRITE, Message.BOOL, String.valueOf(false), Message.LOCAL, "Go_Passthrough");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg);
            
            req = new Message(Message.WRITE, Message.BOOL, String.valueOf(true), Message.LOCAL, "Go");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg);

            Thread.sleep(2000);

            req = new Message(Message.WRITE, Message.BOOL, String.valueOf(false), Message.LOCAL, "Go");
            reply = client.send(req.toString());
            replyMsg = new Message(reply);
            interpret(replyMsg); 
            
            Thread.sleep(15000);
			
			return testPassed;
	    
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
