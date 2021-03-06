package codesmell.camel.routepolicy;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ThrottingExceptionRoutePolicyHalfOpenHandlerSedaTest extends CamelTestSupport {
    private static Logger log = LoggerFactory.getLogger(ThrottingExceptionRoutePolicyHalfOpenHandlerSedaTest.class);
    
    private String url = "seda:foo?concurrentConsumers=20";
    private MockEndpoint result;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        this.setUseRouteBuilder(true);
        result = getMockEndpoint("mock:result");
        
        context.getShutdownStrategy().setTimeout(1);
    }
    
    @Test
    public void testHalfOpenCircuit() throws Exception {
        result.expectedMessageCount(2);
        List<String> bodies = Arrays.asList(new String[]{"Message One", "Message Two"}); 
        result.expectedBodiesReceivedInAnyOrder(bodies);
        
        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                String msg = exchange.getIn().getBody(String.class);
                exchange.setException(new ThrottleException(msg));
            }
        });
        
        // send two messages which will fail
        sendMessage("Message One");
        sendMessage("Message Two");
        
        // wait long enough to 
        // have the route shutdown
        Thread.sleep(3000);
        
        // send more messages 
        // but should get there (yet)
        // due to open circuit
        // SEDA will queue it up
        log.debug("sending message three");
        sendMessage("Message Three");

        assertMockEndpointsSatisfied(1000, TimeUnit.MILLISECONDS);
        
        result.reset();
        result.expectedMessageCount(2);
        bodies = Arrays.asList(new String[]{"Message Three", "Message Four"}); 
        result.expectedBodiesReceivedInAnyOrder(bodies);
        
        // wait long enough for
        // half open attempt
        Thread.sleep(4000);
        
        // send message
        // should get through
        log.debug("sending message four");
        sendMessage("Message Four");
        
        assertMockEndpointsSatisfied(1000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                int threshold = 2;
                long failureWindow = 30;
                long halfOpenAfter = 5000;
                ThrottlingExceptionRoutePolicy policy = new ThrottlingExceptionRoutePolicy(threshold, failureWindow, halfOpenAfter, null);
                policy.setHalfOpenHandler(new AlwaysCloseHandler());
                
                from(url)
                    .routePolicy(policy)
                    .log("${body}")
                    .to("log:foo?groupSize=10")
                    .to("mock:result");
            }
        };
    }
    
    public class AlwaysCloseHandler implements ThrottingExceptionHalfOpenHandler {

        @Override
        public boolean isReadyToBeClosed() {
            return true;
        }
        
    }
    
    protected void sendMessage(String bodyText) {
        try {
            template.sendBody(url, bodyText);
        } catch (Exception e) {
            log.debug("Error sending:" + e.getCause().getMessage());
        }
    }
}
