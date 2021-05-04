package com.softwareag.wm.e2e.agent.skywalking;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;

public class TriggerTools {

	public static final String getTransactionId(IData pipeline) {
		
		String t = null;
		IDataCursor c = pipeline.getCursor();
		
		while(c.hasMoreData()) {
			c.next();
			if (c.getValue() instanceof IData) {
				t = lookInDoc((IData) c.getValue());
				
				if (t != null) 
					break;
			}
		}
		
		return t;
	}
	
	public static final String lookInDoc(IData doc) {
		
		String t = null;
		IDataCursor c = doc.getCursor();
		IData env = IDataUtil.getIData(c, "_env");
		
		if (env != null) {
			IDataCursor ec = env.getCursor();
			t = IDataUtil.getString(ec, "transactionId");
			ec.destroy();
		}
		
		c.destroy();
		
		return t;
	}
}
