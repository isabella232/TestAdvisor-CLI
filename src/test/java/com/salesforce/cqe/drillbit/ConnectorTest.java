package com.salesforce.cqe.drillbit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.salesforce.cqe.helper.DrillbitException;
import com.salesforce.cqe.helper.SecretsManager;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Test;

/**
 * @author gneumann
 * @author Yibing Tao
 */
public class ConnectorTest {

    @Test(expected = DrillbitException.class)
	public void testApexPostBadRequest() throws Exception {
		CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        HttpPost httpPost = mock(HttpPost.class);
        CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);

        //and:
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(httpPost)).thenReturn(httpResponse);

        //and:
        SecretsManager secretsManager = new SecretsManager();
        try(Connector connector = new Connector(httpClient,httpPost,secretsManager)){
            connector.postApex("endpoint", "");
        }
	}
}
