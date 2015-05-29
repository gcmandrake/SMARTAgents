/*This is for sending ACL messages over RTI.
 * 
 * For this to work, both the Messenger and Receiver must have the same service name.
 * This requires a separate service for each direction of message travel i.e. one service for A->B, and one for B->A

Jack Chaplin
20150227

*/

package RTI.ACL;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Base64;

import com.rti.connext.infrastructure.WriteSample;
import com.rti.connext.requestreply.Requester;
import com.rti.connext.requestreply.RequesterParams;
import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.domain.DomainParticipantFactory;
import com.rti.dds.infrastructure.StatusKind;
import com.rti.dds.infrastructure.Duration_t;

import jade.lang.acl.ACLMessage;


public class TestACLMessengerRequestReply {
	
	private DomainParticipant participant;
	private Requester<TestACL, TestACL> requester;
	String serviceName;	
	
	public TestACLMessengerRequestReply (int domainID, String requesterName, String replierName) {
		
		serviceName = "DirectCommunication-" + requesterName + "-to-" + replierName;
		
		System.out.println("Creating ACLMessenger with topic " + serviceName);
		
		//Create participant
		participant = DomainParticipantFactory.get_instance()
				.create_participant(
						domainID, 
						DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
						null,
						StatusKind.STATUS_MASK_NONE
				);
		
		if (participant == null) {
			throw new RuntimeException("Participant Creation Failed! - JC TestACLMessenger1");
		}
		
		//Create Requester
		requester = new Requester<TestACL, TestACL> (
				new RequesterParams (
						participant,
						TestACLTypeSupport.get_instance(),
						TestACLTypeSupport.get_instance()
						)
				.setServiceName(serviceName)				
		);	
		
	}
	
	//Send an ACL message
	
	public void sendACLMessage(ACLMessage message) {
		
		System.out.println("Sending ACLMessage on topic " + requester.getRequestDataWriter().get_topic().get_name());
		
		String serialisedString = null;
		
		//Encode the ACL message into a base64 byte stream
		try {
			 ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos);
		     oos.writeObject(message);
		     oos.close();
		     serialisedString = new String(Base64Coder.encode( baos.toByteArray()));
		} catch (IOException ioe) {
			System.out.println("ACL message could not be encoded. - JC TestACLMessenger1");
			System.out.println(message.toString());
		}
		
		if (serialisedString == null) {
			System.out.println("ACL Message could not be encoded. - JC TestACLMessenger2");
			return;
		}
		
		//Create a sample writer
		WriteSample<TestACL> DDSMessage = requester.createRequestSample();
		// Set data
		DDSMessage.getData().serialisedMessage = serialisedString;
		
		requester.sendRequest(DDSMessage);
		
		//Do not wait for a reply!		
	}
	
	//Terminate this request/reply stream	
	public void close() {
        if (requester != null) {
            requester.close();
            requester = null;
        }
        
        if (participant != null) {
            participant.delete_contained_entities();
                DomainParticipantFactory.get_instance()
                    .delete_participant(participant);
            participant = null;
        }

    }
	
	
	
	

}
