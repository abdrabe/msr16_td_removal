/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.apache.jmeter.protocol.http.sampler;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.control.Header;

/**
 * Sampler to handle SOAP Requests.
 * 
 * @author Jordi Salvat i Alabart
 * @version $Id$
 */
public class SoapSampler extends HTTPSampler {
	private static Logger log = LoggingManager.getLoggerForClass();

	public static final String XML_DATA = "HTTPSamper.xml_data";

	public static final String URL_DATA = "SoapSampler.URL_DATA";

	public static final String SOAP_ACTION = "SoapSampler.SOAP_ACTION";

	public static final String SEND_SOAP_ACTION = "SoapSampler.SEND_SOAP_ACTION";

	public void setXmlData(String data) {
		setProperty(XML_DATA, data);
	}

	public String getXmlData() {
		return getPropertyAsString(XML_DATA);
	}

	public String getURLData() {
		return getPropertyAsString(URL_DATA);
	}

	public void setURLData(String url) {
		setProperty(URL_DATA, url);
	}

	public String getSOAPAction() {
		return getPropertyAsString(SOAP_ACTION);
	}

	public void setSOAPAction(String action) {
		setProperty(SOAP_ACTION, action);
	}

	public boolean getSendSOAPAction() {
		return getPropertyAsBoolean(SEND_SOAP_ACTION);
	}

	public void setSendSOAPAction(boolean action) {
		setProperty(SEND_SOAP_ACTION, String.valueOf(action));
	}

	/**
	 * Set the HTTP request headers in preparation to open the connection and
	 * sending the POST data.
	 * 
	 * @param connection
	 *            <code>URLConnection</code> to set headers on
	 * @exception IOException
	 *                if an I/O exception occurs
	 */
	public void setPostHeaders(URLConnection connection) throws IOException {
		((HttpURLConnection) connection).setRequestMethod("POST");
		connection.setRequestProperty("Content-Length", "" + getXmlData().length());
		// my first attempt at fixing the bug failed, due to user
		// error on my part. HeaderManager does not use the normal
		// setProperty, and getPropertyAsString methods. Instead,
		// it uses it's own String array and Header object.
		if (getHeaderManager() != null) {
			// headerManager was set, so let's set the connection
			// to use it.
			HeaderManager mngr = getHeaderManager();
			int headerSize = mngr.size();
			// we set all the header properties
			for (int idx = 0; idx < headerSize; idx++) {
				Header hd = mngr.getHeader(idx);
				connection.setRequestProperty(hd.getName(), hd.getValue());
			}
		} else {
			// otherwise we use "text/xml" as the default
			connection.setRequestProperty("Content-Type", "text/xml");
			if(getSendSOAPAction()) connection.setRequestProperty("SOAPAction", "\"" + getSOAPAction() + "\"");
		}
		connection.setDoOutput(true);
	}

	/**
	 * Send POST data from <code>Entry</code> to the open connection.
	 * 
	 * @param connection
	 *            <code>URLConnection</code> of where POST data should be sent
	 * @exception IOException
	 *                if an I/O exception occurs
	 */
	public void sendPostData(URLConnection connection) throws IOException {
		PrintWriter out = new PrintWriter(connection.getOutputStream());
		out.print(getXmlData());
		out.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Sampler#sample(Entry)
	 */
	public SampleResult sample(Entry e) {
		HTTPSampleResult sampleResult = null;
		Exception ex = null;
		try {
			URL url = new URL(getURLData());
			setDomain(url.getHost());
			setPort(url.getPort());
			setProtocol(url.getProtocol());
			setMethod(POST);
			if (url.getQuery() != null && url.getQuery().compareTo("") != 0) {
				setPath(url.getPath() + "?" + url.getQuery());
			} else {
				setPath(url.getPath());
			}
			// make sure the Post header is set
			URLConnection conn = url.openConnection();
			setPostHeaders(conn);
			sampleResult = (HTTPSampleResult) super.sample(e);
		} catch (MalformedURLException e1) {
			ex=e1;
			log.error("Bad url: " + getURLData(), e1);
		} catch (IOException e1) {
			ex=e1;
			log.error("Bad url: " + getURLData(), e1);
		}
		if (ex != null){
			if (sampleResult == null) {
				sampleResult = new HTTPSampleResult();
				sampleResult.setSampleLabel(getName());
			}
			sampleResult.setResponseCode("000");
			sampleResult.setResponseMessage(ex.getLocalizedMessage());
		}
        // Bug 39252 set SoapSampler sampler data from XML data
		// TODO: need to set both at present, because POST data for some reason
		// is stored in the query string, not as sampler data ...
        sampleResult.setSamplerData(getXmlData());
        sampleResult.setQueryString(getXmlData());
        return sampleResult;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer(150);
		try {
			sb.append(this.getUrl().toString());
		} catch (MalformedURLException e) {
			sb.append(e.getLocalizedMessage());
		}
		sb.append("\nXML Data: ");
		String xml = getXmlData();
		if (xml.length() > 100) {
			sb.append(xml.substring(0, 100));
		} else {
			sb.append(xml);
		}
		return sb.toString();
	}
}
