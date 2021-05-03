package com.softwareag.wm.e2e.agent.skywalking;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class AgentConfig {

	public static class Plugin {
        // NOTE, if move this annotation on the `Plugin` or `SpringMVCPluginConfig` class, it no longer has any effect. 
        @PluginConfig(root = AgentConfig.class)
        public static class ISAgent {
            /**
             * If true, the fully qualified method name will be used as the endpoint name instead of the request URL,
             * default is false.
             */
            public static boolean USE_QUALIFIED_NAME_AS_ENDPOINT_NAME = false;

            /**
             * This config item controls that whether the SpringMVC plugin should collect the parameters of the
             * request.
             */
            public static boolean COLLECT_HTTP_PARAMS = true;
        }
    }
}
