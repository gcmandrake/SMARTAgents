package resourceAgents;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Recipe implements Serializable {
	
	private String UID = "INVALID_DEFAULT_UID";
	private String identifier =  "";	
	private HashMap<Integer, Requirement> requirements = new HashMap<Integer, Requirement>();
	
	
	
	public Recipe(String uID, String identifier, ArrayList<Requirement> requirements) {		
		UID = uID;
		
		if (identifier != null) {
			this.identifier = identifier;
		} else {
			this.identifier = "";
		}
		
		for (int i = 0; i < requirements.size(); i++) {
			this.requirements.put(requirements.get(i).getRequirementUID(), requirements.get(i));
			
		}
	}

	public Recipe(String UID) {
		this.UID = UID;
	}
	
	public String getUID() {
		return UID;
	}
	
	public String getIdentifier() {
		return identifier;
	}
	
	public void setIdentifier(String indentifier) {		
		this.identifier = indentifier;
	}
	
	public HashMap<Integer, Requirement> getRequirements() {
		return requirements;
	}
	
	public void addRequirement(Requirement requirement) {
		this.requirements.put(requirement.getRequirementUID(), requirement);
	}
	
	public void setAllRequirements(HashMap<Integer, Requirement> requirements) {
		this.requirements = requirements;
	}
	
	

}
