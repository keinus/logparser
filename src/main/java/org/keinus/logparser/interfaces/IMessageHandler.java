package org.keinus.logparser.interfaces;

import org.keinus.logparser.schema.Message;

public interface IMessageHandler extends Runnable {
	public int handleMessage(Message message);
}
