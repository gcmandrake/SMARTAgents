package resourceAgents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import plcClient.Message;
import plcClient.PLCClient;
import resourceAgents.stationBehaviours.doActions.DispatchContainerBehaviour;
import resourceAgents.stationBehaviours.doActions.FillStationBehaviour;
import resourceAgents.stationBehaviours.doActions.LidContainerBehaviour;
import resourceAgents.stationBehaviours.doActions.LoadContainerBehaviour;
import resourceAgents.stationBehaviours.doActions.MeasureContainerBehaviour;
import resourceAgents.stationBehaviours.passThrough.DispatchContainerPassThroughBehaviour;
import resourceAgents.stationBehaviours.passThrough.FillStationPassThroughBehaviour;
import resourceAgents.stationBehaviours.passThrough.LidContainerPassThroughBehaviour;
import resourceAgents.stationBehaviours.passThrough.MeasureContainerPassThroughBehaviour;
import RTI.ACL.TestACLMessengerChannel;
import RTI.ACL.TestACLReceiverChannel;
import RTI.keyValue.KeyValuePair;
import RTI.keyValue.KeyValuePairPublisherModule;
import RTI.keyValue.KeyValuePairSubscriberModule;
import RTI.keyValue.KeyValueSimple;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ThreadedBehaviourFactory;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

/* The Resource represents a physical element in the system with a PLC.   
 * 
 * Arguments should be:
 * Strings: Capabilities
 * 
 *  @author Jack Chaplin
 *  @foundingDate 20150305
 *  @version 0.1
 *  
 *  
 */

public class ResourceAgent extends Agent {
	
	protected final int DOMAIN_ID = 5;
	protected final String GENERAL_PUBLISHER_TOPIC = "general_announce";	 
	protected final long TICK_TIME_PRODUCT = 10000;
	protected final long TICK_TIME_REANNOUNCE = 10000;
	protected final long TICK_TIME_LONG = 500;
	protected final long TICK_TIME_SHORT = 100;
	
	protected final String ANNOUNCE_NEW_STRING = "announce_new";
	protected final String ANNOUNCE_INFO_STRING = "announce_info";	
	protected final String ANNOUNCE_RECIPE_FINISHED = "announce_recipe_finished";
	protected final String REQUEST_ACL_CONVERSATION_TYPE = "Capability_Request";	
	
	protected final String BARCODE_IDENTIFIER = "Barcode";
	protected final String UNKNOWN_BARCODE_NAME = "+++Unknown_Barcode+++";
	
	protected final String LOAD_CONTAINER_CAPABILITY = "load_container"; 
	protected final String FILL_RED_CAPABILITY = "fill_red";
	protected final String FILL_YELLOW_CAPABILITY = "fill_yellow";
	protected final String FILL_BLUE_CAPABILITY = "fill_blue";
	protected final String TEST_CONTAINER_CAPABILITY = "test_container";
	protected final String LID_CONTAINER_CAPABILITY = "lid_container";
	protected final String DISPATCH_CONTAINER_CAPABILITY = "dispatch_container";
	
	protected final String STATUS_UNCLAIMED = "Unclaimed";
	protected final String STATUS_WAITING = "Waiting";
	protected final String STATUS_IN_PROGRESS = "Claimed";
	protected final String STATUS_COMPLETED = "Completed";
	protected final String STATUS_FAILED = "Failed";	

	//Stores the last barcode scanned
	protected String lastBarcodeScanned = "XXXX";	
	protected ReentrantLock barcodeInUseLock = new ReentrantLock();
	
	protected String[] fakeBarcodes = {"AAAA", "BBBB", "CCCC"};
	protected Boolean[] barcodesInUse = {false, false, false};
	
	protected final String POINT_OF_CONTACT_CAPABILITY = "Point_Of_Contact";	
	protected boolean HAS_POCA_BEEN_FOUND = false;
	
	//A fair hardware lock ensures two threads don't try and use the resource at the same time
	protected ReentrantLock hardwareInUseLock = new ReentrantLock(true);
	
	//As RTI routines are loops, regular behaviours will never terminate and the agent will hang. Threading will prevent this.
	protected ThreadedBehaviourFactory tbf = new ThreadedBehaviourFactory();
	
	//Stores details of known agents and their capabilities (includes self)
	protected HashMap<String, ArrayList<String>> knownResources = new HashMap<String, ArrayList<String>>();	
	
	//Create an RTI general publisher e.g. announce existence.
	protected KeyValuePairPublisherModule generalPublisher;	
	//Create an RTI general subscriber
	protected KeyValuePairSubscriberModule generalSubscriber = null;	
	//Queue of received general messages
	protected ConcurrentLinkedQueue<KeyValueSimple> keyValuePairs;	
	
	//A mapping from Integer IDs to requested capabilities. A List of requirements is required as a agent could be requested to perform multiple actions on the same object.
	//Key: Recipe UID. Value: Requirements
	protected ReentrantLock toDoListLock = new ReentrantLock(true);
	protected HashMap<String, ArrayList<Requirement>> toDoList = new HashMap<String, ArrayList<Requirement>>();
	//A requst might have to occur after other capabilities. Store known information here.
	protected ConcurrentHashMap<String, Recipe> referencedRecipes = new ConcurrentHashMap<String, Recipe>();
	protected ReentrantLock referencedRecipesLock = new ReentrantLock(true);	
	
	//Direct messaging channel to PoC agent
	private TestACLMessengerChannel channelToPoCAgent;	
	//Receiver for messages from PoC Agent
	private TestACLReceiverChannel channelFromPoCAgent;
	//Queue of ACLMessages because JADE's own doesn't appear to work.
	protected ConcurrentLinkedQueue<ACLMessage> aclMessages = new ConcurrentLinkedQueue<ACLMessage>();
	
	//Client for connecting to PLC
	protected  PLCClient client;
	//Verbose status?
	protected boolean verbose = true;
	//Connected to PLC?
	protected boolean connectedToPLC = false;
	//Communication port
	protected final int DEFAULT_PORT = 850;
	//IP Address
	private static final String HARDCODED_ETHERNET_IP = "192.254.1.2";
	
	//Is this agent connected to a PLC? Having a capability 'testing' will fake connection. Untested for mixed testing/not testing environments.
	protected final static String TESTING_CAPABILITY = "testing";
	boolean testingAgent = false;
	
	
	//===========================================================================================================================================
	
	//Perform all necessary startup routines.
	protected void setup() {
		
		//1: Get Arguments
		//1a: Get agent name	
		System.out.println("New Agent Created. Name: " + getName());			
		//1b: get args		
		ArrayList<String> capabilities = getAgentCapabilitiesFromArgs();		
		
		//2: Add self to list of capabilities
		knownResources.put(getName(), capabilities);
		
		//2a: Create Subscriber for feedback on existing agents
		generalSubscriber = new KeyValuePairSubscriberModule(DOMAIN_ID, getName(), true);		
		
		//3: Create general publisher
		generalPublisher = new KeyValuePairPublisherModule(DOMAIN_ID, getName(), true);
		
		//4: Create Capability Map
		keyValuePairs = new ConcurrentLinkedQueue<KeyValueSimple>();		
		
		//5: Adds received KeyValuePairs to a queue for processing every tick
		receiveGeneralKeyValuePairs();
		
		//6: pops a key value pair off the general message queue for processing every tick	
		processKeyValuePairs();	
		
		//7: Start General Subscriber		
		startGeneralSubscriber();		
		
		//8: Announce self to world
		generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_NEW_STRING, createNameAndCapabilitiesString(getName(), capabilities));
		
		//9: Announce self again periodically in case anyone missed it.	
		periodicReannounce(capabilities);
		
		//10: Listen for Requests
		checkForRequests();		
		
		//11: Periodically look for updates on productStatusSubscibers
		receiveProductStatusUpdates();
		
		//12: connect to my PLC
		//TODO: Check this works
		if (!testingAgent) {
			while (!connectedToPLC) {
				connectToPLC();
			}
		}
		
		//13: check for new products that might need capabilities performing
		checkForNewProduct();				
		
		//LAST: Anything else
		additionalStartupCommands();
	
		
		//TESTING--------------------------------------------------------------------------------------------			
		//testingRoutines();	
	}
	
	//===========================================================================================================================================	
	//Add testing routines
	private void testingRoutines() {
		addBehaviour (new OneShotBehaviour(this) {			
			@Override
			public void action() {				
				runTestRoutine();					
			}
		});
	}
	
	//===========================================================================================================================================	

	//Lavindra's Code: Starts a client to connect to PLC
	protected void connectToPLC() {
		
		try {
		      client = new PLCClient(HARDCODED_ETHERNET_IP, DEFAULT_PORT, verbose);

		      while(true) {
		    	  // connect via ethernet cable to the 'JavaSMC' server listening on the PLC
		    	  if(!client.connectToPLC()) {
		          
		    		  System.out.println("Could not open a socket connection with the JavaSMC-server on the PLC");
		    		  Thread.sleep(5000);
		    	  } else {		 
		    		  connectedToPLC = true;
		    		  break;
		    	  }
		    }		    
		
		
		} catch(Exception e) {
			
			System.out.println("Could not open a socket connection with the JavaSMC-server on the PLC");
			throw new RuntimeException("Could not open a socket connection with the JavaSMC-server on the PLC");
	    }
		
	}
	
	
	//===========================================================================================================================================	

	//Has a new product arrived and needs dealing with?
	protected void checkForNewProduct() {
		
		addBehaviour (new TickerBehaviour(this, TICK_TIME_PRODUCT) {

			@Override
			protected void onTick() {
				
				if (!hardwareInUseLock.isLocked() && !barcodeInUseLock.isLocked()) {
				
					String barcode = parseReturnedBarcode(getCurrentBarcode());					
										
					if (!barcode.equalsIgnoreCase(lastBarcodeScanned)) {
						lastBarcodeScanned = barcode;
						System.out.println(getName() + " scanned barcode " + barcode + ". Checking toDoList.");
						performActionOnNewBarcode(barcode);
					} else {
						System.out.println(getName() + " scanned barcode " + barcode + ". Not a new barcode.");
					}
					
				}
				
				//Or: am I a loader and should just load the container?
				
				if (!hardwareInUseLock.isLocked() && ((ResourceAgent)this.myAgent).canResourceAgentFulfilCapability(LOAD_CONTAINER_CAPABILITY)) {
					
					//perform action on a barcode-less container because the barcode hasnt been set yet..
					performActionOnNewBarcode("");
					
				}
			
		}});
	}
	
	//PLCs return Barcodes as <BARCODE> SUCCESS or <BARCODE> FAILURE. Remove success/failure.
	protected String parseReturnedBarcode(String barcodeIn) {
		
		String barcode = barcodeIn;	
		barcode = barcode.replace(" SUCCESS", "");
		barcode = barcode.replace(" FAILURE", "");
		
		return barcode;
		
	}
	
	//Deal with the new product. Do we need to perform anything?
	protected void performActionOnNewBarcode(String barcode) {
		
		boolean somethingHasBeenDone = false;

		try{	
			
			referencedRecipesLock.lock();
			toDoListLock.lock();
			barcodeInUseLock.lock();			
			
			//Do we have a job for this barcode?				
			//1: Search all the available recipes for their Barcodes and compare to toDo list
			if (referencedRecipes.size() == 0) {
				
				//PassThrough: no recipes at all!
				//nothing to do with this barcode. Release it.
				System.out.println(getName() + " releasing barcode " + barcode + ". I have no requirements to perform here.");
				releaseProduct();
				
			} else {			
			
				for (Map.Entry<String, Recipe> entry : referencedRecipes.entrySet()) {							
					
					System.out.println(getName() + "Searching toDo list " + (referencedRecipes.size()) + " | ");
					System.out.println("Hash Key: " + entry.getKey() + " Item Barcode: " + entry.getValue().getIdentifier());
					
					if (entry.getValue().getIdentifier().equalsIgnoreCase(barcode)) {
						//this is the matching recipe for the barcode
						//Do we have an entry in the toDo list for this barcode?
						String recipeUID = entry.getValue().getUID();
						
						if (toDoList.containsKey(recipeUID)) {
							//toDolist contains an entry
							
							System.out.print("Barcode found in ToDo list | ");
							
							//Are there any requirments listed under it?
							if (!toDoList.get(recipeUID).isEmpty()) {								
								
								System.out.print("Requirements Exist | ");
								
								ArrayList<Requirement> requirements = toDoList.get(recipeUID);
								
								//Argh to much nesting
								
								//For each requirement stored, have the preconditions been satisfied?
								//Iterator<Requirement> iterRequirements = requirements.iterator();
								Boolean anyRequirementsPossible = false;
								
								for (int i = 0; i < requirements.size(); i++) {
									
									//Requirement requirement = iterRequirements.next();
									Requirement requirement = requirements.get(i);
									//Have preconditions been satisfied?								
									ArrayList<Integer> preconditions = requirement.getRequirementsBefore();
									HashMap<Integer, Requirement> recipeRequirements = referencedRecipes.get(requirement.getRecipeUID()).getRequirements();
									
									//For every pre-requirement
									//more nesting ;_;
									Boolean preReqsMet = true;
									Iterator<Integer> iterPreconditions = preconditions.iterator();
									while (iterPreconditions.hasNext()) {
										
										Integer preconditionIdentifier = iterPreconditions.next();
										Requirement preRequirement = recipeRequirements.get(preconditionIdentifier);
										
										System.out.println("Requirement: " + requirement.getRequiredCapability() + " has a pre-requirement: " + preRequirement.getRequiredCapability() + " : " + preRequirement.getStatus());
										
										// ;_;
										if (preRequirement.getStatus().equalsIgnoreCase(STATUS_COMPLETED)) {
											//Pre-Req met. Do nothing.
										} else {
											//Pre-Req not met. Cannot do this requirement.
											preReqsMet = false;
										}
										
									}
									
									if (preReqsMet) {
										
										System.out.println("Preconditions met. Performing requirement.");
										
										anyRequirementsPossible = true;
										//Remove it from the toDo list:											
										Requirement requirementsToPerform = toDoList.get(recipeUID).remove(0);
										System.out.println(getName() + " conditions met for " + requirementsToPerform.getRequiredCapability());			
										somethingHasBeenDone = true;
										performRequirement(requirementsToPerform);
									} 									
								}	
								
								if (anyRequirementsPossible == false) {
									System.out.println("Preconditions not met. Cannot perform requirement.");
									//No requirements are possible currently.
									System.out.println(getName() + " releasing barcode " + barcode + ". No requirements currently possible.");		
									somethingHasBeenDone = true;
									releaseProduct();
									
								}
								
							} else {								
								//toDoList for that recipe is empty. Release it.
								System.out.println(getName() + " releasing barcode " + barcode + ". Recipe is empty.");
								somethingHasBeenDone = true;
								releaseProduct();
								
							}	
							
						} else {
							//nothing to do with this barcode. Release it.
							System.out.println(getName() + " releasing barcode " + barcode + ". I have no requirements to perform here.");
							System.out.println("ToDoList Size: " + toDoList.size());
							System.out.println("ReferencedRecipes Size: " + referencedRecipes.size());
							somethingHasBeenDone = true;
							releaseProduct();
						}
						
					} 				
				}
			}				
			
		} catch (ConcurrentModificationException e) {
			
			e.printStackTrace();
			e.getMessage();	
			
		} finally {
			
			if (!somethingHasBeenDone) {
				releaseProduct();
			}
			
			if (referencedRecipesLock.isHeldByCurrentThread()) {
				referencedRecipesLock.unlock();	
			}
			if (toDoListLock.isHeldByCurrentThread()) {
				toDoListLock.unlock();	
			}
			if (barcodeInUseLock.isHeldByCurrentThread()) {
				barcodeInUseLock.unlock();
			}
		}
	}
	
	//===========================================================================================================================================	

	//Releases a product that has has its barcode scanned. i.e. This resource does nothing to this barcode.
	protected void releaseProduct() {		
		
		Behaviour releaseProductBehaviour = new OneShotBehaviour(this) {

			@Override
			public void action() {
				
				try {
				
				//Lock the hardware
				hardwareInUseLock.lock();					
				
				//TODO: Check Releasing Hardware Works
				if (!testingAgent) {
					sendMessagesToPLCReleaseContainer();	
				}
				
				//System.out.println(getName() + " releasing container.");
				
				} finally {				
					
					if (hardwareInUseLock.isHeldByCurrentThread()) {
						hardwareInUseLock.unlock();
					}
					//System.out.println(getName() + ": Hardware unlocked after non-used barcode.");
					
				}			
			}			
		};
		
		addBehaviour(tbf.wrap(releaseProductBehaviour));
		
	}
	//===========================================================================================================================================	

	//Lavindra's Code: This bit of code is predicated on an agent having ONE capability. No more;
	protected void sendMessagesToPLCReleaseContainer() {
		
		if (this.canResourceAgentFulfilCapability(LOAD_CONTAINER_CAPABILITY)) {
			
			//This should never have to release a container!
			
		} else if (this.canResourceAgentFulfilCapability(FILL_YELLOW_CAPABILITY) 
				|| this.canResourceAgentFulfilCapability(FILL_BLUE_CAPABILITY)
				|| this.canResourceAgentFulfilCapability(FILL_RED_CAPABILITY)) {
			
			FillStationPassThroughBehaviour fillStationPassThroughBehaviour = new FillStationPassThroughBehaviour(client);
			fillStationPassThroughBehaviour.sendMessagesToPLC();
			
		} else if (this.canResourceAgentFulfilCapability(TEST_CONTAINER_CAPABILITY)) {
			
			MeasureContainerPassThroughBehaviour measureContainerPassThroughBehaviour = new MeasureContainerPassThroughBehaviour(client);
			measureContainerPassThroughBehaviour.sendMessagesToPLC();			
		
		} else if (this.canResourceAgentFulfilCapability(LID_CONTAINER_CAPABILITY)) {
			
			LidContainerPassThroughBehaviour lidContainerPassThroughBehaviour = new LidContainerPassThroughBehaviour(client);
			lidContainerPassThroughBehaviour.sendMessagesToPLC();
		
		} else if (this.canResourceAgentFulfilCapability(DISPATCH_CONTAINER_CAPABILITY)) {
			
			DispatchContainerPassThroughBehaviour dispatchContainerPassThroughBehaviour = new DispatchContainerPassThroughBehaviour(client);
			dispatchContainerPassThroughBehaviour.sendMessagesToPLC();
		}		
		
	}
	
	
	//===========================================================================================================================================	

	//Performs a requirement on a product
	protected void performRequirement(Requirement requirement) {
		
		
		Behaviour performTaskBehaviour = new OneShotBehaviour(this) {

			@Override
			public void action() {			
				System.out.println(getName() + " performing requirement " + requirement.getRequiredCapability() + " for " + requirement.getRecipeUID());
				
				try {	
					
					while (hardwareInUseLock.isLocked()) {
						
					}
					
					//Lock the hardware
					hardwareInUseLock.lock();					
					//System.out.println(getName() + ": Hardware locked!");
					
					//Don't forget arguements!
					boolean succeeded = true;
					String assignedBarcode = "NO_VALID_BARCODE";
					
					if (requirement.getRequiredCapability().equalsIgnoreCase(LOAD_CONTAINER_CAPABILITY)) {
						
						//TODO: Actually assign barcode
						//Get Barcode	
						if (!testingAgent) {
							
							assignedBarcode = parseReturnedBarcode(sendMessagesToPLCIdentifier(requirement));
							
						} else {						
						
							//Dummy Code ------
							for (int i = 0; i < fakeBarcodes.length; i++) {
								if (barcodesInUse[i] == false) {
									barcodesInUse[i] = true;
									assignedBarcode = fakeBarcodes[i];
									break;
								}
							}
							
							Thread.sleep(5000);
							
							//-----						
						}
						
					} else {
						
						if (!testingAgent) {
					
							//TODO: Actually perform requirement.
							succeeded = sendMessagesToPLCDoJob(requirement, client);
							System.out.println("Succeeded = " + succeeded);
						
						} else {						
							
							//Dummy Code ----
							succeeded = true;
							Thread.sleep(5000);						
							//----								
							
						}
					
					}
					
					//Horrible special case code
					//Announce this container's barcode					
					if (requirement.getRequiredCapability().equalsIgnoreCase(LOAD_CONTAINER_CAPABILITY)) {
					
						generalPublisher.sendKeyValueMessage(
								requirement.getRecipeUID(),
								BARCODE_IDENTIFIER, 
								assignedBarcode
								);
						
					} 
					
					if (!succeeded) {
						
						//Announce that this requirement has failed				
						generalPublisher.sendKeyValueMessage(
								requirement.getRecipeUID(),
								requirement.getRequirementUID().toString(), 
								STATUS_FAILED
								);
						
						System.out.println(getName() + " publishing that " + requirement.getRequiredCapability() + " is " + STATUS_FAILED);
						
					} else {
					
						//Announce that this requirement is complete				
						generalPublisher.sendKeyValueMessage(
								requirement.getRecipeUID(),
								requirement.getRequirementUID().toString(), 
								STATUS_COMPLETED
								);
						
						System.out.println(getName() + " publishing that " + requirement.getRequiredCapability() + " is " + STATUS_COMPLETED);
					}

				} catch (Exception e) {
					
					System.out.println(getName() + " encountered an exception: ");
					e.printStackTrace();
				
				} finally {
					
					if (hardwareInUseLock.isHeldByCurrentThread()) {
						hardwareInUseLock.unlock();
					}
						
				}
			}			
		};
		
		addBehaviour(tbf.wrap(performTaskBehaviour));
		
		
	}

	//===========================================================================================================================================	

	//Send messages to PLC.
	//TRUE = success
	//FALSE = failure

	protected boolean sendMessagesToPLCDoJob(Requirement requirement, PLCClient client) {
		
		String requirementType = requirement.getRequiredCapability();
		
		switch(requirementType) {
		
			//-------------------------------------------------------------------------
		
			case LOAD_CONTAINER_CAPABILITY:
				
				System.out.println(getName() + " attempting to load container capability incorrectly in switch statement.");
				
			return false;
			
			//-------------------------------------------------------------------------
			
			case FILL_RED_CAPABILITY:
				
				FillStationBehaviour fillRedBehaviour = new FillStationBehaviour(requirement, client);
			
			return fillRedBehaviour.sendMessagesToPLC();		
			
			//-------------------------------------------------------------------------
			
			case FILL_YELLOW_CAPABILITY:
				
				FillStationBehaviour fillYellowBehaviour = new FillStationBehaviour(requirement, client);
				
			return fillYellowBehaviour.sendMessagesToPLC();
			
			//-------------------------------------------------------------------------
			
			case FILL_BLUE_CAPABILITY:
				
				FillStationBehaviour fillBlueBehaviour = new FillStationBehaviour(requirement, client);
				
			return fillBlueBehaviour.sendMessagesToPLC();
			
			//-------------------------------------------------------------------------
			
			case TEST_CONTAINER_CAPABILITY:
				
				MeasureContainerBehaviour measureContainerBehaviour = new MeasureContainerBehaviour(requirement, client);
				
			return measureContainerBehaviour.sendMessagesToPLC();
			
			//-------------------------------------------------------------------------
			
			case LID_CONTAINER_CAPABILITY:
				
				LidContainerBehaviour lidContainerBehaviour = new LidContainerBehaviour(requirement, client);
				
			return lidContainerBehaviour.sendMessagesToPLC();
			
			//-------------------------------------------------------------------------
			
			case DISPATCH_CONTAINER_CAPABILITY:
			
				DispatchContainerBehaviour dispatchContainerBehaviour = new DispatchContainerBehaviour(requirement, client);
			
			return dispatchContainerBehaviour.sendMessagesToPLC();
			
			default:
				System.out.println("Unknown requirement");
			return false;
		
		}
		
	}
		
	//===========================================================================================================================================	

	protected String sendMessagesToPLCIdentifier(Requirement requirement) {
		
		String requiredCapability = requirement.getRequiredCapability();
		
		switch (requiredCapability) {
		
			case LOAD_CONTAINER_CAPABILITY:
				
				LoadContainerBehaviour loadContainerBehaviour = new LoadContainerBehaviour(requirement, client);
				return loadContainerBehaviour.sendMessagesToPLC();				
			
			default:
				
				System.out.println("Unknown identifier requirement");
				
			return "INVALID_BARCODE"; 
		
		}
		
			
		
	}
	
	//===========================================================================================================================================	
	
	//Simulates the scanning of barcodes
	//TODO: Barcode code
	protected String getCurrentBarcode() {
		
		if (testingAgent) {
			Random random = new Random();
			int randomNum = random.nextInt(5);
			
			//4 times out of 5 keep same barcode.
			//1 time get new barcode from list of possible barcodes
	
			if (randomNum > 0) {
				//Do nothing
				return lastBarcodeScanned;
			} else {
				int randomBarcodeNumber = random.nextInt(fakeBarcodes.length);			
				return fakeBarcodes[randomBarcodeNumber];
			}
		} else {
		
		//Actual Barcode Reading Code
		
			try {
				
				System.out.println("[getCurrentBarcode] Attempting to read barcode");
				
				Message req = new Message(Message.READ, Message.STRING, Message.LOCAL, "LastBarcode");
		        String reply = client.send(req.toString());
				//System.out.println("[getCurrentBarcode] Sent message/received message");
				
		        Message replyMsg = new Message(reply);
		        interpret(replyMsg);
				//System.out.println("[getCurrentBarcode] Interpretted message");
	
		        String barCode = replyMsg.getValue();
				System.out.println("[getCurrentBarcode] Saved Barcode: " + barCode);
	
		        
		        return parseReturnedBarcode(barCode);
		        
			} catch (Exception e) {
				
				e.printStackTrace();
				System.out.println(getName() + " could not read barcode");
				
				return "FAILED_TO_READ_BARCODE";
			}
		}
        
	}
	
	//===========================================================================================================================================	
	
	//Interprets a message
	protected void interpret(Message msgReply) {

	    if(msgReply.getResult() == Message.ERROR) {
	      System.out.println("[Interpret] Operation could not be successfully performed on the PLC");
	    }
	    else if(msgReply.getResult() == Message.SUCCESS) {
	      System.out.println("[Interpret] Operation was successful on the PLC, with value: " + msgReply.getValue());
	    }
	  }  
	
	//===========================================================================================================================================	

	//Periodically check for REQUESTS from the ACLMessage queue
	private void checkForRequests() {
		//Add behaviour for determining if we've received a capability request
		addBehaviour (new TickerBehaviour(this, TICK_TIME_LONG) {

			@Override
			public void onTick() {
				
				//System.out.println(getName() + " is checking for requests");				
				
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
				ACLMessage request = aclMessages.peek();
				
				if (request != null) {
					//System.out.println("Attempting to match message: " + request.getConversationId());
				}
				
				try {
					if (request != null && mt.match(request)) {
						
						System.out.println(getName() + " found a request");	
						request = aclMessages.poll();
						
						referencedRecipesLock.lock();
						toDoListLock.lock();
						
						/*System.out.println("Dealing with Message!");
						System.out.println("Performative: " + request.getPerformative());
						System.out.println("Conversation ID: " + request.getConversationId());
						System.out.println("Content: " + request.getContent());*/
						
						RequestAndRecipe requestAndRecipe = (RequestAndRecipe) request.getContentObject();
						
						//Can I do this?
						if (((ResourceAgent)myAgent).canResourceAgentFulfilCapability(requestAndRecipe.requirement.getRequiredCapability())) {
							
							System.out.println("I am " + myAgent.getName() + " and I can do " + requestAndRecipe.requirement.getRequiredCapability());
							
							//Get existing toDo list for this recipe
							ArrayList<Requirement> toDoForRecipe = toDoList.get(requestAndRecipe.requirement.getRecipeUID());
							if (toDoForRecipe != null) {
								toDoForRecipe.add(requestAndRecipe.requirement);
							} else {
								toDoForRecipe = new ArrayList<Requirement>();
								toDoForRecipe.add(requestAndRecipe.requirement);
							}
							
							//HORRIBLE SPECIAL CASE CODE GOES HERE
							//If the request we just got was a load_container...
							if (requestAndRecipe.requirement.getRequiredCapability().equalsIgnoreCase(LOAD_CONTAINER_CAPABILITY)) {
																
								//Don't add to toDo list.								
								
							} else {
								
								//Add request to toDoList
								toDoList.put(requestAndRecipe.requirement.getRecipeUID(), toDoForRecipe);
							}
							
							
							//Add whole recipe to references recipes
							referencedRecipes.put(requestAndRecipe.recipe.getUID(), requestAndRecipe.recipe);
							
							//Compose Request
							ACLMessage acknowledge = new ACLMessage(ACLMessage.AGREE);							
							acknowledge.setConversationId(REQUEST_ACL_CONVERSATION_TYPE);	
							acknowledge.setSender(getAID());
							
							//Send message
							//System.out.println(getName() + " sending Reply");
							TestACLMessengerChannel channel = channelToPoCAgent;
							channel.sendACLMessage(acknowledge);
							
							//Subscribe/Publish to that recipe's topic
							generalPublisher.createTopic(requestAndRecipe.recipe.getUID());
							//productStatusPublishers.put(requestAndRecipe.recipe.getUID(), new KeyValuePairPublisher(DOMAIN_ID, requestAndRecipe.recipe.getUID(), myAgent.getName()));							
							generalSubscriber.createTopic(requestAndRecipe.recipe.getUID());
							//productStatusSubscribers.put(requestAndRecipe.recipe.getUID(), new KeyValuePairSubscriber(DOMAIN_ID, requestAndRecipe.recipe.getUID(), myAgent.getName()));
							
							Behaviour startSubscriber = new OneShotBehaviour(this.myAgent) {	
								@Override
								public void action() {
									generalSubscriber.startReading();
								}
							};
				
							addBehaviour(tbf.wrap(startSubscriber));							
							
							//publish that I'm now taking that capability
							generalPublisher.sendKeyValueMessage(requestAndRecipe.recipe.getUID(), requestAndRecipe.requirement.getRequirementUID().toString(), STATUS_WAITING);
							
							//Was it a container load?
							if (requestAndRecipe.requirement.getRequiredCapability().equalsIgnoreCase(LOAD_CONTAINER_CAPABILITY)) {
								
								//Do it immediatly.
								performRequirement(requestAndRecipe.requirement);								
							}					
						
						} else {
							
							//TODO: Reject capability request
						}					
						
					} else {

						block(1000);
					}
					
				} catch (UnreadableException e) {					
					System.out.println("Could not deserialise ACL message");
					e.printStackTrace();
				} finally {					
					
					if (referencedRecipesLock.isHeldByCurrentThread()) {
						referencedRecipesLock.unlock();
					}
					
					if (toDoListLock.isHeldByCurrentThread()) {
						toDoListLock.unlock();
					}		
					
				}
				
			}
			
		});
	}	

	//===========================================================================================================================================	

	//Reannounce this agent's behaviours so other agents can spot it.
	//NB: This could be replaced with RTI's QoS settings
	private void periodicReannounce(ArrayList<String> capabilities) {
		Behaviour reannounceBehaviour = new TickerBehaviour(this, TICK_TIME_REANNOUNCE) {
			
			@Override
			protected void onTick() {				
				((ResourceAgent)myAgent).generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_INFO_STRING, createNameAndCapabilitiesString(getName(), capabilities));
				
			}
		};	
		
		addBehaviour(reannounceBehaviour);
	}
	
	//===========================================================================================================================================	

	//Gets this agent's args, which are this agent's capabilities
	//First arg is IP address
	private ArrayList<String> getAgentCapabilitiesFromArgs() {
		Object[] args = getArguments();
		ArrayList<String> capabilities = new ArrayList<String>();
		int argsLength = 0;		
		
		if (args != null && args.length > 0) {			
			
			argsLength = args.length;
			
			for (int i = 0; i < argsLength; i++) {	
				
				if (((String)args[i]).equalsIgnoreCase(TESTING_CAPABILITY)) {
					
					this.testingAgent = true;
					
				} else {
				
					capabilities.add((String)args[i]);
					System.out.println(getName() + " has capability " + (String)args[i]);
				
				}
			}	
		}
		
		return capabilities;
	}
	
	//===========================================================================================================================================	

	//Starts the general publisher in its own thread
	private void startGeneralSubscriber() {
		Behaviour startSubscriber = new OneShotBehaviour(this) {	
			@Override
			public void action() {
				generalSubscriber.startReading();
			}
		};
				
		addBehaviour(tbf.wrap(startSubscriber));
	}
	
	//===========================================================================================================================================	

	//Deals with a KVP every tick
	private void processKeyValuePairs() {
		addBehaviour(new TickerBehaviour(this, TICK_TIME_SHORT) {
			
			@Override
			public void onTick() {
				((ResourceAgent)myAgent).processGeneralKeyValuePair();
				
			}
		});
	}
	
	//===========================================================================================================================================	

	//Check generalSubscriber for new KVPs every tick
	private void receiveGeneralKeyValuePairs() {
		
		addBehaviour(new TickerBehaviour(this, TICK_TIME_LONG) {		
			
			@Override
			public void onTick() {		
				
				if (generalSubscriber != null) {
					
					KeyValueSimple kvs = generalSubscriber.getReceivedKeyValuePair(GENERAL_PUBLISHER_TOPIC);
					if (kvs != null) {
						keyValuePairs.add(kvs);
					}
					
					//System.out.println("KVP now has " + keyValuePairs.size() + " entries");
					
				} else {
					System.out.println("+++ ERROR " + getName() + " general subscriber not started! (ReceiveGeneralKVPairs)");
				}
			}					
		});
	}
	
	//===========================================================================================================================================	

	//Periodically look for updates on that publisher
	private void receiveProductStatusUpdates() {
	
		addBehaviour(new TickerBehaviour(this, TICK_TIME_LONG) {	
			
			@Override
			public void onTick() {					
				
				try {
					
					referencedRecipesLock.lock();
					
					ArrayList<String> allTopics = generalSubscriber.getAllRecipeTopics();					
				
					//For every product status subscriber
					for (int topicCount = 0; topicCount < allTopics.size(); topicCount++) {			
	
						//if the subscriber is working and we have stored the referenced recipe
						if (allTopics.get(topicCount) != null && referencedRecipes.get(allTopics.get(topicCount)) != null) {					
							
							//Get all updates
							KeyValueSimple update = generalSubscriber.getReceivedKeyValuePair(allTopics.get(topicCount));
							
							if (update != null) {					
								
								//Is it a Barcode Update?							
								if (update.getKey().equalsIgnoreCase(BARCODE_IDENTIFIER)) {
									
									String recipeUID = allTopics.get(topicCount);								
									String newBarcode = update.getValue();		
									
									
									Recipe updatedRecipe = referencedRecipes.get(recipeUID);
									updatedRecipe.setIdentifier(newBarcode);
									referencedRecipes.put(recipeUID, updatedRecipe);
									
									System.out.println(getName() + " updating barcode in toDoList");
									
								} else {
									//Its a requirement update
									String recipeUID = allTopics.get(topicCount);
									//System.out.println(getName() + " got a status update for recipe " + recipeUID);
									String requestUID = update.getKey();
									String newStatus = update.getValue();		
									
									
									//Create updated requirement
									Recipe recipeForUpdate = referencedRecipes.get(recipeUID.trim());
									HashMap<Integer, Requirement> requirementsForUpdate = recipeForUpdate.getRequirements();
									Requirement updatedRequirement = requirementsForUpdate.get(Integer.parseInt(requestUID));									
									updatedRequirement.setStatus(newStatus);
									
									//Update recipe
									Recipe updatedRecipe = referencedRecipes.get(recipeUID);
									updatedRecipe.getRequirements().put(updatedRequirement.getRequirementUID(), updatedRequirement);							
									referencedRecipes.put(recipeUID, updatedRecipe);
								}							
							}
							
						} else {
							System.out.println("+++ ERROR " + getName() + " No recipe for that topic! (ReceiveProductStatusUpdates)");	
							/*System.out.println("All Topics: " + allTopics.toString());
							System.out.println("All Topics size: " + allTopics.size());
							
							System.out.println("Topic to investigate = " + topicCount);
							System.out.println("Referenced Recipes: " + referencedRecipes.toString());
							System.out.println("Referenced Recipes Count: " + referencedRecipes.size());*/
							
						}
					}
				} catch (NullPointerException e) {
					System.out.println(getName() + " has a problem");
					e.printStackTrace();
					
				} finally {
					
					if (referencedRecipesLock.isHeldByCurrentThread()) {
						referencedRecipesLock.unlock();	
					}
					
				}	
				
			}					
		});
	}
	
	//===========================================================================================================================================	

	//Hiding place for inherited additional startup
	protected void additionalStartupCommands() {
		
		//For base agent, do nothing
		
	}

	
	
	//===========================================================================================================================================	
	
	//Pops the first key value pair from the kvp queue and processes it.
	protected void processGeneralKeyValuePair() {
		
		try {			
			
			KeyValueSimple kvs = keyValuePairs.remove();		
			//System.out.println(getName() + "received KVP " + kvs.getKey());
			
			if (kvs.getKey().equalsIgnoreCase(ANNOUNCE_NEW_STRING)) {
				
				//System.out.println(getName() + "Found something new! " + kvp.sourceAgent);
				this.processAnnounceString(kvs);
				
			} else if (kvs.getKey().equalsIgnoreCase(ANNOUNCE_INFO_STRING)) {
				
				//System.out.println(getName() + "Found something info!" + kvp.sourceAgent);
				this.processAnnounceString(kvs);			
			
			} else if (kvs.getKey().equalsIgnoreCase(ANNOUNCE_RECIPE_FINISHED)) {	
				
				String recipeUID = kvs.getValue();
				//System.out.println(getName() + " removing references to " + recipeUID);
				
				try {							
				
					//Remove recipe from all stores
					
					System.out.println("Recipe being removed");
					
					//Remove publisher				
					generalPublisher.removeTopic(recipeUID);
					
					//stop subscriber
					generalSubscriber.removeTopic(recipeUID);					
					
					//remove entry in toDoList
					toDoList.remove(recipeUID);
					
					//TODO: Barcode code
					//If I load containers, free barcode.
					referencedRecipesLock.lock();
					
					if (testingAgent) {
						
						//Dummy Code -----
						//This code should not require an equivilent in full produciton code.
						Recipe recipe = referencedRecipes.get(recipeUID);
						
						if (recipe != null) {
							String barcode = recipe.getIdentifier();
							for (int i = 0; i < fakeBarcodes.length; i++) {
								if (fakeBarcodes[i].equalsIgnoreCase(barcode)) {
									barcodesInUse[i] = false;
								}
							}
						}
						//---									
					}
					//remove entry in referenced recipes					
					referencedRecipes.remove(recipeUID);					
					
				} catch (NullPointerException e) {
					
					System.out.println(getName() + " has encountered a problem with unregistering recipe");
					e.printStackTrace();
				}
								
			} else {
				
				System.out.println("+++ ERROR " + getName() + ": Unknown message type " + kvs.getKey());
			}
		
		} catch (NoSuchElementException e) {
			
			//Do Nothing. The list is empty.
		} finally {
			
			if (referencedRecipesLock.isHeldByCurrentThread()) {
				referencedRecipesLock.unlock();
			 }	
			
		}
		
	}
	
	//===========================================================================================================================================
		
	//Parse announce Strings
	protected void processAnnounceString(KeyValueSimple kvs) {
		
		 /*System.out.println("Agent " +  getName() + " processing Data!" + "\n" +
			"--- Key: " + kvs.getKey() + "\n" +
			"--- Value: " + kvs.getValue());*/
		
		//Parse into agent name and capabilities
		String[] components = kvs.getValue().split("\\|");		
		String agentName = components[0];
		ArrayList<String> agentCapabilities = new ArrayList<String>();
		
		for (int i = 1; i < components.length; i++) {
			
			if (components[i].equalsIgnoreCase("|")) {
				//ignore
			} else {
				agentCapabilities.add(components[i]);
				
				//if an agent advertises a capability as 'Point_Of_Contact', this is the Point of Contact Agent.
				if (components[i].equalsIgnoreCase(POINT_OF_CONTACT_CAPABILITY) && !HAS_POCA_BEEN_FOUND) {
					System.out.println("Found POCA");
					//this agent is the PoCA. Handle
					createPoCACommunicationChannel(agentName);
				}
			}
		}
		
		//Add to existing hashmap
		mergeCapabilities(agentName, agentCapabilities);		
	}	
	
	//===========================================================================================================================================	
	
	//Handles the discovery of the PoCA and creates the request/reply channels necessary
	protected void createPoCACommunicationChannel(String PoCAName) {
		
		//Create Messager to PoCA
		channelToPoCAgent = new TestACLMessengerChannel(DOMAIN_ID, getName(), PoCAName+"PoC");
		
		//Create Receiver from PoCA
		channelFromPoCAgent = new TestACLReceiverChannel(DOMAIN_ID, PoCAName+"PoC", getName());
		
		//Start communication channel receiver
		addBehaviour(tbf.wrap(new OneShotBehaviour(this) {

			@Override
			public void action() {
				channelFromPoCAgent.startReading();					
			}				
		}));
		
		//Periodically add messages from PoCA to JADE message queue.
		addBehaviour(tbf.wrap(new TickerBehaviour(this, TICK_TIME_LONG) {

			@Override
			protected void onTick() {
				
				try {
					//System.out.println("Checking messages from PoCA on channel " + channelFromPoCAgent.getTopicName());
					ACLMessage message = channelFromPoCAgent.getMessage();					
					
					if (message != null) {
						
						System.out.println("Posting Message");
						aclMessages.add(message);						
					} else {
						//System.out.println("No message received");
					}
				} catch (NullPointerException e) {
					//Do Nothing
				}		
			}			
		}));		
		
		this.HAS_POCA_BEEN_FOUND = true;
		
	}
	
	//===========================================================================================================================================	
	
	//Inserts capabilities into agent representation of capabilities.
	//If agent already exists, replaces existing capaiblities with new capabilities
	protected void mergeCapabilities(String nameOfAgent, ArrayList<String> capabilitiesOfAgent) {
		
		if (knownResources.containsKey(nameOfAgent)) {
			//Resource already in database, so replace capabilities
			knownResources.remove(nameOfAgent);
			knownResources.put(nameOfAgent, capabilitiesOfAgent);
			
		} else {
			//Add resource to database
			knownResources.put(nameOfAgent, capabilitiesOfAgent);			
		}		
	}
	
	//===========================================================================================================================================
	
	//DEPRECATED: Agents now reannounce self periodically
	//if an agent announces itself as new, we should publish our database of capabilities to let it fill in it's details
	protected void publishKnownCapabilities() {
		
		
		//Note: The knownCapabilities map includes this agent's resource as well.
		for (Map.Entry<String, ArrayList<String>> entry : knownResources.entrySet()) {
			
			//For all capabilities, for each resource, publish.					
			generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_INFO_STRING, createNameAndCapabilitiesString(entry.getKey(), entry.getValue()));
				
		}		
	}
	
	//===========================================================================================================================================
	
	//Converts an agent's name and capabilities into a string
	protected String createNameAndCapabilitiesString(String inName, ArrayList<String> inCapabilities) {
		
		//NameAndCapabilities Format: "AgentGUID" + "|" + <capability> + "|" + <capability>
		String nameAndCapabilities = "";
		nameAndCapabilities = nameAndCapabilities + inName;
		nameAndCapabilities = nameAndCapabilities + "|";

		if (inCapabilities.size() == 0) {

			//Do nothing

		} else if (inCapabilities.size() == 1) {

			nameAndCapabilities = nameAndCapabilities + inCapabilities.get(0);

		} else if (inCapabilities.size() > 1) {

			for (int i = 0; i < inCapabilities.size()-1; i++ ) {

				nameAndCapabilities = nameAndCapabilities + inCapabilities.get(i) + "|";

			}

			nameAndCapabilities = nameAndCapabilities + inCapabilities.get(inCapabilities.size()-1);
		}	
		
		return nameAndCapabilities;
	}
	
	//===========================================================================================================================================

	//Returns if this agent can fulfil a given capability
	protected boolean canResourceAgentFulfilCapability(String capability) {
		
		ArrayList<String> thisAgentsCapabilities = knownResources.get(getName());
		
		Boolean iCanDoIt = false;
		
		for (int i = 0; i < thisAgentsCapabilities.size(); i++) {
			
			if (thisAgentsCapabilities.get(i).equalsIgnoreCase(capability)) {
				
				iCanDoIt = true;
			}			
		}
		
		return iCanDoIt;
		
	}
	
	
	//===========================================================================================================================================
	
	private void runTestRoutine() {
		
		System.out.println("TESTING - Entering test routine");
		printKnownCapabilitiesBehaviourTEST();
		
		
	}
	
	private void printKnownCapabilitiesBehaviourTEST() {
		
		System.out.println("TESTING - Adding behaviour to print known capabilities periodically.");
	
		Behaviour printResourcesTEST = new TickerBehaviour(this, 10000) {			
			@Override
			protected void onTick() {
				((ResourceAgent)myAgent).printAllKnownResourcesTEST();			
			}
		};	
		
		addBehaviour(tbf.wrap(printResourcesTEST));
	}
	
	private void printAllKnownResourcesTEST() {
		
		String outputString = "";
		
		outputString = outputString + "TESTING - Printing known capabilities" + "\n";
		outputString = outputString + "Running Agent Name: " + getName() + "\n";
		
		
		for (Map.Entry<String, ArrayList<String>> entry : knownResources.entrySet()) {			
			
			outputString = outputString + "Agent Name: " + entry.getKey() + "\n";
		
			Iterator<String> capablitiesIter = entry.getValue().iterator();
			while (capablitiesIter.hasNext()) {
				outputString = outputString + "--- " + capablitiesIter.next() + "\n";
			}			
		}
		
		System.out.println(outputString);
	}
			

}
