package com.ops.server.scheduler;

import com.ops.common.model.AlarmModel;
import com.ops.server.mapper.AlarmRecordMapper;
import com.ops.server.mapper.NodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HeartbeatChecker {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatChecker.class);

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Value("${server.heart-second:30}")
    private int heartSecond;

    @Value("${server.offline-second:90}")
    private int offlineSecond;

    @Scheduled(fixedRate = 10000)
    public void checkOffline() {
        long cutoff = System.currentTimeMillis() - offlineSecond * 1000L;
        List<Map<String, Object>> nodes = nodeMapper.getOfflineCandidates(cutoff);
        if (nodes == null || nodes.isEmpty()) return;

        for (Map<String, Object> node : nodes) {
            Long nodeId = (Long) node.get("id");
            String nodeName = (String) node.get("name");
            String nodeIp = (String) node.get("ip");

            nodeMapper.updateStatusOffline(nodeId);
            log.info("Node {} ({}) marked as OFFLINE", nodeName, nodeIp);

            // Trigger alarm
            String content = "节点[" + nodeName + "] " + nodeIp + " 心跳超时，状态变更为离线";
            AlarmModel alarm = new AlarmModel();
            alarm.setNodeId(nodeId);
            alarm.setType("OFFLINE");
            alarm.setContent(content);
            alarm.setSendResult(0); // PENDING
            alarm.setCreateTime(System.currentTimeMillis());
            alarmRecordMapper.insert(alarm);
        }
    }
}
