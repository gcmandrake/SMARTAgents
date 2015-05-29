package RTI.keyValue;

public class KeyValueSimple {
	
	
	private String key;
	private String value;
	private String sourceAgent;
	
	public KeyValueSimple(String key, String sourceAgent, String value) {
		this.key = key;
		this.sourceAgent = sourceAgent;
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getSourceAgent() {
		return sourceAgent;
	}

	public void setSourceAgent(String sourceAgent) {
		this.sourceAgent = sourceAgent;
	}

}
