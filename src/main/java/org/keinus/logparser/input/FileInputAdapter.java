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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.interfaces.InputAdapter;


public class FileInputAdapter  implements InputAdapter {
	private static final Logger logger = LoggerFactory.getLogger( FileInputAdapter.class );
	private String type = "";

	Charset charset = StandardCharsets.UTF_8;
	static final byte LINE_FEED = 0x0A;

	Path filePath;
	long currentPosition;
	long interval = 1000;

	ByteBuffer buffer = ByteBuffer.allocateDirect(1024*1024);
	Queue<String> lines = new LinkedList<>();
	FileChannel srcFileChannel;

	long cdate;
	boolean isFromBeginning = false;

	@Override
	public void init(Map<String, String> obj) {
		this.type = obj.get("messagetype");
		this.filePath = Paths.get(obj.get("path"));
		this.isFromBeginning = Boolean.parseBoolean(obj.get("isFromBeginning"));
		
		File file = filePath.toFile();
		if(!file.exists()) {
			logger.error("FileInputAdapter: {} file not found.", filePath);
			return;
		}
		if(isFromBeginning)
			this.currentPosition = 0;
		else
			this.currentPosition = file.length();
		
		this.open();
		
		logger.info("File Input Adapter Init.");
	}

	private void open() {
		try {
			this.srcFileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
			this.cdate = getFileCreationTime(filePath);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	private long getFileCreationTime(Path filePath) {
		try {
			BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
			return attr.creationTime().toMillis();
		} catch (IOException e) {
			logger.error(e.getMessage());
            return 0;
		}
	}

	private String readFile() throws IOException {
		String line = "";

		long readBytes = srcFileChannel.read(buffer);
		if(readBytes < 1) {
			return line;
		}
		final int contentLength = buffer.position();
		int newLinePosition = buffer.position();

		boolean hasLineFeed = false;
		boolean needCompact = true;
		while (newLinePosition > 0) {
			if (buffer.get(--newLinePosition) == LINE_FEED) {
				if (newLinePosition + 1 == buffer.capacity()) {
					needCompact = false;
				}
				buffer.position(0); 
				buffer.limit(++newLinePosition);
				line = charset.decode(buffer).toString();
				hasLineFeed = true;
				break;
			}
		}

		currentPosition += readBytes;

		if (!hasLineFeed) {
			return "";
		}

		if (needCompact) {
			buffer.limit(contentLength);
			buffer.compact();
		} else {
			buffer.clear();
		}
		return line;
	}

	@Override
	public String run() {
		if(!lines.isEmpty()) {
			return lines.poll();
		} 
		
		if(cdate != getFileCreationTime(filePath)) {
			currentPosition = 0;
            open();
		}

		if(filePath.toFile().length() == currentPosition) {
			return null;
		}

		try {
			srcFileChannel.position(currentPosition);
			String line = readFile();
			if(line.length() > 0) {
				String[] lineSplit = line.split(System.getProperty("line.separator"));
				Collections.addAll(lines, lineSplit);
				return lines.poll();
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
		return null;
    }
    
	@Override
	public void close() throws IOException {
		srcFileChannel.close();
		filePath = null;
		currentPosition = 0;
		buffer.clear();
	}

	@Override
	public String getHost() {
		return "localhost";
	}

	@Override
	public String getType() {
		return this.type;
	}
}
