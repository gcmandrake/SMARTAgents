package RTI.keyValue;


/* keyValuePairPublisher.java

   A publication of data of type keyValuePair

   This file is derived from code automatically generated by the rtiddsgen 
   command:

   rtiddsgen -language java -example <arch> .idl

   Example publication of type keyValuePair automatically generated by 
   'rtiddsgen' To test them follow these steps:

   (1) Compile this file and the example subscription.

   (2) Start the subscription with the command
       java keyValuePairSubscriber <domain_id> <sample_count>
       
   (3) Start the publication with the command
       java keyValuePairPublisher <domain_id> <sample_count>

   (4) [Optional] Specify the list of discovery initial peers and 
       multicast receive addresses via an environment variable or a file 
       (in the current working directory) called NDDS_DISCOVERY_PEERS.  
       
   You can run any number of publishers and subscribers programs, and can 
   add and remove them dynamically from the domain.
              
   Example:
        
       To run the example application on domain <domain_id>:
            
       Ensure that $(NDDSHOME)/lib/<arch> is on the dynamic library path for
       Java.                       
       
        On Unix: 
             add $(NDDSHOME)/lib/<arch> to the 'LD_LIBRARY_PATH' environment
             variable
                                         
        On Windows:
             add %NDDSHOME%\lib\<arch> to the 'Path' environment variable
                        

       Run the Java applications:
       
        java -Djava.ext.dirs=$NDDSHOME/class keyValuePairPublisher <domain_id>

        java -Djava.ext.dirs=$NDDSHOME/class keyValuePairSubscriber <domain_id>        

       
       
modification history
------------ -------         
*/

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import resourceAgents.Requirement;
import RTI.ACL.Base64Coder;
import RTI.ACL.TestACL;

import com.rti.dds.domain.*;
import com.rti.dds.infrastructure.*;
import com.rti.dds.publication.*;
import com.rti.dds.topic.*;
import com.rti.ndds.config.*;

// ===========================================================================

public class KeyValuePairPublisherModule {
	
	 DomainParticipant participant;
     Publisher publisher;
     HashMap<String, Topic> topics = new HashMap<String, Topic>();
     HashMap<String, KeyValuePairDataWriter> writers = new HashMap<String, KeyValuePairDataWriter>();
     
     String parentAgent = "";
     String typeName;
     
     final String REQUIREMENTS_MESSAGE_KEY = "Requirements";
     final String GENERAL_TOPIC_NAME = "general_announce";    
 
     
    //This method creates a Key:Value publisher. General means create a general publisher. Otherwise, you've got to create seperate topics manually.
	public KeyValuePairPublisherModule(int domainId, String parentAgentName, boolean general) {
		
		DomainParticipantFactoryQos factoryQoS = new DomainParticipantFactoryQos();
   		DomainParticipantFactory.TheParticipantFactory.get_qos(factoryQoS);
   	
   		factoryQoS.resource_limits.max_objects_per_thread = 16384; 
   		DomainParticipantFactory.TheParticipantFactory.set_qos(factoryQoS);
		
		this.parentAgent = parentAgentName;
        
        // --- Create participant --- //

        //To customize participant QoS, use the configuration file  USER_QOS_PROFILES.xml

       participant = DomainParticipantFactory.TheParticipantFactory.
            create_participant(
                domainId, DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                null, StatusKind.STATUS_MASK_NONE);
        if (participant == null) {
            System.err.println("create_participant error\n");
            return;
        }       
                
        // --- Create publisher --- //

        //To customize publisher QoS, use
        //the configuration file USER_QOS_PROFILES.xml

       publisher = participant.create_publisher(
            DomainParticipant.PUBLISHER_QOS_DEFAULT, null,
            StatusKind.STATUS_MASK_NONE);
        if (publisher == null) {
            System.err.println("create_publisher error\n");
            return;
        }
       
        if (general) {
            
    
	        // --- Create topic --- //        	
        	
	
	        /* Register type before creating topic */
        	//System.out.println("Registered type general");
       		String typeName = KeyValuePairTypeSupport.get_type_name();
			KeyValuePairTypeSupport.register_type(participant, typeName);				
			
	
	         /*To customize topic QoS, use
	           the configuration file USER_QOS_PROFILES.xml */
	
	        Topic topic = participant.create_topic(
	            GENERAL_TOPIC_NAME,
	            typeName, DomainParticipant.TOPIC_QOS_DEFAULT,
	            null /* listener */, StatusKind.STATUS_MASK_NONE);
	        if (topic == null) {
	            System.err.println("create_topic error\n");
	            return;
	        }  else {
	        	topics.put(GENERAL_TOPIC_NAME, topic);
	        }
	            
	        // --- Create writer --- //
	
	        /* To customize data writer QoS, use
	           the configuration file USER_QOS_PROFILES.xml */
	
	        KeyValuePairDataWriter writer = (KeyValuePairDataWriter)
	            publisher.create_datawriter(
	                topics.get(GENERAL_TOPIC_NAME), Publisher.DATAWRITER_QOS_DEFAULT,
	                null /* listener */, StatusKind.STATUS_MASK_NONE);
	        if (writer == null) {
	            System.err.println("create_datawriter error\n");
	            return;
	        }   else {
	        	writers.put(GENERAL_TOPIC_NAME, writer);
	        	//System.out.println("Publisher topic added: " + GENERAL_TOPIC_NAME);
	        }
	        
        } 
	}
	
	public void createTopic(String topicName) {	
		
		ArrayList<String> alreadyPublishedTopics = this.getAllTopics();
		if (alreadyPublishedTopics.contains(topicName)) {
			System.out.println("+++ERROR: Topic " + topicName + " already exists. This is fine for PoCA Agent");
		} else {
		
			
			//Register type before creating topic 
			//Uncommenting this if statement crashed the program hard. No idea why.
			//if (typeRegistered == false) {
				//System.out.println("Registered type not general");
				String typeName = KeyValuePairTypeSupport.get_type_name();
				KeyValuePairTypeSupport.register_type(participant, typeName);
				
			//}
	        
			Topic topic = participant.create_topic(
		            topicName,
		            typeName, DomainParticipant.TOPIC_QOS_DEFAULT,
		            null , StatusKind.STATUS_MASK_NONE);
		        if (topic == null) {
		            System.err.println("create_topic error\n");
		            return;
		        }  else {
		        	
		        	topics.put(topicName, topic);
		        	
		        }
		            
		        // --- Create writer --- //
		
		        //To customize data writer QoS, use
		         //the configuration file USER_QOS_PROFILES.xml
		
		        KeyValuePairDataWriter writer = (KeyValuePairDataWriter)
		            publisher.create_datawriter(
		                topics.get(topicName), Publisher.DATAWRITER_QOS_DEFAULT,
		                null, StatusKind.STATUS_MASK_NONE);
		        if (writer == null) {
		            System.err.println("create_datawriter error\n");
		            return;
		        }   else {
		        	writers.put(topicName, writer);
		        	//System.out.println("Publisher topic added: " + topicName);
		        }
			}
		
	}
	
	public void removeTopic(String topicName) {
		
		topics.remove(topicName);
		writers.remove(topicName);
	}
	
	//Send a message	
	public void sendKeyValueMessage(String topic, String key, String value) {
		
    	/* Create data sample for writing */
        KeyValuePair instance = new KeyValuePair();
    	instance.key = key;
    	instance.source = parentAgent;
    	instance.value = value;
        InstanceHandle_t instance_handle = InstanceHandle_t.HANDLE_NIL;
        
        /*System.out.println("KeyValuePairPublisher - Sending a message");
    	System.out.println("--- Topic: " + topic.get_name());
    	System.out.println("--- Domain: " + topic.get_participant().get_domain_id());
    	System.out.println("--- Key: " + instance.key);
    	System.out.println("--- Value: " + instance.value);*/
        
        writers.get(topic).write(instance, instance_handle);
		
	}
	
	public void sendKeyValueMessageNoSource(String topic, String key, String value) {
		
		/* Create data sample for writing */
        KeyValuePair instance = new KeyValuePair();
    	instance.key = key;
    	instance.source = "";
    	instance.value = value;
        InstanceHandle_t instance_handle = InstanceHandle_t.HANDLE_NIL;
        
        /*System.out.println("KeyValuePairPublisher - Sending a message");
    	System.out.println("--- Topic: " + topic.get_name());
    	System.out.println("--- Domain: " + topic.get_participant().get_domain_id());
    	System.out.println("--- Key: " + instance.key);
    	System.out.println("--- Value: " + instance.value);*/
        
        writers.get(topic).write(instance, instance_handle);
		
	}
	
	public void sentRequirements(String topic, ArrayList<Requirement> requirements, String requester) {
		
		String serialisedString = null;
		
		//Encode the ACL message into a base64 byte stream
		try {
			 ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos);
		     oos.writeObject(requirements);
		     oos.close();
		     serialisedString = new String(Base64Coder.encode( baos.toByteArray()));
		} catch (IOException ioe) {
			System.out.println("Requirements could not be encoded. - JC KeyValuePairPublisher");
			System.out.println(requirements.toString());
		}
		
		if (serialisedString == null) {
			System.out.println("Requirements could not be encoded. - JC KeyValuePairPublisher");
			return;
		}
		
		KeyValuePair instance = new KeyValuePair();
		instance.key = REQUIREMENTS_MESSAGE_KEY;
		instance.source = requester;
		instance.value = serialisedString;
		InstanceHandle_t instance_handle = InstanceHandle_t.HANDLE_NIL;
		
		writers.get(topic).write(instance, instance_handle);
	}
	
	public ArrayList<String> getAllTopics() {
    	
    	ArrayList<String> returnedTopicNames = new ArrayList<String>();
    	
      	for (Map.Entry<String, KeyValuePairDataWriter> entry : writers.entrySet()) {
      		
      		String topic = entry.getValue().get_topic().get_name();
      		returnedTopicNames.add(topic);      		
      	}
      	
      	return returnedTopicNames;
    }
	
	//Shut down Publisher
	public void shutDownPublisher() {
		
		if(participant != null) {
            participant.delete_contained_entities();

            DomainParticipantFactory.TheParticipantFactory.
                delete_participant(participant);
        }
	}           
}

        