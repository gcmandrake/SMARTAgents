package application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Map;
import RTI.ACL.Base64Coder;
import RTI.ACL.TestACL;
import RTI.keyValue.KeyValuePairPublisherModule;
import resourceAgents.Requirement;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBoxBuilder;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class RecipeCreatorController {
	
	final long CONTAINER_MAX_VOLUME = 4;
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
	
	final int DOMAIN_ID = 5;
	final String RECIPE_TOPIC_NAME = "recipe_topic";
	final String RECIPE_DISPATCH_NAME = "Recipe_Dispatch";
	
	private RTI.keyValue.KeyValuePairPublisherModule publisher;

	
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
	
	private Main mainApp;
	
	public RecipeCreatorController() {
		
		
	}
	
	@FXML
	private void initialize() {
		
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
		
		publisher = new KeyValuePairPublisherModule(DOMAIN_ID, RECIPE_DISPATCH_NAME, false);
		publisher.createTopic(RECIPE_TOPIC_NAME);
		
	}
	
	public boolean isGoClicked() {
		return goClicked;
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
			
			//Publish Recipe
			
			System.out.println("Recipe Dispatched! Number of Requirements = " + requirementsForDispatch.size());
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
						
			publisher.sentRequirements(RECIPE_TOPIC_NAME, requirementsForDispatch, RECIPE_DISPATCH_NAME);			
			
			
			
		} catch (NumberFormatException e) {
			
			Stage dialogStage = new Stage();
			dialogStage.initModality(Modality.WINDOW_MODAL);
			dialogStage.setScene(new Scene(VBoxBuilder.create().
			    children(new Text("Incorrect number entered (quantities are integers 0-4).\nOr the total particulate is too high (Max Total Particulate: " + CONTAINER_MAX_VOLUME + ").")).
			    alignment(Pos.CENTER).padding(new Insets(5)).build()));
			dialogStage.show();		
			
		}
		
	}
	
	public void setMainApp(Main mainApp) {
		this.mainApp = mainApp;
	}
	
	public void stop() {
		
	}
	
	
	
	

}
