package codesmell.camel.routepolicy;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ThrottlingExceptionRoutePolicyKeepOpenOnInitTest extends CamelTestSupport {

    private String url = "seda:foo?concurrentConsumers=20";
    private MockEndpoint result;
    private int size = 5;

    private ThrottlingExceptionRoutePolicy policy;

    @Override
    @Before
    public void setUp() throws Exception {
        this.createPolicy();

        super.setUp();
        this.setUseRouteBuilder(true);
        result = getMockEndpoint("mock:result");
        context.getShutdownStrategy().setTimeout(1);
    }

    protected void createPolicy() {
        int threshold = 2;
        long failureWindow = 30;
        long halfOpenAfter = 1000;
        boolean keepOpen = true;
        policy = new ThrottlingExceptionRoutePolicy(threshold, failureWindow, halfOpenAfter, null, keepOpen);
    }

    @Test
    public void testThrottlingRoutePolicyStartWithAlwaysOpenOn() throws Exception {

        log.debug("---- sending some messages");
        for (int i = 0; i < size; i++) {
            template.sendBody(url, "Message " + i);
            Thread.sleep(3);
        }

        // gives time for policy half open check to run every second
        // and should not close b/c keepOpen is true
        Thread.sleep(2000);

        // gives time for policy half open check to run every second
        // but it should never close b/c keepOpen is true
        result.expectedMessageCount(0);
        assertMockEndpointsSatisfied(1000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testThrottlingRoutePolicyStartWithAlwaysOpenOnThenClose() throws Exception {

        for (int i = 0; i < size; i++) {
            template.sendBody(url, "Message " + i);
            Thread.sleep(3);
        }

        // gives time for policy half open check to run every second
        // and should not close b/c keepOpen is true
        Thread.sleep(2000);

        result.expectedMessageCount(0);
        assertMockEndpointsSatisfied(2000, TimeUnit.MILLISECONDS);

        // set keepOpen to false
        // now half open check will succeed
        policy.setKeepOpen(false);

        // gives time for policy half open check to run every second
        // and should close and get all the messages
        result.expectedMessageCount(5);
        assertMockEndpointsSatisfied(2000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(url)
                    .routePolicy(policy)
                    .log("${body}")
                    .to("log:foo?groupSize=10")
                    .to("mock:result");
            }
        };
    }

}
