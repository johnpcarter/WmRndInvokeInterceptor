package com.softwareag.wm.e2e.agent.skywalking;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.SW6CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.dependencies.com.google.common.base.Strings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.softwareag.wm.e2e.agent.skywalking.uhm.CustomTags;
import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.Service;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;
import com.wm.lang.ns.NSService;
import com.wm.net.EncodeURL;

public class SpanTools {

	private static final ILog logger = LogManager.getLogger(SpanTools.class);
	/**
	 * This is set on uhm dev, test machines to set tenantId.
	 */
	private static final boolean DEV_MODE = Boolean.parseBoolean(System.getProperty("UHM_DEV_MODE", "false"));
	/**
	 * This is set on local laptops of developers to generate any trace data.
	 */
	private static final boolean DEVELOPER_MODE = Boolean.parseBoolean(System.getProperty("UHM_DEVELOPER_MODE", "false"));
	private static final String UHM_DEFAULT_TENANT_ID = System.getProperty("UHM_DEFAULT_TENANT_ID", "");
	private static final String WMIC_EXECUTION_PARAM_HEADER_KEY = "X-WMIC-EXECUTION-CONTROL-PARAMETERS-AS-JSON";
	private static final String X_WMIC_SUBDOMAIN_HEADER_KEY = "X-WMIC-SUBDOMAIN";
	private static final String ERROR_MSG = "Error in ";
		
	private static ThreadLocal<SpanTracker> PARENT_SPAN = new ThreadLocal<>();

	private SpanTools() {
		// do nothing
	}

	public static boolean isDeveloperMode() {
		return DEVELOPER_MODE;
	}
	
	/**
	 * Starts an entry span from the HTTPState
	 * 
	 * @param gState - current HTTPState
	 * @throws ParseException 
	 */
	public static void startEntrySpan(String serviceName, String entityName, String customTransactionId, InvokeState state, Map<String, String> swValues, String component) {
			
		ContextCarrier contextCarrier = createContextCarrier(swValues);

		// start a entry span to denote that we have entered into IS
		AbstractSpan span = ContextManager.createEntrySpan(serviceName, contextCarrier);
				
 		System.out.println(Thread.currentThread().getId() + " - Started entry span for " + serviceName + " / " + ContextManager.getGlobalTraceId() + " / " + ContextManager.activeSpan().getSpanId());

		// only if a valid span is created, we should populate the rest of span
		if (!(ContextManager.activeSpan() instanceof NoopSpan)) {
			
			populateSpanData(span, entityName, serviceName, customTransactionId, state, component);

			SpanLayer.asHttp(span);
		}
	}
	
	 /* Starts an entry span from the HTTPState
	 * 
	 * @param gState - current HTTPState
	 * @throws ParseException 
	 */
	public static void startEntrySpan(String serviceName, String transactionId, Map<String, String> swValues, String component) throws ParseException {

		ContextCarrier contextCarrier = createContextCarrier(swValues);

		// start a entry span to denote that we have entered into IS
		ContextManager.createEntrySpan(serviceName, contextCarrier);
		 
 		System.out.println(Thread.currentThread().getId() + " - Started entry span for " + serviceName + " / " + ContextManager.getGlobalTraceId() + " / " + ContextManager.activeSpan().getSpanId());
	}

	public static void startLocalSpan(String serviceName, String entityName, String customTransactionId, InvokeState state, String component) {

 		System.out.println(Thread.currentThread().getId() + " - Starting local span " + serviceName + " under " + getServiceNameForActiveSpan() + " / " + ContextManager.getGlobalTraceId());

		AbstractSpan span = ContextManager.createLocalSpan(serviceName);
		
		populateSpanData(span, entityName, serviceName, customTransactionId, state, component);
	}
	
	public static void startLocalSpanFromContext(String serviceName, final EnhancedInstance objInst) {
		
		if (objInst.getSkyWalkingDynamicField() != null) {

	 		System.out.println(Thread.currentThread().getId() + " - Starting local span from thread context " + serviceName + " under " + getServiceNameForActiveSpan() + " / " + ContextManager.getGlobalTraceId());

			SpanTracker snap = (SpanTracker) objInst.getSkyWalkingDynamicField();
			// start a span from the given context
			
			AbstractSpan span = ContextManager.createLocalSpan(serviceName);
			ContextManager.continued(snap.snapshot);
												
			PARENT_SPAN.set(snap);
									
	 		System.out.println(Thread.currentThread().getId() + " - Starting in new thread local span " + serviceName + " under " + getServiceNameForSpan(snap.parentSpan) + " / " + ContextManager.getGlobalTraceId());
		}
	}
	
	public static IData startExitSpanWithHttpHeaders(String serviceName, String remotePeer, IData httpHeaders) throws ParseException {
		
		Map<String, String> header = startExitSpan(serviceName, remotePeer);
		
		if (httpHeaders == null) {
			httpHeaders = IDataFactory.create();	
		}
		
		IDataCursor c = httpHeaders.getCursor();
		for (String key : header.keySet()) {
			IDataUtil.put(c, key, header.get(key));
		}
		c.destroy();
		
		return httpHeaders;
	}
	
	public static Map<String, String> startExitSpan(String serviceName, String remotePeer) throws ParseException {
 		
 		System.out.println(Thread.currentThread().getId() + " - Starting exit span " + serviceName + " under " + getServiceNameForActiveSpan() + "/" + ContextManager.getGlobalTraceId() + " / " + ContextManager.activeSpan().getSpanId());
		
 		ContextCarrier contextCarrier = createContextCarrier(null);
 		ContextManager.createExitSpan(serviceName, contextCarrier, remotePeer);
 		ContextManager.inject(contextCarrier); // this is the magic where the context carrier gets updated with the current context.
 		
 		System.out.println(Thread.currentThread().getId() + " - Parent landscape is " + contextCarrier.getParentLandscape());
		System.out.println(Thread.currentThread().getId() + " - Parent endpoint is " + contextCarrier.getParentEndpointName());
		System.out.println(Thread.currentThread().getId() + " - Parent instance is " + contextCarrier.getParentServiceInstanceId());
		System.out.println(Thread.currentThread().getId() + " - trace instance is " + contextCarrier.getTraceSegmentId());
		
		System.out.println(Thread.currentThread().getId() + " - entry point instance is " + contextCarrier.getEntryServiceInstanceId());
		System.out.println(Thread.currentThread().getId() + " - entry name is " + contextCarrier.getEntryEndpointName());
 		
 		HashMap<String, String> vals = new HashMap<String, String>();
 		
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            vals.put(next.getHeadKey(), next.getHeadValue());
        }
        
        return vals;
	}
	
	public static void log(String subject, String text) {

		AbstractSpan span = ContextManager.activeSpan();
		
		if (span != null) {
			//span.tag(subject, text); // deprecated!!
			//span.info(text); // only as of 8.x
			
			// try this instead
			Map<String, String> trace = new HashMap<String, String>();
			trace.put(subject, text);
			
			span.log(new Date().getTime(), trace); 
		}
	}
	
	/**
	 * Handles any intercepted exception.
	 * 
	 * @param t
	 */
	public static void handleInterceptorException(Throwable t) {
		try {
			if (ContextManager.isActive()) {
				ContextManager.activeSpan().errorOccurred().log(t);
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
	}

	public static void stopSpan(String serviceName) {
			
		if (ContextManager.isActive() && serviceName.equals(getServiceNameForActiveSpan())) {
					
			stopSpan();
		} else if (getServiceNameForActiveSpan() != null) {
			System.out.println(Thread.currentThread().getId() + " - mismatched spans " + serviceName + " / " + getServiceNameForActiveSpan());
		}
	}
	
	/**
	 * Stops the active entry span.
	 */
	public static void stopSpan() {
		
		try {
			if (ContextManager.isActive()) {
				// always stop the span, no matter what type
				
				String opName = ContextManager.activeSpan().getOperationName();
				
				ContextManager.stopSpan();
				
				if (!ContextManager.isActive()) {
					System.out.println(Thread.currentThread().getId() + " - top level span successfully stopped: " + opName);
				} else {
					System.out.println(Thread.currentThread().getId() + " - stopped span " + opName + ", active span is now " + ContextManager.activeSpan().getOperationName());
				}
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
	}
	
	public static void prepareForAsync(final EnhancedInstance objInst) {
		
		AbstractSpan span = ContextManager.activeSpan();
		int id = span.getSpanId();
		
		SpanTracker snap = new SpanTracker(ContextManager.capture(), span, (SpanTracker) PARENT_SPAN.get());
		
		objInst.setSkyWalkingDynamicField(snap);		
	}
	
	public static void asyncCompleted(String serviceName, final EnhancedInstance objInst) {	
		
		if (objInst.getSkyWalkingDynamicField() != null) {
		
			SpanTracker snap = (SpanTracker) objInst.getSkyWalkingDynamicField();
	   		stopSpan(serviceName);
			snap.complete();
			
			PARENT_SPAN.set(null);
			
		} else {
			System.out.println("no dynamic fields");
		}
	}
	
	/**
	 * Populates all data in given span from http object.
	 * 
	 * @param gState - current HTTPState
	 * @param span   - current active span
	 */
	public static void populateSpanData(AbstractSpan span, String opName, String fullyQualifiedName, String customTransactionId, InvokeState gState, String component) {
		// decorate the span with tags
		
		//Tags.URL.set(span, Utils.sanitizeFullyQualifiedName(gState.getRequestUrl()));
		//Tags.HTTP.METHOD.set(span, gState.getRequestTypeAsString());
		
		span.tag(CustomTags.OPERATION_NAME, opName);
		span.tag(CustomTags.FULLY_QUALIFIED_NAME, fullyQualifiedName);
		
		if (customTransactionId != null) {
			span.tag(CustomTags.CUSTOM_TRANSACTION_ID, customTransactionId);
		}
		
		if (gState != null) {
			span.tag(CustomTags.STAGE, gState.getStageID());
			span.tag(CustomTags.TENANT_ID, getTenantID(gState));
		}
		
		span.tag(CustomTags.COMPONENT, component);
	}

	public static String omitPath(String service) {
		
		if (service != null && service.indexOf(":") != -1) {
			return service.substring(service.indexOf(":") + 1);
		} else {
			return service;
		}
	}
	
	/**
	 * Creates contextCarrier with given http request.
	 * 
	 * @param gState
	 * @return
	 * @throws ParseException
	 */
	public static ContextCarrier createContextCarrier(Map<String, String> swIdentifiers) {
		
		ContextCarrier contextCarrier = new ContextCarrier();
		
		if (swIdentifiers != null) {
			
			// used for non http clients such as UM etc.
			
			CarrierItem next = contextCarrier.items();
	        
			while (next.hasNext()) {
				next = next.next();

				next.setHeadValue(swIdentifiers.get(next.getHeadKey()));
			}
			
			System.out.println(Thread.currentThread().getId() + " - Parent landscape is " + contextCarrier.getParentLandscape());
			System.out.println(Thread.currentThread().getId() + " - Parent endpoint is " + contextCarrier.getParentEndpointName());
			System.out.println(Thread.currentThread().getId() + " - Parent instance is " + contextCarrier.getParentServiceInstanceId());
			System.out.println(Thread.currentThread().getId() + " - trace instance is " + contextCarrier.getTraceSegmentId());
			
			System.out.println(Thread.currentThread().getId() + " - entry point instance is " + contextCarrier.getEntryServiceInstanceId());
			System.out.println(Thread.currentThread().getId() + " - entry name is " + contextCarrier.getEntryEndpointName());

		} else if (Service.getHttpRequestHeader() != null) {
			
			// propagate existing header fields
			
			Map<String, String> headers = Service.getHttpRequestHeader().getFieldsMap();
			
			CarrierItem next = contextCarrier.items();
	        
			while (next.hasNext()) {
				next = next.next();
				String v = headers.get(next.getHeadKey());
								
				if (v != null) {
					next.setHeadValue(v);
				}
			}
		}
			
		return contextCarrier;
	}
	
	public static String getServiceNameForActiveSpan() {
		
		if (ContextManager.isActive()) {
			return getServiceNameForSpan(ContextManager.activeSpan());
		} else {
			return null;
		}
	}
	
	public static String getServiceNameForSpan(AbstractSpan span) {
	
		String name = null;
		
		if (span != null) {
			
			name = span.getOperationName();
			
			List<TagValuePair> tags = span.getTags();
			
			if (tags != null) {
				for (TagValuePair t : tags) {
					if (t.getKey().equals(CustomTags.FULLY_QUALIFIED_NAME)) {
						name = t.getValue();
						break;
					}
				}
			}
		}
		
		return name;
	}
	
	
	public static String getGlobalTraceId() {
		
		if (!ContextManager.getGlobalTraceId().equals("N/A")) {
			
			return ContextManager.getGlobalTraceId();
		} else {
			// get http headers from gState of httpDispatch
			
			Map<String, String> reqHeaders = Service.getHttpRequestHeader().getFieldsMap();
			String sw6Value = reqHeaders.get(SW6CarrierItem.HEADER_NAME);
			logger.debug("sw6 value in req header " + sw6Value);
		
			// check if value is present for the sw6 header or not
			// if not then check if it has X-WMIC-EXECUTION-CONTROL-PARAMETERS-AS-JSON
			// header from WMIC-CTP
				
			if (sw6Value == null) {
				String wmicExecutionParamHeader = reqHeaders.get(WMIC_EXECUTION_PARAM_HEADER_KEY);
			
				if (!Strings.isNullOrEmpty(wmicExecutionParamHeader)) {
					JSONParser parser = new JSONParser();
					try {
						JSONObject json = (JSONObject) parser.parse(EncodeURL.decode(wmicExecutionParamHeader));
						sw6Value = (String) json.get(SW6CarrierItem.HEADER_NAME);
						logger.debug("sw6 value in wmic execution param header " + sw6Value);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				} else {
					logger.debug("sw6 value is null in wmic execution param header ");
				}
			}
			
			return sw6Value;
		}
	}
	
	/**
	 * Get the tenantID from the invokeState of the gState
	 * 
	 * @param gState - current HTTPState
	 * @return
	 */
	public static String getTenantID(InvokeState gState) {
		
		if (DEV_MODE) {
			// UHM local environments are setup as on-premise API Gateway, so we wont get
			// tenantID from it. this value is set in setenv.bat/sh file of IS
			return UHM_DEFAULT_TENANT_ID;
		} else {
			// If subdomain is available use it.
			String subdomain = Service.getHttpRequestHeader().getFieldValue(X_WMIC_SUBDOMAIN_HEADER_KEY);
			logger.debug("Sub domain = " + subdomain);

			// use sub-domain if available, else use the tenantId (the no of wmic LJ)
			if (null != subdomain) {
				return subdomain;
			} else {
				if(logger.isDebugEnable()) {
					//log all http headers of IS to debug why sub-domain is not present in it
					Map<String, String> fieldsMap = Service.getHttpRequestHeader().getFieldsMap();
					logger.debug(fieldsMap.toString());
				}
				
				return gState.getTenantID();
			}
		}
	}
	
	public static String getServiceName(BaseService baseService) {
		return baseService.getNSName().getFullName();
	}
	    
	protected static String getCustomContextId() {
    	
    	return getContextIDsForService()[3];
    }
   
	public static String getRootContextId(ServiceStatus status) {
       
	   if (status.isTopService()) {   		
   			return Service.getCurrentActivationID();
   		} else {
   			return getContextIDsForService()[0];
   		}
	}
   
	public static String getParentContextId(ServiceStatus status) {
       	
   		if (status.isTopService()) {
   			return Service.getCurrentActivationID();
   		} else {
   			return getContextIDsForService()[1];
   		}
	}
   
    /**
     * Return the package name associated with the context of the calling service.
     *
     * @return package name associated with the calling context
     */
    public static String getPackageForCaller() 
    {
        return getPackageForCaller(false);
    }
    
    public static String getPackageForCaller(boolean ifNotExistGetCurrent) {
    	
    	String packageName = null;

        try {
            NSService caller = Service.getCallingService();
            if (caller == null && ifNotExistGetCurrent) {
            	packageName = Service.getPackageName();
            } else {
            	packageName = caller.getPackage().getName();
            }
        } catch (Exception e) {
           // throw new RuntimeException("Cannot determine package name");
        }

        return packageName;
    }
    
    /**
     * Extract given keys from pipeline
	  */
	   
	public static Map<String, Object> extractDataFromPipeline(String[][] keys, IData pipeline) {
		   
		Map<String, Object> vals = new HashMap<String, Object>();
		   
		if (keys != null) {
		   for (String[] k : keys) {
			   Object obj = _extractDataFromPipeline(k[0], pipeline);
			   
			   if (obj != null) {
				   vals.put(k[1], obj); // USED simple name, risky because it might not be be unique, perhaps should use xpath ?
			   }
		   }
		}
		   
		return vals;
   }
	   
   private static Object _extractDataFromPipeline(String xpath, IData doc) {
   		
	   return _extractDataFromPipeline(new StringTokenizer(xpath, "/"), doc);
   }
	   
   private static Object _extractDataFromPipeline(StringTokenizer path, IData doc) {
      
	   String next = path.nextToken();
	   
	   if (next != null) {
		   if (next.contains(";")) {
			   next = next.substring(0, next.indexOf(";"));
		   }
		
		   IDataCursor c = doc.getCursor();
		   Object n = IDataUtil.get(c, next);
		   c.destroy();
			   
		   if (path.hasMoreTokens() && n instanceof IData) {
			   return _extractDataFromPipeline(path, (IData) n);
		   } else {
			   return n;
		   }
	   } else {
		   return null;
	   }
  	}
  
    protected static String[] getContextIDsForService() {
        
    	String[] contextIDs = {null, null, null, null};

        try {
            InvokeState currentInvokeState = InvokeState.getCurrentState();
            String contextIDStack[] = currentInvokeState.getAuditRuntime().getContextStack();

            String contextId = null;
            String parentContextId = null;

            if (contextIDStack.length > 0) {
            	int contextId_index = contextIDStack.length - 1;

            	contextId = contextIDStack[contextId_index];
            	if (contextId_index > 0) {
            		parentContextId = contextIDStack[contextId_index - 1];
            	}
            
            	contextIDs[0] = contextIDStack[0]; // root context id
            	contextIDs[1] = parentContextId;
            	contextIDs[2] = contextId;
            
            if (currentInvokeState.getCustomAuditContextID() != null) {
            	contextIDs[3] = currentInvokeState.getCustomAuditContextID();
            }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return contextIDs;
    }
    
    private static class SpanTracker {
		
		ContextSnapshot snapshot = null;
		
		private AbstractSpan parentSpan;
		SpanTracker grandadTracker = null;
		
		private int count = 0;
		
		SpanTracker(ContextSnapshot snapshot, AbstractSpan span, SpanTracker grandadTracker) {
			
			this.snapshot = snapshot;
			this.parentSpan = span;
			this.grandadTracker = grandadTracker;
						
			span.prepareForAsync();

			if (this.grandadTracker != null) {
				this.grandadTracker.count += 1;
				System.out.println(Thread.currentThread().getId() + " - Incrementing dependents for " + this.grandadTracker.parentSpan.getOperationName() + " to " + this.grandadTracker.count);
			}
			
	 		System.out.println(Thread.currentThread().getId() + " - asyncing span " + getServiceNameForSpan(span));			
		}
		
		protected synchronized void complete() {
			
			if (this.count > 0 ) {
				System.out.println(Thread.currentThread().getId() + " - deferring sync for span " + parentSpan.getOperationName() + " / " + count);
				this.count -= 1;
			} else {
				
				System.out.println(Thread.currentThread().getId() + " - syncing span " + getServiceNameForSpan(parentSpan));

				this.parentSpan.asyncFinish();
								
				if (grandadTracker != null) {// && grandadSpan.isFinished()) {
				// check if we can finally finish grandparent too		
				
					grandadTracker.complete();
				}
			}
		}
	}
}
