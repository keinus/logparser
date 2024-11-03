package org.keinus.logparser.dispatch;

import java.io.IOException;
import java.util.Date;
import org.keinus.logparser.schema.Message;
import org.keinus.logparser.interfaces.InputAdapter;


public class InputAdapterProcedure {
    private InputAdapter mInputAdapter;

    public InputAdapterProcedure(InputAdapter adapter) {
        mInputAdapter = adapter;
    }

    public Message process() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            mInputAdapter.close();
        } else {
            String originMsg = mInputAdapter.run();
            if(originMsg != null) {
                Message retval = new Message();
                retval.put("@timestamp", new Date());
                retval.put("host", mInputAdapter.getHost());
                retval.setOriginText(originMsg);
                retval.setType(mInputAdapter.getType());
                return retval;
            }
        }
        return null;
    }
}
