package com.solutionarchitects;

import com.google.gson.Gson;
import com.google.gson.internal.StringMap;
import com.solutionarchitects.protocol.SocketIOHandshake;
import com.solutionarchitects.protocol.SocketIOMessageType;
import com.solutionarchitects.protocol.SocketIOPacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class SocketIOWebSocketProtocolHandler implements org.springframework.web.socket.WebSocketHandler {


    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final ConcurrentHashMap<WebSocketSession, String> webSocketSessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SocketIOConnectionHandler> socketIOConnectionHandlerConcurrentHashMap = new ConcurrentHashMap<>();




    @Resource
    private SocketIOConfig socketIOConfig;


    @PostConstruct
    private void afterInit(){
        logger.info("SocketIO Configuration : {} ", socketIOConfig);
    }



    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        logger.info("New WebSocket Connection Established : Session ID = {}", session);


        Gson gson = new Gson();

        String sessionId = UUID.randomUUID().toString();
        SocketIOHandshake h = new SocketIOHandshake();
        h.sid= sessionId;
        h.upgrades=new String[]{};
        h.pingInterval=socketIOConfig.pingInterval;
        h.pingTimeout=socketIOConfig.pingTimeout;

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

        SocketIOPacketType packetType= ParsePacketType(packet);

        switch (packetType){

            case Open:
                break;
            case Close:
                break;
            case Ping:
                HandlePingPong(session);
                break;
            case Pong:
                break;
            case Message:
                HandleMessage(session,packet);
                break;
            case Upgrade:
                break;
            case NoOp:
                logger.info("NoOp");
            default:
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

        SocketIOMessageType messageType = SocketIOMessageType.parseMessageType(GetOpCode(data.charAt(1)));

        switch (messageType){



            case Connect:
                HandleConnect( session, data);
                break;
            case DisConnect:
                break;
            case Event:
                HandleEventMessage(session,data);
                break;
            case Ack:
                break;
            case Error:
                break;
            case BinaryEvent:
                break;
        }
    }

    private int GetOpCode(char c){
        return c-48;
    }

    private SocketIOPacketType ParsePacketType(String data){


        int c = GetOpCode(data.charAt(0));

        return SocketIOPacketType.parsePacketType(c);

    }



    private String DecodeConnectMessage(String message){

        int index = 0;

        SocketIOPacketType packetType= SocketIOPacketType.parsePacketType(GetOpCode(message.charAt(index)));
        ++index;

        SocketIOPacketType messageType = SocketIOPacketType.parsePacketType(GetOpCode(message.charAt(index)));
        index++;

        String nameSpace = null;

        if('/' == message.charAt(index)) {


            nameSpace=message.substring(index);

        }




        return nameSpace;

    }


    private void DecodeEventMessage(WebSocketSession webSocketSession, String message){

        int index = 0;

        SocketIOPacketType packetType= SocketIOPacketType.parsePacketType(GetOpCode(message.charAt(index)));
        ++index;

        SocketIOMessageType messageType = SocketIOMessageType.parseMessageType(GetOpCode(message.charAt(index)));
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

        logger.info("SocketIOPacketType : [{}] MessageType : [{}] Namespace : [{}] AckId : [{}] JSON : {}", packetType,messageType,nameSpace,ackId,jsonData);

        Gson gson = new Gson();

        ArrayList arrayList = gson.fromJson(jsonData,ArrayList.class);

        String eventName = (String) arrayList.get(0);
        StringMap eventData = (StringMap) arrayList.get(1);

        logger.info("Event Name = [{}]  Data : {} ",eventName,eventData);



        String sessionId = webSocketSessionMap.get(webSocketSession);

        String sessionNamespaceKey = String.format("%s#%s",sessionId,nameSpace);

        SocketIOConnectionHandler socketIOConnectionHandler = socketIOConnectionHandlerConcurrentHashMap.get(sessionNamespaceKey);

        socketIOConnectionHandler.receiveEvent(eventName,eventData);


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
