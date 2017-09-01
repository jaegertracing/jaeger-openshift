/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.openshift;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.arquillian.cube.kubernetes.annotations.Named;
import org.arquillian.cube.kubernetes.annotations.Port;
import org.arquillian.cube.kubernetes.annotations.PortForward;
import org.arquillian.cube.requirement.ArquillianConditionalRunner;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import brave.Tracing;
import io.fabric8.kubernetes.api.model.v2_2.Service;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.okhttp3.OkHttpSender;

/**
 * @author Pavol Loffay
 */
@RunWith(ArquillianConditionalRunner.class)
public class AllInOneETest {
    private static final String SERVICE_NAME = "jaeger-query";
    private static final String ZIPKIN_SERVICE_NAME = "zipkin";

    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .build();

    @Named(SERVICE_NAME)
    @ArquillianResource
    private Service jaegerService;

    @Named(SERVICE_NAME)
    @PortForward
    @ArquillianResource
    private URL jaegerUiUrl;

    @Port(9411)
    @Named(ZIPKIN_SERVICE_NAME)
    @PortForward
    @ArquillianResource
    private URL zipkinUrl;

    @Test
    public void testUiResponds() throws IOException, InterruptedException {
        Request request = new Request.Builder()
                .url(jaegerUiUrl)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            Assert.assertEquals(200, response.code());
        }
    }

    @Test
    public void testReportZipkinSpanToCollector() throws IOException, InterruptedException {
        Tracing tracing = createZipkinTracer("service2");
        tracing.tracer().newTrace().name("foo").start().finish();
        tracing.close();

        Request request = new Request.Builder()
            .url(jaegerUiUrl + "api/traces?service=service2")
            .get()
            .build();

        await().atMost(5, TimeUnit.SECONDS).until(() -> {
          Response response = okHttpClient.newCall(request).execute();
          String body = response.body().string();
          return body.contains("foo");
        });

        try (Response response = okHttpClient.newCall(request).execute()) {
          assertEquals(200, response.code());
          assertTrue(response.body().string().contains("foo"));
        }
    }

    protected Tracing createZipkinTracer(String serviceName) {
        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .reporter(AsyncReporter.builder(OkHttpSender.create(zipkinUrl + "api/v1/spans"))
                        .build()).build();
    }
}
