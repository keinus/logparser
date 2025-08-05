package org.keinus.logparser.dispatch;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.schema.FilteredMessage;
import org.keinus.logparser.interfaces.OutputAdapter;

@Slf4j
public class OutputAdapterProcedure implements Runnable {
    private Gson gson = new Gson();

    private BlockingQueue<FilteredMessage> outputMessageQueue = new LinkedBlockingQueue<>(1000);
    
    private OutputAdapter outputAdapter;

    private boolean isRunning = true;

    public OutputAdapterProcedure(OutputAdapter outputAdapter) {
        this.outputAdapter = outputAdapter;
    }

    public void enqueue(FilteredMessage message) {
        try {
            this.outputMessageQueue.put(message);
        } catch (InterruptedException e) {
            log.error("Queue is full.");
        }
    }

    @Override
    public void run() {
        boolean addOriginText = outputAdapter.getAddOriginText();
        while(isRunning) {
            FilteredMessage message;
            try {
                message = outputMessageQueue.take();
            } catch (InterruptedException e) {
                continue;
            }
            
            if(message != null) {
                var msg = message.getMsg();
                if(addOriginText)
                    msg.put("origin_text", message.getOriginText());
                String jsonString = gson.toJson(msg);
                outputAdapter.send(msg, jsonString);
            }
        }
    }

    public void close() {
        try {
            outputAdapter.close();
            this.isRunning = false;
            outputMessageQueue.clear();
        } catch (IOException e) {
            log.error("Error: {}", e.getMessage());
        }
    }
}
