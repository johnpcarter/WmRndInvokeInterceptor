package com.softwareag.wm.e2e.agent.skywalking;

import org.apache.skywalking.apm.agent.core.context.SW6CarrierItem;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class TriggerTools {

	public static final String PUB_ENV_ATTRIB_TRACE_ID = "transactionId";

	public static final String getGlobalTraceId(IData pipeline) {
		
		String t = null;
		IDataCursor c = pipeline.getCursor();
		
		while(c.hasMoreData()) {
			c.next();
			if (c.getKey().equals("JMSMessage")) {
				t = extractGlobalTraceIdFromJMSMessage((IData) c.getValue());
			} else if (c.getValue() instanceof IData) {
				t = lookForTransactionIdInMessagingEnvelope((IData) c.getValue());
				
				if (t != null) 
					break;
			}
		}
		
		return t;
	}
	
	public static final void updatePublishDocWithGlobalTraceId(IData publishDocument, IData skywHeader) {
	
    	IDataCursor dc = publishDocument.getCursor();
    	IData env = IDataUtil.getIData(dc, "_env");
    	
		if (env == null) {
    		env = IDataFactory.create();
    	}
    	
    	IDataCursor ec = env.getCursor();
    	IDataCursor hc = skywHeader.getCursor();
    	
    	IDataUtil.put(ec, TriggerTools.PUB_ENV_ATTRIB_TRACE_ID, IDataUtil.get(hc, SW6CarrierItem.HEADER_NAME));

    	IDataUtil.put(dc, "_env", env);
    	hc.destroy();
    	ec.destroy();
    	dc.destroy();
	}
	
	public static final void updateJMSPublishDocWithGlobalTraceId(IData publishDocument, IData skywHeader) {
		
    	IDataCursor dc = publishDocument.getCursor();
    	IData properties = IDataUtil.getIData(dc, "properties");
    	
		if (properties == null) {
			properties = IDataFactory.create();
    	}
    	
    	IDataCursor ec = properties.getCursor();
    	IDataCursor hc = skywHeader.getCursor();
    	
    	IDataUtil.put(ec, TriggerTools.PUB_ENV_ATTRIB_TRACE_ID, IDataUtil.get(hc, SW6CarrierItem.HEADER_NAME));

    	IDataUtil.put(dc, "properties", properties);
    	hc.destroy();
    	ec.destroy();
    	dc.destroy();
	}
	
	private static final String lookForTransactionIdInMessagingEnvelope(IData doc) {
		
		String t = null;
		IDataCursor c = doc.getCursor();
		IData env = IDataUtil.getIData(c, "_env");
		
		if (env != null) {
			IDataCursor ec = env.getCursor();
			t = IDataUtil.getString(ec, PUB_ENV_ATTRIB_TRACE_ID);
			ec.destroy();
		}
		
		c.destroy();
		
		return t;
	}
	
	private static final String extractGlobalTraceIdFromJMSMessage(IData doc) {
		
		String t = null;
		
		IDataCursor dc = doc.getCursor();
    	IData properties = IDataUtil.getIData(dc, "properties");
    	
		if (properties != null) {
			t = IDataUtil.getString(dc, TriggerTools.PUB_ENV_ATTRIB_TRACE_ID);
		}
		
		return t;
	}
}
