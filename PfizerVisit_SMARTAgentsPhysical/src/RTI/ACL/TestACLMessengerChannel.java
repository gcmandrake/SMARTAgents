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

import RTI.keyValue.KeyValuePairDataWriter;
import RTI.keyValue.KeyValuePairTypeSupport;

import com.rti.connext.infrastructure.WriteSample;
import com.rti.connext.requestreply.Requester;
import com.rti.connext.requestreply.RequesterParams;
import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.domain.DomainParticipantFactory;
import com.rti.dds.infrastructure.InstanceHandle_t;
import com.rti.dds.infrastructure.StatusKind;
import com.rti.dds.infrastructure.Duration_t;
import com.rti.dds.publication.Publisher;
import com.rti.dds.topic.Topic;

import jade.lang.acl.ACLMessage;


public class TestACLMessengerChannel {
	
	private DomainParticipant participant;
	Publisher publisher = null;
    Topic topic = null;
    TestACLDataWriter writer = null;
	
	public TestACLMessengerChannel (int domainID, String requesterName, String replierName) {
		
		String topicName = "DirectCommunication-" + requesterName + "-to-" + replierName;
		
		System.out.println("Creating ACLMessenger with topic " + topicName);
		
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
		publisher = participant.create_publisher(
			DomainParticipant.PUBLISHER_QOS_DEFAULT, null /* listener */,
            StatusKind.STATUS_MASK_NONE);
        if (publisher == null) {
            System.err.println("create_publisher error\n");
            return;
        }   
        
        // --- Create topic --- //

        /* Register type before creating topic */
        String typeName = TestACLTypeSupport.get_type_name();
        TestACLTypeSupport.register_type(participant, typeName);

        /* To customize topic QoS, use
           the configuration file USER_QOS_PROFILES.xml */

        topic = participant.create_topic(
            topicName,
            typeName, DomainParticipant.TOPIC_QOS_DEFAULT,
            null /* listener */, StatusKind.STATUS_MASK_NONE);
        if (topic == null) {
            System.err.println("create_topic error\n");
            return;
        }   
        
        // --- Create writer --- //

        /* To customize data writer QoS, use
           the configuration file USER_QOS_PROFILES.xml */

        writer = (TestACLDataWriter)
            publisher.create_datawriter(
                topic, Publisher.DATAWRITER_QOS_DEFAULT,
                null /* listener */, StatusKind.STATUS_MASK_NONE);
        if (writer == null) {
            System.err.println("create_datawriter error\n");
            return;
        }   
		
	}
	
	//Send an ACL message
	
	public void sendACLMessage(ACLMessage message) {
		
		//System.out.println("Sending ACLMessage on topic");
		
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
		
        InstanceHandle_t instance_handle = InstanceHandle_t.HANDLE_NIL;

        TestACL instance = new TestACL();
        instance.serialisedMessage = serialisedString;
		//Create a sample writer
		writer.write(instance, instance_handle);
		System.out.println("Message sent!");
		//Do not wait for a reply!		
	}
	
	//Terminate this request/reply stream	
	public void shutDownPublisher() {
		
		if(participant != null) {
            participant.delete_contained_entities();

            DomainParticipantFactory.TheParticipantFactory.
                delete_participant(participant);
        }
	}   
	
	public String getTopicName() {
		
		return topic.get_name();
	}
	
	
	
	

}
