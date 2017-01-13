package codesmell.camel.routepolicy;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ThrottlingExceptionRoutePolicyTransactedTest extends CamelTestSupport {
    private static Logger log = LoggerFactory.getLogger(ThrottlingExceptionRoutePolicyTransactedTest.class);

    private String url = "seda:foo?concurrentConsumers=20";
    private MockEndpoint result;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        this.setUseRouteBuilder(true);
        result = getMockEndpoint("mock:result");

        context.getShutdownStrategy().setTimeout(1);
    }

    @Override 
    protected CamelContext createCamelContext() throws Exception { 
        CamelContext context = new DefaultCamelContext(createSimpleRegistry()); 
        return context; 
    } 
    
    private Registry createSimpleRegistry() { 
        SimpleRegistry registry = new SimpleRegistry(); 
        registry.put("transactionManager", new DummyTransactionManager()); 
        return registry; 
    } 
    
    @Test
    public void testOpenCircuitToPreventMessageThree() throws Exception {
        result.reset();
        result.expectedMessageCount(2);
        List<String> bodies = Arrays.asList(new String[] { "Message One", "Message Two" });
        result.expectedBodiesReceivedInAnyOrder(bodies);

        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                String msg = exchange.getIn().getBody(String.class);
                exchange.setException(new ThrottleException(msg));
            }
        });

        // send two messages which will fail
        template.sendBody(url, "Message One");
        template.sendBody(url, "Message Two");

        // wait long enough to
        // have the route shutdown
        Thread.sleep(3000);

        // send more messages
        // but never should get there
        // due to open circuit
        log.debug("sending message three");
        template.sendBody(url, "Message Three");

        Thread.sleep(2000);

        assertMockEndpointsSatisfied(1000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                int threshold = 2;
                long failureWindow = 30;
                long halfOpenAfter = 10000;
                ThrottlingExceptionRoutePolicy policy = new ThrottlingExceptionRoutePolicy(threshold, failureWindow, halfOpenAfter, null);
                policy.setHalfOpenHandler(new NeverCloseHandler());

                from(url)
                    .routePolicy(policy)
                    .transacted()
                    .log("${body}")
                    .to("log:foo?groupSize=10")
                    .to("mock:result");
            }
        };
    }

    public class NeverCloseHandler implements ThrottingExceptionHalfOpenHandler {

        @Override
        public boolean isReadyToBeClosed() {
            return false;
        }

    }

    public class DummyTransactionManager implements PlatformTransactionManager {
        public TransactionStatus getTransaction(TransactionDefinition transactionDefinition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        public void commit(TransactionStatus transactionStatus) throws TransactionException {
        }

        public void rollback(TransactionStatus transactionStatus) throws TransactionException {
        }
    }
}
