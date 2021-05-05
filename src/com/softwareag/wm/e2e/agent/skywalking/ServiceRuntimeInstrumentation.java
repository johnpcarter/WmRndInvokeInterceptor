package com.softwareag.wm.e2e.agent.skywalking;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.description.method.MethodDescription;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher;

import com.wm.app.b2b.server.AuditLogManager;

import static org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class ServiceRuntimeInstrumentation extends ClassEnhancePluginDefine {
	
	private static final String INTERCEPT_CLASS = "com.wm.app.b2b.server.AuditLogManager";
	//private static final String INTERCEPT_CLASS = "com.wm.app.b2b.server.invoke.DispatchProcessor";
    private static final String CALLABLE_CALL_METHOD = "process";
    private static final String CALLABLE_CALL_METHOD_INTERCEPTOR = "com.softwareag.wm.e2e.agent.skywalking.ServiceRuntimeInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
    	
    	System.out.println("** IS AGENT ** - Setting up AuditLogManager agent");
    	
    	return byName(INTERCEPT_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(CALLABLE_CALL_METHOD).and(takesArguments(4));
                }

                @Override
                public String getMethodsInterceptor() {
                    return CALLABLE_CALL_METHOD_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
    	    	
        return new StaticMethodsInterceptPoint[0];
    }

    /*@Override
    public boolean isBootstrapInstrumentation() {
        return true;
    }*/
}