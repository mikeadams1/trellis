/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.dropwizard;

import static java.util.Collections.emptyList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.dropwizard.TrellisUtils.getAuthFilters;
import static org.trellisldp.dropwizard.TrellisUtils.getCorsConfiguration;
import static org.trellisldp.dropwizard.TrellisUtils.getWebacCache;

import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.trellisldp.dropwizard.config.BasicAuthConfiguration;
import org.trellisldp.dropwizard.config.JwtAuthConfiguration;
import org.trellisldp.dropwizard.config.TrellisConfiguration;
import org.trellisldp.http.AgentAuthorizationFilter;
import org.trellisldp.http.CacheControlFilter;
import org.trellisldp.http.CrossOriginResourceSharingFilter;
import org.trellisldp.http.TrellisHttpFilter;
import org.trellisldp.http.TrellisHttpResource;
import org.trellisldp.http.WebSubHeaderFilter;
import org.trellisldp.http.core.ServiceBundler;
import org.trellisldp.webac.WebAcFilter;
import org.trellisldp.webac.WebAcService;

/**
 * A base class for Dropwizard-based Trellis applications.
 */
public abstract class AbstractTrellisApplication<T extends TrellisConfiguration> extends Application<T> {

    private static final Logger LOGGER = getLogger(AbstractTrellisApplication.class);

    /** The configuration key controlling whether an application should initialize its own root resource. **/
    public static final String CONFIG_DROPWIZARD_INITIALIZE_ROOT = "trellis.dropwizard.initialize.root";

    /**
     * Get the Trellis {@link ServiceBundler}. This object collects the various
     * Trellis services used in an application.
     * @return the ServiceBundler
     */
    protected abstract ServiceBundler getServiceBundler();

    /**
     * Get any additional components to register with Jersey.
     *
     * @implSpec By default, this returns an empty list.
     * @return any additional components.
     */
    protected List<Object> getComponents() {
        return emptyList();
    }

    /**
     * Get the TrellisHttpResource matcher.
     *
     * @param config the configuration
     * @param initialize true if the TrellisHttpResource object should be initialized; false otherwise
     * @return the LDP resource matcher
     */
    protected Object getLdpComponent(final T config, final boolean initialize) {
        final TrellisHttpResource ldpResource = new TrellisHttpResource(getServiceBundler(), config.getBaseUrl());
        if (initialize) {
            ldpResource.initialize();
        }
        return ldpResource;
    }

    /**
     * Setup the trellis application.
     *
     * @apiNote This method is called at the very beginning of the {@link Application#run} method. It can be used
     *          to configure or register any of the Trellis-related services that an implementation instantiates.
     * @param config the configuration
     * @param environment the environment
     */
    protected void initialize(final T config, final Environment environment) {
        LOGGER.debug("Initializing Trellis application with {}", config.getClass());
    }

    @Override
    public String getName() {
        return "Trellis LDP";
    }

    @Override
    public void initialize(final Bootstrap<T> bootstrap) {
        // Allow configuration property substitution from environment variables
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                    new EnvironmentVariableSubstitutor(false)));
    }

    @Override
    public void run(final T config, final Environment environment) throws Exception {
        initialize(config, environment);

        getAuthFilters(config).forEach(environment.jersey()::register);

        // Resource matchers
        environment.jersey().register(getLdpComponent(config, ConfigProvider.getConfig()
                    .getOptionalValue(CONFIG_DROPWIZARD_INITIALIZE_ROOT, Boolean.class).orElse(Boolean.TRUE)));

        // Authentication
        final AgentAuthorizationFilter agentFilter = new AgentAuthorizationFilter(getServiceBundler().getAgentService(),
                new HashSet<>(config.getAuth().getAdminUsers()));

        // Filters
        environment.jersey().register(agentFilter);
        environment.jersey().register(new TrellisHttpFilter());
        environment.jersey().register(new CacheControlFilter(config.getCache().getMaxAge(),
                    config.getCache().getMustRevalidate(), config.getCache().getNoCache()));

        // Authorization
        getWebacCache(config).ifPresent(cache -> {
            final WebAcService webac = new WebAcService(getServiceBundler().getResourceService(), cache);
            final List<String> challenges = new ArrayList<>();
            of(config.getAuth().getJwt()).filter(JwtAuthConfiguration::getEnabled).map(x -> "Bearer")
                .ifPresent(challenges::add);
            of(config.getAuth().getBasic()).filter(BasicAuthConfiguration::getEnabled).map(x -> "Basic")
                .ifPresent(challenges::add);
            environment.jersey().register(new WebAcFilter(webac, challenges, config.getAuth().getRealm(),
                        config.getBaseUrl()));
        });

        // WebSub
        ofNullable(config.getHubUrl()).ifPresent(hub -> environment.jersey().register(new WebSubHeaderFilter(hub)));

        // CORS
        getCorsConfiguration(config).ifPresent(cors -> environment.jersey().register(
                new CrossOriginResourceSharingFilter(cors.getAllowOrigin(), cors.getAllowMethods(),
                    cors.getAllowHeaders(), cors.getExposeHeaders(), cors.getAllowCredentials(), cors.getMaxAge())));

        // Additional components
        getComponents().forEach(environment.jersey()::register);
    }
}
