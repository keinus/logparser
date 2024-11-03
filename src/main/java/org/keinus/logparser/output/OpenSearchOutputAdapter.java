package org.keinus.logparser.output;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

import org.keinus.logparser.interfaces.OutputAdapter;


public class OpenSearchOutputAdapter implements OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( OpenSearchOutputAdapter.class );
    String host;
    String index;
	List<String> indexVars = null;
	int retry = 30;
	private final ConcurrentHashMap<String, List<String>> dataMap = new ConcurrentHashMap<>();

	public void init(Map<String, String> obj) {
		try {
			if (obj == null) {
            	LOGGER.info("Property not found.");
                throw new IOException("Property not found.");
            }
            
            host = obj.get("host");	
            index = obj.get("index");
			indexVars = extractBracedStrings(index);
			            
            LOGGER.info("Elastic Output Adapter Init. {}", host);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
        }
	}

	public List<String> extractBracedStrings(String input) {
        List<String> extractedStrings = new ArrayList<>();
        Pattern pattern = Pattern.compile("%\\{(.*?)}");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            extractedStrings.add(matcher.group(1));
        }

        return extractedStrings;
    }

	public void addJsonString(String index, String jsonString) {
        dataMap.computeIfAbsent(index, k -> new ArrayList<>()).add(jsonString);
    }

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
	}

	@Override
	public void close() throws IOException {
		dataMap.clear();
	}

	public static String listToStringWithNewLines(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append(str).append("\n");
        }
        return sb.toString();
    }

	public String bulkIndex(Socket socket, String index, String body) {
		StringBuilder sb = new StringBuilder();
		sb.append("POST /_bulk" + " HTTP/1.0\r\n");
		sb.append("Content-Length: " + body.length() + "\r\n");
		sb.append("Content-Type: application/json\r\n");
		sb.append("\r\n");
		sb.append(body);
		sb.append("\r\n");
		ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(sb.toString());
		
		try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
			dos.write(byteBuffer.array());
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
			LOGGER.error(e.getMessage());
			return "";
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
			    response.append(line);
			}
			return response.toString();
		} catch (IOException e) {
			e.printStackTrace();
			LOGGER.error(e.getMessage());
		}
		return "";
	}

	private Socket connect() throws IOException {
		Socket socket = null;
		for(int count = 0; count <= retry; count++) {
			try {
				socket = new Socket(host, 9200);
				socket.setReuseAddress(true);
				break;
			} catch (IOException e) {
				e.printStackTrace();
				LOGGER.error(e.getMessage());
			}
		}
		return socket;
	}

	@Override
	public void flush() {
		try (Socket socket = connect()) {
			for (Entry<String, List<String>> entry : this.dataMap.entrySet()) {
				String indexTarget = entry.getKey();
				List<String> value = entry.getValue();
				String body = listToStringWithNewLines(value);
				bulkIndex(socket, indexTarget, body);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			LOGGER.error(e.getMessage());
		}
	}
}
