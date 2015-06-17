/*This is for receiving ACL messages over RTI.
 * 
 * For this to work, both the Messenger and Receiver must have the same service name.
 * This requires a separate service for each direction of message travel i.e. one service for A->B, and one for B->A
 * 
 * NOTE: Due to limitations on MAX_WAIT, this code will only function for 24 days before needing reinitialisation.

Jack Chaplin
20150303

*/

package RTI.ACL;

import jade.lang.acl.ACLMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import RTI.keyValue.KeyValueSimple;

import com.rti.connext.infrastructure.Sample;
import com.rti.connext.requestreply.Replier;
import com.rti.connext.requestreply.ReplierParams;
import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.domain.DomainParticipantFactory;
import com.rti.dds.domain.DomainParticipantQos;
import com.rti.dds.infrastructure.Duration_t;
import com.rti.dds.infrastructure.StatusKind;

public class TestACLReceiverRequestReply {
	
	private DomainParticipant participant;
	//Note that RTI standard requires a reply. Actually, we're just putting the message into the agent's queue and forgetting about it. The agent will handle replies.
	private Replier<TestACL, TestACL> replier;
	String serviceName;
	
	private LinkedBlockingQueue<ACLMessage> receivedMessages = new LinkedBlockingQueue<ACLMessage>();
	
	private final static Duration_t MAX_WAIT = new Duration_t(Integer.MAX_VALUE, 0);
	
	public TestACLReceiverRequestReply(int domainID, String requesterName, String replierName) {		
				
		serviceName = "DirectCommunication-" + requesterName + "-to-" + replierName;
		
		System.out.println("+Creating ACLReceiver with topic " + serviceName);
		
		DomainParticipantQos dpqos = new DomainParticipantQos();
        DomainParticipantFactory.TheParticipantFactory.get_default_participant_qos(dpqos);

        Random random = new Random(System.currentTimeMillis());
    	dpqos.wire_protocol.rtps_app_id = dpqos.wire_protocol.rtps_app_id + random.nextInt();
		
		//Create Participant
		participant = DomainParticipantFactory.get_instance().create_participant(
				domainID,
				dpqos,
				null, 
				StatusKind.STATUS_MASK_NONE);
		
		if (participant == null) {
			throw new RuntimeException("Participant Creation Failed! - JC TestACLReceiver1");
		}
				
		//Create replier (note: replier just adds messages to queue)
		replier = new Replier<TestACL, TestACL>(
				new ReplierParams<TestACL, TestACL> (
						participant,
						TestACLTypeSupport.get_instance(),
						TestACLTypeSupport.get_instance())
				.setServiceName(serviceName)					
		);		
		
	}
	
	public void runReceiver() {		
		
		//Create empty message
		Sample<TestACL> incomingMessage = replier.createRequestSample();
		
		//Wait for messages
		while (replier.receiveRequest(incomingMessage, MAX_WAIT)) {
			
			if (!incomingMessage.getInfo().valid_data) {
				System.out.println("ACL Messaged recieved but invalid - JC TestACLReceiver1");
				continue;
			}			
			
			try {
				//System.out.println("ACLReceiver: Some information received");
				//Deserialise message
				String serialisedString = incomingMessage.getData().serialisedMessage;
				byte [] messageData = Base64Coder.decode(serialisedString);
			    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(messageData));
			    ACLMessage aclMessage  = (ACLMessage) ois.readObject();
			    
			    ois.close();
			    
			    if (aclMessage != null) {
			    	//System.out.println(aclMessage.getConversationId());
			    }
			    
			    ACLMessage anotherMessage = aclMessage;
			    
			    if (anotherMessage != null) {
					//System.out.println("TestACLReceiver: Message Received");						
					receivedMessages.put(anotherMessage);
					//System.out.println("Messages in queue: " + receivedMessages.size());
				}
			    
			}  catch (IOException ioe) {
				System.out.println("ACL Message could not be decoded. - JC TestACLMessenger1");
				continue;
			} catch (ClassNotFoundException e) {
				System.out.println("ACL Message could not be decoded. - JC TestACLMessenger2");
				continue;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 		
		}		
	}
	
	//Collect gathered ACLMessages	
	public ACLMessage getMessage() {  		
		
		return receivedMessages.poll();
	
	}	
	
	public String getTopic() {
		
		return replier.getReplyDataWriter().get_topic().get_name();
	}
	
	//Terminate this request/reply stream	
	public void close() {
        if (replier != null) {
            replier.close();
            replier = null;
        }
        
        if (participant != null) {
            participant.delete_contained_entities();
                DomainParticipantFactory.get_instance()
                    .delete_participant(participant);
            participant = null;
        }

    }
	
	
	
	

}
