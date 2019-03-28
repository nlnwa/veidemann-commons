/*
 * Copyright 2019 National Library of Norway.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.nb.nna.veidemann.commons.auth;

import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import no.nb.nna.veidemann.api.config.v1.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

/**
 * Add authorization to all requests made to this service.
 * <p>
 * This interceptor is meant to be inserted after one or more {@link AuAuServerInterceptor}s
 * which map credentials into a list of roles.
 */
public class AuthorisationAuAuServerInterceptor extends AuAuServerInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorisationAuAuServerInterceptor.class);

    public static final Listener NOOP_LISTENER = new Listener() {
    };

    private Grants grants = new Grants();

    public AuthorisationAuAuServerInterceptor() {
    }

    /**
     * Add authorization to all requests made to this service.
     *
     * @param bindableService to intercept
     * @return the serviceDef with an authorization interceptor
     */
    @Override
    public ServerServiceDefinition intercept(BindableService bindableService) {
        return intercept(bindableService.bindService(), bindableService.getClass());
    }

    /**
     * Add authorization to all requests made to this service.
     *
     * @param serviceDef to intercept
     * @return the serviceDef with an authorization interceptor
     */
    public ServerServiceDefinition intercept(ServerServiceDefinition serviceDef) {
        return intercept(serviceDef, serviceDef.getClass());
    }

    private ServerServiceDefinition intercept(ServerServiceDefinition serviceDef, Class serviceClass) {
        String serviceName = serviceDef.getServiceDescriptor().getName();
        Arrays.stream(serviceClass.getDeclaredMethods())
                .forEach(m -> grants.addGrants(
                        serviceName + "/" + m.getName().substring(0, 1).toUpperCase() + m.getName().substring(1),
                        m.getDeclaredAnnotation(AllowedRoles.class)));
        return ServerInterceptors.intercept(serviceDef, this);
    }

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata requestHeaders,
            ServerCallHandler<ReqT, RespT> next) {

        String method = call.getMethodDescriptor().getFullMethodName();
        LOG.debug("Method: {}", method);

        Collection<Role> roles = getRoleList();

        // All users should have the ANY role. Even if their not logged in.
        roles.add(Role.ANY);

        // Check if user is logged in
        if (!roles.contains(Role.ANY_USER)) {
            if (grants.isRequireAuthenticatedUser(method)) {
                call.close(Status.UNAUTHENTICATED, new Metadata());
                return NOOP_LISTENER;
            } else {
                Context contextWithEmailAndRoles = Context.current()
                        .withValue(RolesContextKey.getKey(), roles);
                return Contexts.interceptCall(contextWithEmailAndRoles, call, requestHeaders, next);
            }
        }

        // Check if user has required role
        if (!grants.isAllowed(method, roles)) {
            call.close(Status.PERMISSION_DENIED, new Metadata());
            return NOOP_LISTENER;
        }

        return next.startCall(call, requestHeaders);
    }
}
