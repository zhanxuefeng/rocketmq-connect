/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.rocketmq.connect.runtime.service;

import io.openmessaging.KeyValue;
import io.openmessaging.connector.api.component.connector.Connector;
import org.apache.rocketmq.connect.runtime.common.ConnectKeyValue;
import org.apache.rocketmq.connect.runtime.config.RuntimeConfigDefine;
import org.apache.rocketmq.connect.runtime.connectorwrapper.Worker;
import org.apache.rocketmq.connect.runtime.store.KeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Interface for config manager. Contains connector configs and task configs. All worker in a cluster should keep the
 * same configs.
 */
public abstract class AbstractConfigManagementService implements ConfigManagementService {
    /**
     * Current connector configs in the store.
     */
    protected KeyValueStore<String, ConnectKeyValue> connectorKeyValueStore;

    @Override
    public void recomputeTaskConfigs(String connectorName, Connector connector, Long currentTimestamp, ConnectKeyValue configs) {
        int maxTask = configs.getInt(RuntimeConfigDefine.MAX_TASK, 1);
        ConnectKeyValue connectConfig = connectorKeyValueStore.get(connectorName);
        boolean directEnable = Boolean.parseBoolean(connectConfig.getString(RuntimeConfigDefine.CONNECTOR_DIRECT_ENABLE));
        List<KeyValue> taskConfigs = connector.taskConfigs(maxTask);
        List<ConnectKeyValue> converterdConfigs = new ArrayList<>();
        int taskId = 0;
        for (KeyValue keyValue : taskConfigs) {
            ConnectKeyValue newKeyValue = new ConnectKeyValue();
            for (String key : keyValue.keySet()) {
                newKeyValue.put(key, keyValue.getString(key));
            }
            if (directEnable) {
                newKeyValue.put(RuntimeConfigDefine.TASK_TYPE, Worker.TaskType.DIRECT.name());
                newKeyValue.put(RuntimeConfigDefine.SOURCE_TASK_CLASS, connectConfig.getString(RuntimeConfigDefine.SOURCE_TASK_CLASS));
                newKeyValue.put(RuntimeConfigDefine.SINK_TASK_CLASS, connectConfig.getString(RuntimeConfigDefine.SINK_TASK_CLASS));
            }
            // put task id
            newKeyValue.put(RuntimeConfigDefine.TASK_ID, taskId);
            newKeyValue.put(RuntimeConfigDefine.TASK_CLASS, connector.taskClass().getName());
            newKeyValue.put(RuntimeConfigDefine.UPDATE_TIMESTAMP, currentTimestamp);

            newKeyValue.put(RuntimeConfigDefine.CONNECT_TOPICNAME, configs.getString(RuntimeConfigDefine.CONNECT_TOPICNAME));
            newKeyValue.put(RuntimeConfigDefine.CONNECT_TOPICNAMES, configs.getString(RuntimeConfigDefine.CONNECT_TOPICNAMES));
            Set<String> connectConfigKeySet = configs.keySet();
            for (String connectConfigKey : connectConfigKeySet) {
                if (connectConfigKey.startsWith(RuntimeConfigDefine.TRANSFORMS)) {
                    newKeyValue.put(connectConfigKey, configs.getString(connectConfigKey));
                }
            }
            converterdConfigs.add(newKeyValue);
            taskId++;
        }
        putTaskConfigs(connectorName, converterdConfigs);
    }

    protected abstract void putTaskConfigs(String connectorName, List<ConnectKeyValue> configs);

}
