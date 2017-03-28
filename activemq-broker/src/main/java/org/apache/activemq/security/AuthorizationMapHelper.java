package org.apache.activemq.security;

import com.google.common.collect.Sets;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ConsumerInfo;

import java.util.Set;

/**
 * Created by mputilov on 18/03/17.
 */
public final class AuthorizationMapHelper {
    public static Set<?> getReadOrBrowseAllowedACLs(AuthorizationMap authorizationMap, ConsumerInfo info) {
        return getReadOrBrowseAllowedACLs(authorizationMap, info, info.getDestination());
    }

    public static Set<?> getReadOrBrowseAllowedACLs(AuthorizationMap authorizationMap,
                                                    ConsumerInfo info,
                                                    ActiveMQDestination destination) {
        Set<?> allowedACLs = destination.isTemporary() ?
                authorizationMap.getTempDestinationReadACLs() :
                authorizationMap.getReadACLs(destination);
        if (info.isBrowser()) {
            Set<?> browserAllowedACLs = destination.isTemporary() ?
                    authorizationMap.getTempDestinationBrowseACLs() :
                    authorizationMap.getBrowseACLs(destination);
            return Sets.union(allowedACLs, browserAllowedACLs); // fixme: переместить логику в authorization map???
        }
        return allowedACLs;
    }
}
