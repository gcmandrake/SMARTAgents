package resourceAgents;

import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import RTI.ACL.TestACLMessengerChannel;
import RTI.ACL.TestACLMessengerRequestReply;
import RTI.ACL.TestACLReceiverChannel;
import RTI.ACL.TestACLReceiverRequestReply;

public class RequestReplyMessageChannel {
	
	//Direct messaging channel to agent
	private TestACLMessengerChannel channelToAgent;	
	//Receiver for messages from Agent
	private TestACLReceiverChannel channelFromAgent;
	
	public RequestReplyMessageChannel(int domainID, String PoCAgentName, String targetName) {
		
		this.setupChannelToAgent(domainID, PoCAgentName, targetName);
		this.setupChannelFromAgent(domainID, PoCAgentName, targetName);		
	}
	
	//Create Messenger to Agent	
	public void setupChannelToAgent(int domainID, String PoCAgentName, String targetName) {
		
		channelToAgent = new TestACLMessengerChannel(domainID, PoCAgentName+"PoC", targetName);
	}
	
	//Create Reciever from Agent
	public void setupChannelFromAgent(int domainID, String PoCAgentName, String targetName) {
		
		channelFromAgent = new TestACLReceiverChannel(domainID, targetName, PoCAgentName+"PoC");
		//channelFromAgent = new TestACLReceiver(domainID, PoCAgentName, targetName);
		
	}
	
	public TestACLReceiverChannel getSubscriber() {
		return this.channelFromAgent;
	}
	
	public ACLMessage getMessageFromAgent() {
		
		return this.channelFromAgent.getMessage();
		
	}
	
	public void sendMessageToAgent(ACLMessage message) {
		
		this.channelToAgent.sendACLMessage(message);
	}
}
