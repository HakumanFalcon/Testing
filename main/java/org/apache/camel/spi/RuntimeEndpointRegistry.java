/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.StaticService;

/**
 * A registry which listen for runtime usage of {@link org.apache.camel.Endpoint} during routing in Camel.
 */
public interface RuntimeEndpointRegistry extends StaticService {

    /**
     * Whether gathering runtime usage is enabled or not.
     */
    boolean isEnabled();

    /**
     * Sets whether gathering runtime usage is enabled or not.
     */
    void setEnabled(boolean enabled);

    /**
     * Maximum number of endpoints to keep in the cache per route.
     * <p/>
     * The default value is <tt>1000</tt>
     */
    int getLimit();

    /**
     * Sets the maximum number of endpoints to keep in the cache per route.
     */
    void setLimit(int limit);

    /**
     * Clears the runtime usage gathered
     */
    void reset();

    /**
     * Number of endpoints currently in the cache.
     */
    int size();

    /**
     * Gets all the endpoint uris captured during runtime routing that are in-use of the routes.
     *
     * @param includeInputs whether to include route inputs
     */
    List<String> getAllEndpoints(boolean includeInputs);

    /**
     * Gets all the endpoint uris captured from the given route during runtime routing that are in-use of the routes.
     *
     * @param routeId       the route id
     * @param includeInputs whether to include route inputs
     */
    List<String> getEndpointsPerRoute(String routeId, boolean includeInputs);
}
