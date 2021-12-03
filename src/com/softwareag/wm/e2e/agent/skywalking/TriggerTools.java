package com.softwareag.wm.e2e.agent.skywalking;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.SW6CarrierItem;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class TriggerTools {

	public static final String PUB_ENV_ATTRIB_TRACE_ID = "transactionId";

	public static final Map<String, String> getGlobalTraceId(IData pipeline) {
		
		IData t = null;
		IDataCursor c = pipeline.getCursor();
		HashMap<String, String> vals = new HashMap<String, String>();
		
		while(c.hasMoreData()) {
			c.next();
			if (c.getKey().equals("JMSMessage")) {
				t = extractGlobalTraceIdFromJMSMessage((IData) c.getValue());
				break;
			} else if (c.getValue() instanceof IData) {
				t = lookForTransactionIdInMessagingEnvelope((IData) c.getValue());
				
				if (t != null) 
					break;
			}
		}
		
		if (t != null) {
			IDataCursor tc = t.getCursor();
			while(tc.hasMoreData()) {
				tc.next();
				if (tc.getValue() instanceof String) {
					vals.put(tc.getKey(), (String) tc.getValue());
				}
			}
			
			tc.destroy();
		}
		
		return vals;
	}
	
	public static final void updatePublishDocWithGlobalTraceId(IData publishDocument, Map<String, String> swHeader) {
	
    	IDataCursor dc = publishDocument.getCursor();
    	IData env = IDataUtil.getIData(dc, "_env");
    	
		if (env == null) {
    		env = IDataFactory.create();
        	IDataUtil.put(dc, "_env", env);
    	}
    	
    	IDataCursor ec = env.getCursor();
    	
    	//for (String swKey : swHeader.keySet()) {
    		IDataUtil.put(ec, TriggerTools.PUB_ENV_ATTRIB_TRACE_ID, swHeader.get(SW6CarrierItem.HEADER_NAME));
    	//}
    	    	
    	ec.destroy();
    	dc.destroy();
	}
	
	public static final void updateJMSPublishDocWithGlobalTraceId(IData publishDocument, Map<String, String> swHeader) {
		
    	IDataCursor dc = publishDocument.getCursor();
    	IData properties = IDataUtil.getIData(dc, "properties");
    	
		if (properties == null) {
			properties = IDataFactory.create();
			IDataUtil.put(dc, "properties", properties);
    	}
    	
    	IDataCursor ec = properties.getCursor();
    	
    	for (String swKey : swHeader.keySet()) {
    		IDataUtil.put(ec, swKey, swHeader.get(swKey));
    	}

    	ec.destroy();
    	dc.destroy();
	}
	
	private static final IData lookForTransactionIdInMessagingEnvelope(IData doc) {
		
		String t = null;
		IDataCursor c = doc.getCursor();
		IData env = IDataUtil.getIData(c, "_env");
		c.destroy();
		
		return env;
	}
	
	private static final IData extractGlobalTraceIdFromJMSMessage(IData doc) {
		
		String t = null;
		
		IDataCursor dc = doc.getCursor();
    	IData properties = IDataUtil.getIData(dc, "properties");
    	dc.destroy();
		
		return properties;
	}
}
