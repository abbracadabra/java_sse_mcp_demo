package com.hellobike.cooltest.repeater.console.web.controller;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/mcp")
@Slf4j
public class McpController {
    private Map<String, SseEmitter> SESSIONS = new HashMap<>();

    @GetMapping("/connect")
    public SseEmitter connect() throws IOException {
        final SseEmitter sse = new SseEmitter(TimeUnit.MINUTES.toMillis(2));
        String uuid = UUID.randomUUID().toString().replaceAll("-","");
        SESSIONS.put(uuid, sse);
        sse.send(SseEmitter.event().name("endpoint").data("/mcp/message?sessionId="+uuid));
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000);
                    sse.send(SseEmitter.event().name("heartbeat").data(System.currentTimeMillis()));
                } catch (Exception e) {
                    sse.complete();
                    SESSIONS.remove(uuid);
                    return;
                }
            }
        }).start();
        return sse;
    }

    @RequestMapping(value = "message", method = RequestMethod.POST)
    public JSONObject message(@RequestBody JSONRpc body, @RequestParam("sessionId") String sessionId) throws IOException {
        SseEmitter sessionSse = SESSIONS.get(sessionId);
        if (sessionSse == null) {
            return new JSONObject().fluentPut("error", "No SSE session with that sessionId");
        }
        new Thread(()->{
            try {
                String res = "";
                if (body.getMethod().equalsIgnoreCase("initialize")) {
                    String ver = (String) body.getParams().get("protocolVersion");
                    res = StrUtil.format("{\"jsonrpc\":\"2.0\",\"id\":{},\"result\":{\"protocolVersion\":\"{}\",\"capabilities\":{\"logging\":{},\"resources\":{\"subscribe\":true,\"listChanged\":true},\"prompts\":{\"listChanged\":true},\"tools\":{\"listChanged\":true}},\"serverInfo\":{\"name\":\"ExampleServer\",\"version\":\"1.0.0\"}}}", body.getId(), ver);
                }
                if (body.getMethod().equalsIgnoreCase("tools/list")) {
                    res = StrUtil.format("{\"jsonrpc\":\"2.0\",\"id\":{},\"result\":{\"tools\":[{\"name\":\"get_weather\",\"description\":\"Get current weather information for a location\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\",\"description\":\"City name or zip code\"}},\"required\":[\"location\"]}}]}}", body.getId());
                }
                if (body.getMethod().equalsIgnoreCase("tools/call")) {
                     String toolName = (String) body.getParams().get("name");
                     Map<String,Object> args = (Map<String,Object>) body.getParams().get("arguments");
                    if (toolName.equalsIgnoreCase("get_weather")) {
                        res = StrUtil.format("{\"jsonrpc\":\"2.0\",\"id\":{},\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}}",body.getId(),"temperature is 23 celsius, sunny");
                    } else  {
                        res = StrUtil.format("{\"jsonrpc\":\"2.0\",\"id\":{},\"error\":{\"code\":-32601,\"message\":\"No such tool {}\"}}",body.getId(),toolName);
                    }
                }
                sessionSse.send(SseEmitter.event().name("message").data(res));
            } catch (Exception e) {
                log.error("message err",e);
            }
        }).start();
        return JSONObject.parseObject(StrUtil.format("{\"jsonrpc\":\"2.0\",\"id\":{},\"result\":{\"ack\":\"Received {}\"}}",body.getId(),body.getMethod()));
    }

    @Data
    public static class JSONRpc {
        private Map<String, Object> params;
        private String method;
        private String id;
    }
}
