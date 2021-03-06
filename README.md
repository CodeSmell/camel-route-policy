# Camel Route Policy: Endpoint Circuit Breaker

Initial code as proof concept for creating a Circuit Breaker EIP implementation that would stop reading from an endpoint on a Camel Route if an error threshold was met. 

The initial capability is available in Camel 2.19
- see [CAMEL-10718](https://issues.apache.org/jira/browse/CAMEL-10718)

The `keepOpen` capability is available in Camel 2.21
- see [CAMEL-12125](https://issues.apache.org/jira/browse/CAMEL-12125)
- see [CAMEL-12133](https://issues.apache.org/jira/browse/CAMEL-12133)

More details on how it works can be found [here](https://theagilejedi.wordpress.com/2017/05/11/yet-another-camel-circuit-breaker/)
