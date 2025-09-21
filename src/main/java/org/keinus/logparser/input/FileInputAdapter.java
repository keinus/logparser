package org.keinus.logparser.input;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.core.interfaces.InputAdapter;
import org.keinus.logparser.core.util.ThreadUtil;
import org.keinus.logparser.core.schema.LogEvent;

/**
 * 지정된 파일로부터 로그 메시지를 라인 단위로 읽어오는 입력 어댑터입니다.
 * <p>
 * {@link InputAdapter}를 구현하며, Non-blocking I/O({@link FileChannel})를 사용하여
 * 파일의 새로운 내용을 효율적으로 추적하고 읽어들입니다. 'tail -f'와 유사한 동작을 수행합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *     <li><b>파일 추적:</b> 파일의 마지막 위치를 기억하고, 새로운 내용이 추가될 때만 읽습니다.</li>
 *     <li><b>로그 로테이션 감지:</b> 파일 크기가 줄어드는 것을 감지하여 로그 파일이 로테이션되었음을 인지하고,
 *         파일을 다시 열어 처음부터 읽기 시작합니다.</li>
 *     <li><b>버퍼링:</b> {@link ByteBuffer}를 사용하여 파일 내용을 버퍼에 읽어온 후, 라인 단위로 분리하여
 *         내부 큐에 저장합니다. 이를 통해 I/O 호출 횟수를 줄입니다.</li>
 *     <li><b>시작 위치 설정:</b> {@code isFromBeginning} 설정을 통해 파일의 처음부터 읽을지,
 *         아니면 현재 끝에서부터 읽을지를 결정할 수 있습니다.</li>
 * </ul>
 *
 * @see org.keinus.logparser.core.interfaces.InputAdapter
 * @see java.nio.channels.FileChannel
 * @see java.nio.ByteBuffer
 */
public class FileInputAdapter extends InputAdapter {
    private static final Logger logger = LoggerFactory.getLogger(FileInputAdapter.class);
    private static final Charset charset = StandardCharsets.UTF_8;
    private static final byte LINE_FEED = 0x0A;
    private static final byte CARRIAGE_RETURN = 0x0D;
    private static final int MIN_BUFFER_SIZE = 4096;
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;
    private final Path filePath;
    private long currentPosition;
    private final boolean isFromBeginning;
    private ByteBuffer buffer;
    private final Queue<String> lines = new LinkedList<>();
    private FileChannel srcFileChannel;
    private final String hostName;
    private int fileOpenRetryCount = 0;
    private static final int MAX_FILE_OPEN_RETRY = 12; // 1분 동안 재시도

    public FileInputAdapter(Map<String, String> obj) throws IOException {
        super(obj);
        String pathStr = obj.get("path");
        if (pathStr == null || pathStr.isEmpty()) {
            throw new IllegalArgumentException("File path must not be null or empty.");
        }
        this.filePath = Paths.get(pathStr);
        if (Files.exists(filePath) && Files.isDirectory(filePath)) {
            throw new IllegalArgumentException("File path must not be a directory: " + filePath);
        }
        this.isFromBeginning = Boolean.parseBoolean(obj.getOrDefault("isFromBeginning", "false"));
        this.hostName = java.net.InetAddress.getLocalHost().getHostName();
        // 버퍼 크기 동적 결정 (파일 크기 기반, 최소/최대 적용)
        long fileSize = Files.exists(filePath) ? Files.size(filePath) : MIN_BUFFER_SIZE;
        int bufferSize = (int)Math.max(MIN_BUFFER_SIZE, Math.min(fileSize, MAX_BUFFER_SIZE));
        this.buffer = ByteBuffer.allocateDirect(bufferSize);
        logger.info("File Input Adapter initialized for path: {}. Reading from beginning: {}. Host: {}. Buffer size: {}", filePath, isFromBeginning, hostName, bufferSize);
    }

    /**
     * Opens the file channel and sets the initial position.
     * This method is called lazily when needed.
     */
    private void openFile() {
        while (fileOpenRetryCount < MAX_FILE_OPEN_RETRY) {
            try {
                if (!Files.exists(filePath)) {
                    logger.error("File not found: {}. Waiting for file to be created...", filePath);
                    ThreadUtil.sleep(5000);
                    fileOpenRetryCount++;
                    continue;
                }
                if (Files.isDirectory(filePath)) {
                    throw new IOException("File path is a directory: " + filePath);
                }
                this.srcFileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
                File file = filePath.toFile();
                if (isFromBeginning) {
                    this.currentPosition = 0;
                } else {
                    this.currentPosition = file.length();
                }
                logger.info("File channel opened for {}. Initial position set to {}", filePath, currentPosition);
                fileOpenRetryCount = 0;
                return;
            } catch (IOException e) {
                logger.error("Failed to open file channel for {}: {}", filePath, e.getMessage());
                ThreadUtil.sleep(5000);
                fileOpenRetryCount++;
            }
        }
        throw new IllegalStateException("File could not be opened after multiple retries: " + filePath);
    }

    /**
     * Reads from the file into the buffer, extracts all complete lines, and adds them to the lines queue.
     * Handles partial lines by compacting the buffer.
     * @return The number of bytes read from the channel.
     * @throws IOException If an I/O error occurs.
     */
    private long readAndBufferLines() throws IOException {
        long bytesRead = srcFileChannel.read(buffer);
        if (bytesRead <= 0) {
            return bytesRead;
        }
        buffer.flip();
        int startOfLine = 0;
        while (buffer.hasRemaining()) {
            int pos = buffer.position();
            byte currentByte = buffer.get();
            if (currentByte == LINE_FEED) {
                int lineLength = pos - startOfLine;
                if (lineLength > 0) {
                    byte[] lineBytes = new byte[lineLength];
                    int oldPos = buffer.position();
                    buffer.position(startOfLine);
                    buffer.get(lineBytes);
                    buffer.position(oldPos);
                    String line = new String(lineBytes, charset);
                    if (!line.isEmpty() && line.charAt(line.length() - 1) == CARRIAGE_RETURN) {
                        line = line.substring(0, line.length() - 1);
                    }
                    lines.add(line);
                }
                startOfLine = pos + 1;
            }
        }
        buffer.position(startOfLine);
        buffer.compact();
        return bytesRead;
    }

    @Override
    public LogEvent run() {
        if (!lines.isEmpty()) {
            return createLogEvent(lines.poll());
        }
        if (srcFileChannel == null) {
            try {
                openFile();
            } catch (Exception e) {
                logger.error("File open failed: {}", e.getMessage());
                return null;
            }
        }
        if (srcFileChannel == null) {
            return null;
        }
        try {
            long fileLength = Files.size(filePath);
            if (fileLength < currentPosition) {
                logger.info("Log rotation detected. Re-opening file and resetting position.");
                close();
                openFile();
                if (srcFileChannel == null) {
                    return null;
                }
                fileLength = Files.size(filePath);
                currentPosition = 0;
            }
            if (fileLength <= currentPosition) {
                return null;
            }
            srcFileChannel.position(currentPosition);
            long bytesRead = readAndBufferLines();
            if (bytesRead > 0) {
                currentPosition += bytesRead;
                if (!lines.isEmpty()) {
                    return createLogEvent(lines.poll());
                }
            }
        } catch (IOException e) {
            logger.error("An error occurred while reading the file: {}", e.getMessage());
            ThreadUtil.sleep(5000);
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (srcFileChannel != null && srcFileChannel.isOpen()) {
            srcFileChannel.close();
        }
        srcFileChannel = null;
        currentPosition = 0;
        buffer.clear();
        lines.clear();
        logger.info("File Input Adapter closed.");
    }
}
