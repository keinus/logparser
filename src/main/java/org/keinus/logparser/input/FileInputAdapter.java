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
import org.keinus.logparser.interfaces.InputAdapter;
import org.keinus.logparser.schema.Message;
import org.keinus.logparser.util.ThreadUtil;

public class FileInputAdapter extends InputAdapter {
    private static final Logger logger = LoggerFactory.getLogger(FileInputAdapter.class);

    // UTF-8 Charset for decoding bytes to string
    private final Charset charset = StandardCharsets.UTF_8;
    // Line feed byte to identify line breaks
    private static final byte LINE_FEED = 0x0A;
    private static final byte CARRIAGE_RETURN = 0x0D;

    private final Path filePath;
    private long currentPosition;
    private final boolean isFromBeginning;

    // Direct byte buffer for efficient I/O operations
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
    // Queue to hold lines read from the file
    private final Queue<String> lines = new LinkedList<>();
    // The file channel for reading
    private FileChannel srcFileChannel;

    public FileInputAdapter(Map<String, String> obj) throws IOException {
        super(obj);
        this.filePath = Paths.get(obj.get("path"));
        this.isFromBeginning = Boolean.parseBoolean(obj.get("isFromBeginning"));

        logger.info("File Input Adapter initialized for path: {}. Reading from beginning: {}.", filePath, isFromBeginning);
    }

    /**
     * Opens the file channel and sets the initial position.
     * This method is called lazily when needed.
     */
    private void openFile() {
        try {
            if (!Files.exists(filePath)) {
                logger.error("File not found: {}. Waiting for file to be created...", filePath);
                // Wait for a short period before retrying
                ThreadUtil.sleep(5000);
                return;
            }
            this.srcFileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
            File file = filePath.toFile();
            if (isFromBeginning) {
                this.currentPosition = 0;
            } else {
                this.currentPosition = file.length();
            }
            logger.info("File channel opened for {}. Initial position set to {}", filePath, currentPosition);
        } catch (IOException e) {
            logger.error("Failed to open file channel for {}: {}", filePath, e.getMessage());
        }
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

        // Set the buffer limit to the new data and get ready to read from the start of the new data
        buffer.flip();

        int startOfLine = 0;
        int i = 0;
        while (buffer.hasRemaining()) {
            byte currentByte = buffer.get();
            i = buffer.position() - 1;

            if (currentByte == LINE_FEED) {
                // Found a complete line
                int lineLength = i - startOfLine;
                byte[] lineBytes = new byte[lineLength];
                // Read the line bytes from the buffer, not affecting the main buffer position
                ByteBuffer lineBuffer = (ByteBuffer) buffer.duplicate().position(startOfLine).limit(i);
                lineBuffer.get(lineBytes);

                String line = new String(lineBytes, charset);
                // Handle potential carriage return byte (\r) at the end of the line
                if (!line.isEmpty() && line.charAt(line.length() - 1) == CARRIAGE_RETURN) {
                    line = line.substring(0, line.length() - 1);
                }
                
                lines.add(line);
                startOfLine = i + 1;
            }
        }

        // Handle the partial line at the end of the buffer
        buffer.position(startOfLine);
        buffer.compact();
        
        return bytesRead;
    }

    @Override
    public Message run() {
        // First, check if there are any lines already in the queue
        if (!lines.isEmpty()) {
            return new Message(lines.poll(), "localhost");
        }

        // If the file channel is not open, try to open it
        if (srcFileChannel == null) {
            openFile();
        }
        
        // If the file channel is still null (e.g., file not found), return
        if (srcFileChannel == null) {
            return null;
        }

        try {
            long fileLength = Files.size(filePath);

            // Check for log rotation (file length is smaller than current position)
            if (fileLength < currentPosition) {
                logger.info("Log rotation detected. Re-opening file and resetting position.");
                close();
                openFile();
                if (srcFileChannel == null) { // Re-check in case open failed
                    return null;
                }
                fileLength = Files.size(filePath); // Get the new file size
            }

            // If there is no new data to read, return null
            if (fileLength <= currentPosition) {
                return null;
            }

            // Position the channel to the last read position
            srcFileChannel.position(currentPosition);

            // Read data and buffer all complete lines
            long bytesRead = readAndBufferLines();
            if (bytesRead > 0) {
                currentPosition += bytesRead;
                // After buffering new lines, try to return one
                if (!lines.isEmpty()) {
                    return new Message(lines.poll(), "localhost");
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
