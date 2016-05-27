package com.solutionarchitects;

import com.google.gson.Gson;
import com.solutionarchitects.protocol.Handshake;
import com.solutionarchitects.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketHandler implements org.springframework.web.socket.WebSocketHandler {


    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    ConcurrentHashMap<WebSocketSession, String> webSocketSessionMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, SocketIOConnectionHandler> socketIOConnectionHandlerConcurrentHashMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        logger.info("New WebSocket Connection Established : Session ID = {}", session);


        Gson gson = new Gson();

        String sessionId = UUID.randomUUID().toString();
        Handshake h = new Handshake();
        h.sid= sessionId;
        h.upgrades=new String[]{};
        h.pingInterval=25000;
        h.pingTimeout=60000;

        TextMessage msg = new TextMessage(String.format("%d%s",0,gson.toJson(h)));

        session.sendMessage(msg);

        msg =new TextMessage(String.format("%d%d",4,0));

        session.sendMessage(msg);

        webSocketSessionMap.put(session,sessionId);



    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {

        logger.info("Message = {} Session ID : {}", message, session);

        String payload = (String)message.getPayload();

        try{

            HandlePacket(session,payload);
        }catch (Exception exp){
            logger.error("Error handlign packet", exp);
        }

    }


    private void HandlePacket(WebSocketSession session , String packet) throws IOException {

        PacketType packetType= ParsePacketType(packet);

        switch (packetType){

            case OPEN:
                break;
            case CLOSE:
                break;
            case PING:
                HandlePingPong(session);
                break;
            case PONG:
                break;
            case MESSAGE:
                HandleMessage(session,packet);
                break;
            case UPGRADE:
                break;
            case NOOP:
                break;
            case CONNECT:
                break;
            case DISCONNECT:
                break;
            case EVENT:
                break;
            case ACK:
                break;
            case ERROR:
                break;
            case BINARY_EVENT:
                break;
        }
    }

    private void HandlePingPong(WebSocketSession session) throws IOException {

        logger.info("Received Ping ..... Sending Pong");

        TextMessage t = new TextMessage(String.format("%d", 3));

        session.sendMessage(t);
    }

    private void HandleEventMessage(WebSocketSession session ,String data){

        try {
            DecodeEventMessage(session,data);
        }catch (Exception exp){

            logger.error("Error decoding data", exp);
        }

    }


    private void HandleConnect(WebSocketSession session, String data) throws IOException {


        logger.info("Received Connected {}", data);
        String nameSpace = DecodeConnectMessage(data);

        TextMessage msg = new TextMessage(String.format("%d%d%s", 4, 0, nameSpace));

        session.sendMessage(msg);


        logger.info("Connected Namespace : {}",nameSpace);

        String sessionId = webSocketSessionMap.get(session);
        String sessionIdNamespaceKey= String.format("%s#%s",sessionId,nameSpace);


        SocketIOConnectionHandler socketIOConnectionHandler = new SocketIOConnectionHandler(session,sessionId,nameSpace);

        socketIOConnectionHandlerConcurrentHashMap.put(sessionIdNamespaceKey,socketIOConnectionHandler);





    }

    private void HandleOpen(WebSocketSession session, String data) throws IOException {

        String nameSpace = data.substring(2);

        TextMessage msg = new TextMessage(String.format("%d%d%s", 4, 0, nameSpace));
        session.sendMessage(msg);


    }


    private void HandleMessage(WebSocketSession session, String data) throws IOException {

        PacketType messageType = PacketType.valueOfInner(GetOpCode(data.charAt(1)));

        switch (messageType){


            case OPEN:
                HandleOpen( session, data);
            case NOOP:
                break;
            case CONNECT:
                HandleConnect( session, data);
                break;
            case DISCONNECT:
                break;
            case EVENT:
                HandleEventMessage(session,data);
                break;
            case ACK:
                break;
            case ERROR:
                break;
            case BINARY_EVENT:
                break;
        }
    }

    private int GetOpCode(char c){
        return c-48;
    }

    private PacketType ParsePacketType(String data){


        int c = GetOpCode(data.charAt(0));

        return PacketType.valueOf(c);

    }



    private String DecodeConnectMessage(String message){

        int index = 0;

        PacketType packetType= PacketType.valueOf(GetOpCode(message.charAt(index)));
        ++index;

        PacketType messageType = PacketType.valueOfInner(GetOpCode(message.charAt(index)));
        index++;

        String nameSpace = null;

        if('/' == message.charAt(index)) {


            nameSpace=message.substring(index);

        }




        return nameSpace;

    }


    private void DecodeEventMessage(WebSocketSession webSocketSession, String message){

        int index = 0;

        PacketType packetType= PacketType.valueOf(GetOpCode(message.charAt(index)));
        ++index;

        PacketType messageType = PacketType.valueOfInner(GetOpCode(message.charAt(index)));
        index++;

        StringBuilder sb = new StringBuilder();

        if('/' == message.charAt(index)) {

            while(message.charAt(index) != ','){
                sb.append(message.charAt(index++));
            }

            index++;

        }

        StringBuilder idSb = new StringBuilder();

        while(Character.isDigit(message.charAt(index))){
            idSb.append(message.charAt(index));
            index++;
        }

        String ackId = idSb.toString();

        String nameSpace = sb.toString();

        String jsonData = message.substring(index);

        logger.info("PacketType : [{}] MessageType : [{}] Namespace : [{}] AckId : [{}] JSON : {}", packetType,messageType,nameSpace,ackId,jsonData);

        Gson gson = new Gson();

        ArrayList arrayList = gson.fromJson(jsonData,ArrayList.class);

        String eventName = (String) arrayList.get(0);
        StringMap eventData = (StringMap) arrayList.get(1);

        logger.info("Event Name = [{}]  Data : {} ",eventName,eventData);



        String sessionId = webSocketSessionMap.get(webSocketSession);

        String sessionNamespaceKey = String.format("%s#%s",sessionId,nameSpace);

        SocketIOConnectionHandler socketIOConnectionHandler = socketIOConnectionHandlerConcurrentHashMap.get(sessionNamespaceKey);

        socketIOConnectionHandler.eventReceived(eventName,eventData);


    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

        logger.warn("Transport Session ID  : {}  Error : {}",session.getId(),exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {

        logger.info("Connection Closed =========================== {} Session ID : {} ",closeStatus, session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }


}
