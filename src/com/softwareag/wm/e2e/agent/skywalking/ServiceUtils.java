package com.softwareag.wm.e2e.agent.skywalking;


import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

import java.util.HashMap;
import java.util.List;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.RuntimeContext;
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

public class ServiceUtils {
	
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
				System.out.println("Incrementing dependents for " + this.grandadTracker.parentSpan.getOperationName() + " to " + this.grandadTracker.count);
			}
			
	 		System.out.println("asyncing span " + getFullyQualifiedName(span));			
		}
		
		boolean isFinished() {
			return count == 0;
		}
		
		protected synchronized void complete() {
			
			if (this.count > 0 ) {
				System.out.println("deferring sync for span " + parentSpan.getOperationName() + " / " + count);
				this.count -= 1;
			} else {
				
				System.out.println("syncing span " + getFullyQualifiedName(parentSpan));

				this.parentSpan.asyncFinish();
								
				if (grandadTracker != null) {// && grandadSpan.isFinished()) {
				// check if we can finally finish grandparent too		
				
					grandadTracker.complete();
				}
			}
		}
	}

	private static final ILog logger = LogManager.getLogger(ServiceUtils.class);
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

	private ServiceUtils() {
		// do nothing
	}

	/**
	 * Starts an entry span from the HTTPState
	 * 
	 * @param gState - current HTTPState
	 * @throws ParseException 
	 */
	public static void startEntrySpan(String entityName, String serviceName, IData pipeline, String customTransactionId, InvokeState state, String component) {
			
 		System.out.println("Starting entry span for " + serviceName);

		ContextCarrier contextCarrier = createContextCarrier(getGlobalTraceId(pipeline));

		// start a entry span to denote that we have entered into IS
		AbstractSpan span = ContextManager.createEntrySpan(entityName, contextCarrier);
				
		//ContextManager.inject(contextCarrier); // sw6 key is only required on exitspan
				
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
	public static void startEntrySpan(String entityName, String transactionId) throws ParseException {

 		System.out.println("Starting entry span for " + entityName);

		ContextCarrier contextCarrier = createContextCarrier(transactionId);

		// start a entry span to denote that we have entered into IS
		 ContextManager.createEntrySpan(entityName, contextCarrier);
	}

	public static void startLocalSpan(String entityName, String serviceName, String customTransactionId, InvokeState state, String component) {

 		System.out.println("Starting local span " + serviceName + " under " + getFullyQualifiedName());

		AbstractSpan span = ContextManager.createLocalSpan(serviceName);
		
		populateSpanData(span, entityName, serviceName, customTransactionId, state, component);
	}
	
 	public static IData startExitSpan(String entityName, IData headers, String remotePeer) throws ParseException {
	
		ContextCarrier contextCarrier = createContextCarrier(null);
 		ContextManager.createExitSpan(entityName, contextCarrier, remotePeer);
		ContextManager.inject(contextCarrier);
 		
 		if (headers == null) {
 			headers = IDataFactory.create();
 		}
 		
 		IDataCursor c = headers.getCursor();
 		
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            IDataUtil.put(c, next.getHeadKey(), next.getHeadValue());
        }
        
        c.destroy();
        
        return headers;
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
	
		if (ContextManager.isActive() && serviceName.equals(getFullyQualifiedName())) {
					
	 		System.out.println("stopping span " + serviceName);

			stopSpan();
		} else if (getFullyQualifiedName() != null) {
			System.out.println("mismatched spans " + serviceName + " / " + getFullyQualifiedName());
		}
	}
	
	/**
	 * Stops the active entry span.
	 */
	public static void stopSpan() {
		
		try {
			if (ContextManager.isActive()) {
				// always stop the span, no matter what type
				
				System.out.println("span closed " + getFullyQualifiedName());
				ContextManager.stopSpan();				
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
	
	public static void startLocalSpanFromContext(String serviceName, final EnhancedInstance objInst) {
	
		if (objInst.getSkyWalkingDynamicField() != null) {

			SpanTracker snap = (SpanTracker) objInst.getSkyWalkingDynamicField();
			// start a span from the given context
			
			AbstractSpan span = ContextManager.createLocalSpan(serviceName);
			ContextManager.continued(snap.snapshot);
												
			PARENT_SPAN.set(snap);
									
	 		System.out.println("Starting in new thread local span " + serviceName + " under " + getFullyQualifiedName(snap.parentSpan));
	 		
	 		System.out.println("Global trace id is " + getGlobalTraceId());
		}
	}
	
	public static void asyncCompleted(String serviceName, final EnhancedInstance objInst) {	
		
		if (objInst.getSkyWalkingDynamicField() != null) {
		
			SpanTracker snap = (SpanTracker) objInst.getSkyWalkingDynamicField();
			AbstractSpan span = ContextManager.activeSpan();
	   		ServiceUtils.stopSpan(serviceName);
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
	public static ContextCarrier createContextCarrier(String transactionId) {
		
		ContextCarrier contextCarrier = new ContextCarrier();
		
		if (transactionId != null) {
			
			// used for non http clients such as UM etc.
			
			CarrierItem next = contextCarrier.items();
	        
			while (next.hasNext()) {
				next = next.next();

				if (next.getHeadKey().contentEquals(SW6CarrierItem.HEADER_NAME)) {
					next.setHeadValue(transactionId);
				}
			}
		} else if (getGlobalTraceId() != null) {
			
			// propagate existing header fields
			
			Map<String, String> headers = Service.getHttpRequestHeader().getFieldsMap();
			
			CarrierItem next = contextCarrier.items();
	        
			while (next.hasNext()) {
				next = next.next();
				String v = headers.get(next.getHeadKey());
				
				System.out.println("looking for " + next.getHeadKey() + " = " + v);
				
				if (v != null) {
					next.setHeadValue(v);
				}
			}
		}
		
		return contextCarrier;
	}
	
	public static String getFullyQualifiedName() {
		
		if (ContextManager.isActive()) {
			return getFullyQualifiedName(ContextManager.activeSpan());
		} else {
			return null;
		}
	}
	
	public static String getFullyQualifiedName(AbstractSpan span) {
	
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
	
	public static BizDocWrapper getBizdocFromPipeline(IData pipeline) {
		    
	    	IDataCursor c = pipeline.getCursor();
	    	IData bizDoc = IDataUtil.getIData(c, "bizdoc");
	    	c.destroy();
	    	
	    	return bizDoc != null ? new BizDocWrapper(bizDoc) : null;
	}
	 
	public static String getGlobalTraceId(IData pipeline) {
		
		BizDocWrapper bizdoc = getBizdocFromPipeline(pipeline);
		
		if (bizdoc == null || bizdoc.getGlobalTracingId() == null) {
			return getGlobalTraceId();
		} else {
			return bizdoc.getGlobalTracingId();
		}
	}

	public static String getGlobalTraceId(BizDocWrapper bizdoc) {
		
		String id = bizdoc.getGlobalTracingId();
		
		if (id == null) {
			return getGlobalTraceId();
		} else {
			return id;
		}
	}
	
	public static String getGlobalTraceId() {
		
		if (ContextManager.getGlobalTraceId() != "N/A") {
			
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
}
