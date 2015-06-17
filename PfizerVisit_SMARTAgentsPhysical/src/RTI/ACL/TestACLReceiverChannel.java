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

import RTI.keyValue.KeyValuePair;
import RTI.keyValue.KeyValuePairDataReader;
import RTI.keyValue.KeyValuePairSeq;
import RTI.keyValue.KeyValuePairTypeSupport;
import RTI.keyValue.KeyValueSimple;

import com.rti.connext.infrastructure.Sample;
import com.rti.connext.requestreply.Replier;
import com.rti.connext.requestreply.ReplierParams;
import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.domain.DomainParticipantFactory;
import com.rti.dds.domain.DomainParticipantQos;
import com.rti.dds.infrastructure.Duration_t;
import com.rti.dds.infrastructure.RETCODE_NO_DATA;
import com.rti.dds.infrastructure.ResourceLimitsQosPolicy;
import com.rti.dds.infrastructure.StatusKind;
import com.rti.dds.subscription.DataReader;
import com.rti.dds.subscription.DataReaderAdapter;
import com.rti.dds.subscription.InstanceStateKind;
import com.rti.dds.subscription.SampleInfo;
import com.rti.dds.subscription.SampleInfoSeq;
import com.rti.dds.subscription.SampleStateKind;
import com.rti.dds.subscription.Subscriber;
import com.rti.dds.subscription.ViewStateKind;
import com.rti.dds.topic.Topic;

public class TestACLReceiverChannel extends DataReaderAdapter {
	
	DomainParticipant participant;
	Subscriber subscriber = null;
	Topic topic = null; 
	TestACLDataReader reader = null;
	
	//Time to wait between sample reads
    final long receivePeriodMillisec = 250;
	
	 //True while we want to keep reading
    private boolean continueReading = true;  
	
	private LinkedBlockingQueue<ACLMessage> receivedMessages = new LinkedBlockingQueue<ACLMessage>();
	
	//private final static Duration_t MAX_WAIT = new Duration_t(Integer.MAX_VALUE, 0);
	
	public TestACLReceiverChannel(int domainID, String requesterName, String replierName) {		
				
		String topicName = "DirectCommunication-" + requesterName + "-to-" + replierName;
		
		System.out.println("Creating ACLReceiver with topic " + topicName);
		
		DomainParticipantQos dpqos = new DomainParticipantQos();
        DomainParticipantFactory.TheParticipantFactory.get_default_participant_qos(dpqos);

        Random random = new Random(System.currentTimeMillis());
    	dpqos.wire_protocol.rtps_app_id = dpqos.wire_protocol.rtps_app_id + random.nextInt();
		
		//Create Participant
		 participant = DomainParticipantFactory.TheParticipantFactory.
            create_participant(
                domainID, dpqos,
                null /* listener */, StatusKind.STATUS_MASK_NONE);
        if (participant == null) {
            System.err.println("create_participant error\n");
            return;
        }  
				
		 // --- Create subscriber --- //

        /* To customize subscriber QoS, use
           the configuration file USER_QOS_PROFILES.xml */

        subscriber = participant.create_subscriber(
            DomainParticipant.SUBSCRIBER_QOS_DEFAULT, null /* listener */,
            StatusKind.STATUS_MASK_NONE);
        if (subscriber == null) {
            System.err.println("create_subscriber error\n");
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
        
        // --- Create reader --- //       

        /* To customize data reader QoS, use
           the configuration file USER_QOS_PROFILES.xml */

        reader = (TestACLDataReader)
            subscriber.create_datareader(
                topic, Subscriber.DATAREADER_QOS_DEFAULT, this,
                StatusKind.STATUS_MASK_ALL);
        if (reader == null) {
            System.err.println("create_datareader error\n");
            return;
        }
		
	}
	
    public void startReading() {   	
    	
    	// --- Wait for data --- //  
    	while (continueReading) {       		
            try {     
            	//System.out.println(topic.get_name() + " waiting");
                Thread.sleep(receivePeriodMillisec);  // in millisec
            } catch (InterruptedException ix) {
                System.err.println("INTERRUPTED");
                break;
            }
        }        
    }      

    //Stop listening
    public void shutDownKeyValueListener() {
    	
    	continueReading = false;
    	
    	if(participant != null) {
            participant.delete_contained_entities();

            DomainParticipantFactory.TheParticipantFactory.
                delete_participant(participant);
        }
    }           
    	
   TestACLSeq _dataSeq = new TestACLSeq();
    SampleInfoSeq _infoSeq = new SampleInfoSeq();

    public void on_data_available(DataReader reader) {    	
    	
    	
    	//System.out.println("TestACLReceiver: Message Received");

    	
    	TestACLDataReader testACLReader =
            (TestACLDataReader)reader;
        
    	TestACL acl = null;
    	ACLMessage anotherMessage = null;
    	
        try {
           testACLReader.take(
                _dataSeq, _infoSeq,
                ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                SampleStateKind.ANY_SAMPLE_STATE,
                ViewStateKind.ANY_VIEW_STATE,
                InstanceStateKind.ANY_INSTANCE_STATE);

            for(int i = 0; i < _dataSeq.size(); ++i) {
                SampleInfo info = (SampleInfo)_infoSeq.get(i);

                if (info.valid_data) {
                	TestACL aclReceived = ((TestACL)_dataSeq.get(i));
                	acl = new TestACL(aclReceived);   
                	//Deserialise message
   					String serialisedString = acl.serialisedMessage;
   					byte [] messageData = Base64Coder.decode(serialisedString);
   					ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(messageData));
   					ACLMessage aclMessage  = (ACLMessage) ois.readObject();
   			    
   					ois.close();			    
   			   
   					anotherMessage = aclMessage;
   					
   					if (anotherMessage != null) {
   						//System.out.println("TestACLReceiver: Message Received");						
   						receivedMessages.put(anotherMessage);
   						//System.out.println("Messages in queue: " + receivedMessages.size());
   				}
   					
                }
            }
        } catch (RETCODE_NO_DATA noData) {
            // No data to process
        } catch (IOException ioe) {
			System.out.println("ACL Message could not be decoded. - JC TestACLMessenger1");
			
		} catch (ClassNotFoundException e) {
			System.out.println("ACL Message could not be decoded. - JC TestACLMessenger2");
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
            testACLReader.return_loan(_dataSeq, _infoSeq);  
            
        }
    }
    
    
	//Collect gathered ACLMessages	
	public ACLMessage getMessage() {  		
		
		return receivedMessages.poll();
	}	
	
	public String getTopicName() {
		
		return topic.get_name();
	}

}
