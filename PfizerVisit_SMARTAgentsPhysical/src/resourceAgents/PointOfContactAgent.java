package resourceAgents;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

import RTI.ACL.Base64Coder;
import RTI.keyValue.KeyValuePairPublisherModule;
import RTI.keyValue.KeyValuePairSubscriberModule;
import RTI.keyValue.KeyValueSimple;

/* The Point_of_contact Resource represents a physical element in the system with a PLC, but also one that dispatches instructions.   
 * 
 * Arguments should be:
 * Strings: Capabilities
 * 
 *  @author Jack Chaplin
 *  @foundingDate 20150317
 *  @version 0.1
 */

public class PointOfContactAgent extends ResourceAgent {	
	
	int orderCounter = 0;
	protected HashMap<String, RequestReplyMessageChannel> channelsToAgents = new HashMap<String, RequestReplyMessageChannel>();	
	
	//Property names
	protected final String TICK_TIME_POCA_RECIPE_PROP_NAME = "tickTimePOCARecipe";
	protected final String TICK_TIME_PROCESS_RECIPE_PROP_NAME = "tickTimePOCARecipeProcess";
	protected final String TICK_TIME_POCA_ACL_PROP_NAME = "tickTimeACLPOCA";
	
	private enum RecipeStatus {
		
		IN_PROGRESS, COMPLETED, FAILED_EMPTY_RECIPE, FAILED_UNABLE, 
		FAILED_BID_FAILURE, FAILED_RESOURCE_FAILURE, FAILED_SERIALIZE_ERROR		
		
	}
	
	final static String RECIPE_TOPIC_NAME = "recipe_topic";	
	ConcurrentLinkedQueue<KeyValueSimple> recipes = new ConcurrentLinkedQueue<KeyValueSimple>();
	
	@Override
	protected void additionalStartupCommands() {
		
		//Start a subscriber to receive recipes		
		generalSubscriber.createTopic(RECIPE_TOPIC_NAME);
		
		Behaviour startRecipeSubscriber = new OneShotBehaviour(this) {	
			@Override
			public void action() {
				generalSubscriber.startReading();
				System.out.println("Recipe Subscriber Started");
				
			}
		};
				
		addBehaviour(tbf.wrap(startRecipeSubscriber));
		
		//Take recipes periodically
		addBehaviour(new TickerBehaviour(this, Long.parseLong(properties.getProperty(TICK_TIME_POCA_RECIPE_PROP_NAME))) {		
			
			@Override
			public void onTick() {		
				
				if (generalSubscriber.containsTopic(RECIPE_TOPIC_NAME)) {
					
					KeyValueSimple kvs = generalSubscriber.getReceivedKeyValuePair(RECIPE_TOPIC_NAME);
					if (kvs != null) {
						recipes.add(kvs);
					}
				
					//System.out.println("KVP now has " + keyValuePairs.size() + " entries");
					
				} else {
					System.out.println("+++ ERROR " + getName() + " recipe subscriber not started");
				}
			}					
		});
		
		
		//Process recipes periodically
		addBehaviour(new TickerBehaviour(this, Long.parseLong(properties.getProperty(TICK_TIME_PROCESS_RECIPE_PROP_NAME))) {
			
			@Override
			public void onTick() {
				
				if (recipes.isEmpty()) {
					
					//do Nothing
					
				} else {						
					
					//Get kvp
					KeyValueSimple kvs = recipes.remove();
					//Deserialise ArrayList
					ArrayList<Requirement> requirementsForDispatch = null;
					
					try {
						//Deserialise message
						String serialisedString = kvs.getValue();
						byte [] messageData = Base64Coder.decode(serialisedString);
					    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(messageData));
					    requirementsForDispatch  = (ArrayList<Requirement>) ois.readObject();
					    ois.close();
					}  catch (IOException ioe) {
						System.out.println("Requirements could not be decoded. - JC PointOfContactAgent");
						
					} catch (ClassNotFoundException e) {
						System.out.println("Requirements could not be decoded. - JC PointOfContactAgent");
						
					}
					
					if (requirementsForDispatch != null) {
						
						System.out.println("+++Received Recipe!+++");
						
						for (int i = 0; i < requirementsForDispatch.size(); i++) {
							
							Requirement requirement = requirementsForDispatch.get(i);
							System.out.println("----------------------------------------------------------------------------");
							System.out.println("Requirement ID: " + requirement.getRequirementUIDAsInt());
							System.out.println("Requirement: " + requirement.getRequiredCapability());
							ArrayList<Integer> requirementsBefore = requirement.getRequirementsBefore();
							for (int j = 0; j < requirementsBefore.size(); j++) {
								System.out.println("Pre-Condition Requirement: " + requirementsBefore.get(j).toString());
							}
							for (Map.Entry<String, String> entry : requirement.getArguments().entrySet()) {	
								System.out.println("Argument: " + entry.getKey() + " = " + entry.getValue());
							}
							System.out.println("----------------------------------------------------------------------------");									
						}
						
						dispatchRecipeContractNet(requirementsForDispatch);
					}
				}					
			}			
		});
		
	}
	
	//===========================================================================================================================================
	//===========================================================================================================================================
		
	//Compares a recipe against known resources to determine if the recipe is completeable
	
	private Boolean checkRecipeAgainstCapabilities(Recipe recipe, HashMap<String, ArrayList<String>> knownResources) {
		
		Boolean canBeCompleted = true;
		
		// for all requirements
		for (Map.Entry<Integer, Requirement> entryRequirements : recipe.getRequirements().entrySet()) {
			
			//Get requirement				
			String requestedCapability = entryRequirements.getValue().getRequiredCapability();
			Boolean matchFound = false;
			
			//Is there a matching capability?
			//For every known agent
			for (Map.Entry<String, ArrayList<String>> entryResources : knownResources.entrySet()) {		
											
				//Check all capabilities					
				for (int j = 0; j < entryResources.getValue().size(); j++) {
					
					//if it matches
					if (entryResources.getValue().get(j).equalsIgnoreCase(requestedCapability)) {
						
						matchFound = true;
						//Don't check this agent further
						break;
					}							
				}		
				
				if (matchFound) {
					//dont check this requirement further
					break;
				}
			}	
			
			if (!matchFound) {
				canBeCompleted = false;
			}
		}
		
		return canBeCompleted;
		
	}
	
	//===========================================================================================================================================
	//===========================================================================================================================================
			
	//Returns potential fulfillers for a requirement.
	
	private Vector<String> checkRequirementAgainstAgents(Requirement requirement, HashMap<String, ArrayList<String>> knownResources) {
		
		String requestedCapability = requirement.getRequiredCapability();
		Vector<String> matchingAgents = new Vector<String>();
		
		for (Map.Entry<String, ArrayList<String>> entryResources : knownResources.entrySet()) {		
			
			//Check all capabilities					
			for (int j = 0; j < entryResources.getValue().size(); j++) {
				
				//if it matches
				if (entryResources.getValue().get(j).equalsIgnoreCase(requestedCapability)) {
					
					matchingAgents.add(entryResources.getValue().get(j));
					//Don't check this agent further
					break;
				}							
			}					
		}	
		
		return matchingAgents;
		
	}
	
	//===========================================================================================================================================
	//===========================================================================================================================================
	
	public void dispatchRecipeContractNet(ArrayList<Requirement> requirements) {
		
		Behaviour completeOrderBehaviourCNET = new Behaviour() {
			
			int FINAL_STEP = 6;			
			int step = 0;
			RecipeStatus recipeStatus = RecipeStatus.IN_PROGRESS; 
			
			//Recipe to implement
			Recipe recipe;
			
			//Message template to filter replies.
			MessageTemplate mt;
			
			//Number of agents bids requested from
			int bidsRequested = 0;
			
			//Holds bid objects
			HashMap<Integer, Vector<Bid>> bids = new HashMap<Integer, Vector<Bid>>();

			@Override
			public void action() {
				
				switch (step) {
				
				//===========================================================================================================================================
				//0: Do we have available capabilities to perform this?
				case 0:
					
					System.out.println("+++ Deal with Recipe: Step 0: Match to agent capabilities");
					
					//Convert requirements to recipe
					String recipeUID = getName() + ":" + orderCounter++;					
					recipe = Recipe.convertRequirements(requirements, recipeUID);
					
					if (recipe.getRequirements().size() <= 0) {
						
						//Recipe is empty, don't bother
						recipeStatus = RecipeStatus.FAILED_EMPTY_RECIPE;
						step = FINAL_STEP;
						break;
					} 
					
					//Can we meet all requirements?
					Boolean canBeCompleted = checkRecipeAgainstCapabilities(recipe, knownResources);
					
					if (canBeCompleted) {
						
						System.out.println("Recipe can be completed with available resources.");
						step++;
						
					} else {
						
						System.out.println("Cannot complete recipe with available resources.");
						recipeStatus = RecipeStatus.FAILED_UNABLE;
						step = FINAL_STEP;
						break;
						
					}
					
				break;
				
				//===========================================================================================================================================
				//1: Housekeeping and Recipe Topic Creation
				case 1:
					
					System.out.println("+++ Deal with Recipe: Step 1: Start product status publishers / subscribers");
					
					//Create and subscribe to topic
					generalPublisher.createTopic(recipe.getUID());					
					generalSubscriber.createTopic(recipe.getUID());
					
					Behaviour startSubscriber = new OneShotBehaviour(this.getAgent()) {	
						
						@Override
						public void action() {						
						
								generalSubscriber.startReading();							
						}
					};
							
					addBehaviour(tbf.wrap(startSubscriber));
					
					//Publish that barcode is unknown
					generalPublisher.sendKeyValueMessageNoSource(recipe.getUID(), BARCODE_IDENTIFIER, UNKNOWN_BARCODE_NAME);
					
					//Publish that all requirements are waiting for matching
					for (Map.Entry<Integer, Requirement> entry : recipe.getRequirements().entrySet()) {
						
						String requirementUID = entry.getKey().toString();
						//Note: Requirement status defaults to "Unclaimed"
						String requirementStatus = entry.getValue().getStatus();						
						
						generalPublisher.sendKeyValueMessageNoSource(recipe.getUID(), requirementUID, requirementStatus);
					}
					
					step++;					
					
				break;
				
				//===========================================================================================================================================
				//2: Send bid requests to matching agents
				case 2:
					
					System.out.println("+++ Deal with Recipe: Step 2: Send bid requests");					
					
					HashMap<Integer, Requirement> recipeRequirements = recipe.getRequirements();
					
					//Matches requirements to potential agents
					HashMap<Requirement, Vector<String>> requirementsAgainstAgents = new HashMap<Requirement, Vector<String>>();
					
										
					//For every requirement, find matching agents
					for (Map.Entry<Integer, Requirement> requirementEntry : recipeRequirements.entrySet()) {
						
						//Find list of Agents for each requirement	
						Vector<String> matchedAgents = checkRequirementAgainstAgents(requirementEntry.getValue(), knownResources);						
						requirementsAgainstAgents.put(requirementEntry.getValue(), matchedAgents);						
					}
					
					//Send bid requests to agents
					for (Map.Entry<Requirement, Vector<String>> requirementEntry : requirementsAgainstAgents.entrySet()) {
						
						for (int i = 0; i < requirementEntry.getValue().size(); i++) {
							
							//Compose Request
							Requirement requestedRequirement = requirementEntry.getKey();
							ACLMessage requestForBid = new ACLMessage(ACLMessage.REQUEST);
							
							try {
							
								RequestAndRecipe rar = new RequestAndRecipe();
								rar.recipe = recipe;
								rar.requirement = requestedRequirement;
								requestForBid.setContentObject(rar);
								requestForBid.setConversationId(REQUEST_ACL_CONVERSATION_TYPE);								
								
								//Send message
								RequestReplyMessageChannel channel = channelsToAgents.get(requirementEntry.getValue().get(i));
								channel.sendMessageToAgent(requestForBid);	
								bidsRequested++;
								
							} catch (IOException e) {
								
								step = FINAL_STEP;
								recipeStatus = RecipeStatus.FAILED_SERIALIZE_ERROR;
								break;
								
							}							
							
						}					
						
					}
					
					mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
					
					//Data structure to hold bids.
					
					if (step == 2) {
						step++;
					}
					
				break;
				
				//===========================================================================================================================================
				//3: Wait for bids
				case 3:
					
					if (bids.size() == bidsRequested) {
						
						//move on to determining winners for bids.
						step++;					
					
					} else {
					
						ACLMessage replyBid = aclMessages.peek();
						
						if (replyBid != null) {
							
							if (mt.match(replyBid)) {
							
								//Reply received							
								//Content is bid | agent identifier
								String bidAndIdentifier = replyBid.getContent();
								String[] components = bidAndIdentifier.split("\\|");
								int bidValue = Integer.valueOf(components[0]);
								int agentIdentifier = Integer.valueOf(components[1]);								
								
								//Conversation ID is requirement bidding for
								Integer requirementUID = Integer.valueOf(replyBid.getConversationId());
								//name of agent							
								
								//Create bid object
								Bid bid = new Bid(replyBid.getSender().getName(), requirementUID, agentIdentifier, bidValue);
								//Add to bids array
								bids.get(bid.getRequirementUID()).add(bid);					
								
							}						
						}
					}
					
				break;
				
				//===========================================================================================================================================
				//4: Determine bid winners
				case 4:
					
					//For every requirement
					for (Map.Entry<Integer, Vector<Bid>> bidEntry : bids.entrySet()) {
						
						Bid lowBid = null;
						
						//Find lowest bid
						Vector<Bid> requirementBids = bidEntry.getValue();
						
						for (int i = 0; i < requirementBids.size(); i++) {
							
							if (lowBid != null) {
								
								if (lowBid.getBidValue() > requirementBids.get(i).getBidValue()) {									
									lowBid = requirementBids.get(i);									
								}
								
							} else {
								
								lowBid = requirementBids.get(i);								
							}						
						}
						
						//Send confirm message to winner
						if (lowBid != null) {
							
							String targetAgent = lowBid.getAgentName();
							
							ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);	
							accept.setContent(String.valueOf(lowBid.getAgentsIdentifier()));
							accept.setConversationId(ACCEPT_ACL_CONVERSATION_TYPE);
							
							RequestReplyMessageChannel channel = channelsToAgents.get(targetAgent);
							channel.sendMessageToAgent(accept);
							
							
						} else {
							
							//something horribly wrong
							System.out.println("No viable bids for requirement " + bidEntry.getKey());
							recipeStatus = RecipeStatus.FAILED_BID_FAILURE;
							step = FINAL_STEP;
						}						
					}
					
					if (step != FINAL_STEP) {
						step++;
					}
					
					break;
					
				//===========================================================================================================================================
				//5: Wait for completion
				case 5:
					
					Boolean allDone = false;
					
					try {						
					
						referencedRecipesLock.lock();			
						
						allDone = true;
						
						for (Map.Entry<Integer, Requirement> entry : referencedRecipes.get(recipe.getUID()).getRequirements().entrySet()) { 
							
							Requirement checkedRequirement = entry.getValue();
							if (checkedRequirement.getStatus().equalsIgnoreCase(STATUS_COMPLETED)) {
								System.out.println("Product " + recipe.getUID() + " requirement: " + checkedRequirement.getRequiredCapability() + " complete!");
								//Do Nothing
								
							} else if (checkedRequirement.getStatus().equalsIgnoreCase(STATUS_FAILED)) {
								//Process failed so remove recipe.
								allDone = false;
								recipeStatus = RecipeStatus.FAILED_RESOURCE_FAILURE;
								step = FINAL_STEP;							
								
							} else {	
								//System.out.println("Checked Requirement: " + checkedRequirement.getRequiredCapability() + " incomplete");
								allDone = false;
							}								
						}
						
						
					
					} catch (Exception e) {
						
						System.out.println("Exception in Stage 5 of recipe dealing: ");
						e.printStackTrace();
						allDone = false;						
						
					} finally {
						
						if (referencedRecipesLock.isHeldByCurrentThread()) {
							referencedRecipesLock.unlock();	
						}
					}
					
					System.out.println("==========================================================================================");					
					
					if (allDone) {
						
						recipeStatus = RecipeStatus.COMPLETED;
						step = FINAL_STEP;	
					
					} else {
						
						block(1000);
					}
				
				break;
				
				//===========================================================================================================================================
				//6: Clean Up
				case 6:
					
					switch (recipeStatus) {
					
					case IN_PROGRESS:
						
						System.out.println("[Fundamental Logic Error] PoCAgent cleaning up while recipe in progress: dispatchRecipeContractNet. Should never be here.");
						step = 7;
						break;
						
					case COMPLETED:
						
						System.out.println("+++ Recipe: " + recipe.getUID() + " Step 6: Product Completed");
						generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_RECIPE_FINISHED, recipe.getUID());
						step = 7;
						break;
						
					case FAILED_BID_FAILURE:
						
						System.out.println("No bids received for a requirement: dispatchRecipeContractNet.");
						generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_RECIPE_FINISHED, recipe.getUID());
						step = 7;
						break;
						
					case FAILED_EMPTY_RECIPE:
						System.out.println("Recipe is empty: dispatchRecipeContractNet.");
						generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_RECIPE_FINISHED, recipe.getUID());
						step = 7;
						break;
						
					case FAILED_RESOURCE_FAILURE:
						System.out.println("Resource failed. Restart recipe if possible: dispatchRecipeContractNet.");
						generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_RECIPE_FINISHED, recipe.getUID());
						((PointOfContactAgent)getAgent()).dispatchRecipeContractNet(requirements);
						step = 7;
						break;
						
					case FAILED_UNABLE:
						System.out.println("Resources not available: dispatchRecipeContractNet.");
						generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_RECIPE_FINISHED, recipe.getUID());
						step = 7;
						break;		
						
					case FAILED_SERIALIZE_ERROR:
						System.out.println("Failed to serialize requirement. Attempting recipe restart: dispatchRecipeContractNet.");
						generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_RECIPE_FINISHED, recipe.getUID());
						((PointOfContactAgent)getAgent()).dispatchRecipeContractNet(requirements);
						step = 7;
						break;
						
					default:
						System.out.println("[Fundamental Logic Error] PoCAgent default entry in cleanup switch statement: dispatchRecipeContractNet. Should never be here.");
						step = 7;
						break;
					
					}					
					
				break;
				
				default:
					
					System.out.println("[Fundamental Logic Error] PoCAgent default entry in switch statement: dispatchRecipeContractNet. Should never be here.");
					
				break;
				
				}
				
			}		

			@Override
			public boolean done() {
				
				return (step == 7);
			}
			
		};	
		
	}
	
	
	
	//===========================================================================================================================================
	//===========================================================================================================================================
	
	public void receiveRecipeForDispatch(ArrayList<Requirement> requirements) {			 	
		
		//Behaviour to deal with orders. Long! TODO: Refactor!
		Behaviour completeOrderBehaviour = new Behaviour() {	
			
			//Add Recipe UID to all requirements
			String recipeUID = getName() + ":" + orderCounter++;
			ArrayList<Requirement> requirementsWithUIDs = new ArrayList<Requirement>();		
			//The recipe. The barcode is currently unknown. //TODO: How to resolve this?
			Recipe newRecipe;
			//Step through switch execution
			int step = 0;
			//Number of replies
			int repliesCount = 0;
			//Message template to filter replies.
			MessageTemplate mt;
			//Matching agents to their requirements.
			HashMap<String, Requirement> matchedRequirementsToAgents = new HashMap<String, Requirement>();			

			@Override
			public void action() {				
				
				switch (step) {
				
				//===========================================================================================================================================
				//0: Can we do this?
				case 0:
					
					System.out.println("+++ Deal with Recipe: Step 0: Match to agent capabilities");
					
					//Create Recipe from requirements
					for (int i = 0; i < requirements.size(); i++) {
						Requirement modifiedRequirement = requirements.get(i);
						modifiedRequirement.setRecipeUID(recipeUID);
						requirementsWithUIDs.add(modifiedRequirement);
					}
					
					newRecipe = new Recipe(recipeUID, null, requirementsWithUIDs);	
					Boolean canBeCompleted = true;
					
					// for all requirements
					for (Map.Entry<Integer, Requirement> entryRequirements : newRecipe.getRequirements().entrySet()) {
						
						//Get requirement				
						String requestedCapability = entryRequirements.getValue().getRequiredCapability();
						Boolean matchFound = false;
						
						//Is there a matching capability?
						for (Map.Entry<String, ArrayList<String>> entryResources : knownResources.entrySet()) {		
														
							//Check all capabilities					
							for (int j = 0; j < entryResources.getValue().size(); j++) {
								
								//if it matches
								if (entryResources.getValue().get(j).equalsIgnoreCase(requestedCapability)) {
									
									//assign agent to map of agents to be contacted
									matchedRequirementsToAgents.put(entryResources.getKey(), entryRequirements.getValue());
									matchFound = true;
									//Don't check this agent further
									break;
								}							
							}		
							
							if (matchFound) {
								//dont check this requirement further
								break;
							}
						}	
						
						if (!matchFound) {
							canBeCompleted = false;
						}
					}
					
					//If all is well, go further			
					if (newRecipe.getRequirements().size() <= 0) {
						//recipe is empty, so don't bother
						step = 6;
					}
					else if (canBeCompleted) {
						System.out.println("Matched agents: " + matchedRequirementsToAgents.size());
						step = 1;
					} else {
						step = 6;
						System.out.println("Cannot complete recipe with available resources");					
						
					}
					
				break;
				
				
				//===========================================================================================================================================
				//1: Check For Matching Agent Capabilities
				case 1:
					
					System.out.println("+++ Deal with Recipe: Step 1: Start product status publishers / subscribers");
					
					generalPublisher.createTopic(newRecipe.getUID());
					
					generalSubscriber.createTopic(newRecipe.getUID());
					
					Behaviour startSubscriber = new OneShotBehaviour(this.getAgent()) {	
						
						@Override
						public void action() {						
						
								generalSubscriber.startReading();							
						}
					};
							
					addBehaviour(tbf.wrap(startSubscriber));					
					
					//Publish all variables and statuses
					
					//Firstly, Barcode:					
					generalPublisher.sendKeyValueMessageNoSource(newRecipe.getUID(), BARCODE_IDENTIFIER, UNKNOWN_BARCODE_NAME);
					
					//Secondly, all capability request statusus
					for (Map.Entry<Integer, Requirement> entry : newRecipe.getRequirements().entrySet()) {
						
						String requirementUID = entry.getKey().toString();
						String requirementStatus = entry.getValue().getStatus();						
						
						generalPublisher.sendKeyValueMessageNoSource(newRecipe.getUID(), requirementUID, requirementStatus);
					}
					
					step = 2;
					
					
				break;
				
				//===========================================================================================================================================
				//2: Send requests to matching Agents
				case 2:
					
					System.out.println("+++ Deal with Recipe: Step 2: Send requests");
					System.out.println("Number of channels: " + channelsToAgents.size());
					
					//We already have a bidrectional communication channel with all agents.
					try {
						//For every agent
						for (Map.Entry<String, Requirement> entry : matchedRequirementsToAgents.entrySet()) {			
							
							
							String targetAgent = entry.getKey();
							//System.out.println("Sending request to " + entry.getKey());
							Requirement requestedRequirement = entry.getValue();
							
							//Compose Request
							ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
							
							RequestAndRecipe rar = new RequestAndRecipe();
							rar.recipe = newRecipe;
							rar.requirement = requestedRequirement;
							request.setContentObject(rar);
							request.setConversationId(REQUEST_ACL_CONVERSATION_TYPE);						
							
							//Send message
							RequestReplyMessageChannel channel = channelsToAgents.get(targetAgent);
							channel.sendMessageToAgent(request);				
							//System.out.println("message sent to " + targetAgent + " on topic " + channel.getChannelToAgentTopic());
						}
							
					} catch (IOException e) {						
						System.out.println("+++ERROR+++ : Request failed to serialise.");							
					}	
					
				mt = MessageTemplate.MatchConversationId(REQUEST_ACL_CONVERSATION_TYPE);
				
				//TODO: Error checking on serialise errors
				step = 3;
				
					
				break;				
				
				//===========================================================================================================================================
				//3: Wait for acks. TODO: Functionality for timeouts.
				case 3:
					
					//System.out.println("+++ Deal with Recipe: Step 3: Receive Acknowledgements");		
					
					MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.AGREE);
					ACLMessage reply = aclMessages.peek();
					
					if (reply != null) {
		
						if (mt.match(reply)) {
							//Reply received.
							//is it an accept?
							reply = aclMessages.poll();
							if (reply.getPerformative() == ACLMessage.AGREE) {
								//System.out.println("Received Reply from " + reply.getSender().getName());								
								repliesCount++;
								//System.out.println("Current replies: " + repliesCount + ". Expected replies: " + matchedRequirementsToAgents.size());
								//Set recipe status to Claimed
								
							} else {
								//TODO: Ask someone else.							
							}
						}
					}
					
					//Have we received all replies?
					if (repliesCount >= matchedRequirementsToAgents.size()) {
						System.out.println("Step 3: All agrees received");
						step = 4;
					}
					
				break;
				
				//4: Wait for completion
				//===========================================================================================================================================
				case 4:
					
					//System.out.println("+++ Deal with Recipe: Step 4: Monitor recipe status");

					Boolean allDone = false;
					
					try {						
					
						referencedRecipesLock.lock();			
						
						allDone = true;
						
						for (Map.Entry<Integer, Requirement> entry : referencedRecipes.get(newRecipe.getUID()).getRequirements().entrySet()) { 
							
							Requirement checkedRequirement = entry.getValue();
							if (checkedRequirement.getStatus().equalsIgnoreCase(STATUS_COMPLETED)) {
								System.out.println("Product " + newRecipe.getUID() + " requirement: " + checkedRequirement.getRequiredCapability() + " complete!");
								//Do Nothing
							} else if (checkedRequirement.getStatus().equalsIgnoreCase(STATUS_FAILED)) {
								//Process failed so remove recipe.
								allDone = false;
								step = 5;							
								
							} else {	
								//System.out.println("Checked Requirement: " + checkedRequirement.getRequiredCapability() + " incomplete");
								allDone = false;
							}								
						}
						
						
					
					} catch (Exception e) {
						
						System.out.println("Exception in Stage 4 of recipe dealing: ");
						e.printStackTrace();
						allDone = false;						
						
					} finally {
						
						if (referencedRecipesLock.isHeldByCurrentThread()) {
							referencedRecipesLock.unlock();	
						}
					}
					
					System.out.println("==========================================================================================");					
					
					if (allDone) {
						step = 5;	
					} else {
						block(500);
					}
					
				break;
				
				//5: Remove Recipe
				//This product is now completed.
				case 5:
					
					System.out.println("+++ Recipe: " + newRecipe.getUID() + " Step 5: Product Completed");
					
					generalPublisher.sendKeyValueMessage(GENERAL_PUBLISHER_TOPIC, ANNOUNCE_RECIPE_FINISHED, newRecipe.getUID());	
					//System.out.println("Finished recipe announce published");
					//TODO: Agents unsubscribe from finished recipes
					step = 6;
					
				break;
				
				default:
					
					System.out.println("[Fundamental Logic Error] PoCAgent default entry in switch statement: receiveRecipeForDispatch. Should never be here.");
					
				break;
				
				}				
			}	
			
			
			public boolean done() {
				return (step == 6);
			}
			
		};
		
		addBehaviour(completeOrderBehaviour);
		
	}
	
	//===========================================================================================================================================
	
	//PoCA has to do things a little differently. When a new agent is announced, it needs to create a bi-directional ACL RTI message channel.
	
	@Override
	protected void mergeCapabilities(String nameOfAgent, ArrayList<String> capabilitiesOfAgent) {
		
		if (knownResources.containsKey(nameOfAgent) && channelsToAgents.containsKey(nameOfAgent)) {
			
			//Resource already in database, so replace capabilities
			knownResources.remove(nameOfAgent);
			knownResources.put(nameOfAgent, capabilitiesOfAgent);
			
		} else if (!knownResources.containsKey(nameOfAgent) || !channelsToAgents.containsKey(nameOfAgent)) {
			
			//Add resource to database
			knownResources.put(nameOfAgent, capabilitiesOfAgent);
			
			//+++ Major Change. Create communication channel +++
			channelsToAgents.put(nameOfAgent, new RequestReplyMessageChannel(
													Integer.parseInt(properties.getProperty(DOMAIN_ID_PROP_NAME)),
													getName(),
													nameOfAgent)
													);
								
			//Start communication channel receiver
			addBehaviour(tbf.wrap(new OneShotBehaviour(this) {

				@Override
				public void action() {
					channelsToAgents.get(nameOfAgent).getSubscriber().startReading();					
				}				
			}));
			
			//Periodically fetch messages
			addBehaviour(new TickerBehaviour(this, Long.parseLong(properties.getProperty(TICK_TIME_POCA_ACL_PROP_NAME))) {

				@Override
				protected void onTick() {
					
					//For all communication channels in the hashap
					for (Map.Entry<String, RequestReplyMessageChannel> entry : channelsToAgents.entrySet()) {	
						
						ACLMessage aclm = entry.getValue().getMessageFromAgent();
						if (aclm != null) {							
							
							aclMessages.add(aclm);
							//System.out.println("Message posted");
						}
												
					}					
				}			
			});			
			
		}		
	}
}


//===========================================================================================================================================
//===========================================================================================================================================





