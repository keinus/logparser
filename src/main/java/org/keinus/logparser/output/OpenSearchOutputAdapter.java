package org.keinus.logparser.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
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

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;




import org.keinus.logparser.interfaces.OutputAdapter;


public class OpenSearchOutputAdapter implements OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( OpenSearchOutputAdapter.class );
	String host;
	int port;
	String index;
	List<String> indexVars = null;
	int retry = 30;
	private final ConcurrentHashMap<String, List<String>> dataMap = new ConcurrentHashMap<>();
	CloseableHttpClient httpClient;

	@Override
	public void init(Map<String, String> obj) {
		try {
			if (obj == null) {
				LOGGER.info("Property not found.");
				throw new IOException("Property not found.");
			}
			
			host = obj.get("host");	
			port = Integer.parseInt(obj.get("port"));
			index = obj.get("index");
			indexVars = extractBracedStrings(index);
						
			LOGGER.info("Elastic Output Adapter Init. {}:{}", host, port);
		} catch (IOException e) {
			e.printStackTrace();
			LOGGER.error(e.getMessage());
		}

		try {
			class AllTrustingTrustManager implements X509TrustManager {
				@Override
				public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
			
				@Override
				public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
			
				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			}
			
			// SSLContext 설정
			SSLContext sslContext = SSLContext.getInstance("TLS");
			TrustManager[] trustAllCerts = new TrustManager[]{new AllTrustingTrustManager()};
			sslContext.init(null, trustAllCerts, null);
			
			// SSLConnectionSocketFactory 생성
			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
			
			// HttpClient 생성
			this.httpClient = HttpClients.custom()
					.setSSLSocketFactory(sslsf)
					.build();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
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
		
		for(var variable : indexVars) {
			if(variable.startsWith("yy")) {
				String time = new SimpleDateFormat(variable).format(new Date());
				if(time != null && !time.equals(""))
					target = target.replace("%{"+variable+"}", time);
			} else {
				var value = json.get(variable);
				if(value != null)
					target = target.replace("%{"+variable+"}", value.toString());
			}
		}
		addJsonString(target, jsonString);
		
		int size = 0;
		for(List<String> entry: dataMap.values()) {
			size += entry.size();
		}
		if(size >= 2000)
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
				e.printStackTrace();
			}
		}
		this.dataMap.clear();
	}

	public void sendRest(String url, String json) throws IOException {
        String credentials = "admin:Rlaqudwls1!";
        String base64Credentials = Base64.encodeBase64String(credentials.getBytes(StandardCharsets.UTF_8));
        String authorization = "Basic " + base64Credentials;
        HttpPost httpPost = new HttpPost(url);

        httpPost.setHeader("Content-Type", "application/json");
		httpPost.setHeader("Authorization", authorization);
        httpPost.setEntity(new StringEntity(json));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
            } else {
				String responseBody = new BasicResponseHandler().handleResponse(response);
                throw new IOException("Index Failed. " + responseBody);
            }
        }
    }

}
