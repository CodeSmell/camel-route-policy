package codesmell.camel.routepolicy;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ThrottlingExceptionRoutePolicyOpenViaConfigTest extends CamelTestSupport {
    private static Logger log = LoggerFactory.getLogger(ThrottlingExceptionRoutePolicyOpenViaConfigTest.class);

    private String url = "seda:foo?concurrentConsumers=20";
    private MockEndpoint result;
    private int size = 5;

    private ThrottlingExceptionRoutePolicy policy;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        this.setUseRouteBuilder(true);
        result = getMockEndpoint("mock:result");

        context.getShutdownStrategy().setTimeout(1);

        // start off policy w/ open circuit
        policy.setKeepOpen(false);
    }


    @Test
    public void testThrottlingRoutePolicyAlwaysOpen() throws Exception {
        result.expectedMinimumMessageCount(0);

        for (int i = 0; i < size; i++) {
            template.sendBody(url, "Message " + i);
            Thread.sleep(3);
        }

        // gives time for policy half open check to run every second
        // but it should never close b/c keepOpen is true
        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testThrottlingRoutePolicyAlwaysOpenClosed() throws Exception {
        result.expectedMinimumMessageCount(size);

        for (int i = 0; i < size; i++) {
            template.sendBody(url, "Message " + i);
            Thread.sleep(3);
        }

        // gives time for policy half open check to run every second
        // and should not close b/c keepOpen is true
        Thread.sleep(3000);

        // set keepOpen to false
        // now half open check will succeed
        policy.setKeepOpen(false);

        // gives time for policy half open check to run every second
        // and should close and get all the messages
        assertMockEndpointsSatisfied(2000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                int threshold = 2;
                long failureWindow = 30;
                long halfOpenAfter = 1000;
                policy = new ThrottlingExceptionRoutePolicy(threshold, failureWindow, halfOpenAfter, null);

                from(url)
                    .routePolicy(policy)
                    .log("${body}")
                    .to("log:foo?groupSize=10")
                    .to("mock:result");
            }
        };
    }

}
