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

import com.nimbusds.jwt.JWTClaimsSet;
import io.grpc.Attributes;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import net.minidev.json.JSONArray;
import no.nb.nna.veidemann.api.config.v1.Role;
import no.nb.nna.veidemann.api.eventhandler.v1.Data;
import no.nb.nna.veidemann.api.eventhandler.v1.EventHandlerGrpc;
import no.nb.nna.veidemann.api.eventhandler.v1.EventObject;
import no.nb.nna.veidemann.api.eventhandler.v1.EventObject.Severity;
import no.nb.nna.veidemann.api.eventhandler.v1.ListCountResponse;
import no.nb.nna.veidemann.api.eventhandler.v1.ListRequest;
import no.nb.nna.veidemann.api.eventhandler.v1.SaveRequest;
import no.nb.nna.veidemann.commons.client.EventHandlerClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthorisationAuAuServerInterceptorTest {

    private final String uniqueServerName = "in-process server for " + getClass();

    private InProcessServerBuilder inProcessServerBuilder;

    private ManagedChannel inProcessChannel;

    private EventHandlerGrpc.EventHandlerBlockingStub blockingStub;

    @Before
    public void beforeEachTest() throws IOException {
        inProcessServerBuilder = InProcessServerBuilder.forName(uniqueServerName).directExecutor();
        inProcessChannel = InProcessChannelBuilder.forName(uniqueServerName).directExecutor().build();
        blockingStub = EventHandlerGrpc.newBlockingStub(inProcessChannel);
    }

    @After
    public void afterEachTest() {
        inProcessChannel.shutdownNow();
    }

    @Test
    public void interceptCall() throws IOException {
        // Create Service and interceptors
        TestService service = new TestService();

        AuthorisationAuAuServerInterceptor authorisationAuAuServerInterceptor = new AuthorisationAuAuServerInterceptor();

        ApiKeyRoleMapper apiKeyRoleMapper = new ApiKeyRoleMapperFromFile("src/test/resources/apikey_rolemapping");
        ApiKeyAuAuServerInterceptor apiKeyAuAuServerInterceptor = new ApiKeyAuAuServerInterceptor(apiKeyRoleMapper);

        IdTokenValidator idValidatorMock = mock(IdTokenValidator.class);
        UserRoleMapper roleMapperMock = mock(UserRoleMapper.class);
        when(idValidatorMock.verifyIdToken("token1"))
                .thenReturn(new JWTClaimsSet.Builder()
                        .claim("email", "user@example.com")
                        .claim("groups", new JSONArray())
                        .build());
        when(roleMapperMock.getRolesForUser(eq("user@example.com"), anyList(), anyCollection()))
                .thenAnswer((Answer<Collection<Role>>) invocation -> {
                    Collection<Role> roles = invocation.getArgument(2);
                    roles.add(Role.ANY_USER);
                    roles.add(Role.READONLY);
                    return roles;
                });

        IdTokenAuAuServerInterceptor idTokenAuAuServerInterceptor = new IdTokenAuAuServerInterceptor(roleMapperMock, idValidatorMock);

        // Test with only AuthorisationAuAuServerInterceptor
        Server inProcessServer = inProcessServerBuilder
                .addService(authorisationAuAuServerInterceptor.intercept(service))
                .build().start();

        assertThatExceptionOfType(StatusRuntimeException.class)
                .isThrownBy(() ->blockingStub.withCallCredentials(createCredentials("myApiKey", "token1")).countEventObjects(ListRequest.getDefaultInstance()))
        .withMessage("UNAUTHENTICATED");

        inProcessServer.shutdownNow();


        // Test with several interceptors
        inProcessServer = inProcessServerBuilder
                .addService(idTokenAuAuServerInterceptor.intercept(apiKeyAuAuServerInterceptor.intercept(authorisationAuAuServerInterceptor.intercept(service))))
                .build().start();

        blockingStub.withCallCredentials(createCredentials("myApiKey", "token1")).countEventObjects(ListRequest.getDefaultInstance());
        assertThat(service.lastRoles).containsExactlyInAnyOrder(Role.ANY, Role.ANY_USER, Role.READONLY, Role.SYSTEM);

        assertThatExceptionOfType(StatusRuntimeException.class)
                .isThrownBy(() ->blockingStub.withCallCredentials(createCredentials("wrongApiKey", "token1")).saveEventObject(SaveRequest.getDefaultInstance()))
                .withMessage("PERMISSION_DENIED");

        inProcessServer.shutdownNow();
    }

    @Test
    public void testWithClient() throws IOException {
        // Create Service and interceptors
        TestService service = new TestService();

        AuthorisationAuAuServerInterceptor authorisationAuAuServerInterceptor = new AuthorisationAuAuServerInterceptor();

        ApiKeyRoleMapper apiKeyRoleMapper = new ApiKeyRoleMapperFromFile("src/test/resources/apikey_rolemapping");
        ApiKeyAuAuServerInterceptor apiKeyAuAuServerInterceptor = new ApiKeyAuAuServerInterceptor(apiKeyRoleMapper);

        IdTokenValidator idValidatorMock = mock(IdTokenValidator.class);
        UserRoleMapper roleMapperMock = mock(UserRoleMapper.class);
        when(idValidatorMock.verifyIdToken("token1"))
                .thenReturn(new JWTClaimsSet.Builder()
                        .claim("email", "user@example.com")
                        .claim("groups", new JSONArray())
                        .build());
        when(roleMapperMock.getRolesForUser(eq("user@example.com"), anyList(), anyCollection()))
                .thenAnswer((Answer<Collection<Role>>) invocation -> {
                    Collection<Role> roles = invocation.getArgument(2);
                    roles.add(Role.ANY_USER);
                    roles.add(Role.READONLY);
                    return roles;
                });

        IdTokenAuAuServerInterceptor idTokenAuAuServerInterceptor = new IdTokenAuAuServerInterceptor(roleMapperMock, idValidatorMock);

        EventHandlerClient client = new EventHandlerClient(inProcessChannel, "myApiKey");

        Server inProcessServer = inProcessServerBuilder
                .addService(idTokenAuAuServerInterceptor.intercept(apiKeyAuAuServerInterceptor.intercept(authorisationAuAuServerInterceptor.intercept(service))))
                .build().start();

        EventObject eo = client.createEvent("foo", "system", Severity.INFO, "Create", Data.newBuilder().setKey("text").setValue("foo").build());
        assertThat(service.lastRoles).containsExactlyInAnyOrder(Role.ANY, Role.ANY_USER, Role.SYSTEM);

        inProcessServer.shutdownNow();
    }

    public class TestService extends EventHandlerGrpc.EventHandlerImplBase {
        Collection<Role> lastRoles;

        @Override
        @AllowedRoles({Role.ADMIN, Role.CURATOR, Role.READONLY})
        public void countEventObjects(ListRequest request, StreamObserver<ListCountResponse> responseObserver) {
            lastRoles = RolesContextKey.roles();
            responseObserver.onNext(ListCountResponse.newBuilder().setCount(5).build());
            responseObserver.onCompleted();
        }

        @Override
        @AllowedRoles({Role.ADMIN, Role.CURATOR, Role.SYSTEM})
        public void saveEventObject(SaveRequest request, StreamObserver<EventObject> responseObserver) {
            lastRoles = RolesContextKey.roles();
            responseObserver.onNext(request.getObject());
            responseObserver.onCompleted();
        }
    }

    private CallCredentials createCredentials(String apiKey, String bearerToken) {
        return new CallCredentials() {
            @Override
            public void applyRequestMetadata(MethodDescriptor<?, ?> method, Attributes attrs, Executor appExecutor, MetadataApplier applier) {
                Metadata headers = new Metadata();
                headers.put(AuAuServerInterceptor.AUTHORIZATION_KEY, "ApiKey " + apiKey);
                headers.put(AuAuServerInterceptor.AUTHORIZATION_KEY, "Bearer " + bearerToken);
                applier.apply(headers);
            }

            @Override
            public void thisUsesUnstableApi() {

            }
        };
    }
}