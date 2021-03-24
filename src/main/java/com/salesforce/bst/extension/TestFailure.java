package com.salesforce.bst.extension;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Yibing Tao This class defines a test failure
 */
public class TestFailure {
    @JsonProperty
    private String failureSource;
    @JsonProperty
    private String failureType; // Regex "[a-zA-Z0-9]{1,80}"
    // time are defined as the time difference in milliseconds between
    // the current time and midnight, January 1, 1970, UTC
    @JsonProperty
    private long failureTime;

    public TestFailure(){}
    public TestFailure(String source, String type, long time){
        this.failureSource = source;
        this.failureTime = time;
        this.failureType = type; 
    }

    public String getFailureSource(){
        return this.failureSource;
    }
    
    public String getFailureType(){
        return this.failureType;
    }

    public long getFailureTime(){
        return this.failureTime;
    }
}
