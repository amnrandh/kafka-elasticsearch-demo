package org.example;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)

public class Project {
	
	 	@JsonProperty("id") 
	    private String id;
	 	@JsonProperty("name") 
	    private String name;
	 	@JsonProperty("customer_id") 
	    private String customerId;

	    public String getId() {
	        return id;
	    }

	    public void setId(String id) {
	        this.id = id;
	    }

	    public String getName() {
	        return name;
	    }

	    public void setName(String name) {
	        this.name = name;
	    }

	    public String getCustomerId() {
	        return customerId;
	    }

	    public void setCustomerId(String customerId) {
	        this.customerId = customerId; 
	    }
	    

    @Override
    public String toString() {
        return "Project{id='" + id + "', name='" + name + "'}";
    }
}


