package com.softwareag.wm.e2e.agent.skywalking.uhm;

public class SegmentTraceDataFieldKeys {

	// UPDATE ALL THESE STATUS IN Tags.java present in agent-core
	public static final String FULLY_QUALIFIED_NAME = "fqn";
	public static final String STAGE = "stg";
	public static final String ERROR_MSG = "err";
	public static final String TRANSACTION_STATUS = "stat";
	public static final String TRANSACTION_ID = "txn";
	public static final String CUSTOM_TRANSACTION_ID = "txn_custom";
	public static final String COMPONENT = "cmp";
	public static final String DURATION = "time";
	public static final String TENANT_ID = "tId";
	public static final String LEVEL = "lvl";
	public static final String OPERATION_NAME = "op";
	public static final String LANDSCAPE = "lscp";
	public static final String PARENT_LANDSCAPE = "plscp";
	public static final String URL = "url";
	public static final String REQUEST_ID = "requestID";
	
	public static final String TRACE_DATA = "trace_data";
	public static final String NAME_SEPARATOR_FOR_ES_FIELDS = ".";
	
	public static final class SegmentTraceDataFullyQualifiedFieldName {
		public static final String STATUS = TRACE_DATA + NAME_SEPARATOR_FOR_ES_FIELDS
					+ SegmentTraceDataFieldKeys.TRANSACTION_STATUS;
		public static final String LEVEL = TRACE_DATA + NAME_SEPARATOR_FOR_ES_FIELDS + SegmentTraceDataFieldKeys.LEVEL;
		public static final String TENANT = TRACE_DATA + NAME_SEPARATOR_FOR_ES_FIELDS
					+ SegmentTraceDataFieldKeys.TENANT_ID;
		public static final String COMPONENT = TRACE_DATA + NAME_SEPARATOR_FOR_ES_FIELDS
					+ SegmentTraceDataFieldKeys.COMPONENT;
		public static final String COMPONENT_KEYWORD = TRACE_DATA + NAME_SEPARATOR_FOR_ES_FIELDS
					+ SegmentTraceDataFieldKeys.COMPONENT + ".keyword";
		public static final String OPERATION_NAME_KEYWORD = TRACE_DATA + NAME_SEPARATOR_FOR_ES_FIELDS
					+ SegmentTraceDataFieldKeys.OPERATION_NAME + ".keyword";
		public static final String OPERATION_NAME = TRACE_DATA + NAME_SEPARATOR_FOR_ES_FIELDS
					+ SegmentTraceDataFieldKeys.OPERATION_NAME;
	}		
}
