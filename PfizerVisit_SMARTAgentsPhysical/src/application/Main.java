package application;
	
import java.io.IOException;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;


public class Main extends Application {

	private Stage primaryStage;
	private BorderPane rootLayout;
	
	@Override
	public void start(Stage primaryStage) {
		
		this.primaryStage = primaryStage;
		this.primaryStage.setTitle("Recipe Dispatch");
		this.primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				System.exit(0);
			}
		});
		
		initRootLayout();		
		
		showRecipeCreator();
		
	}
	
	public void initRootLayout() {
		
		try {
			
			//Load Root Layout from File
			FXMLLoader loader = new FXMLLoader();
			loader.setLocation(Main.class.getResource("RootLayout.fxml"));
			rootLayout = (BorderPane) loader.load();
			
			//Show scene
			Scene scene = new Scene(rootLayout);
			primaryStage.setScene(scene);
			primaryStage.show();
			
		} catch (Exception e) {
			//TODO
			e.printStackTrace();
		}
		
	}
	
	public void showRecipeCreator() {
		try {
			
			FXMLLoader loader = new FXMLLoader();
			loader.setLocation(Main.class.getResource("RecipeCreator.fxml"));
			AnchorPane recipeCreator = (AnchorPane) loader.load();
			
			rootLayout.setCenter(recipeCreator);
			
			RecipeCreatorController controller = loader.getController();
			controller.setMainApp(this);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	public Stage getPrimaryStage() {
		return primaryStage;
	}
	
	public static void main(String[] args) {
		launch(args);
	}	
}
