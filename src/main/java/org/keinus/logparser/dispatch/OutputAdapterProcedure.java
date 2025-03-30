package org.keinus.logparser.dispatch;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.Gson;

import org.keinus.logparser.schema.FilteredMessage;
import org.keinus.logparser.util.ThreadUtil;
import org.keinus.logparser.interfaces.OutputAdapter;

public class OutputAdapterProcedure implements Runnable {
    private Gson gson = new Gson();

    private BlockingQueue<FilteredMessage> outputMessageQueue = new LinkedBlockingQueue<>(100);
    
    private OutputAdapter outputAdapter;

    private boolean isRunning = true;

    public OutputAdapterProcedure(OutputAdapter outputAdapter) {
        this.outputAdapter = outputAdapter;
    }

    public void enqueue(FilteredMessage message) {
        while(!this.outputMessageQueue.offer(message)) {
            ThreadUtil.sleep(100);
        }
    }

    @Override
    public void run() {
        boolean addOriginText = outputAdapter.getAddOriginText();
        while(isRunning) {
            FilteredMessage message = outputMessageQueue.poll();
            
            if(message != null) {
                var msg = message.getMsg();
                if(addOriginText)
                    msg.put("origin_text", message.getOriginText());
                String jsonString = gson.toJson(msg);
                outputAdapter.send(msg, jsonString);
            } else {
                outputAdapter.flush();
            }
        }
    }

    public void close() {
        try {
            outputAdapter.close();
            this.isRunning = false;
            outputMessageQueue.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
