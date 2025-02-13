/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.agent.core.task;

import org.apache.inlong.agent.conf.TaskProfile;
import org.apache.inlong.agent.core.AgentBaseTestsHelper;
import org.apache.inlong.agent.core.task.file.TaskManager;
import org.apache.inlong.common.enums.TaskStateEnum;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class TestTaskManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskManager.class);
    private static TaskManager manager;
    private static AgentBaseTestsHelper helper;

    @BeforeClass
    public static void setup() {
        helper = new AgentBaseTestsHelper(TestTaskManager.class.getName()).setupAgentHome();
        try {
            manager = new TaskManager();
            manager.start();
        } catch (Exception e) {
            Assert.assertTrue("manager start error", false);
        }
    }

    @AfterClass
    public static void teardown() throws Exception {
        manager.stop();
        helper.teardownAgentHome();
    }

    @Test
    public void testTaskManager() {
        String pattern = helper.getTestRootDir() + "/YYYYMMDD.log_[0-9]+";
        TaskProfile taskProfile1 = helper.getTaskProfile(1, pattern, false, 0L, 0L, TaskStateEnum.RUNNING, "GMT+8:00");
        String taskId1 = taskProfile1.getTaskId();
        taskProfile1.setTaskClass(MockTask.class.getCanonicalName());
        List<TaskProfile> taskProfiles1 = new ArrayList<>();
        taskProfiles1.add(taskProfile1);
        // test add
        manager.submitTaskProfiles(taskProfiles1);
        await().atMost(3, TimeUnit.SECONDS).until(() -> manager.getTask(taskId1) != null);
        LOGGER.info("state {}", manager.getTaskProfile(taskId1).getState());
        Assert.assertTrue(manager.getTaskProfile(taskId1).getState() == TaskStateEnum.RUNNING);

        // test froze
        taskProfile1.setState(TaskStateEnum.FROZEN);
        manager.submitTaskProfiles(taskProfiles1);
        await().atMost(3, TimeUnit.SECONDS).until(() -> manager.getTask(taskId1) == null);
        Assert.assertTrue(manager.getTaskProfile(taskId1).getState() == TaskStateEnum.FROZEN);
        taskProfile1.setState(TaskStateEnum.RUNNING);
        manager.submitTaskProfiles(taskProfiles1);
        await().atMost(3, TimeUnit.SECONDS).until(() -> manager.getTask(taskId1) != null);
        Assert.assertTrue(manager.getTaskProfile(taskId1).getState() == TaskStateEnum.RUNNING);

        // test delete
        TaskProfile taskProfile2 = helper.getTaskProfile(2, pattern, false, 0L, 0L, TaskStateEnum.RUNNING, "GMT+8:00");
        taskProfile2.setTaskClass(MockTask.class.getCanonicalName());
        List<TaskProfile> taskProfiles2 = new ArrayList<>();
        taskProfiles2.add(taskProfile2);
        manager.submitTaskProfiles(taskProfiles2);
        await().atMost(3, TimeUnit.SECONDS).until(() -> manager.getTask(taskId1) == null);
        Assert.assertTrue(manager.getTaskProfile(taskId1) == null);
        String taskId2 = taskProfile2.getTaskId();
        await().atMost(3, TimeUnit.SECONDS).until(() -> manager.getTask(taskId2) != null);
        Assert.assertTrue(manager.getTaskProfile(taskId2).getState() == TaskStateEnum.RUNNING);
    }
}
