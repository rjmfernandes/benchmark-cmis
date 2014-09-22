/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.bm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.alfresco.bm.api.v1.ResultsRestAPI;
import org.alfresco.bm.api.v1.TestRestAPI;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventRecord;
import org.alfresco.bm.event.ResultService;
import org.alfresco.bm.session.SessionService;
import org.alfresco.bm.test.TestRunServicesCache;
import org.alfresco.bm.test.TestService;
import org.alfresco.bm.test.mongo.MongoTestDAO;
import org.alfresco.bm.tools.BMTestRunner;
import org.alfresco.bm.tools.BMTestRunnerListener;
import org.alfresco.bm.tools.BMTestRunnerListenerAdaptor;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataServiceImpl;
import org.alfresco.mongo.MongoDBFactory;
import org.alfresco.mongo.MongoDBForTestsFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.context.ApplicationContext;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

/**
 * Execute the CMIS load test against no existing server, which will validate that the
 * test is properly structured to start.
 * 
 * @author Derek Hulley
 * @since 1.0
 */
@RunWith(JUnit4.class)
public class BMCmisTest extends BMTestRunnerListenerAdaptor
{
    private static Log logger = LogFactory.getLog(BMCmisTest.class);
    
    private MongoDBForTestsFactory dbFactory;
    private DB testDB;
    private String testDBHost;
    
    /**
     * We need access to the test DB in order to access the users
     */
    @Before
    public void setUp() throws Exception
    {
        dbFactory = new MongoDBForTestsFactory();
        String uriWithoutDB = dbFactory.getMongoURIWithoutDB();
        testDBHost = new MongoClientURI(uriWithoutDB).getHosts().get(0);
        testDB = new MongoDBFactory(new MongoClient(testDBHost), "bm20-data").getObject();
        
        // Create a user for use
        UserDataServiceImpl userDataService = new UserDataServiceImpl(testDB, "mirrors.cmis.alfresco.com.users");
        userDataService.afterPropertiesSet();
        
        UserData user = new UserData();
        user.setUsername("admin");
        user.setPassword("admin");
        user.setCreationState(DataCreationState.Created);
        // The rest is not useful for this specific test
        {
            user.setEmail("bmarley@reggae.com");
            user.setDomain("reggae");
            user.setFirstName("Bob");
            user.setLastName("Marley");
        }
        userDataService.createNewUser(user);
    }
    
    @After
    public void tearDown() throws Exception
    {
        if (dbFactory != null)
        {
            dbFactory.destroy();
        }
    }
    
    @Test
    public void runSample() throws Exception
    {
        BMTestRunner runner = new BMTestRunner(60000L);         // Should be done in 60s
        runner.addListener(this);
        runner.run(null, testDBHost, null);
    }

    /**
     * A listener method that allows the test to check results <b>before</b> the in-memory MongoDB instance
     * is discarded.
     * <p/>
     * Check that the exact number of results are available, as expected
     * 
     * @see BMTestRunnerListener
     */
    @Override
    public void testRunFinished(ApplicationContext testCtx, String test, String run)
    {
        TestRunServicesCache services = testCtx.getBean(TestRunServicesCache.class);
        MongoTestDAO testDAO = services.getTestDAO();
        SessionService sessionService = services.getSessionService(test, run);
        TestService testService = services.getTestService();
        ResultService resultService = services.getResultService(test, run);
        assertNotNull(resultService);
        TestRestAPI testAPI = new TestRestAPI(testDAO, testService, services);
        ResultsRestAPI resultsAPI = testAPI.getTestRunResultsAPI(test, run);
        // Let's check the results before the DB gets thrown away (we didn't make it ourselves)
        
        // Get the summary CSV results for the time period and check some of the values
        String summary = BMTestRunner.getResultsCSV(resultsAPI);
        logger.info(summary);
        
        // Dump one of each type of event for information
        Set<String> eventNames = new TreeSet<String>(resultService.getEventNames());
        logger.info("Showing 1 of each type of event:");
        for (String eventName : eventNames)
        {
            List<EventRecord> eventRecord = resultService.getResults(eventName, 0, 1);
            logger.info("   " + eventRecord);
            assertFalse(
                    "An event was created that has no available processor or producer.  Use the TerminateEventProducer to absorb events.",
                    eventRecord.contains("processedBy=unknown"));
        }
        
        // One successful START event
        assertEquals("Incorrect number of start events.", 1, resultService.countResultsByEventName(Event.EVENT_NAME_START));
        List<EventRecord> results = resultService.getResults(0L, Long.MAX_VALUE, false, 0, 1);
        if (results.size() != 1 || !results.get(0).getEvent().getName().equals(Event.EVENT_NAME_START))
        {
            fail(Event.EVENT_NAME_START + " failed: \n" + results.toString());
        }

        /*
         * 'start' = 1 result
         * 'cmis.createSessions' = 2 results
         * 'cmis.scenario.01.startSession' = 200 results
         * Successful processing generates a No-op for each 
         */
        Set<String> expectedEventNames = new TreeSet<String>();
        expectedEventNames.add("start");
        expectedEventNames.add("cmis.createSessions");
        expectedEventNames.add("cmis.startSession");
        expectedEventNames.add("cmis.scenario.01.findFolder");
        expectedEventNames.add("cmis.scenario.01.listFolderContents");
        expectedEventNames.add("cmis.scenario.02.retrieveTestFolder");
        expectedEventNames.add("cmis.scenario.02.createTestFolder");
        expectedEventNames.add("cmis.scenario.02.uploadFile");
        expectedEventNames.add("cmis.scenario.02.downloadFile");
        expectedEventNames.add("cmis.scenario.02.deleteTestFolder");
        expectedEventNames.add("cmis.scenario.03.retrieveTestFolder");
        expectedEventNames.add("cmis.scenario.03.createTestFolder");
        expectedEventNames.add("cmis.scenario.03.searchInFolder");
        expectedEventNames.add("cmis.scenario.03.deleteTestFolder");
        // Use the toString() as the TreeSet is ordered and the difference reporting is better
        assertEquals("Unexpected event names. ", expectedEventNames.toString(), eventNames.toString());
        assertEquals(
                "Incorrect number of events: " + "cmis.startSession",
                20, resultService.countResultsByEventName("cmis.startSession"));
        
        // Check for failures
        long failures = resultService.countResultsByFailure();
        if (failures > 0L)
        {
            // Get the failures for information
            List<EventRecord> allResults = resultService.getResults(null, 0, Integer.MAX_VALUE);
            StringBuilder sb = new StringBuilder(2048);
            sb.append("Failures are:");
            for (EventRecord result : allResults)
            {
                if (result.isSuccess())
                {
                    continue;
                }
                sb.append("\n").append("   ").append(result.toString());
            }
            logger.error(sb.toString());
        }
        assertEquals("Did not expect failures (at present). ", 0L, failures);
        
        // Check totals
        long countScenario01 = resultService.countResultsByEventName("cmis.scenario.01.findFolder");
        long countScenario02 = resultService.countResultsByEventName("cmis.scenario.02.retrieveTestFolder");
        long countScenario03 = resultService.countResultsByEventName("cmis.scenario.03.retrieveTestFolder");
        long countExpected = 2 + (countScenario01 * 3) + (countScenario02 * 6) + (countScenario03 * 5);
        long successes = resultService.countResultsBySuccess();
        assertEquals("Incorrect number of successful events. ", countExpected, successes);
        
        // Make sure that events received a traceable session ID
        assertEquals("Incorrect number of sessions: ", 20, sessionService.getAllSessionsCount());
        results = resultService.getResults("cmis.scenario.02.retrieveTestFolder", 0, 20);
        for (EventRecord result : results)
        {
            assertNotNull("All scenario events must have a session ID: " + result, result.getEvent().getSessionId());
        }
    }
}
