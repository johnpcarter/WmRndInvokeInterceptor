package com.softwareag.wm.e2e.agent.skywalking;


import java.util.Map;
import java.util.StringTokenizer;

import static java.util.Objects.nonNull;

import java.util.HashMap;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
//import org.apache.skywalking.apm.agent.core.plugin.uhm;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
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
	
	private ServiceUtils() {
		// do nothing
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

	public static void stopSpan(String entityName) {
	
		if (ContextManager.isActive() && ContextManager.activeSpan().getOperationName().contentEquals(entityName)) {
			stopSpan();
		}
		
	}
	
	/**
	 * Stops the active entry span.
	 */
	public static void stopSpan() {
		try {
			if (ContextManager.isActive()) {
				// always stop the span, no matter what type
				
				System.out.println("span closed " + ContextManager.activeSpan().getOperationName());
				ContextManager.stopSpan();
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
	}

	/**
	 * Discards the active entry span.
	 */
	/*public static void discardEntrySpan() {
		try {
			// if the TxID is not set then we need to discard this span and this TxID should
			// be set
			// for WMIC-IS by EventEmitter.emitEvent()
			// for API by BaseCollectionManager.publish()
			if (!DEVELOPER_MODE && ContextManager.isActive() && nonNull(ContextManager.activeSpan())
					&& !ServiceUtils.isTransactionIDAvailable(ContextManager.activeSpan())) {
								
				//ContextManager.discardActiveSpan(); // added by SAG
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
	}*/

	/**
	 * Starts an entry span from the HTTPState
	 * 
	 * @param gState - current HTTPState
	 * @throws ParseException 
	 */
	public static void startEntrySpan(String entityName, String customTransactionId, InvokeState state) throws ParseException {
			
		ContextCarrier contextCarrier = createContextCarrier(null);

		// start a entry span to denote that we have entered into IS
		AbstractSpan span = ContextManager.createEntrySpan(entityName, contextCarrier);
				
		//ContextManager.inject(contextCarrier); // sw6 key is only required on exitspan
				
		// only if a valid span is created, we should populate the rest of span
		if (!(ContextManager.activeSpan() instanceof NoopSpan)) {

			populateSpanData(span, entityName, customTransactionId, state);

			SpanLayer.asHttp(span);
		}
	}

	 /* Starts an entry span from the HTTPState
	 * 
	 * @param gState - current HTTPState
	 * @throws ParseException 
	 */
	public static void startEntrySpan(String entityName, String transactionId) throws ParseException {
			
		ContextCarrier contextCarrier = createContextCarrier(transactionId);

		// start a entry span to denote that we have entered into IS
		AbstractSpan span = ContextManager.createEntrySpan(entityName, contextCarrier);
				
	}
	 
	public static void startLocalSpan(String entityName) {

		AbstractSpan span = ContextManager.createLocalSpan(entityName);
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
	 * Helper method to find if TransactionID is available in a span. It will loop
	 * through all the tags and find if it has "txn" key present & it has some value
	 * 
	 * @param activeSpan - currently active span
	 * @return
	 *
	private static boolean isTransactionIDAvailable(AbstractSpan activeSpan) {
		
		activeSpan.of
		List<TagValuePair> tags = activeSpan.getTags();
		if (CollectionUtils.isNotEmpty(tags)) {
			for (TagValuePair tag : tags) {
				if (tag.getKey().key().equalsIgnoreCase(Tags.UHM.TRANSACTION_ID.key()) && null != tag.getValue()
						&& !tag.getValue().isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}*/
	
	/**
	 * Populates all data in given span from http object.
	 * 
	 * @param gState - current HTTPState
	 * @param span   - current active span
	 */
	public static void populateSpanData(AbstractSpan span, String entityName, String customTransactionId, InvokeState gState) {
		// decorate the span with tags
		
		//Tags.URL.set(span, Utils.sanitizeFullyQualifiedName(gState.getRequestUrl()));
		//Tags.HTTP.METHOD.set(span, gState.getRequestTypeAsString());
		
		span.tag(CustomTags.OPERATION_NAME, entityName);
		span.tag(CustomTags.FULLY_QUALIFIED_NAME, entityName);
		
		if (customTransactionId != null) {
			span.tag(CustomTags.CUSTOM_TRANSACTION_ID, customTransactionId);
		}
		
		span.tag(CustomTags.STAGE, gState.getStageID());
		span.tag(CustomTags.TENANT_ID, getTenantID(gState));

		span.tag(CustomTags.COMPONENT, "Integration");
	}

	/**
	 * Creates contextCarrier with given http request.
	 * 
	 * @param gState
	 * @return
	 * @throws ParseException
	 */
	public static ContextCarrier createContextCarrier(String transactionId) throws ParseException {
		
		ContextCarrier contextCarrier = new ContextCarrier();
		
		if (transactionId != null) {
			
			// used for non http clients such as UM etc.
			
			CarrierItem next = contextCarrier.items();
	        
			while (next.hasNext()) {
				next = next.next();

				if (next.getHeadKey().contentEquals(SW8CarrierItem.HEADER_NAME)) {
					next.setHeadValue(transactionId);
				}
			}
		} else if (getTraceId() != null) {
			
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

	public static String getTraceId() throws ParseException {
		
		if (ContextManager.getGlobalTraceId() != "N/A") {
			
			return ContextManager.getGlobalTraceId();
		} else {
			// get http headers from gState of httpDispatch
			
			Map<String, String> reqHeaders = Service.getHttpRequestHeader().getFieldsMap();
			String sw6Value = reqHeaders.get(SW8CarrierItem.HEADER_NAME);
			logger.debug("sw6 value in req header " + sw6Value);
		
			// check if value is present for the sw6 header or not
			// if not then check if it has X-WMIC-EXECUTION-CONTROL-PARAMETERS-AS-JSON
			// header from WMIC-CTP
				
			if (sw6Value == null) {
				String wmicExecutionParamHeader = reqHeaders.get(WMIC_EXECUTION_PARAM_HEADER_KEY);
			
				if (!Strings.isNullOrEmpty(wmicExecutionParamHeader)) {
					JSONParser parser = new JSONParser();
					JSONObject json = (JSONObject) parser.parse(EncodeURL.decode(wmicExecutionParamHeader));
					sw6Value = (String) json.get(SW8CarrierItem.HEADER_NAME);
					logger.debug("sw6 value in wmic execution param header " + sw6Value);
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
