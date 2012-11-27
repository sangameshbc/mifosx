package org.mifosng.platform.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.mifosng.platform.api.commands.RoleCommand;
import org.mifosng.platform.api.commands.RolePermissionCommand;
import org.mifosng.platform.api.data.EntityIdentifier;
import org.mifosng.platform.api.data.PermissionUsageData;
import org.mifosng.platform.api.data.RoleData;
import org.mifosng.platform.api.data.RolePermissionsData;
import org.mifosng.platform.api.infrastructure.PortfolioApiJsonSerializerService;
import org.mifosng.platform.api.infrastructure.PortfolioCommandDeserializerService;
import org.mifosng.platform.infrastructure.api.ApiParameterHelper;
import org.mifosng.platform.security.PlatformSecurityContext;
import org.mifosng.platform.user.service.PermissionReadPlatformService;
import org.mifosng.platform.user.service.RoleReadPlatformService;
import org.mifosplatform.infrastructure.commands.api.data.CommandSourceData;
import org.mifosplatform.infrastructure.commands.service.PortfolioCommandSourceWritePlatformService;
import org.mifosplatform.infrastructure.commands.service.PortfolioCommandsReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/roles")
@Component
@Scope("singleton")
public class RolesApiResource {

    @Autowired
    private RoleReadPlatformService roleReadPlatformService;

    @Autowired
    private PermissionReadPlatformService permissionReadPlatformService;

    @Autowired
    private PortfolioApiJsonSerializerService apiJsonSerializerService;

    @Autowired
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    private PortfolioCommandsReadPlatformService commandSourceReadPlatformService;

    @Autowired
    private PortfolioCommandDeserializerService commandDeserializerService;

    @Autowired
    private PlatformSecurityContext context;

    private final String resourceNameForPermissions = "ROLE";

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveAllRoles(@Context final UriInfo uriInfo) {

        context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);

        final Set<String> responseParameters = ApiParameterHelper.extractFieldsForResponseIfProvided(uriInfo.getQueryParameters());
        final boolean prettyPrint = ApiParameterHelper.prettyPrint(uriInfo.getQueryParameters());

        final Collection<RoleData> roles = this.roleReadPlatformService.retrieveAllRoles();

        return this.apiJsonSerializerService.serializeRoleDataToJson(prettyPrint, responseParameters, roles);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String createRole(final String apiRequestBodyAsJson) {
        
        final List<String> allowedPermissions = Arrays.asList("ALL_FUNCTIONS", "USER_ADMINISTRATION_SUPER_USER", "CREATE_ROLE");
        context.authenticatedUser().validateHasPermissionTo("CREATE_ROLE", allowedPermissions);

        final EntityIdentifier result = this.commandsSourceWritePlatformService.logCommandSource("CREATE", "roles", null,
                apiRequestBodyAsJson);

        return this.apiJsonSerializerService.serializeEntityIdentifier(result);
    }

    @GET
    @Path("{roleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveRole(@PathParam("roleId") final Long roleId, @Context final UriInfo uriInfo) {

        context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);

        final Set<String> responseParameters = ApiParameterHelper.extractFieldsForResponseIfProvided(uriInfo.getQueryParameters());
        final boolean prettyPrint = ApiParameterHelper.prettyPrint(uriInfo.getQueryParameters());

        final Long commandId = ApiParameterHelper.commandId(uriInfo.getQueryParameters());

        RoleData role = this.roleReadPlatformService.retrieveRole(roleId);
        if (commandId != null) {
             RoleData currentChanges = handleRequestToIntegrateProposedChangesFromRoleCommand(roleId, commandId);
             
             role = RoleData.integrateChanges(role, currentChanges);
        }

        return this.apiJsonSerializerService.serializeRoleDataToJson(prettyPrint, responseParameters, role);
    }
    
    private RoleData handleRequestToIntegrateProposedChangesFromRoleCommand(final Long roleId, final Long commandId) {
        final CommandSourceData entry = this.commandSourceReadPlatformService.retrieveById(commandId);
        return assembleRoleChanges(roleId, entry);
    }

    private RoleData assembleRoleChanges(final Long roleId, final CommandSourceData entry) {
        final RoleCommand changesOnly = this.commandDeserializerService.deserializeRoleCommand(roleId, entry.json(), false);
        return RoleData.changes(changesOnly.getName(), changesOnly.getDescription());
    }

    @PUT
    @Path("{roleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateRole(@PathParam("roleId") final Long roleId, final String apiRequestBodyAsJson) {

        final List<String> allowedPermissions = Arrays.asList("ALL_FUNCTIONS", "USER_ADMINISTRATION_SUPER_USER", "UPDATE_ROLE");
        context.authenticatedUser().validateHasPermissionTo("UPDATE_ROLE", allowedPermissions);
        
        final EntityIdentifier result = this.commandsSourceWritePlatformService.logCommandSource("UPDATE", "roles", roleId,
                apiRequestBodyAsJson);

        return this.apiJsonSerializerService.serializeEntityIdentifier(result);
    }

    @GET
    @Path("{roleId}/permissions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveRolePermissions(@PathParam("roleId") final Long roleId, @Context final UriInfo uriInfo) {

        context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);

        final Set<String> responseParameters = ApiParameterHelper.extractFieldsForResponseIfProvided(uriInfo.getQueryParameters());
        final boolean prettyPrint = ApiParameterHelper.prettyPrint(uriInfo.getQueryParameters());
        final Long commandId = ApiParameterHelper.commandId(uriInfo.getQueryParameters());

        final RoleData role = this.roleReadPlatformService.retrieveRole(roleId);
        final Collection<PermissionUsageData> permissionUsageData = this.permissionReadPlatformService.retrieveAllRolePermissions(roleId);
        Collection<PermissionUsageData> currentChanges = null;
        if (commandId != null) {
            currentChanges = handleRequestToIntegrateProposedChangesFromCommand(roleId, commandId);
        }

        final RolePermissionsData permissionsData = role.toRolePermissionData(permissionUsageData, currentChanges);

        return this.apiJsonSerializerService.serializeRolePermissionDataToJson(prettyPrint, responseParameters, permissionsData);
    }

    private Collection<PermissionUsageData> handleRequestToIntegrateProposedChangesFromCommand(final Long roleId, final Long commandId) {
        final CommandSourceData entry = this.commandSourceReadPlatformService.retrieveById(commandId);
        return assemblePermissionChanges(roleId, entry);
    }

    private Collection<PermissionUsageData> assemblePermissionChanges(final Long roleId, final CommandSourceData entry) {
        final RolePermissionCommand changesOnly = this.commandDeserializerService.deserializeRolePermissionCommand(roleId, entry.json(),
                false);

        // assemble map of string/booleans into PermissionUsageData
        Collection<PermissionUsageData> proposedChanges = new ArrayList<PermissionUsageData>();
        for (final String permissionCode : changesOnly.getPermissions().keySet()) {
            final boolean isSelected = changesOnly.getPermissions().get(permissionCode).booleanValue();
            final PermissionUsageData item = PermissionUsageData.from(permissionCode, isSelected);

            proposedChanges.add(item);
        }

        return proposedChanges;
    }

    @PUT
    @Path("{roleId}/permissions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateRolePermissions(@PathParam("roleId") final Long roleId, final String apiRequestBodyAsJson) {

        final List<String> allowedPermissions = Arrays.asList("ALL_FUNCTIONS", "USER_ADMINISTRATION_SUPER_USER", "PERMISSIONS_ROLE");
        context.authenticatedUser().validateHasPermissionTo("PERMISSIONS_ROLE", allowedPermissions);
        
        final EntityIdentifier result = this.commandsSourceWritePlatformService.logCommandSource("UPDATEPERMISSIONS", "roles", roleId,
                apiRequestBodyAsJson);

        return this.apiJsonSerializerService.serializeEntityIdentifier(result);
    }
}