/*
 * Copyright 2011, Mahmood Ali.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Mahmood Ali. nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notnoop.c2dm.internal;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.notnoop.c2dm.*;
import com.notnoop.c2dm.exceptions.NetworkIOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;

public class GCMPooledService extends AbstractGCMService implements GCMService {

    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final GCMDelegate delegate;
    private final ResponseParser responseParser = new ResponseParser();

    public GCMPooledService(HttpClient httpClient, String serviceUri, String apiKey,
                            ExecutorService executor, GCMDelegate delegate) {
        super(serviceUri, apiKey);
        this.httpClient = httpClient;
        this.executor = executor;
        this.delegate = delegate;
    }

    @Override
    public void push(final GCMNotification message) {
        executor.execute(new Runnable() {
            public void run() {
                HttpResponse httpResponse = null;
                try {
                    httpResponse = httpClient.execute(postMessage(message));
                    if (delegate != null) {
                        GCMResponse cResponse = responseParser.parse(httpResponse);
                        GCMResponseStatus status = cResponse.getStatus();
                        if (status == GCMResponseStatus.SUCCESSFUL) {
                            delegate.messageSent(message, cResponse);
                        } else {
                            delegate.messageFailed(message, cResponse);
                        }
                    }
                } catch (IOException e) {
                    throw new NetworkIOException(e);
                } finally {
                    try {
                        if (httpResponse != null) EntityUtils.consume(httpResponse.getEntity());
                    } catch (IOException e) {
                        System.err.println("Unable close response " + e);
                    }
                }
            }
        });
    }

    @Override
    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        httpClient.getConnectionManager().shutdown();
    }

}