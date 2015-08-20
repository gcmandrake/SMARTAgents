package application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import RTI.ACL.Base64Coder;
import RTI.ACL.TestACL;
import RTI.keyValue.KeyValuePairPublisherModule;
import RTI.keyValue.KeyValuePairSubscriberModule;
import RTI.keyValue.KeyValueSimple;
import resourceAgents.Requirement;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBoxBuilder;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class RecipeCreatorController {
	
	final long CONTAINER_MAX_VOLUME = 4;
	final int LABEL_MAX_CHARACTERS = 11;
	final String POINT_OF_CONTACT_CAPABILITY = "Point_Of_Contact";
	final String LOAD_CONTAINER_CAPABILITY = "load_container"; 
	final String FILL_RED_CAPABILITY = "fill_red";
	final String FILL_YELLOW_CAPABILITY = "fill_yellow";
	final String FILL_BLUE_CAPABILITY = "fill_blue";
	final String TEST_CONTAINER_CAPABILITY = "test_container";
	final String LID_CONTAINER_CAPABILITY = "lid_container";
	final String DISPATCH_CONTAINER_CAPABILITY = "dispatch_container";
	
	final String FILL_QUANTITY_ARGUMENT = "quantity";
	final String TEST_QUANTITY_ARGUMENT = "test_quantity";
	final String LABEL_LID_ARGUMENT = "label_lid";
	
	final String ANNOUNCE_NEW_STRING = "announce_new";
	final String ANNOUNCE_INFO_STRING = "announce_info";	
	
	final int DOMAIN_ID = 5;
	final String RECIPE_TOPIC_NAME = "recipe_topic";
	final String RECIPE_DISPATCH_NAME = "Recipe_Dispatch";
	final String GENERAL_PUBLISHER_TOPIC = "general_announce";
	
	private RTI.keyValue.KeyValuePairPublisherModule publisher;
	private RTI.keyValue.KeyValuePairSubscriberModule subscriber;
	
	private ConcurrentHashMap<String, Integer> timeouts;
	
	private Vector<String> pointOfContactAgents = new Vector<String>();
	
	@FXML
	CheckBox PoCOnline;
	@FXML
	CheckBox loadContainerOnline;
	@FXML
	CheckBox fillRedOnline;
	@FXML
	CheckBox fillYellowOnline;
	@FXML
	CheckBox fillBlueOnline;
	@FXML
	CheckBox testOnline;
	@FXML
	CheckBox lidOnline;
	@FXML
	CheckBox dispatchOnline;

	
	@FXML
	CheckBox loadContainer;
	@FXML
	CheckBox lidContainer;
	@FXML
	CheckBox dispatchContainer;
	@FXML
	CheckBox testContainer;
		
	@FXML
	TextField quantityRed;
	@FXML
	TextField quantityBlue;
	@FXML
	TextField quantityYellow;	
	
	@FXML
	TextField testQuantity;
	
	@FXML
	TextField labelPrint;	
	
	boolean goClicked = false;
	
	KeyValueSimple message;
		
	private Main mainApp;
	
	public RecipeCreatorController() {
		
		
	}
	
	@FXML
	private void initialize() {
		
		PoCOnline.setSelected(false);
		loadContainerOnline.setSelected(false);
		fillRedOnline.setSelected(false);
		fillYellowOnline.setSelected(false);
		fillBlueOnline.setSelected(false);
		testOnline.setSelected(false);
		lidOnline.setSelected(false);
		dispatchOnline.setSelected(false);
		
		PoCOnline.setDisable(true);
		loadContainerOnline.setDisable(true);
		fillRedOnline.setDisable(true);
		fillYellowOnline.setDisable(true);
		fillBlueOnline.setDisable(true);
		testOnline.setDisable(true);
		lidOnline.setDisable(true);
		dispatchOnline.setDisable(true);
		
		loadContainer.setSelected(false);
		lidContainer.setSelected(false);
		dispatchContainer.setSelected(false);
		testContainer.setSelected(false);
		
		quantityRed.setText("0");
		quantityYellow.setText("0");
		quantityBlue.setText("0");
		
		testQuantity.setText("0");
		testQuantity.setEditable(false);	
		
		labelPrint.setText("");
		
		timeouts = new ConcurrentHashMap<String, Integer>();		
		
		publisher = new KeyValuePairPublisherModule(DOMAIN_ID, RECIPE_DISPATCH_NAME, false);
		publisher.createTopic(RECIPE_TOPIC_NAME);		
		
		subscriber = new KeyValuePairSubscriberModule(DOMAIN_ID, "Recipe Dispatch", true);
		
		//Subscriber must be in seperate thread
		Thread subscriberThread = new Thread(new Runnable() {
			public void run() {
				
				subscriber.startReading();
		     }
		});
		
		subscriberThread.start();
		
		long timeNow = System.currentTimeMillis();
		
		//Every 0.25 seconds get a message
		new Timer().schedule(new TimerTask() {
						        @Override
						        public void run() {	
						        	Platform.runLater(() -> {						        		
						        		
						        		findResources();
						        	});
						        }
								}, 0, 250);
		
		new Timer().schedule(new TimerTask() {
								@Override
								public void run() {
									Platform.runLater(() -> {
										decrementAndCheckTimeouts();
									});
								}
								}, 0, 5000);			
		
	}
	
	private void decrementAndCheckTimeouts() {
		
		Integer zero = new Integer(0);
		
		for (Map.Entry<String, Integer> entry : timeouts.entrySet()) {
		    
			Integer newTimeout = entry.getValue()-5;
			if (newTimeout.compareTo(zero) <= 0 ) {
				
				if (entry.getKey().equalsIgnoreCase(POINT_OF_CONTACT_CAPABILITY)) {					
					PoCOnline.setSelected(false);	
					
				} else if (entry.getKey().equalsIgnoreCase(LOAD_CONTAINER_CAPABILITY)) {					
					loadContainerOnline.setSelected(false);							
					
				} else if (entry.getKey().equalsIgnoreCase(FILL_RED_CAPABILITY)) {					
					fillRedOnline.setSelected(false);						
					
				} else if (entry.getKey().equalsIgnoreCase(FILL_YELLOW_CAPABILITY)) {					
					fillYellowOnline.setSelected(false);
										
				} else if (entry.getKey().equalsIgnoreCase(FILL_BLUE_CAPABILITY)) {					
					fillBlueOnline.setSelected(false);						
					
				} else if (entry.getKey().equalsIgnoreCase(TEST_CONTAINER_CAPABILITY)) {					
					testOnline.setSelected(false);					
					
				} else if (entry.getKey().equalsIgnoreCase(LID_CONTAINER_CAPABILITY)) {					
					lidOnline.setSelected(false);					
					
				} else if (entry.getKey().equalsIgnoreCase(DISPATCH_CONTAINER_CAPABILITY)) {					
					dispatchOnline.setSelected(false);							
				}
				
				timeouts.put(entry.getKey(), zero);
				
			} else {
				
				timeouts.put(entry.getKey(), newTimeout);
				
			}
			
		}
		
	}
	
	public boolean isGoClicked() {
		return goClicked;	}
	
	
	
	private void findResources() {
		
		//System.out.println("Checking for Capabilities");
		
		message = subscriber.getReceivedKeyValuePair(GENERAL_PUBLISHER_TOPIC);
		
		if (message != null) {			
			
			if (message.getKey().equalsIgnoreCase(ANNOUNCE_NEW_STRING) || message.getKey().equalsIgnoreCase(ANNOUNCE_INFO_STRING)) {
				
				//Parse into agent name and capabilities
				String messageContent = message.getValue();
				
				//Use capabilties for tickboxes------------------				
				if (messageContent.contains(POINT_OF_CONTACT_CAPABILITY)) {					
					PoCOnline.setSelected(true);	
					timeouts.put(POINT_OF_CONTACT_CAPABILITY, new Integer(30));
				}
				
				if (messageContent.contains(LOAD_CONTAINER_CAPABILITY)) {					
					loadContainerOnline.setSelected(true);		
					timeouts.put(LOAD_CONTAINER_CAPABILITY, new Integer(30));
					
				} else if (messageContent.contains(FILL_RED_CAPABILITY)) {					
					fillRedOnline.setSelected(true);	
					timeouts.put(FILL_RED_CAPABILITY, new Integer(30));
					
				} else if (messageContent.contains(FILL_YELLOW_CAPABILITY)) {					
					fillYellowOnline.setSelected(true);
					timeouts.put(FILL_YELLOW_CAPABILITY, new Integer(30));
					
				} else if (messageContent.contains(FILL_BLUE_CAPABILITY)) {					
					fillBlueOnline.setSelected(true);	
					timeouts.put(FILL_BLUE_CAPABILITY, new Integer(30));
					
				} else if (messageContent.contains(TEST_CONTAINER_CAPABILITY)) {					
					testOnline.setSelected(true);				
					timeouts.put(TEST_CONTAINER_CAPABILITY, new Integer(30));
					
				} else if (messageContent.contains(LID_CONTAINER_CAPABILITY)) {					
					lidOnline.setSelected(true);
					timeouts.put(LID_CONTAINER_CAPABILITY, new Integer(30));
					
				} else if (messageContent.contains(DISPATCH_CONTAINER_CAPABILITY)) {					
					dispatchOnline.setSelected(true);		
					timeouts.put(DISPATCH_CONTAINER_CAPABILITY, new Integer(30));
				}
				//End Use capabilties for tickboxes------------------------------------
				
				//Use capabiltiies to generate available PoCA list
				if (messageContent.contains(POINT_OF_CONTACT_CAPABILITY)) {			
				
					String[] components = messageContent.split("\\|");		
					String agentName = components[0];	
					
					//if vector doens't already contain this PoCA
					if (pointOfContactAgents.indexOf(POINT_OF_CONTACT_CAPABILITY) == -1) {		
						
						pointOfContactAgents.add(agentName);						
					}
					
				}				
			}
		}
	
		
	}
	
	
	@SuppressWarnings("deprecation")
	@FXML
	private void handleGoButton() {
		
		try {		
			
		
			int redQuantity = Integer.parseInt(quantityRed.getText());
			int blueQuantity = Integer.parseInt(quantityBlue.getText());
			int yellowQuantity = Integer.parseInt(quantityYellow.getText());
			
			int totalQuantity = redQuantity + blueQuantity + yellowQuantity;
			
			boolean fillRed = false;
			if (redQuantity > 0) {
				fillRed = true;
			}
			
			boolean fillYellow = false;
			if (yellowQuantity > 0) {
				fillYellow = true;
			}
			
			boolean fillBlue = false;
			if (blueQuantity > 0) {
				fillBlue = true;
			}			
			
			testQuantity.setText(Long.toString(totalQuantity));
			
			if (totalQuantity > CONTAINER_MAX_VOLUME) {
				throw new NumberFormatException();
			}
			
			boolean loadContainerBool = loadContainer.isSelected();	
			boolean lidContainerBool = lidContainer.isSelected();			
			
			String labelContainerLid = labelPrint.getText().trim();
			if (labelContainerLid.length() > LABEL_MAX_CHARACTERS) {
				
				labelContainerLid = labelContainerLid.substring(0, LABEL_MAX_CHARACTERS-1);
				
			} else if (labelContainerLid.length() < LABEL_MAX_CHARACTERS) {
					
				String fillerString = "************";
				labelContainerLid = labelContainerLid.concat(fillerString.substring(0, (LABEL_MAX_CHARACTERS - labelContainerLid.length())));
				
			}
			
			boolean dispatchContainerBool = dispatchContainer.isSelected();
			boolean testContainerBool = testContainer.isSelected();
			
			ArrayList<Requirement> requirementsForDispatch = new ArrayList<Requirement>();
			
			
			//1. Load Container
			if (loadContainerBool) {
				
				Requirement requirementLoadContainer = new Requirement(new Integer(1), LOAD_CONTAINER_CAPABILITY);
				
				requirementsForDispatch.add(requirementLoadContainer);				
			}
			
			//2: Add Red
			if (fillRed) {
				Requirement requirementFillRed = new Requirement(new Integer(2), FILL_RED_CAPABILITY);
				requirementFillRed.addRequirementBefore(1);
				requirementFillRed.addArgument(FILL_QUANTITY_ARGUMENT, Long.toString(redQuantity));
				
				requirementsForDispatch.add(requirementFillRed);
			}
			
			//3: Add Yellow
			if (fillYellow) {
				Requirement requirementFillYellow = new Requirement(new Integer(3), FILL_YELLOW_CAPABILITY);
				requirementFillYellow.addRequirementBefore(1);
				requirementFillYellow.addArgument(FILL_QUANTITY_ARGUMENT, Long.toString(yellowQuantity));
				
				requirementsForDispatch.add(requirementFillYellow);
			}
			
			//4: Add Blue
			if (fillBlue) {
				Requirement requirementFillBlue = new Requirement(new Integer(4), FILL_BLUE_CAPABILITY);
				requirementFillBlue.addRequirementBefore(1);
				requirementFillBlue.addArgument(FILL_QUANTITY_ARGUMENT, Long.toString(blueQuantity));
				
				requirementsForDispatch.add(requirementFillBlue);
			}
			
			//5: Test Pot
			if (testContainerBool) {			
				Requirement requirementTestPot = new Requirement(new Integer(5), TEST_CONTAINER_CAPABILITY);
				requirementTestPot.addRequirementBefore(1);
				if (fillRed) { requirementTestPot.addRequirementBefore(2); }
				if (fillYellow) { requirementTestPot.addRequirementBefore(3); }
				if (fillBlue) { requirementTestPot.addRequirementBefore(4); }
				requirementTestPot.addArgument(TEST_QUANTITY_ARGUMENT, Long.toString(totalQuantity));
				
				requirementsForDispatch.add(requirementTestPot);
			}
			
			//6: Lid and Label Pot
			if (lidContainerBool) {
				Requirement requirementLidPot = new Requirement(new Integer(6), LID_CONTAINER_CAPABILITY);
				requirementLidPot.addRequirementBefore(1);
				if (fillRed) { requirementLidPot.addRequirementBefore(2); }
				if (fillYellow) { requirementLidPot.addRequirementBefore(3); }
				if (fillBlue) { requirementLidPot.addRequirementBefore(4); }
				if (testContainerBool) { requirementLidPot.addRequirementBefore(5); }
				if (!labelContainerLid.equalsIgnoreCase("")) {
					requirementLidPot.addArgument(LABEL_LID_ARGUMENT, labelContainerLid);
				}
				requirementsForDispatch.add(requirementLidPot);				
			}
			
			//7: Dispatch Pot
			if (dispatchContainerBool) {
				Requirement requirementDispatchPot = new Requirement(new Integer(7), DISPATCH_CONTAINER_CAPABILITY);
				requirementDispatchPot.addRequirementBefore(1);
				if (fillRed) { requirementDispatchPot.addRequirementBefore(2); }
				if (fillYellow) { requirementDispatchPot.addRequirementBefore(3); }
				if (fillBlue) { requirementDispatchPot.addRequirementBefore(4); }
				if (testContainerBool) { requirementDispatchPot.addRequirementBefore(5); }
				if (lidContainerBool) { requirementDispatchPot.addRequirementBefore(6); }
				
				requirementsForDispatch.add(requirementDispatchPot);
				
			}
			
			//Publish Recipe only if a PoCA exists:
			
			if (pointOfContactAgents.size() > 0) {
			
				//Select random PoCA
				int maxIndex = pointOfContactAgents.size();			
			    Random generator = new Random();
			    int randomPoCA = generator.nextInt(maxIndex);
			    String randomPoCAName = pointOfContactAgents.get(randomPoCA);
				
				System.out.println("Recipe Dispatched! Number of Requirements = " + requirementsForDispatch.size());
				System.out.println("Recipe Target: " + randomPoCAName);
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
				
				
							
				publisher.sentRequirements(RECIPE_TOPIC_NAME, requirementsForDispatch, RECIPE_DISPATCH_NAME, randomPoCAName);		
				
			} else {
				
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle("Error");
				alert.setHeaderText("No Point of Contact found");
				alert.setContentText("No point of contact agent found. Recipe not dispatched");

				alert.showAndWait();
				
			}
			
			
		} catch (NumberFormatException e) {
			
			Alert alert = new Alert(AlertType.INFORMATION);
			alert.setTitle("Information");
			alert.setHeaderText("Incorrect Number Entered");
			alert.setContentText("Incorrect number entered (quantities are integers 0-4).\nOr the total particulate is too high (Max Total Particulate: " + CONTAINER_MAX_VOLUME + ").");
			
			alert.showAndWait();			
		}
		
	}
	
	public void setMainApp(Main mainApp) {
		this.mainApp = mainApp;
	}
	
	public void stop() {
		
	}
	
	
	
	

}
