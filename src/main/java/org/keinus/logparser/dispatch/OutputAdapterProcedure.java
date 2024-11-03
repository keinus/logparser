package org.keinus.logparser.dispatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import org.keinus.logparser.schema.Message;
import org.keinus.logparser.util.ThreadUtil;
import org.keinus.logparser.interfaces.OutputAdapter;

public class OutputAdapterProcedure implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutputAdapterProcedure.class);
    private Gson gson = new Gson();

    private BlockingQueue<Message> outputMessageQueue = new LinkedBlockingQueue<>(100);
    
    private List<OutputAdapter> mOutputAdapterList = new ArrayList<>();
    private String outputType = "";
    private boolean addOriginText = false;

    public OutputAdapterProcedure(String outputType, boolean addOriginText) {
        this.outputType = outputType;
        this.addOriginText = addOriginText;
    }

    public String getType() {
        return outputType;
    }

    public void enqueue(Message message) {
        while(true) {
            try {
                this.outputMessageQueue.add(message);
                break;
            } catch(IllegalStateException e) { 
                ThreadUtil.sleep(1); 
            }
        }
    }

    public void addOutputAdapter(OutputAdapter adapter) {
        mOutputAdapterList.add(adapter);
        LOGGER.info("New OutputAdapter is ready. {}", adapter.getClass().getSimpleName());
    }

    @Override
    public void run() {
        LOGGER.info("Output process started. {}", this.outputType);

        while(true) {
            Message message = null;
            message = outputMessageQueue.poll();
            
            if(message != null) {
                var msg = message.getMsg();
                if(this.addOriginText)
                    msg.put("origin_text", message.getOriginText());
                String jsonString = gson.toJson(msg);
                for(var adapter : mOutputAdapterList) {
                    adapter.send(msg, jsonString);
                }
            } else {
                for(var adapter : mOutputAdapterList) {
                    adapter.flush();
                }
                ThreadUtil.sleep(10); 
            }
        }
    }

    public void close() {
        for(var adapter : mOutputAdapterList) {
            try {
                adapter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
