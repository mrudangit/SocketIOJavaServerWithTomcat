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

import java.util.UUID;

public class WebSocketHandler implements org.springframework.web.socket.WebSocketHandler {



    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass().getName());



    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {


        LOGGER.info("WebSocket Connection Initialized Session :{} ",webSocketSession);

        Gson gson = new Gson();

        Handshake h = new Handshake();
        h.sid= UUID.randomUUID().toString();
        h.upgrades = new String[]{};
        h.pingInterval=25000;
        h.pingTimeout=60000;


        TextMessage t = new TextMessage(String.format("%d%s",0,gson.toJson(h)));

        webSocketSession.sendMessage(t);
        t = new TextMessage(String.format("%d%d",4,0));

        webSocketSession.sendMessage(t);

    }

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage) throws Exception {

        LOGGER.info("Message Received on Session : {} Message : {}",webSocketSession,webSocketMessage);

        String payload = (String)webSocketMessage.getPayload();

        try{

            HandlePacket(webSocketSession,payload);

        }catch(Exception exp){
            LOGGER.error("Error handling message",exp);
        }

    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) throws Exception {

        LOGGER.info("Transport Error : Reason :{} ", throwable.getMessage());

    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) throws Exception {


        LOGGER.info("WebSocket Connection closed session : {} Close Status : {}",webSocketSession,closeStatus);

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }




    private void HandlePacket(WebSocketSession webSocketSession, String packet)throws java.io.IOException {

        PacketType packetType = ParsePacketType(packet);

        switch (packetType){

            case Open:
                break;
            case Close:
                break;
            case Ping:
                HandlePing(webSocketSession);
                break;
            case Pong:
                break;
            case Message:
                break;
            case Upgrade:
                break;
            case NoOp:
                break;
            case Connect:
                break;
            case DisConnect:
                break;
            case Event:
                break;
            case Ack:
                break;
            case Error:
                break;
            case BinaryEvent:
                break;
        }




    }



    private void HandlePing(WebSocketSession webSocketSession)throws java.io.IOException{


        LOGGER.info("Received Ping ... sending Pong");

        TextMessage t = new TextMessage(String.format("%d",3));

        webSocketSession.sendMessage(t);

    }



    private int GetOpCode(char c){
        return c-48;
    }


    private PacketType ParsePacketType(String data){

        int opCode = GetOpCode(data.charAt(0));

        return PacketType.parsePacketType(opCode);

    }



}
