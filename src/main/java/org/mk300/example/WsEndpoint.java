/*
 * Copyright 2016 Masazumi Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mk300.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author mkobayas@redhat.com
 *
 */
@ServerEndpoint("/ws")
public class WsEndpoint {
    private static final Logger log = Logger.getLogger(WsEndpoint.class);

    private static Queue<Session> queue = new ConcurrentLinkedQueue<>();
    private static final String[] parts = { "助詞", "名詞", "動詞", "助動詞", "補助記号", "代名詞", "副詞", "形容詞", "接尾辞", "連体詞", "形状詞",
            "接続詞", "接頭辞", "記号", "感動詞" };

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        log.infof("open:%s,%s", session, config);
        queue.add(session);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        queue.remove(session);
        log.infof("close:%s,%s", session, closeReason);
    }

    @OnError
    public void error(Session session, Throwable t) {
        queue.remove(session);
        log.infof("error:%s,%s", session, t.getMessage());
    }

    public static void send(Map<String, Long> wordCount) {
        try {
            log.infof("send:");

            List<List<Object>> chartFormat = new ArrayList<>();
            for(String part : parts) {
                List<Object> element = new ArrayList<>();
                element.add(part);
                if(wordCount.containsKey(part)) {
                    element.add(wordCount.get(part));
                } else {
                    element.add(0);
                }
                chartFormat.add(element);
            }
            String jsonStr = new ObjectMapper().writeValueAsString(chartFormat);
            for (Session session : queue) {
                if (session.isOpen()) session.getBasicRemote().sendText(jsonStr);
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
