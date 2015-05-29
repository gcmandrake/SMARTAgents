package resourceAgents;



import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Requirement implements Serializable {
	
	private String recipeUID;
	private Integer requirementUID;
	private String requiredCapability = "+++IMPOSSIBLE_CAPABILITY+++";	
	private HashMap<String, String> arguments = new HashMap<String, String>();
	private String status = "Unclaimed";
	private ArrayList<Integer> requirementsBefore = new ArrayList<Integer>();
	
	public Requirement(int UID, String requiredCapability, ArrayList<Integer> requirementsBefore) {		
		
		this.requirementUID = new Integer(UID);
		this.requiredCapability = requiredCapability;
		this.requirementsBefore.addAll(requirementsBefore);
	}
	
	public Requirement(int UID, String requiredCapability) {
		
		this(UID, requiredCapability, new ArrayList<Integer>());
		
	}
	
	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getRequirementUID() {
		return requirementUID;
	}
	
	public int getRequirementUIDAsInt() {
		return requirementUID.intValue();
	}
		
	public String getRequiredCapability() {
		return requiredCapability;
	}
	
	public void setRequiredCapability(String requiredCapability) {
		this.requiredCapability = requiredCapability;
	}
	
	public ArrayList<Integer> getRequirementsBefore() {
		return requirementsBefore;
	}
	
	public HashMap<String, String> getArguments() {
		return this.arguments;
	}
	
	public void setAllArguments(HashMap<String, String> argsIn) {
		this.arguments = argsIn;
	}
	
	public void addArgument(String argNameIn, String argValueIn) {
		
		this.arguments.put(argNameIn, argValueIn);
		
	}
	
	public void addRequirementBefore(Integer uid) {
		this.requirementsBefore.add(uid);
	}
	
	public void setRequirementsBefore(ArrayList<Integer> requirementsBefore) {
		this.requirementsBefore = requirementsBefore;
	}

	public String getRecipeUID() {
		return recipeUID;
	}

	public void setRecipeUID(String recipeUID) {
		this.recipeUID = recipeUID;
	}
	
	
	
	
	

}
