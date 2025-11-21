package org.keinus.logparser.domain.ingestion.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.domain.ingestion.model.InputAdapter;
import org.keinus.logparser.domain.model.LogEvent;


/**
 * TCP 소켓을 통해 라인 단위로 메시지를 수신하는 입력 어댑터입니다.
 * <p>
 * 이 클래스는 지정된 포트에서 {@link ServerSocket}을 열고 클라이언트의 연결을 기다립니다.
 * 연결이 수립되면, 클라이언트로부터 한 줄(line)의 데이터를 읽어 {@link Message} 객체로
 * 생성합니다. 각 라인은 개행 문자(newline)로 구분됩니다.
 * <p>
 * {@code run()} 메서드는 블로킹 방식으로 동작하며, 새로운 클라이언트 연결이 들어올 때까지 대기합니다.
 * 소켓 예외 발생 시, 소켓을 다시 초기화하려는 시도를 포함하고 있습니다.
 *
 * @see org.keinus.logparser.core.interfaces.InputAdapter
 * @see java.net.ServerSocket
 */
public class TcpInputAdapter extends InputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( TcpInputAdapter.class );
	private ServerSocket serverSocket;
	private int port = 0;
    
	public TcpInputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		try {
            port = Integer.parseInt(obj.get("port"));
            initServerSocket();
            
            LOGGER.info("TCP Input Adapter start at port {}", port);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
	}

	private void initServerSocket() throws IOException {
		serverSocket = new ServerSocket(port);
		serverSocket.setReuseAddress(true);
	}

	@Override
	public LogEvent run() {
        try {
            Socket socket = serverSocket.accept();
            if (socket.isConnected()) {
                // try-with-resources를 사용하여 리소스 자동 해제
            	try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String payload = br.readLine();
                    String host = socket.getInetAddress().toString();
                    return createLogEvent(payload, host);
                } finally {
                    // 소켓도 명시적으로 닫기
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                }
            }
        } catch(SocketException e) {
			try {
				initServerSocket();
			} catch (IOException e1) {
				LOGGER.error("TcpInputAdaptor Server Socket Error(Terminate this Adapter): {}", e1.getMessage());
			}
		} catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
        return null;
    }
	
	@Override
	public void close() throws IOException {
		if(serverSocket != null)
			serverSocket.close();
	}
}
