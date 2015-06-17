package resourceAgents.stationBehaviours.doActions;

import plcClient.Message;
import plcClient.PLCClient;
import resourceAgents.Requirement;

public class LoadContainerBehaviour {		

	private PLCClient client;
	private Requirement requirement;
	private String request, reply;
	private Message req, msgReply;
	
	//This code taken from Lavindra's SMCAgent2
	
	
	public LoadContainerBehaviour(Requirement requirement, PLCClient client) {
		
		if (requirement != null) {
			this.requirement = requirement;
		} else {
			System.out.println("LoadContainerBehaviour passed null requirement");
		}
		
		if (client != null) {
			this.client = client;
		} else {
			System.out.println("LoadContainerBehaviour passed null client");
		}
	}
	
	public String sendMessagesToPLC() {
		
		try {	
			
		  // send a request to read the string from the PLC's local variable named 'LastBarCode'
		  req = new Message(Message.READ, Message.STRING, Message.LOCAL, "LastBarcode");
		  reply = client.send(req.toString());
		  Message replyMsg = new Message(reply);
		  interpret(replyMsg);
		  String barCode = replyMsg.getValue();
        
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
	     
          req = new Message(Message.WRITE, Message.BOOL, String.valueOf(true), Message.GLOBAL, "m");
          reply = client.send(req.toString());
          replyMsg = new Message(reply);
          interpret(replyMsg);

          Thread.sleep(2000);

          req = new Message(Message.WRITE, Message.BOOL, String.valueOf(false), Message.GLOBAL, "m");
          reply = client.send(req.toString());
          replyMsg = new Message(reply);
          interpret(replyMsg);
          
          Thread.sleep(5000);
         
	      
	      return barCode;
	        
	      }
	
	      catch(Exception e) {
	    	  
	        System.out.println("Could not communicate with PLC");	        
	        return "ERROR_SCANNING_BARCODE";
	        
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
