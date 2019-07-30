package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * BigpipeWriterClientImpl 管理类
 *
 * @author Zheng Xiaoxiong (zhengxiaoxiong@baidu.com)
 */
@Component
@Slf4j
@Lazy
public class BigpipeWriterManager implements InitializingBean {

    private final BigpipeConfig config;

    private final BigpipeClientProvider bigpipeClientProvider;

    private final HashMap<Integer, BlockingDeque<BigpipeWriterClient>> writersList;

    private HashMap<String, List<Integer>> messagePipeletMap;

    @Autowired
    public BigpipeWriterManager(BigpipeConfig config, @Qualifier("bigpipeClientProvider") BigpipeClientProvider bigpipeClientProvider) {
        this.config = config;
        this.bigpipeClientProvider = bigpipeClientProvider;
        writersList = new HashMap<>();
    }

    @Override
    public void afterPropertiesSet() {
        initMessageWriterMap();
    }


    private void initMessageWriterMap() {
        if (StringUtils.isEmpty(config.getMsgPipeletMap())) {
            log.error("config bigpipe.msg.pipelet.map is empty.");
            return;
        }

        messagePipeletMap = new HashMap<>();
        Map<String, String> messagePipeletMapEntrys = Splitter.on("|")
                .withKeyValueSeparator(":").split(config.getMsgPipeletMap());

        for (Map.Entry<String, String> entry : messagePipeletMapEntrys.entrySet()) {
            String channel = entry.getKey();
            List<Integer> messagePipelets = new ArrayList<>();

            Iterable<String> pipelets = Splitter.on(",").split(entry.getValue());
            for (String pipelet : pipelets) {
                Integer pipeletId = Integer.parseInt(pipelet);
                messagePipelets.add(pipeletId);
            }

            messagePipeletMap.put(channel, messagePipelets);
        }

        log.info("initMessageWriterMap pipelet_map={}", messagePipeletMap);
    }

    public boolean sendMessage(String channel, String message) {
        List<Integer> pipelets;
        if (messagePipeletMap.containsKey(channel)) {
            pipelets = messagePipeletMap.get(channel);
        } else if (messagePipeletMap.containsKey("DEFAULT")) {
            pipelets = messagePipeletMap.get("DEFAULT");
        } else {
            log.debug("channel={} not found, pass", channel);
            return false;
        }
        return sendMessage(pipelets, message);
    }

    public boolean sendMessage(Integer pipeletIndex, String message) {
        BlockingDeque<BigpipeWriterClient> writers;
        try {
            writers = getWriter(pipeletIndex);
        } catch (Exception e) {
            log.error("get writers error", e);
            return false;
        }

        BigpipeWriterClient writer = null;
        try {
            writer = writers.takeFirst();
            writer.sendMessage(message);
            return true;
        } catch (InterruptedException e) {
            log.error("take from dqueue failed, pipeletIndex={}",
                    pipeletIndex, e);
        } catch (BigpipeException e) {
            log.error("Send message error, pipletname={}, pipeletIndex={}",
                    writer.getPipeletName(), pipeletIndex, e);
        } finally {
            if (writer != null) {
                try {
//                writers.putLast(writer);
                    writers.putFirst(writer);
                } catch (InterruptedException e) {
                    log.error("put to dqueue failed, pipeletIndex={}, writer={}",
                            pipeletIndex, writer.getPipeletName(), e);
                }
            }
        }
        return false;
    }

    public boolean sendMessage(List<Integer> pipeletIndexList, String message) {

        if (pipeletIndexList.size() < 1) {
            log.debug("sendMessage pipeletIndexList empty, pass. ");
            return false;
        }

        log.debug("send message to pipeletIndexList={}", pipeletIndexList);

        int start = new Random().nextInt(pipeletIndexList.size());
        int cur = start;
        log.debug("send pipelet={} index={} start", pipeletIndexList.get(cur), cur);
        do {
            if (sendMessage(pipeletIndexList.get(cur), message)) {
                return true;
            }
            log.warn("send pipelet={} index={} failed, try next.", pipeletIndexList.get(cur), cur);
            cur = (++cur) % pipeletIndexList.size();
        } while (cur != start);

        return false;
    }

//    public boolean sendMessage(String pipeletIndexListString, String message) {
//
//        Iterable<String> iter = Splitter.on(",").split(pipeletIndexListString);
//        List<Integer> pipeletIndexList = new ArrayList<>();
//        for (String pipelet : iter) {
//            pipeletIndexList.add(Integer.parseInt(pipelet));
//        }
//
//        return sendMessage(pipeletIndexList, message);
//    }

    private List<BigpipeWriterClient> createWritersForOnePipe(int pipeIndex) {
        List<BigpipeWriterClient> writers = new ArrayList<>();
        String pipeletName = config.getPipeName() + "_" + pipeIndex;
        int clientNumPerPipe = Integer.parseInt(config.getClientNumberPerPipe());
        for (int i = 0; i < clientNumPerPipe; i++) {
            try {
                BigpipeWriterClient writer = bigpipeClientProvider.getBigpipeWriterClient(pipeletName);
                writers.add(writer);
            } catch (BigpipeException e) {
                log.error("Get bigpipe writer client error. pipeletName={}", pipeletName, e);
            }
        }
        return writers;
    }

    private BlockingDeque<BigpipeWriterClient> getWriter(Integer pipeletIndex) throws Exception {
        synchronized (writersList) {

            BlockingDeque<BigpipeWriterClient> writers = writersList.get(pipeletIndex);
            if (writers == null) {
                initWriters(pipeletIndex);
            }
            writers = writersList.get(pipeletIndex);
            if (writers == null) {
                throw new Exception(String.format("Bigpipe pipelet=%d writers init error", pipeletIndex));
            }
            return writers;
        }
    }


    private void initWriters(Integer pipeletIndex) {

        log.info("initWriters pipelet={}", pipeletIndex);


        // create
        List<BigpipeWriterClient> writers = Lists.newArrayList();
        writers.addAll(createWritersForOnePipe(pipeletIndex));
        log.info("Init writers for pipelet, pipeletId={}, clientTotalNum={}",
                pipeletIndex, writers.size());

        // create deque
        BlockingDeque<BigpipeWriterClient> deque = new LinkedBlockingDeque<>();
        deque.addAll(writers);

        // set writers map
        writersList.put(pipeletIndex, deque);

    }
}
