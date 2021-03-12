## WmRndInvokeInterceptor

Example project to show how to inject custom code into invoke chain. All executed services will pass through an instance of the class com.softwareag.wm.invoke.audit.InvokeInterceptor.java. The methods chainPreProcessor() and chainPostProcessor() will be called before and after each service invocation via process() if it have been flagged for auditing. 


# Setup

Copy the file invokemanager.cnf to <SAG_HOME>/IntegrationServer/instances/default/config directory.

Create the jar file via exportJarToWm107TestPackage jar exporter, update the path to either the static directory of one of packages code/jars directory or save
it to the IntegrationServer/lib/jars directory.

You will also need to copy the jar file ./lib/org.apache.comon.jxpath_1.3.0.jar to the same target directory above.

Restart your server.


# Forcing the root context id

webMethods generates a unique transaction id when a service is called external (top level service), which is then propagated to all child services and even across 
Integration Servers if using the publish or publishAndWait services (the id is included in the UM payload and extracted by the receiving IS).

We can do something similar by forcing the root context id based on an attribute from the header, which is shown in the method determineRootContextId() called from the
process() method, which check if a header attribute named 'wm-etoe-transaction-id' exists. If so it overrides the current root context id with that supplied.

**NOTE**: the value for 'wm-etoe-transaction-id' must be a valid UUID field, otherwise it will be ignored.

# Usage

Create a webMethods package with a service, that invokes several other services. Change the audit level of the services to "always" and the level to "start, success and error".
Go to the logged fields tab and select some fields to log as well.

Use the following curl command to invoke your service.

`curl "http://localhost:5555/invoke/jc.test:hello?name=bob" \
     -H 'etoe-transaction-id: f10f14ac-8297-11eb-8dcd-0242ac130003' \
     -u 'Administrator:manage'`    
 
Update the chainPreProcessor() and chainPostProcessor() to call the end-to-end monitor agent.

# Issues

The method _extractDataFromPipeline() leverages the xpath parameter provided by logged fields to extract the value from the pipeline IData object and
required the apache-commons-jxpath library, which is not included in Integration Server so make sure to copy it as described above.

Forcing the root context id will not override the default wm value for the start step, this is because this invoke interceptor is called from within the audit interceptor chain and unfortunately it is not possible to change the order. All sub-services, and the end or error steps will be okay as the value is set by reference.

`Http Request Processor -> Invoke Processor -> AuditLogManager -> InvokeInterceptor -> etc -> SERVICE`


# Relationship between context id's

Integration Server has 3 attributes to track services and their relationship with one another.

1. root context id: Generated once for a service that is invoked via external protocol and propagated to all child services.
2. context id: Unique id for this execution instance (will be the same as the root context id for top level service).
3. parent context id: The unique id of the parent instance that called this service (same as root context id if immediate child service of top level service).

The root context id never changes and is even propagated via UM/broker to other Integration Servers providing the ability to trace services through the ESB.
