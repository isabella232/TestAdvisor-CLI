package com.salesforce.cte.adapter;

import java.time.Instant;

/**
 * @author Yibing Tao
 * ITestSignal interface define how to get signal for a single test case
 */
public interface TestAdvisorTestSignal {
    public String getTestSignalName();
    public String getTestSignalValue();
    public Instant getTestSignalTime();
}
