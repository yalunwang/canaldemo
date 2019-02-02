package com.neo.helloWorld.web;

import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.common.utils.AddressUtils;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleCanalClientExample {
    private static Logger logger= LoggerFactory.getLogger(SimpleCanalClientExample.class);
    public static void main(String args[]) throws Exception{
        // 创建链接
        CanalConnector connector = CanalConnectors.newSingleConnector(new InetSocketAddress(AddressUtils.getHostIp(),
                11111), "example", "", "");
        int batchSize = 1000;
        int emptyCount = 0;
        try {
            connector.connect();
            connector.subscribe(".*\\..*");
            connector.rollback();
            int totalEmptyCount = 120;
            while (true) {
                Message message = connector.getWithoutAck(batchSize); // 获取指定数量的数据
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId == -1 || size == 0) {
                    emptyCount++;
                    System.out.println("empty count : " + emptyCount);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                } else {
                    emptyCount = 0;
                    // System.out.printf("message[batchId=%s,size=%s] \n", batchId, size);
                    printEntry(message.getEntries());
                }

                connector.ack(batchId); // 提交确认
                // connector.rollback(batchId); // 处理失败, 回滚数据
            }

            //System.out.println("empty too many times, exit");
        } finally {
            connector.disconnect();
        }
    }

    private static void printEntry(List<Entry> entrys) throws Exception {
        for (CanalEntry.Entry entry : entrys) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }
            String schemaName = entry.getHeader().getSchemaName();
            String tableName = entry.getHeader().getTableName();

            CanalEntry.RowChange rowChage = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            CanalEntry.EventType eventType = rowChage.getEventType();
            for (CanalEntry.RowData rowData : rowChage.getRowDatasList()) {
                List<CanalEntry.Column> columns = null;
                if (eventType == CanalEntry.EventType.INSERT) {
                    columns = rowData.getAfterColumnsList();
                } else if (eventType == CanalEntry.EventType.UPDATE) {
                    columns = rowData.getAfterColumnsList();
                } else if (eventType == CanalEntry.EventType.DELETE) {
                    columns = rowData.getBeforeColumnsList();
                }

                if (columns != null) {
                    StringBuffer sb = new StringBuffer();

                    for (CanalEntry.Column col : columns) {
                        if (col.getUpdated()) {
                            sb.append(col.getName()).append(":").append(col.getValue()).append(" ");
                        }
                    }
                    String logStr = String.format("数据库[%s] 操作[%s] 表[%s] [%s]", schemaName, eventType.name(), tableName, sb.toString());
                    System.out.println(logStr);
                }
            }
        }

    }

    private static void printColumn(List<Column> columns) {
        for (Column column : columns) {
            System.out.println(column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated());
        }
    }
}
