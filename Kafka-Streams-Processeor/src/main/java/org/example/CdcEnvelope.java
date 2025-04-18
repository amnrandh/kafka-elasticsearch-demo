package org.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CdcEnvelope<T> {
    @JsonProperty("before")
    private T before;
    private String op; // "r"=snapshot, "c"=insert, "u"=update, "d"=delete

    @JsonProperty("op")
    public void setOp(String op) {
        this.op = op;
    }
    public String getOp() { return op; }
    @JsonProperty("after")
    private T after;

    // Add these getter methods
    public T getBefore() {
        return before;
    }

    public T getAfter() {
        return after;
    }

    // Optionally add setters if needed
    public void setBefore(T before) {
        this.before = before;
    }

    public void setAfter(T after) {
        this.after = after;
    }
}
