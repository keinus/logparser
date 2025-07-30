package org.keinus.logparser.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.keinus.logparser.interfaces.OutputAdapter;

public class OpenSearchOutputAdapter extends OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchOutputAdapter.class);
	String host;
	int port;
	String index;
	String credentials = null;
	List<String> indexVars = null;
	int retry = 30;
	private final ConcurrentHashMap<String, List<String>> dataMap = new ConcurrentHashMap<>();
	CloseableHttpClient httpClient;

	public OpenSearchOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);

		host = obj.get("host");
		port = Integer.parseInt(obj.get("port"));
		index = obj.get("index");
		indexVars = extractBracedStrings(index);
		String username = obj.get("username");
		String password = obj.get("password");
		credentials = username + ":" + password;

		LOGGER.info("Elastic Output Adapter Init. {}:{}", host, port);

		try {
			SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(
				SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(), 
				NoopHostnameVerifier.INSTANCE
			);
			this.httpClient = HttpClients.custom().setSSLSocketFactory(scsf).build();

		} catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
			LOGGER.error(e.getMessage());
		}
	}

	private List<String> extractBracedStrings(String input) {
		List<String> extractedStrings = new ArrayList<>();
		Pattern pattern = Pattern.compile("%\\{(.*?)}");
		Matcher matcher = pattern.matcher(input);

		while (matcher.find()) {
			extractedStrings.add(matcher.group(1));
		}

		return extractedStrings;
	}

	private void addJsonString(String index, String jsonString) {
		dataMap.computeIfAbsent(index, k -> new ArrayList<>()).add(jsonString);
	}

	@Override
	public void send(Map<String, Object> json, String jsonString) {
		String target = index;

		for (var variable : indexVars) {
			if (variable.startsWith("yy")) {
				String time = new SimpleDateFormat(variable).format(new Date());
				if (time != null && !time.equals(""))
					target = target.replace("%{" + variable + "}", time);
			} else {
				var value = json.get(variable);
				if (value != null)
					target = target.replace("%{" + variable + "}", value.toString());
			}
		}
		addJsonString(target, jsonString);

		int size = 0;
		for (List<String> entry : dataMap.values()) {
			size += entry.size();
		}
		if (size >= 2000)
			this.flush();
	}

	@Override
	public void close() throws IOException {
		dataMap.clear();
	}

	private static String listToStringWithNewLines(String index, List<String> list) {
		StringBuilder sb = new StringBuilder();
		for (String str : list) {
			sb.append("{ \"index\": { \"_index\": \"");
			sb.append(index);
			sb.append("\"} }");
			sb.append("\n");
			sb.append(str).append("\n");
		}
		return sb.toString();
	}

	@Override
	public void flush() {
		for (Entry<String, List<String>> entry : this.dataMap.entrySet()) {
			long startElapse = System.currentTimeMillis();

			String indexTarget = entry.getKey();
			List<String> value = entry.getValue();
			int count = value.size();
			String url = "https://" + host + ":" + port + "/" + indexTarget + "/_bulk";
			String body = listToStringWithNewLines(indexTarget, value);
			try {
				sendRest(url, body);
				long endElapse = System.currentTimeMillis();
				double elapsedSeconds = (endElapse - startElapse) / 1000.0;
				int processedPerSecond = (int) (count / elapsedSeconds); // Calculate count per second
				LOGGER.info("{} item processed in {} second: {}/s", count, elapsedSeconds, processedPerSecond);
			} catch (IOException e) {
				LOGGER.error(e.getMessage());
			}
		}
		this.dataMap.clear();
	}

	public void sendRest(String url, String json) throws IOException {

		String base64Credentials = Base64.encodeBase64String(credentials.getBytes(StandardCharsets.UTF_8));
		String authorization = "Basic " + base64Credentials;
		HttpPost httpPost = new HttpPost(url);

		httpPost.setHeader("Content-Type", "application/json");
		httpPost.setHeader("Authorization", authorization);
		httpPost.setEntity(new StringEntity(json));

		try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode > 200 || statusCode > 300) {
				String responseBody = new BasicResponseHandler().handleResponse(response);
				throw new IOException("Index Failed. " + responseBody);
			}
		}
	}

}
