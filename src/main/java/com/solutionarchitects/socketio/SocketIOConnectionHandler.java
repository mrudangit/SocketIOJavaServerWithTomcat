package com.solutionarchitects.socketio;

import com.google.gson.Gson;
import com.google.gson.internal.StringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by e211303 on 5/25/2016.
 */
public class SocketIOConnectionHandler {

    private final WebSocketSession webSocketSession;
    private final String nameSpace;
    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private final String sessionid;

    public SocketIOConnectionHandler(WebSocketSession webSocketSession , String sessionId, String nameSpace){
        this.webSocketSession = webSocketSession;
        this.sessionid= sessionId;
        this.nameSpace = nameSpace;

        logger.debug("SocketIONamespaceConnectionHandler Created Namespace : {} SessionId : {}", nameSpace,sessionId);
    }


    public void receiveEvent(String eventName, StringMap payload){

        logger.info("Event Received Namespace : {}  EventName {} Payload {}", nameSpace,eventName,payload);

    }

    public void sendEvent(String eventName, Object payload){

        ArrayList eventPayload = new ArrayList();

        eventPayload.add(eventName);
        eventPayload.add(payload);

        Gson gson = new Gson();

        String json = gson.toJson(eventPayload);

        String eventMessage = String.format("%d%d%s,%s", 4, 2, nameSpace, json);

        try {
            webSocketSession.sendMessage(new TextMessage(eventMessage));
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
