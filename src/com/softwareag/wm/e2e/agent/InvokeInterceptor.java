package com.softwareag.wm.e2e.agent;

import java.util.*;

import com.wm.app.b2b.server.Service;
import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.InvokeException;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.ServiceException;
import com.wm.app.b2b.server.invoke.InvokeChainProcessor;
import com.wm.app.b2b.server.invoke.InvokeManager;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;
import com.wm.lang.ns.NSService;
import com.wm.net.HttpHeader;
import com.wm.util.ServerException;


/**
 * Implements the webMethods InvokeChainProcessor interface to allow services
 * to be intercepted.
 * 
 * Use static register() method below to register this class as part of the invoke chain
 * alternatively you can update the <SAG_HOME>/IntegrationServer/config/invokemananger.cnf file and add
 * the class of the interceptor class. 
 * In addition the classes must be loaded at startup so ensure that the class is packaged in a static jar
 * 
 * @author John Carter (john.carter@softwareag.com)
 */
public class InvokeInterceptor implements InvokeChainProcessor {
		 
	public static final String 			WM_ENDTOEND_TRANSACTION_ID = "wm-etoe-transaction-id";
	public static final String 			WM_ROOT_CONTEXT_ID = "wm-root-context-id";

	/** 
	 * Used to identify the webMethods root context id based in runtime-attribute array
	 * returned by InvokeState. Attention this will have to be tested for each webMethods
	 * version as this is not official.
	 */
	public static final int				WM_ROOT_CONTEXT_ID_INDEX = 0;
	
    private static InvokeInterceptor    _default;
    
    public static void register() {
    	
    	InvokeManager.getDefault().registerProcessor(new InvokeInterceptor());
    }
    
    public static void unregister() {
    	InvokeManager.getDefault().unregisterProcessor(_default);
    }
    
    public InvokeInterceptor() {
    	
        System.out.println("Instantiating com.softwareag.wm.e2e.agent.InvokeInterceptor");
        _default = this;
        
        _default.startup();
    }
    
    /**
     * Ensures that the default interceptor is properly configured and that
     * all context map are instantiated and hooked up to the backing store
     * 
     * @throws com.wm.app.b2b.server.ServiceException
     */
    public static void restart() throws ServiceException {
        if (_default != null)
            _default.startup();
    }
    
    /**
     * Configures the InvokeInterceptor and ensures the persistence store for
     * storing suspended services is instantiated and working properly
     * 
     * @throws com.wm.app.b2b.server.ServiceException
     */
    private void startup() {
       // TODO:
    }
    
    public void process(@SuppressWarnings("rawtypes") Iterator chain, BaseService baseService, IData pipeline, ServiceStatus status) throws ServiceException {
       
    	String traceId = getEtoETransactionId();
    	String rootContextId = getRootContextId(status); // will NOT force root context id to header value identified by WM_ENDTOEND_TRANSACTION_ID
    	String parentContextId = getParentContextId(status); // will be same as root context id if this service is top level!
    	
        String name = getServiceName(baseService); 
        
        try {   	
            if(chain.hasNext()) {
                
                if (chainPreProcessor(traceId, rootContextId, parentContextId, name, baseService, pipeline)) {
                	
                	//TODO: Do we want to make audit destination one of file, db or external monitoring or should it be a complement
                	
                	// ASSUMPTION: we logged via etoe, so disable internal logging, don't need to monitor locally.
                	//baseService.setAuditOption(BaseService._AUDIT_OFF);
                }
                
                HttpHeader header = Service.getHttpRequestHeader();
                
                if (name.equals("pub.client:http") && Service.getHttpHeaderField(WM_ENDTOEND_TRANSACTION_ID, header) != null) {
                	IDataCursor c = pipeline.getCursor();
                	IData headers = IDataUtil.getIData(c, "headers");
                	IDataCursor hc = headers.getCursor();

                	IDataUtil.put(hc, WM_ENDTOEND_TRANSACTION_ID, Service.getHttpHeaderField(WM_ENDTOEND_TRANSACTION_ID, header));
                	hc.destroy();
                	c.destroy();
                }
                
                ((InvokeChainProcessor)chain.next()).process(chain, baseService, pipeline, status);
                
                // service success
                
                chainPostProcessor(traceId, rootContextId, parentContextId, name, baseService, pipeline, status);
            }
        } catch (InvokeException error) {

        	// service exceptions arrive here
        	chainPostProcessor(rootContextId, name, baseService, pipeline, error);
        	
        	throw new ServiceException(error);
		} catch (ServerException error) {
			throw new ServiceException(error);
		}
    }
    
    /**
     * Either uses wm context id or that provided by end to end monitoring (in which case it updates the invoke status to use it
     * This occurs after the start step of auditing so the IS audit will have it's own unique id, rather than the etoe id, end and error
     * steps will be okay as well as any invoked services.
     * 
     * @param status references current runtime info (but not audit settings strangely)
     * @return the current context id of the service
     */
    protected String getRootContextId(ServiceStatus status) {
        
    	if (status.isTopService()) {
    		
    		// do we have an existing root context id, propagated from another IS server
    		
    		if (getSharedRootContextId() != null) {
    			
    			String id = getSharedRootContextId();
                setRootContextIdForThread(id);

            	return id;
    		} else {
        		return Service.getCurrentActivationID();
    		}
    		
    	} else {

    		return InvokeInterceptor.getContextIDsForService()[0];
    	}
    }
    
    protected String getCustomContextId() {
    	
    	return InvokeInterceptor.getContextIDsForService()[3];
    }
    
    protected String getParentContextId(ServiceStatus status) {
        	
    	if (status.isTopService()) {
    		return Service.getCurrentActivationID();
    	} else {

    		return InvokeInterceptor.getContextIDsForService()[1];
    	}
    }

    protected String getServiceName(BaseService baseService) {
        return baseService.getNSName().getFullName();
    }
    
    protected String getEtoETransactionId() {
        // TODO: pull from header
    	
    	HttpHeader header = Service.getHttpRequestHeader();
    	return Service.getHttpHeaderField(WM_ENDTOEND_TRANSACTION_ID, header);
    }
    
    protected String getSharedRootContextId() {
        // TODO: pull from header
    	
    	HttpHeader header = Service.getHttpRequestHeader();
    	return Service.getHttpHeaderField(WM_ROOT_CONTEXT_ID, header);
    }
  
    
    /**
     * Entry point for end to end monitoring to allow service start to be logged if required
     * 
     * @return true if end to end monitoring is activated, false if not
     */
    protected boolean chainPreProcessor(String traceId, String rootContextId, String parentContextId, String serviceName, BaseService baseService, IData pipeline) {
    	
    	boolean didLog = false;
    			
    	// base auditing on requirements from audit sub-system    
    	 
    	if (serviceName.startsWith("wm.tn")) {
    		
    		// TN is a special case, won't do this for any other subproducts, honest!

    		if (serviceName.equals("wm.tn.route:route")) {
        		// processing rule will be called
    			
    			//ContextManager.createLocalSpan(("/b2b/" + getBizDocDocId(pipeline)));
    			
    		} else if (serviceName.equals("wm.tn:log")) {
    			// custom logging
    			
    		} else if (serviceName.equals("wm.tn.route:invoke")) {
    			// wrapper for processing rule service
    			
    		} else if (serviceName.equals("wm.tn.delivery:deliver")) {
    			// wrapper for delivery method
    			
    		}
    		
    	} else if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isStartAuditEnabled()) {
    		
    		System.out.println("Processing start " + serviceName + " / " + traceId + " - " + rootContextId);
    		
    		String[][] businessDataKeys = baseService.getInputAuditFields();
    		
    		Map<String, Object> businessData = extractDataFromPipeline(businessDataKeys, pipeline);
    		
    		//TODO: 
    		
    		System.out.println("no of keys: " + businessData.size());
    		
    		/*if (ContextManager.isActive()) {
    			
    			System.out.println("Creating local span under " + ContextManager.activeSpan().getOperationName() + " for " + serviceName);

    			AbstractSpan span = ContextManager.createLocalSpan(serviceName);
    			
    			final ContextSnapshot context = ContextManager.capture();
		        
			     System.out.println("Context: " + context.getTraceId());
			     
    		} else {
    			System.out.println("Creating entry span for " + serviceName);

    			HttpHeader headers = Service.getHttpRequestHeader();
                
    			ContextCarrier contextCarrier = new ContextCarrier();
                CarrierItem next = contextCarrier.items();
                while (next.hasNext()) {
                    next = next.next();
                    next.setHeadValue(headers.getFieldValue(next.getHeadKey()));
                }

                AbstractSpan span = ContextManager.createEntrySpan(serviceName, contextCarrier);
    			// only if a valid span is created, should we populate the rest of span
    			
    			if (ContextManager.isActive()) {
    				System.out.println("Created successfully");
    				
    				 final ContextSnapshot context = ContextManager.capture();
    			        
    			     System.out.println("Context: " + context.getTraceId());
    			}
    		}*/
    		
    		didLog = true;
    	}
    	
    	return didLog;
    }
    
   
    /**
     *
     */
    protected void chainPostProcessor(String traceId, String rootContextId, String parentContextId, String serviceName, BaseService baseService, IData pipeline, ServiceStatus status) {
    	    	
    	if (serviceName.startsWith("wm.tn")) {
    		
    		// TN is a special case, won't do this for any other subproducts, honest!

    		if (serviceName.equals("wm.tn.route:route")) {
        		// processing rule will be called
    			
    			//ContextManager.stopSpan();
    		} else if (serviceName.equals("wm.tn:log")) {
    			// custom logging
    			
    		} else if (serviceName.equals("wm.tn.route:invoke")) {
    			// wrapper for processing rule service
    			
    		} else if (serviceName.equals("wm.tn.delivery:deliver")) {
    			// wrapper for delivery method
    			
    		}
    	} else if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isCompleteAuditEnabled()) {
    			
    			// TODO: report success
    			
    			String customContextId = getCustomContextId();
    			
        		System.out.println("Processing completion " + serviceName + " / " + traceId + " - " + rootContextId + " : " + customContextId);

        		String[][] businessDataKeys = baseService.getOutputAuditFields();
        		
        		Map<String, Object> businessData = extractDataFromPipeline(businessDataKeys, pipeline);
        		
        		//TODO:
        		
        		System.out.println("no of keys: " + businessData.size());
        		
        		/*if (ContextManager.isActive()) {
    				ContextManager.stopSpan();
    			}*/
    	} 
    }
    
    /**
    *
    */
   protected void chainPostProcessor(String integrationId, String serviceName, BaseService baseService, IData pipeline, InvokeException e) {
   	    	  		   		   				
   		// TODO: report here!
	   	   
	   if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isErrorAuditEnabled()) {
	   		System.out.println("Processing error " + serviceName + " / " + integrationId);   
	   		
	   		/*if (ContextManager.isActive()) {
				ContextManager.activeSpan().errorOccurred().log(e);
			}*/
	   }		   
   }
     
   
   /**
    * Extract given keys from pipeline
    */
   
   protected Map<String, Object> extractDataFromPipeline(String[][] keys, IData pipeline) {
	   
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
   
   private Object _extractDataFromPipeline(String xpath, IData doc)
   {
	   return _extractDataFromPipeline(new StringTokenizer(xpath, "/"), doc);
   }
   
   private Object _extractDataFromPipeline(StringTokenizer path, IData doc) {
       
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
    
    protected static String[] getContextIDsForService() {
        
    	String[] contextIDs = {null, null, null, null};

        try {
            InvokeState currentInvokeState = InvokeState.getCurrentState();
            String contextIDStack[] = currentInvokeState.getAuditRuntime().getContextStack();

            String contextId = null;
            String parentContextId = null;

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

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return contextIDs;
    }
    
     /**
      * Forces root context ID to given value
      * Unfortunately audit start event occurs before the class in
      * the invocation chain and will record wm value
      * 
      * activationId must be a valid UUID v1 String
      * e.g. f10f14ac-8297-11eb-8dcd-0242ac130003
      */
	 @SuppressWarnings("unused")
	private static void setRootContextIdForThread(String activationId) {
         
		 if (activationId == null) {
			 return;
		 }
		 
		 		 		 
		 InvokeState is = InvokeState.getCurrentState();
               
          if (is != null)
          {
              String[] args = null;
 
              if (is.getAuditRuntime() != null) {
            	  
            	  args = is.getAuditRuntime().getContextStack();
                  
                  System.out.println("alternatively it is " + args[WM_ROOT_CONTEXT_ID_INDEX]);
                  
                  if (args.length <= WM_ROOT_CONTEXT_ID_INDEX)
                      args = new String[WM_ROOT_CONTEXT_ID_INDEX+1];
                  
                  args[WM_ROOT_CONTEXT_ID_INDEX] = activationId;
                  
                  InvokeState.getCurrentState().getAuditRuntime().setContextStack(args);
              }
          }
     }
	 
	 private String getBizDocDocId(IData pipeline) {
		 
		 IDataCursor c = pipeline.getCursor();
		 IData bizdoc = IDataUtil.getIData(c, "bizdoc");
		 IDataCursor bc = bizdoc.getCursor();
		 String bizdocId = IDataUtil.getString(bc, "DocumentID");
		 bc.destroy();
		 c.destroy();
		 
		 return bizdocId;
	 }
}
