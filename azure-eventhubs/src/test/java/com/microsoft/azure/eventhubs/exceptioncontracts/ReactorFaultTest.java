package com.microsoft.azure.eventhubs.exceptioncontracts;

import com.microsoft.azure.eventhubs.*;
import com.microsoft.azure.eventhubs.impl.EventHubClientImpl;
import com.microsoft.azure.eventhubs.impl.MessagingFactory;
import com.microsoft.azure.eventhubs.lib.ApiTestBase;
import com.microsoft.azure.eventhubs.lib.TestContext;
import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.reactor.Reactor;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

public class ReactorFaultTest extends ApiTestBase {
    static final String PARTITION_ID = "0";
    static ConnectionStringBuilder connStr;

    @BeforeClass
    public static void initialize() throws Exception {
        connStr = TestContext.getConnectionString();
    }

    @Test()
    public void VerifyReactorRestartsOnProtonBugs() throws Exception {
        final EventHubClient eventHubClient = EventHubClient.createSync(connStr.toString(), TestContext.EXECUTOR_SERVICE);

        try {
            final PartitionReceiver partitionReceiver = eventHubClient.createEpochReceiverSync(
                    "$default", "0", EventPosition.fromStartOfStream(), System.currentTimeMillis());

            try {
                final Field factoryField = EventHubClientImpl.class.getDeclaredField("underlyingFactory");
                factoryField.setAccessible(true);
                final MessagingFactory underlyingFactory = (MessagingFactory) factoryField.get(eventHubClient);

                final Field reactorField = MessagingFactory.class.getDeclaredField("reactor");
                reactorField.setAccessible(true);
                final Reactor reactor = (Reactor) reactorField.get(underlyingFactory);

                org.apache.qpid.proton.engine.Handler handler = reactor.getHandler();
                handler.add(new BaseHandler() {
                    @Override
                    public void handle(org.apache.qpid.proton.engine.Event e) {
                        throw new NullPointerException();
                    }
                });

                final Iterable<EventData> events = partitionReceiver.receiveSync(100);
                Assert.assertTrue(events != null && events.iterator().hasNext());
            } finally {
                partitionReceiver.closeSync();
            }
        } finally {
            eventHubClient.closeSync();
        }
    }
}
