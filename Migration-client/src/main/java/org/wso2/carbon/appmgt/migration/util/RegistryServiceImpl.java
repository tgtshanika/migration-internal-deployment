/*
 *   Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.appmgt.migration.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.appmgt.api.APIProvider;
import org.wso2.carbon.appmgt.api.AppManagementException;
import org.wso2.carbon.appmgt.api.model.WebApp;
import org.wso2.carbon.appmgt.impl.APIManagerFactory;
import org.wso2.carbon.appmgt.impl.AppMConstants;
import org.wso2.carbon.appmgt.impl.service.ServiceReferenceHolder;
import org.wso2.carbon.appmgt.impl.utils.AppManagerUtil;
import org.wso2.carbon.appmgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.ActionConstants;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.realm.RegistryAuthorizationManager;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;


public class RegistryServiceImpl implements RegistryService {
    private static final Log log = LogFactory.getLog(RegistryServiceImpl.class);
    private Tenant tenant = null;
    private APIProvider apiProvider = null;

    @Override
    public void startTenantFlow(Tenant tenant) {
        if (this.tenant != null) {
            throw new IllegalStateException("Previous tenant flow has not been ended, " +
                    "'RegistryService.endTenantFlow()' needs to be called");
        }
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenant.getDomain(), true);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenant.getId(), true);
        this.tenant = tenant;
    }

    @Override
    public void endTenantFlow() {
        PrivilegedCarbonContext.endTenantFlow();
        this.tenant = null;
        this.apiProvider = null;
    }

    @Override
    public void rollbackGovernanceRegistryTransaction() throws UserStoreException, RegistryException {
        getGovernanceRegistry().rollbackTransaction();
    }

    @Override
    public void rollbackConfigRegistryTransaction() throws UserStoreException, RegistryException {
        getConfigRegistry().rollbackTransaction();
    }

    @Override
    public void addDefaultLifecycles() throws RegistryException, UserStoreException, FileNotFoundException, XMLStreamException {
        // CommonU.addDefaultLifecyclesIfNotAvailable(getConfigRegistry(), CommonUtil.getRootSystemRegistry(tenant.getId()));
    }

    @Override
    public GenericArtifact[] getGenericArtifacts(String artifactType) {

        GenericArtifact[] artifacts = {};

        try {
            Registry registry = getGovernanceRegistry();
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, artifactType);

            if (artifactManager != null) {
                artifacts = artifactManager.getAllGenericArtifacts();
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No " + artifactType + " artifacts found in registry for tenant " + tenant.getDomain()
                            + '(' + tenant.getId() + ')');
                }
            }
        } catch (RegistryException e) {
            log.error("Error occurred when retrieving " + artifactType + " artifacts from registry for tenant "
                    + tenant.getDomain(), e);
        } catch (UserStoreException e) {
            log.error("Error occurred while obtaining information regarding tenant " + tenant.getDomain()
                    + "(" + tenant.getId() + ")", e);
        } catch (AppManagementException e) {
            log.error("Failed to initialize" + artifactType + " GenericArtifactManager for tenant " + tenant.getDomain() +
                    '(' + tenant.getId() + ')', e);
        }
        return artifacts;
    }

    @Override
    public void updateGenericArtifacts(String artifactType, GenericArtifact[] artifacts) {

        try {
            Registry registry = getGovernanceRegistry();
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, artifactType);

            for (GenericArtifact artifact : artifacts) {
                artifactManager.updateGenericArtifact(artifact);
            }

        } catch (UserStoreException e) {
            log.error("Error occurred while obtaining information regarding tenant " + tenant.getDomain()
                    + "(" + tenant.getId() + ")", e);
        } catch (RegistryException e) {
            log.error("Error occurred when updating " + artifactType + " artifacts in registry for tenant " +
                    tenant.getDomain(), e);
        } catch (AppManagementException e) {
            log.error("Failed to initialize" + artifactType + " GenericArtifactManager for tenant " + tenant.getDomain() +
                    '(' + tenant.getId() + ')', e);
        }
    }

    @Override
    public WebApp getAPI(GenericArtifact artifact) {
        log.debug("Calling getAPI");
        WebApp api = null;

        try {
            api = AppManagerUtil.getAPI(artifact);
        } catch (AppManagementException e) {
            log.error("Error when getting api artifact " + artifact.getId() + " from registry", e);
        }

        return api;
    }

    @Override
    public String getGenericArtifactPath(GenericArtifact artifact) throws UserStoreException, RegistryException {
        return GovernanceUtils.getArtifactPath(getGovernanceRegistry(), artifact.getId());
    }

    @Override
    public boolean isConfigRegistryResourceExists(String registryLocation) throws UserStoreException, RegistryException {
        return getConfigRegistry().resourceExists(registryLocation);
    }

    @Override
    public boolean isGovernanceRegistryResourceExists(String registryLocation) throws UserStoreException, RegistryException {
        return getGovernanceRegistry().resourceExists(registryLocation);
    }

    @Override
    public Object getConfigRegistryResource(final String registryLocation) throws UserStoreException, RegistryException {
        Object content = null;

        Registry registry = getConfigRegistry();

        if (registry.resourceExists(registryLocation)) {
            Resource resource = registry.get(registryLocation);
            content = resource.getContent();
        }

        return content;
    }

    @Override
    public Object getGovernanceRegistryResource(final String registryLocation) throws UserStoreException, RegistryException {
        Object content = null;

        Registry registry = getGovernanceRegistry();

        if (registry.resourceExists(registryLocation)) {
            Resource resource = registry.get(registryLocation);
            content = resource.getContent();
        }

        return content;
    }

    @Override
    public void addConfigRegistryResource(final String registryLocation, final String content,
                                          final String mediaType) throws UserStoreException, RegistryException {
        Registry registry = getConfigRegistry();

        Resource resource = registry.newResource();
        resource.setContent(content);
        resource.setMediaType(mediaType);
        registry.put(registryLocation, resource);
    }

    @Override
    public void addGovernanceRegistryResource(final String registryLocation, final String content,
                                              final String mediaType) throws UserStoreException, RegistryException {
        Registry registry = getGovernanceRegistry();

        Resource resource = registry.newResource();
        resource.setContent(content);
        resource.setMediaType(mediaType);
        registry.put(registryLocation, resource);
    }

    @Override
    public void updateConfigRegistryResource(final String registryLocation, final String content)
            throws UserStoreException, RegistryException {
        Registry registry = getConfigRegistry();
        Resource resource = registry.get(registryLocation);
        resource.setContent(content);
        registry.put(registryLocation, resource);
    }


    @Override
    public void updateGovernanceRegistryResource(final String registryLocation, final String content)
            throws UserStoreException, RegistryException {
        Registry registry = getGovernanceRegistry();

        Resource resource = registry.get(registryLocation);
        resource.setContent(content);
        registry.put(registryLocation, resource);
    }

    @Override
    public void setGovernanceRegistryResourcePermissions(String visibility, String[] roles,
                                                         String resourcePath) throws AppManagementException {
        initAPIProvider();
        AppManagerUtil.setResourcePermissions(tenant.getAdminName(), visibility, roles, resourcePath);
    }

    private void initAPIProvider() throws AppManagementException {
        if (apiProvider == null) {
            apiProvider = APIManagerFactory.getInstance().getAPIProvider(tenant.getAdminName());
        }
    }


    private Registry getConfigRegistry() throws UserStoreException, RegistryException {
        if (tenant == null) {
            throw new IllegalStateException("The tenant flow has not been started, " +
                    "'RegistryService.startTenantFlow(Tenant tenant)' needs to be called");
        }

        String adminName = ServiceHolder.getRealmService().getTenantUserRealm(tenant.getId()).
                getRealmConfiguration().getAdminUserName();
        log.debug("Tenant admin username : " + adminName);
        ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());
        return ServiceHolder.getRegistryService().getConfigUserRegistry(adminName, tenant.getId());
    }


    public Registry getGovernanceRegistry() throws UserStoreException, RegistryException {
        if (tenant == null) {
            throw new IllegalStateException("The tenant flow has not been started, " +
                    "'RegistryService.startTenantFlow(Tenant tenant)' needs to be called");
        }

        String adminName = ServiceHolder.getRealmService().getTenantUserRealm(tenant.getId()).
                getRealmConfiguration().getAdminUserName();
        log.debug("Tenant admin username : " + adminName);
        ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());
        return ServiceHolder.getRegistryService().getGovernanceUserRegistry(adminName, tenant.getId());
    }

    /* 
     * Update the RXT file in the registry 
     * 
     */
    @Override
    public void updateRXTResource(String rxtName, final String rxtPayload) throws UserStoreException, RegistryException {
        if (tenant == null) {
            throw new IllegalStateException("The tenant flow has not been started, "
                    + "'RegistryService.startTenantFlow(Tenant tenant)' needs to be called");
        }
        ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());

        Registry registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(tenant.getId());

        //Update RXT resource
        String resourcePath = Constants.RXT_REG_PATH + RegistryConstants.PATH_SEPARATOR + rxtName + ".rxt";

        String govRelativePath = RegistryUtils.getRelativePathToOriginal(resourcePath,
                AppManagerUtil.getMountedPath(RegistryContext.getBaseInstance(),
                        RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH));
        // calculate resource path
        RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager(
                ServiceReferenceHolder.getUserRealm());
        resourcePath = authorizationManager.computePathOnMount(resourcePath);
        org.wso2.carbon.user.api.AuthorizationManager authManager = ServiceReferenceHolder.getInstance()
                .getRealmService()
                .getTenantUserRealm(tenant.getId())
                .getAuthorizationManager();

        if (registry.resourceExists(govRelativePath)) {
            Resource resource = registry.get(govRelativePath);
            resource.setContent(rxtPayload.getBytes(Charset.defaultCharset()));
            resource.setMediaType(AppMConstants.RXT_MEDIA_TYPE);
            registry.put(govRelativePath, resource);
            authManager.authorizeRole(AppMConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
        }

        //Update RXT UI Configuration
        Registry configRegistry = ServiceHolder.getRegistryService().getConfigSystemRegistry(tenant.getId());
        String rxtUIConfigPath = Constants.GOVERNANCE_ARTIFACT_CONFIGURATION_PATH + rxtName;
        if (configRegistry.resourceExists(rxtUIConfigPath)) {
            Resource rxtUIResource = configRegistry.get(rxtUIConfigPath);
            rxtUIResource.setContent(ResourceUtil.getArtifactUIContentFromConfig(rxtPayload));
            configRegistry.put(rxtUIConfigPath, rxtUIResource);
        }

    }
}
