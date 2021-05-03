package com.softwareag.wm.e2e.agent.skywalking;


import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
//import org.apache.skywalking.apm.agent.core.plugin.uhm;

import com.wm.app.b2b.server.HTTPState;

public class Utils {

	public static void startEntrySpan(String serviceName, HTTPState gState) {
		try {
			ContextCarrier contextCarrier = createContextCarrier(gState);

			// start a entry span to denote that we have entered into IS
			AbstractSpan span = ContextManager.createEntrySpan(serviceName, contextCarrier);
			// only if a valid span is created, should we populate the rest of span
			
			if (ContextManager.isActive()) {
				System.out.println("** IS AGENT ** - Created the Entry span..." + span.getOperationName());

				populateSpanData(serviceName, gState, span);

				SpanLayer.asHttp(span);
			}
		} catch (Exception t) {
			
		}
	}
	
	/**
	 * Populates all data in given span from http object.
	 * 
	 * @param gState - current HTTPState
	 * @param span   - current active span
	 */
	public static void populateSpanData(String serviceName, HTTPState gState, AbstractSpan span) {
		// decorate the span with tags
		
		//Tags.UHM.OPERATION_NAME.set(span, serviceName);
		Tags.URL.set(span, serviceName);
		Tags.HTTP.METHOD.set(span, gState.getRequestTypeAsString());
		
		//Tags.UHM.FULLY_QUALIFIED_NAME.set(span, serviceName);
		//Tags.UHM.STAGE.set(span, gState.getInvokeState().getStageID());
		//Tags.UHM.TENANT_ID.set(span, getTenantID(gState));
		
		/*if (Config.Agent.SERVICE_NAME != null) {
			Tags.UHM.COMPONENT.set(span, Config.Agent.SERVICE_NAME.trim());
		} else {
			Tags.UHM.COMPONENT.set(span, "");
		}*/
	}
	

	/**
	 * Creates contextCarrier with given http request.
	 * 
	 * @param gState
	 * @return
	 * @throws ParseException
	 */
	public static ContextCarrier createContextCarrier(HTTPState gState) {
		// get http headers from gState of httpDispatch
		Map<String, String> reqHeaders = gState.getReqHdr().getFieldsMap();
		String sw6Value = reqHeaders.get(SW8CarrierItem.HEADER_NAME);
		
		// check if value is present for the sw6 header or not
		// if not then check if it has X-WMIC-EXECUTION-CONTROL-PARAMETERS-AS-JSON
		// header from WMIC-CTP
		/*String wmicExecutionParamHeader = reqHeaders.get(WMIC_EXECUTION_PARAM_HEADER_KEY);
		if (!Strings.isNullOrEmpty(wmicExecutionParamHeader)) {
			JSONParser parser = new JSONParser();
			JSONObject json = (JSONObject) parser.parse(EncodeURL.decode(wmicExecutionParamHeader));
			sw6Value = (String) json.get(SW6CarrierItem.HEADER_NAME);
			logger.debug("sw6 value in wmic execution param header " + sw6Value);
		} else {
			logger.debug("sw6 value is null in wmic execution param header ");
		}*/

		// create a context carrier with incoming request headers (we are looking for
		// sw6 header key)
		ContextCarrier contextCarrier = new ContextCarrier();
		CarrierItem contextCarrierItem = contextCarrier.items();
		while (contextCarrierItem.hasNext()) {
			contextCarrierItem = contextCarrierItem.next();
			contextCarrierItem.setHeadValue(sw6Value);
		}
		return contextCarrier;
	}

}
